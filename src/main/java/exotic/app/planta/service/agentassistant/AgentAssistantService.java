package exotic.app.planta.service.agentassistant;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import exotic.app.planta.model.users.ModuloSistema;
import exotic.app.planta.model.users.User;
import exotic.app.planta.model.users.UserAccessEvaluator;
import exotic.app.planta.repo.agentassistant.AgentAssistantQueryRepository;
import exotic.app.planta.repo.agentassistant.AgentAssistantQueryRepository.QueryResult;
import exotic.app.planta.service.commons.BackupTotalExportService;
import exotic.app.planta.service.commons.DatabaseImportMaintenanceService;
import exotic.app.planta.service.users.AgentAssistantAccessService;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Owns authorization, proposals and execution. The provider never receives database tools. */
@Service
@RequiredArgsConstructor
public class AgentAssistantService {
    public static final String READ_ONLY_MESSAGE = "Las operaciones de escritura todavía no están soportadas. Puedo ayudarte con consultas de solo lectura.";
    public static final String OUT_OF_SCOPE_MESSAGE = AgentAssistantContext.OUT_OF_SCOPE_MESSAGE;
    private static final Duration PROPOSAL_TTL = Duration.ofMinutes(10);
    private static final Duration OPERATION_TTL = Duration.ofMinutes(20);
    private final AgentAssistantAccessService access;
    private final Optional<AssistantAiGateway> gateway;
    private final AgentAssistantQueryRepository queries;
    private final BackupTotalExportService backups;
    private final ObjectMapper mapper;
    private final AgentAssistantContext context;
    private final DatabaseImportMaintenanceService maintenance;
    private final Map<String, Proposal> proposals = new ConcurrentHashMap<>();
    private final Map<String, RunningOperation> operations = new ConcurrentHashMap<>();
    private volatile long connectionEpoch;
    private volatile String connectionId = UUID.randomUUID().toString();
    private String providerConnectionId;
    private final Set<Long> generating = ConcurrentHashMap.newKeySet();
    private final Semaphore generationSlots = new Semaphore(2, true);
    private final Semaphore inferenceTurn = new Semaphore(1, true);
    private final ThreadPoolExecutor workers = new ThreadPoolExecutor(2, 2, 0, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(4), runnable -> {
                Thread thread = new Thread(runnable, "agent-assistant-read");
                thread.setDaemon(true);
                return thread;
            });

    public AssistantStatus status(Authentication authentication) {
        access.requireRead(authentication);
        boolean enabled = gateway.map(AssistantAiGateway::enabled).orElse(false);
        boolean available = false;
        if (enabled) {
            try { available = gateway.orElseThrow().available(); }
            catch (ResponseStatusException ex) { /* Provider details are not part of the public status. */ }
        }
        synchronizeConnection();
        AssistantAiGateway.Usage usage = new AssistantAiGateway.Usage(false, null);
        try { usage = gateway.map(AssistantAiGateway::usage).orElse(usage); }
        catch (ResponseStatusException ex) { available = false; }
        String message = available ? "El servicio de Asistente Exotic está disponible."
                : "El asistente no está disponible en este momento. Inténtalo más tarde.";
        if (usage.configured() && usage.remainingPercent() != null && usage.remainingPercent() <= 0)
            message = "Se agotó el cupo asignado al asistente. Contacta al administrador.";
        return new AssistantStatus(enabled, available, message, true, connectionId, usage);
    }

    private synchronized void invalidateConnection() {
        connectionEpoch++;
        connectionId = UUID.randomUUID().toString();
        proposals.clear();
        operations.values().forEach(RunningOperation::cancel);
    }

    private synchronized void synchronizeConnection() {
        String current = gateway.map(AssistantAiGateway::connectionId).orElse(null);
        if (!java.util.Objects.equals(providerConnectionId, current)) {
            if (providerConnectionId != null) invalidateConnection();
            providerConnectionId = current;
        }
    }

    private long connectionEpoch() { synchronizeConnection(); return connectionEpoch; }

