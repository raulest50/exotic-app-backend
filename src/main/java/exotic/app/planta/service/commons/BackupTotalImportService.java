package exotic.app.planta.service.commons;

import exotic.app.planta.config.AppTime;
import exotic.app.planta.model.commons.dto.importacion.BackupTotalImportJobResponseDTO;
import exotic.app.planta.model.users.User;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import javax.sql.DataSource;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class BackupTotalImportService {

    private static final Duration JOB_TTL = Duration.ofMinutes(15);
    private static final long PG_RESTORE_TIMEOUT_MINUTES = 15;
    private static final DateTimeFormatter FILE_TS_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final Pattern PG_TOOL_MAJOR_VERSION_PATTERN = Pattern.compile("(\\d+)(?:\\.\\d+)?");
    private static final String OPERATION_NAME = "La importacion total de base de datos";

    private final DataSource dataSource;
    private final String datasourceUrl;
    private final String datasourceUsername;
    private final String datasourcePassword;
    private final PgDumpExecutableResolver pgDumpExecutableResolver;
    private final DangerousOperationGuard dangerousOperationGuard;
    private final DatabasePurgeService databasePurgeService;

    private final ConcurrentMap<String, ImportJob> jobsById = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> activeJobByUsername = new ConcurrentHashMap<>();
    private final AtomicReference<String> activeGlobalJobId = new AtomicReference<>();
    private final ExecutorService importExecutor;

    private Path importDir;

    public BackupTotalImportService(
            DataSource dataSource,
            @Value("${spring.datasource.url}") String datasourceUrl,
            @Value("${spring.datasource.username}") String datasourceUsername,
            @Value("${spring.datasource.password}") String datasourcePassword,
            PgDumpExecutableResolver pgDumpExecutableResolver,
            DangerousOperationGuard dangerousOperationGuard,
            DatabasePurgeService databasePurgeService
    ) {
        this.dataSource = dataSource;
        this.datasourceUrl = datasourceUrl;
        this.datasourceUsername = datasourceUsername;
        this.datasourcePassword = datasourcePassword;
        this.pgDumpExecutableResolver = pgDumpExecutableResolver;
        this.dangerousOperationGuard = dangerousOperationGuard;
        this.databasePurgeService = databasePurgeService;
        this.importExecutor = Executors.newSingleThreadExecutor(new ImportThreadFactory());
    }

    @PostConstruct
    void init() {
        this.importDir = Path.of(System.getProperty("java.io.tmpdir"), "exotic-backup-imports");
        try {
            Files.createDirectories(importDir);
        } catch (IOException e) {
            throw new IllegalStateException("No fue posible crear el directorio temporal de importaciones", e);
        }
    }

    @PreDestroy
    void shutdown() {
        importExecutor.shutdownNow();
        cleanupExpiredJobsInternal(true);
    }

    public BackupTotalImportJobResponseDTO createJob(User user, MultipartFile file) {
        dangerousOperationGuard.assertLocalOrStagingOnly(OPERATION_NAME);
        cleanupExpiredJobsInternal(false);

        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Debe seleccionar un archivo .dump para continuar.");
        }

        String originalFilename = sanitizeForFilename(file.getOriginalFilename());
        if (!originalFilename.toLowerCase().endsWith(".dump")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La importacion total solo acepta archivos .dump.");
        }

        String existingGlobalJobId = activeGlobalJobId.get();
        if (existingGlobalJobId != null) {
            ImportJob existingGlobalJob = jobsById.get(existingGlobalJobId);
            if (existingGlobalJob != null && existingGlobalJob.isActive()) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Ya existe una importacion total en curso para esta base de datos."
                );
            }
            activeGlobalJobId.compareAndSet(existingGlobalJobId, null);
        }

        String activeJobId = activeJobByUsername.get(user.getUsername());
        if (activeJobId != null) {
            ImportJob existingJob = jobsById.get(activeJobId);
            if (existingJob != null && existingJob.isActive()) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Ya existe una importacion total en curso para este usuario."
                );
            }
            activeJobByUsername.remove(user.getUsername(), activeJobId);
        }

        String jobId = UUID.randomUUID().toString();
        LocalDateTime requestedAt = AppTime.now();
        String dbName = sanitizeForFilename(extractDbName(datasourceUrl));
        String filename = "importacion_total_%s_%s_%s".formatted(
                dbName,
                FILE_TS_FORMAT.format(requestedAt),
                originalFilename
        );
        Path dumpFilePath = importDir.resolve(filename);

        try {
            file.transferTo(dumpFilePath);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "No fue posible almacenar temporalmente el archivo de importacion.");
        }

        if (!activeGlobalJobId.compareAndSet(null, jobId)) {
            deleteFileQuietly(dumpFilePath);
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Ya existe una importacion total en curso para esta base de datos."
            );
        }

        String previousUserJobId = activeJobByUsername.putIfAbsent(user.getUsername(), jobId);
        if (previousUserJobId != null) {
            activeGlobalJobId.compareAndSet(jobId, null);
            deleteFileQuietly(dumpFilePath);
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Ya existe una importacion total en curso para este usuario."
            );
        }

        ImportJob job = new ImportJob(jobId, user.getUsername(), filename, dumpFilePath, requestedAt);
        jobsById.put(jobId, job);

        importExecutor.submit(() -> runImport(job));
        return toDto(job);
    }

    public BackupTotalImportJobResponseDTO getJob(String ownerUsername, String jobId) {
        dangerousOperationGuard.assertLocalOrStagingOnly(OPERATION_NAME);
        ImportJob job = getOwnedJob(ownerUsername, jobId);
        expireJobIfNeeded(job);
        return toDto(job);
    }

    public void deleteJob(String ownerUsername, String jobId) {
        dangerousOperationGuard.assertLocalOrStagingOnly(OPERATION_NAME);
        ImportJob job = getOwnedJob(ownerUsername, jobId);
        if (job.isActive()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No se puede eliminar una importacion total que aun esta en proceso.");
        }
        jobsById.remove(jobId);
        activeJobByUsername.remove(ownerUsername, jobId);
        activeGlobalJobId.compareAndSet(jobId, null);
        deleteFileQuietly(job.dumpFilePath);
    }

    @Scheduled(fixedDelay = 300000, initialDelay = 300000)
    public void cleanupExpiredJobs() {
        cleanupExpiredJobsInternal(false);
    }

    private void runImport(ImportJob job) {
        LocalDateTime startedAt = AppTime.now();
        job.startedAt = startedAt;

        try {
            dangerousOperationGuard.assertLocalOrStagingOnly(OPERATION_NAME);

            job.status = JobStatus.VALIDANDO;
            job.message = "Validando archivo y herramientas de restauracion...";

            if (!Files.exists(job.dumpFilePath) || Files.size(job.dumpFilePath) <= 0) {
                markJobError(job, "IMPORT_FILE_MISSING", "El archivo de importacion no esta disponible o esta vacio.", null);
                return;
            }

            String pgRestoreExecutable = pgDumpExecutableResolver.resolveRestoreExecutable();
            PgRestoreRuntimeValidation validation = validatePgRestoreRuntime(pgRestoreExecutable);
            if (!validation.compatible()) {
                markJobError(job, validation.errorCode(), validation.message(), null);
                return;
            }

            job.status = JobStatus.PURGANDO;
            job.message = "Vaciando completamente el esquema actual antes de restaurar el backup...";
            databasePurgeService.resetCurrentSchemaForFullRestore();

            job.status = JobStatus.RESTAURANDO;
            job.message = "Restaurando backup total PostgreSQL. Esto puede tardar varios minutos...";

            JdbcConnectionInfo connectionInfo = extractConnectionInfo(datasourceUrl);
            List<String> command = new ArrayList<>();
            command.add(pgRestoreExecutable);
            command.add("--verbose");
            command.add("--no-password");
            command.add("--clean");
            command.add("--if-exists");
            command.add("--no-owner");
            command.add("--no-privileges");
            command.add("--single-transaction");
            command.add("--exit-on-error");
            command.add("--host=" + connectionInfo.host());
            command.add("--port=" + connectionInfo.port());
            command.add("--username=" + datasourceUsername);
            command.add("--dbname=" + connectionInfo.dbName());
            command.add(job.dumpFilePath.toAbsolutePath().toString());

            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.redirectErrorStream(true);
            if (datasourcePassword != null && !datasourcePassword.isBlank()) {
                processBuilder.environment().put("PGPASSWORD", datasourcePassword);
            }

            log.warn("Iniciando importacion total PostgreSQL. jobId={}, user={}, file={}",
                    job.jobId, job.ownerUsername, job.dumpFilePath.toAbsolutePath());

            Process process = processBuilder.start();
            StreamCapture streamCapture = new StreamCapture(process.getInputStream());
            Thread captureThread = new Thread(streamCapture, "pg-restore-output-" + job.jobId);
            captureThread.setDaemon(true);
            captureThread.start();

            boolean finished = process.waitFor(PG_RESTORE_TIMEOUT_MINUTES, TimeUnit.MINUTES);
            captureThread.join(TimeUnit.SECONDS.toMillis(2));
            String processOutput = streamCapture.output();

            if (!finished) {
                process.destroyForcibly();
                markJobError(job, "PG_RESTORE_TIMEOUT", "La restauracion total tardo demasiado y fue cancelada.", processOutput);
                return;
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                markJobError(job, "PG_RESTORE_FAILED", "No fue posible restaurar completamente la base de datos.", processOutput);
                return;
            }

            job.status = JobStatus.LISTO;
            job.finishedAt = AppTime.now();
            job.expiresAt = job.finishedAt.plus(JOB_TTL);
            job.message = "La importacion total de la base de datos finalizo correctamente.";

            log.warn("Importacion total PostgreSQL completada. jobId={}, user={}, durationSeconds={}",
                    job.jobId,
                    job.ownerUsername,
                    Duration.between(startedAt, job.finishedAt).toSeconds());
        } catch (PgDumpExecutableResolver.PgDumpResolutionException e) {
            markJobError(job, e.getErrorCode(), e.getMessage(), null);
        } catch (UnsupportedOperationException e) {
            markJobError(job, "IMPORT_NOT_AVAILABLE", e.getMessage(), null);
        } catch (IOException e) {
            markJobError(job, "PG_RESTORE_EXECUTION_ERROR", "No fue posible ejecutar pg_restore en el servidor.", e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            markJobError(job, "PG_RESTORE_INTERRUPTED", "La restauracion total fue interrumpida.", e.getMessage());
        } catch (IllegalArgumentException e) {
            markJobError(job, "IMPORT_CONFIGURATION_INVALID", e.getMessage(), null);
        } catch (Exception e) {
            markJobError(job, "IMPORT_UNKNOWN_ERROR", "Ocurrio un error inesperado durante la importacion total.", e.getMessage());
        } finally {
            activeJobByUsername.remove(job.ownerUsername, job.jobId);
            activeGlobalJobId.compareAndSet(job.jobId, null);
            deleteFileQuietly(job.dumpFilePath);
        }
    }

    private PgRestoreRuntimeValidation validatePgRestoreRuntime(String pgRestoreExecutable) {
        String versionOutput;
        try {
            versionOutput = executeCommandAndCaptureOutput(List.of(pgRestoreExecutable, "--version"));
        } catch (IOException e) {
            log.error("pg_restore no esta disponible en el runtime: {}", e.getMessage());
            return new PgRestoreRuntimeValidation(false, "PG_RESTORE_NOT_FOUND_IN_RUNTIME",
                    "El servidor no tiene instalado pg_restore. Contacte al administrador del sistema.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new PgRestoreRuntimeValidation(false, "PG_RESTORE_INTERRUPTED",
                    "La validacion interna de pg_restore fue interrumpida.");
        }

        Integer pgRestoreMajorVersion = parsePgToolMajorVersion(versionOutput);
        if (pgRestoreMajorVersion == null) {
            log.error("No fue posible interpretar la version de pg_restore: {}", versionOutput);
            return new PgRestoreRuntimeValidation(false, "PG_RESTORE_VERSION_UNKNOWN",
                    "No fue posible verificar la version de pg_restore instalada en el servidor.");
        }

        int serverMajorVersion;
        try (var connection = dataSource.getConnection()) {
            serverMajorVersion = connection.getMetaData().getDatabaseMajorVersion();
        } catch (Exception e) {
            log.error("No fue posible consultar la version de PostgreSQL del servidor", e);
            return new PgRestoreRuntimeValidation(false, "DB_SERVER_VERSION_UNAVAILABLE",
                    "No fue posible verificar la compatibilidad de la restauracion con la base de datos.");
        }

        if (pgRestoreMajorVersion < serverMajorVersion) {
            String message = "La version de pg_restore del servidor no es compatible con la version actual de PostgreSQL.";
            log.error("Incompatibilidad pg_restore/server. pgRestoreMajorVersion={}, serverMajorVersion={}",
                    pgRestoreMajorVersion, serverMajorVersion);
            return new PgRestoreRuntimeValidation(false, "PG_RESTORE_VERSION_INCOMPATIBLE", message);
        }

        return new PgRestoreRuntimeValidation(true, null, null);
    }

    private String executeCommandAndCaptureOutput(List<String> command) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .start();

        String output;
        try (InputStream processInput = process.getInputStream()) {
            output = new String(processInput.readAllBytes(), StandardCharsets.UTF_8).trim();
        }

        boolean finished = process.waitFor(1, TimeUnit.MINUTES);
        if (!finished) {
            process.destroyForcibly();
            throw new IOException("El comando tardo demasiado en responder: " + command.get(0));
        }

        if (process.exitValue() != 0) {
            throw new IOException("El comando devolvio codigo " + process.exitValue() + ": " + output);
        }
        return output;
    }

    private Integer parsePgToolMajorVersion(String versionOutput) {
        Matcher matcher = PG_TOOL_MAJOR_VERSION_PATTERN.matcher(versionOutput);
        if (!matcher.find()) {
            return null;
        }
        return Integer.parseInt(matcher.group(1));
    }

    private ImportJob getOwnedJob(String ownerUsername, String jobId) {
        ImportJob job = jobsById.get(jobId);
        if (job == null || !job.ownerUsername.equals(ownerUsername)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No se encontro una importacion total con ese identificador.");
        }
        return job;
    }

    private void markJobError(ImportJob job, String errorCode, String message, String technicalDetails) {
        job.status = JobStatus.ERROR;
        job.errorCode = errorCode;
        job.message = message;
        job.finishedAt = AppTime.now();
        job.expiresAt = job.finishedAt.plus(JOB_TTL);

        if (technicalDetails == null || technicalDetails.isBlank()) {
            log.error("Importacion total PostgreSQL fallo. jobId={}, user={}, errorCode={}, message={}",
                    job.jobId, job.ownerUsername, errorCode, message);
        } else {
            log.error("Importacion total PostgreSQL fallo. jobId={}, user={}, errorCode={}, message={}, details={}",
                    job.jobId, job.ownerUsername, errorCode, message, technicalDetails);
        }
    }

    private void cleanupExpiredJobsInternal(boolean forceDeleteAllTerminalJobs) {
        LocalDateTime now = AppTime.now();
        for (ImportJob job : jobsById.values()) {
            if (job.isActive()) {
                continue;
            }

            boolean shouldDelete = forceDeleteAllTerminalJobs
                    || (job.expiresAt != null && !now.isBefore(job.expiresAt));

            if (!shouldDelete) {
                continue;
            }

            deleteFileQuietly(job.dumpFilePath);
            if (!forceDeleteAllTerminalJobs) {
                job.status = JobStatus.EXPIRADO;
                job.message = "El resultado de la importacion expiro y fue limpiado del servidor.";
            }
            jobsById.remove(job.jobId);
        }
    }

    private void expireJobIfNeeded(ImportJob job) {
        if (job.isActive() || job.expiresAt == null) {
            return;
        }
        if (AppTime.now().isBefore(job.expiresAt)) {
            return;
        }
        deleteFileQuietly(job.dumpFilePath);
        job.status = JobStatus.EXPIRADO;
        job.message = "El resultado de la importacion expiro y fue limpiado del servidor.";
        jobsById.remove(job.jobId);
    }

    private void deleteFileQuietly(Path filePath) {
        try {
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            log.warn("No fue posible eliminar archivo temporal de importacion: {}", filePath, e);
        }
    }

    private BackupTotalImportJobResponseDTO toDto(ImportJob job) {
        return new BackupTotalImportJobResponseDTO(
                job.jobId,
                job.status.name(),
                job.filename,
                job.requestedAt,
                job.startedAt,
                job.finishedAt,
                job.expiresAt,
                job.errorCode,
                job.message
        );
    }

    private String extractDbName(String jdbcUrl) {
        return extractConnectionInfo(jdbcUrl).dbName();
    }

    private JdbcConnectionInfo extractConnectionInfo(String jdbcUrl) {
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            throw new IllegalArgumentException("La configuracion del datasource no define una URL JDBC valida.");
        }

        String uriCandidate = jdbcUrl.startsWith("jdbc:") ? jdbcUrl.substring(5) : jdbcUrl;
        try {
            URI uri = new URI(uriCandidate);
            String host = uri.getHost();
            int port = uri.getPort() > 0 ? uri.getPort() : 5432;
            String path = uri.getPath();
            String dbName = path != null && path.length() > 1 ? path.substring(1) : null;

            if (host == null || host.isBlank() || dbName == null || dbName.isBlank()) {
                throw new IllegalArgumentException("No fue posible interpretar host y nombre de base de datos desde la URL JDBC actual.");
            }

            return new JdbcConnectionInfo(host, port, dbName);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("La URL JDBC configurada no tiene un formato compatible para importacion total.");
        }
    }

    private String sanitizeForFilename(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return "backup_total.dump";
        }
        return rawValue.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private record JdbcConnectionInfo(String host, int port, String dbName) {
    }

    private record PgRestoreRuntimeValidation(boolean compatible, String errorCode, String message) {
    }

    private enum JobStatus {
        PENDIENTE,
        VALIDANDO,
        PURGANDO,
        RESTAURANDO,
        LISTO,
        ERROR,
        EXPIRADO
    }

    private static final class ImportJob {
        private final String jobId;
        private final String ownerUsername;
        private final String filename;
        private final Path dumpFilePath;
        private final LocalDateTime requestedAt;

        private volatile JobStatus status = JobStatus.PENDIENTE;
        private volatile LocalDateTime startedAt;
        private volatile LocalDateTime finishedAt;
        private volatile LocalDateTime expiresAt;
        private volatile String errorCode;
        private volatile String message;

        private ImportJob(String jobId, String ownerUsername, String filename, Path dumpFilePath, LocalDateTime requestedAt) {
            this.jobId = jobId;
            this.ownerUsername = ownerUsername;
            this.filename = filename;
            this.dumpFilePath = dumpFilePath;
            this.requestedAt = requestedAt;
        }

        private boolean isActive() {
            return status == JobStatus.PENDIENTE
                    || status == JobStatus.VALIDANDO
                    || status == JobStatus.PURGANDO
                    || status == JobStatus.RESTAURANDO;
        }
    }

    private static final class ImportThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "backup-total-import");
            thread.setDaemon(true);
            return thread;
        }
    }

    private static final class StreamCapture implements Runnable {
        private final InputStream inputStream;
        private final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        private StreamCapture(InputStream inputStream) {
            this.inputStream = inputStream;
        }

        @Override
        public void run() {
            try (InputStream in = inputStream; ByteArrayOutputStream out = outputStream) {
                in.transferTo(out);
            } catch (IOException ignored) {
                // Si el proceso termina abruptamente, el output parcial sigue siendo suficiente.
            }
        }

        private String output() {
            return outputStream.toString(StandardCharsets.UTF_8);
        }
    }
}