    public ChatResponse chat(Authentication authentication, ChatRequest request) {
        User actor = requireCatalog(authentication);
        requireEnabled();
        if (request == null || request.message() == null || request.message().isBlank() || request.message().length() > 2000)
            throw error(HttpStatus.BAD_REQUEST, "Escribe una pregunta de hasta 2000 caracteres.");
        List<AssistantAiGateway.Turn> turns = validateHistory(request.history());
        String message = request.message().trim();
        cleanup();
        if (!generating.add(actor.getId())) throw error(HttpStatus.TOO_MANY_REQUESTS, "Ya hay una pregunta en proceso para este usuario.");
        if (!generationSlots.tryAcquire()) {
            generating.remove(actor.getId());
            throw error(HttpStatus.TOO_MANY_REQUESTS, "El asistente está ocupado. Intenta de nuevo en unos instantes.");
        }
        boolean acquiredTurn = false;
        try {
            acquiredTurn = inferenceTurn.tryAcquire(30, TimeUnit.SECONDS);
            if (!acquiredTurn) throw error(HttpStatus.TOO_MANY_REQUESTS, "El asistente sigue ocupado. Conserva tu pregunta e intenta de nuevo.");
            requireCatalog(authentication); // Access can change while this user waits in the queue.
            requireEnabled();
            long epoch = connectionEpoch();
            turns.add(new AssistantAiGateway.Turn("user", message));
            String response = generate(turns);
            requireCatalog(authentication); // Permission changes while waiting for OpenAI take effect here.
            if (epoch != connectionEpoch()) throw error(HttpStatus.CONFLICT, "El servicio cambió durante la respuesta. Solicita una nueva propuesta.");
            JsonNode answer;
            try (JsonParser parser = mapper.createParser(response)) {
                answer = mapper.readTree(parser);
                if (answer == null || !answer.isObject() || parser.nextToken() != null) throw new IllegalArgumentException();
            } catch (Exception ex) { throw error(HttpStatus.BAD_GATEWAY, "La IA devolvió una propuesta con formato no válido. No se ejecutó ninguna consulta."); }
            String kind = answer.path("kind").asText();
            if (kind.equals("out_of_scope")) return new ChatResponse(OUT_OF_SCOPE_MESSAGE, null);
            if (kind.equals("unsupported_write")) return new ChatResponse(READ_ONLY_MESSAGE, null);
            String explanation = answer.path("message").asText("");
            if (explanation.isBlank() || explanation.length() > 4000)
                throw error(HttpStatus.BAD_GATEWAY, "La respuesta de la IA no es válida. No se ejecutó ninguna consulta.");
            if (kind.equals("answer")) return new ChatResponse(explanation, null);
            if (!kind.equals("query")) throw error(HttpStatus.BAD_GATEWAY, "La IA devolvió una operación no admitida.");
            ReadOnlyAssistantSql.CompiledQuery plan;
            try { plan = ReadOnlyAssistantSql.compile(answer.path("sql").asText()); }
            catch (IllegalArgumentException ex) {
                return new ChatResponse(READ_ONLY_MESSAGE + " La propuesta de la IA no cumple los límites permitidos y no se ejecutó. Puedes reformular la pregunta sobre productos o materiales.", null);
            }
            if (proposals.size() >= 128 || proposals.values().stream().filter(p -> p.owner.equals(actor.getId())).count() >= 8)
                throw error(HttpStatus.TOO_MANY_REQUESTS, "Hay demasiadas propuestas pendientes. Espera a que expiren antes de continuar.");
            String id = UUID.randomUUID().toString();
            Instant expires = Instant.now().plus(PROPOSAL_TTL);
            synchronized (this) {
                if (epoch != connectionEpoch())
                    throw error(HttpStatus.CONFLICT, "La conexión cambió durante la respuesta. Solicita una nueva propuesta.");
                proposals.put(id, new Proposal(actor.getId(), plan, expires, epoch));
            }
            return new ChatResponse(explanation, new ProposalView(id, plan.preview(), expires));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw error(HttpStatus.SERVICE_UNAVAILABLE, "La espera del asistente fue interrumpida. Puedes reintentar tu pregunta.");
        } finally {
            if (acquiredTurn) inferenceTurn.release();
            generationSlots.release();
            generating.remove(actor.getId());
        }
    }

    public synchronized Operation execute(Authentication authentication, String proposalId) {
        User actor = requireCatalog(authentication);
        requireEnabled();
        synchronizeConnection();
        requireBackupPermission(actor);
        cleanup();
        RunningOperation existing = operations.get(proposalId);
        if (existing != null && existing.owner.equals(actor.getId())) return existing.view();
        Proposal proposal = proposals.get(proposalId);
        if (proposal == null || !proposal.owner.equals(actor.getId()) || !Instant.now().isBefore(proposal.expires)
                || proposal.epoch != connectionEpoch())
            throw error(HttpStatus.NOT_FOUND, "La propuesta no existe o expiró. Solicita una nueva.");
        if (operations.size() >= 128 || operations.values().stream().anyMatch(op -> op.owner.equals(actor.getId()) && op.pending()))
            throw error(HttpStatus.TOO_MANY_REQUESTS, "Ya existe una consulta en curso o se alcanzó el límite temporal de operaciones.");
        RunningOperation operation = new RunningOperation(proposalId, actor.getId(), proposal.plan.preview());
        operations.put(proposalId, operation);
        try { workers.execute(() -> run(authentication, proposal, operation)); }
        catch (java.util.concurrent.RejectedExecutionException ex) {
            operations.remove(proposalId, operation);
            throw error(HttpStatus.TOO_MANY_REQUESTS, "El asistente está ocupado. Intenta de nuevo después.");
        }
        proposals.remove(proposalId, proposal);
        return operation.view();
    }

    public Operation operation(Authentication authentication, String operationId) {
        Long owner = requireCatalog(authentication).getId();
        requireEnabled();
        synchronizeConnection();
        cleanup();
        RunningOperation operation = operations.get(operationId);
        if (operation == null || !operation.owner.equals(owner)) throw error(HttpStatus.NOT_FOUND, "No se encontró la operación.");
        return operation.view();
    }

    private void run(Authentication authentication, Proposal proposal, RunningOperation operation) {
        try (var permit = maintenance.tryEnterWork()) {
            if (permit == null) {
                operation.fail("La base esta en mantenimiento por importacion total. Solicita una nueva consulta cuando finalice.");
                return;
            }
            if (!operation.pending() || proposal.epoch != connectionEpoch()) return;
            User actor = requireCatalog(authentication);
            requireEnabled();
            requireBackupPermission(actor);
            var backup = backups.createJob(actor);
            operation.setBackup(backup.jobId());
            Instant deadline = Instant.now().plusSeconds(150);
            while (!backup.estado().equals("LISTO")) {
                if (!operation.pending() || Thread.currentThread().isInterrupted()) return;
                if (Set.of("ERROR", "EXPIRADO").contains(backup.estado()) || !Instant.now().isBefore(deadline)) {
                    operation.fail("No se completó el respaldo previo. No se ejecutó la consulta.");
                    return;
                }
                Thread.sleep(500);
                backup = backups.getJob(actor, backup.jobId());
            }
            if (!operation.pending() || proposal.epoch != connectionEpoch()) return;
            // LISTO alone is insufficient if the file disappeared or is empty.
            var payload = backups.getDownloadPayload(actor, backup.jobId());
            try (InputStream file = payload.resource().getInputStream()) {
                if (payload.contentLength() <= 0 || file.read() == -1) {
                    operation.fail("El respaldo no está disponible. No se ejecutó la consulta.");
                    return;
                }
            }
            User current = requireCatalog(authentication);
            requireBackupPermission(current);
            requireEnabled();
            if (!Instant.now().isBefore(proposal.expires)) {
                operation.fail("La propuesta expiró antes de ejecutar. Solicita una nueva consulta.");
                return;
            }
            if (!operation.pending() || proposal.epoch != connectionEpoch()) return;
            QueryResult result = queries.execute(proposal.plan);
            requireCatalog(authentication); // Revoked access also prevents returning collected rows.
            if (operation.pending() && proposal.epoch == connectionEpoch()) operation.complete(result);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            operation.fail("La operación fue interrumpida. No hay un resultado disponible.");
        } catch (Exception ex) {
            operation.fail("No se pudo completar el respaldo o la consulta de lectura. No se realizó ninguna escritura en la base de datos.");
        }
    }

    private User requireCatalog(Authentication authentication) {
        User user = access.requireRead(authentication);
        if (!master(user)) {
            int level = UserAccessEvaluator.tabNivel(user, ModuloSistema.PRODUCTOS, "CONSULTA_PRODUCTOS")
                    .orElseGet(() -> UserAccessEvaluator.tabNivel(user, ModuloSistema.PRODUCTOS, "MAIN").orElse(0));
            if (level < 1) throw error(HttpStatus.FORBIDDEN, "Necesitas permiso de consulta de Productos para este catálogo del asistente.");
        }
        return user;
    }

    private void requireBackupPermission(User user) {
        if (!master(user) && UserAccessEvaluator.tabNivel(user, ModuloSistema.OPERACIONES_CRITICAS_BD, "EXPORTACION_DATOS").orElse(0) < 1)
            throw new BackupPermissionRequired();
    }

    private static boolean master(User user) {
        return "master".equalsIgnoreCase(user.getUsername()) || "super_master".equalsIgnoreCase(user.getUsername());
    }

    private void requireEnabled() {
        if (!gateway.map(AssistantAiGateway::enabled).orElse(false))
            throw error(HttpStatus.SERVICE_UNAVAILABLE, "El asistente no tiene un proveedor de IA habilitado.");
    }

    private String generate(List<AssistantAiGateway.Turn> turns) {
        try {
            return gateway.orElseThrow(() -> error(HttpStatus.SERVICE_UNAVAILABLE,
                    "El asistente no tiene un proveedor de IA habilitado.")).generate(instructions(), turns);
        } catch (ResponseStatusException ex) {
            // Provider diagnostics remain outside the public API.
            if (ex.getStatusCode().value() == 429)
                throw error(HttpStatus.TOO_MANY_REQUESTS, "El servicio del asistente está ocupado o alcanzó un límite temporal. Intenta más tarde.");
            throw error(HttpStatus.SERVICE_UNAVAILABLE, "El servicio de IA no está disponible. Intenta más tarde.");
        }
    }

    private List<AssistantAiGateway.Turn> validateHistory(List<AssistantAiGateway.Turn> history) {
        if (history == null) return new ArrayList<>();
        if (history.size() > 10) throw error(HttpStatus.BAD_REQUEST, "El historial enviado es demasiado largo.");
        int length = 0;
        for (var turn : history) {
            if (turn == null || turn.role() == null || !Set.of("user", "assistant").contains(turn.role()) || turn.content() == null || turn.content().length() > 4000)
                throw error(HttpStatus.BAD_REQUEST, "El historial contiene mensajes no admitidos.");
            length += turn.content().length();
        }
        if (length > 14000) throw error(HttpStatus.BAD_REQUEST, "El historial enviado es demasiado largo.");
        return new ArrayList<>(history);
    }

    private void cleanup() {
        Instant now = Instant.now();
        proposals.values().removeIf(p -> !now.isBefore(p.expires));
        operations.values().removeIf(op -> !op.pending() && !now.isBefore(op.created.plus(OPERATION_TTL)));
    }

    private String instructions() {
        return context.instructions();
    }

    private static ResponseStatusException error(HttpStatus status, String message) { return new ResponseStatusException(status, message); }

    @PreDestroy
    void shutdown() {
        operations.values().forEach(RunningOperation::cancel);
        workers.shutdownNow();
        proposals.clear();
        operations.clear();
    }

    public record ChatRequest(String message, List<AssistantAiGateway.Turn> history) { }
    public record ChatResponse(String message, ProposalView proposal) { }
    public record ProposalView(String id, String sql, Instant expiresAt) { }
    public record AssistantStatus(boolean enabled, boolean available, String message, boolean readOnly, String connectionId,
                                  AssistantAiGateway.Usage usage) { }
    public record Operation(String id, String state, String message, String sql, QueryResult result, String backupId) { }
    public static final class BackupPermissionRequired extends ResponseStatusException {
        public BackupPermissionRequired() {
            super(HttpStatus.FORBIDDEN, "La ejecución requiere el permiso independiente de respaldo y exportación de datos. El permiso del asistente no lo concede.");
        }
    }
    private record Proposal(Long owner, ReadOnlyAssistantSql.CompiledQuery plan, Instant expires, long epoch) { }

    private static final class RunningOperation {
        private final String id;
        private final Long owner;
        private final String sql;
        private final Instant created = Instant.now();
        private String state = "BACKING_UP";
        private String message = "Generando respaldo previo; la consulta todavía no se ejecutó.";
        private String backupId;
        private QueryResult result;
        private RunningOperation(String id, Long owner, String sql) { this.id = id; this.owner = owner; this.sql = sql; }
        private synchronized boolean pending() { return state.equals("BACKING_UP"); }
        private synchronized void setBackup(String id) { backupId = id; }
        private synchronized void complete(QueryResult value) {
            if (!pending()) return;
            result = value;
            message = value.truncated() ? "Consulta completada. El resultado está limitado o truncado. El respaldo previo es temporal (15 minutos)."
                    : "Consulta de lectura completada. El respaldo previo es temporal (15 minutos).";
            state = "COMPLETE";
        }
        private synchronized void fail(String reason) { result = null; message = reason; state = "FAILED"; }
        private synchronized void cancel() { fail("La operación fue cancelada y su resultado se descartó."); }
        private synchronized Operation view() { return new Operation(id, state, message, sql, result, backupId); }
    }
}
