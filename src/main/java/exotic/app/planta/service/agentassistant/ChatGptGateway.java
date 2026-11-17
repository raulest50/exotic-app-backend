package exotic.app.planta.service.agentassistant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import exotic.app.planta.config.runtime.ApplicationRuntimeEnvironmentResolver;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.BufferedReader;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Local SIWC client with one app-owned account, independent of Exotic users.
 * The private profile is exclusively locked across backend and development CLI.
 * Conversation text stays in request memory and is never written to this profile.
 *
 * Protocol: https://developers.openai.com/siwc/token-sharing-open-source/sign-in
 * and models-and-inference / profiles-and-sessions / preview-limitations.
 */
@Service
public class ChatGptGateway {
    private static final String ISSUER = "https://auth.openai.com";
    private static final String AUTHORIZE = ISSUER + "/api/accounts/authorize";
    private static final String TOKEN = ISSUER + "/api/accounts/oauth/token";
    private static final String RESOURCE = "https://api.openai.com/v1";
    private static final String DIRECT_SCOPE = "chatgpt.tokens.use.direct";
    private static final String SCOPES = "openid profile email offline_access resource.invoke " + DIRECT_SCOPE;
    private static final String NEW_CLIENT = "dynamic_agent_client";
    private static final Duration AUTH_TTL = Duration.ofMinutes(10);
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration INFERENCE_TIMEOUT = Duration.ofSeconds(120);
    // Internal slot only; never an application user or database identifier.
    private static final Long ACCOUNT_SLOT = 1L;
    private static final int MAX_ACCOUNTS = 1;
    private static final int MAX_PENDING = 1;
    private static final int MAX_JSON_BYTES = 1_048_576;
    private static final int MAX_STREAM_BYTES = 8_388_608;
    private static final int MAX_OUTPUT_CHARS = 524_288;

    private final ObjectMapper mapper;
    private final ApplicationRuntimeEnvironmentResolver runtime;
    private final boolean configuredEnabled;
    private final ProfileOpener profileOpener;
    private AssistantCredentialStore store;
    private ObjectNode profile;
    private volatile boolean closed;
    private volatile String connectionId = UUID.randomUUID().toString();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER).build();
    private final SecureRandom random = new SecureRandom();
    private final Map<Long, Session> sessions = new ConcurrentHashMap<>();
    private final Map<Long, Pending> pending = new HashMap<>();
    private final Map<Long, String> notices = new ConcurrentHashMap<>();
    private final Semaphore requests = new Semaphore(2);
    private final ScheduledThreadPoolExecutor timer = scheduler("siwc-http-watchdogs");
    // OAuth expiry can wait for the gateway monitor. HTTP watchdogs must never wait behind it.
    private final ScheduledThreadPoolExecutor authorizationExpirations = scheduler("siwc-auth-expirations");
    private final ThreadPoolExecutor callbacks = new ThreadPoolExecutor(2, 2, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(8), r -> daemon(r, "siwc-callback"), new ThreadPoolExecutor.AbortPolicy());
    private Metadata metadata;
    private volatile JsonNode discovery;
    private volatile JsonNode jwks;
    private volatile Instant discoveryExpires = Instant.EPOCH;
    private volatile Instant jwksExpires = Instant.EPOCH;

    @Autowired
    public ChatGptGateway(ObjectMapper mapper, ApplicationRuntimeEnvironmentResolver runtime,
                          @Value("${app.agent-assistant.enabled:false}") boolean configuredEnabled) {
        this(mapper, runtime, configuredEnabled, () -> new AssistantCredentialStore(AssistantCredentialStore.defaultDirectory(), mapper));
    }

    ChatGptGateway(ObjectMapper mapper, ApplicationRuntimeEnvironmentResolver runtime,
                   boolean configuredEnabled, ProfileOpener profileOpener) {
        this.mapper = mapper;
        this.runtime = runtime;
        this.configuredEnabled = configuredEnabled;
        this.profileOpener = profileOpener;
    }

    public boolean enabled() {
        return !closed && configuredEnabled && runtime.isLocal();
    }

    public String connectionId() { return connectionId; }

    public synchronized Status status() {
        Long ownerId = ACCOUNT_SLOT;
        if (!enabled()) return new Status(false, false, null, "El prototipo ChatGPT está deshabilitado o el entorno no es local.");
        metadata();
        Session session = sessions.get(ownerId);
        boolean connected = session != null && !session.closed && session.direct;
        String message = notices.get(ownerId);
        if (message == null) message = connected
                ? "Cuenta conectada con perfil privado persistente."
                : "Ejecute la herramienta de desarrollo para conectar la cuenta del propietario.";
        return new Status(true, connected, session == null ? null : session.label, message);
    }

    public synchronized AuthStart connect() {
        Long ownerId = ACCOUNT_SLOT;
        requireEnabled(ownerId);
        Metadata saved = metadata();
        if (!saved.registrations.containsKey(ownerId) && saved.registrations.size() >= MAX_ACCOUNTS) {
            throw failure(HttpStatus.TOO_MANY_REQUESTS, "Se alcanzó el límite local de cuentas ChatGPT registradas.");
        }
        if (!pending.containsKey(ownerId) && pending.size() >= MAX_PENDING) {
            throw failure(HttpStatus.TOO_MANY_REQUESTS, "Hay demasiadas conexiones ChatGPT pendientes. Intente más tarde.");
        }
        // Resolve trusted metadata before opening the listener; an unavailable issuer leaves no pending socket.
        discovery();
        stopPending(ownerId);
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 4);
            String redirect = "http://127.0.0.1:" + server.getAddress().getPort() + "/auth/callback";
            Registration registration = saved.registrations.get(ownerId);
            Pending attempt = new Pending(ownerId, randomValue(32), randomValue(32), randomValue(64), redirect,
                    registration, Instant.now().plus(AUTH_TTL), server);
            server.createContext("/auth/callback", exchange -> callback(exchange, attempt));
            server.setExecutor(callbacks);
            pending.put(ownerId, attempt);
            server.start();
            attempt.expiration = authorizationExpirations.schedule(() -> expire(attempt), AUTH_TTL.toSeconds(), TimeUnit.SECONDS);
            Map<String, String> query = new LinkedHashMap<>();
            query.put("client_id", registration == null ? NEW_CLIENT : registration.clientId);
            if (registration == null) query.put("agent_name_hint", "Exotic");
            query.put("ext_agent_host_id", saved.hostId);
            query.put("response_type", "code");
            query.put("redirect_uri", redirect);
            query.put("scope", SCOPES);
            query.put("resource", RESOURCE);
            query.put("state", attempt.state);
            query.put("nonce", attempt.nonce);
            query.put("code_challenge_method", "S256");
            query.put("code_challenge", Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(MessageDigest.getInstance("SHA-256").digest(attempt.verifier.getBytes(StandardCharsets.US_ASCII))));
            Session previous = sessions.get(ownerId);
            if (previous != null && !previous.direct) query.put("prompt", "consent");
            notice(ownerId, "Complete la conexión en la ventana de ChatGPT; el intento vence en 10 minutos.");
            return new AuthStart(AUTHORIZE + "?" + form(query));
        } catch (Exception exception) {
            stopPending(ownerId);
            throw failure(HttpStatus.SERVICE_UNAVAILABLE, "No fue posible iniciar el callback OAuth local.");
        }
    }

    public void disconnect() {
        Long ownerId = ACCOUNT_SLOT;
        requireEnabled(ownerId);
        metadata();
        Session session;
        boolean exchangingCode;
        boolean hadStoredSession;
        synchronized (this) {
            Pending authorization = pending.get(ownerId);
            exchangingCode = authorization != null && authorization.consumed;
            stopPending(ownerId);
            hadStoredSession = profile.has("session");
            clearStoredSession();
            session = sessions.remove(ownerId);
            connectionId = UUID.randomUUID().toString();
            if (session != null) {
                session.closed = true;
                close(session.stream);
            }
        }
        boolean confirmed = session == null && !exchangingCode && !hadStoredSession;
        if (session != null) {
            boolean locked = false;
            try {
                locked = session.lock.tryLock(5, TimeUnit.SECONDS);
                if (locked && session.tokens != null) confirmed = revoke(session.tokens.refreshToken, session.clientId);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } finally {
                if (locked) {
                    session.tokens = null;
                    session.lock.unlock();
                } else {
                    // A rotating refresh might still be finishing. Revoke its latest
                    // result under the same lock, instead of racing the old token.
                    try {
                        callbacks.execute(() -> finishDisconnect(session));
                    } catch (java.util.concurrent.RejectedExecutionException exception) {
                        session.tokens = null;
                    }
                }
            }
        }
        notice(ownerId, confirmed ? "Cuenta desconectada de Exotic."
                : "Desconectado localmente; no se confirmó la revocación remota. Puede desconectar Exotic en los ajustes de ChatGPT.");
    }

    public List<ModelChoice> models() {
        Long ownerId = ACCOUNT_SLOT;
        Session session = beginRequest(ownerId);
        try {
            return models(session);
        } finally {
            endRequest(session);
        }
    }

    public String generate(String model, String instructions, List<Turn> turns) {
        Long ownerId = ACCOUNT_SLOT;
        requireEnabled(ownerId);
        if (model == null || model.isBlank() || model.length() > 256 || instructions == null
                || instructions.length() > 40_000 || turns == null || turns.isEmpty() || turns.size() > 60) {
            throw failure(HttpStatus.BAD_REQUEST, "Modelo, instrucciones o historial no válidos.");
        }
        int size = instructions.length();
        ArrayNode input = mapper.createArrayNode();
        for (Turn turn : turns) {
            if (turn == null || !("user".equals(turn.role) || "assistant".equals(turn.role))
                    || turn.content == null || turn.content.isBlank()) {
                throw failure(HttpStatus.BAD_REQUEST, "El historial admite únicamente mensajes de usuario y asistente con texto.");
            }
            size += turn.content.length();
            if (size > 200_000) throw failure(HttpStatus.PAYLOAD_TOO_LARGE, "El contexto de ChatGPT supera el límite local.");
            input.addObject().put("role", turn.role).put("content", turn.content);
        }
        Session session = beginRequest(ownerId);
        try {
            if (models(session).stream().noneMatch(choice -> choice.id.equals(model))) {
                throw failure(HttpStatus.BAD_REQUEST, "Seleccione un modelo del catálogo vigente de su cuenta ChatGPT.");
            }
            ObjectNode body = mapper.createObjectNode();
            body.put("model", model).put("instructions", instructions).put("store", false).put("stream", true);
            body.set("input", input);
            String access = accessToken(session);
            HttpRequest request = HttpRequest.newBuilder(URI.create(RESOURCE + "/responses"))
                    .timeout(INFERENCE_TIMEOUT).header("Authorization", "Bearer " + access)
                    .header("Content-Type", "application/json").header("Accept", "text/event-stream")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(mapper.writeValueAsBytes(body))).build();
            long deadline = System.nanoTime() + INFERENCE_TIMEOUT.toNanos();
            HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
            session.stream = response.body();
            var watchdog = timer.schedule(() -> close(response.body()),
                    Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
            try (InputStream stream = response.body()) {
                ensureCurrent(session);
                if (response.statusCode() != 200) {
                    throw upstream(response.statusCode(), parse(readLimited(stream, MAX_JSON_BYTES)), response);
                }
                String result = readInferenceBody(response.headers().firstValue("content-type").orElse(""), stream);
                ensureCurrent(session);
                return result;
            } finally {
                watchdog.cancel(false);
                session.stream = null;
            }
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw failure(HttpStatus.SERVICE_UNAVAILABLE, "La solicitud ChatGPT fue interrumpida; no se usará una respuesta parcial.");
        } catch (Exception exception) {
            throw failure(HttpStatus.BAD_GATEWAY, "No se completó la respuesta ChatGPT dentro de los límites locales; no se usará contenido parcial.");
        } finally {
            endRequest(session);
        }
    }

    private Session beginRequest(Long ownerId) {
        requireEnabled(ownerId);
        metadata();
        Session session = sessions.get(ownerId);
        if (session == null || session.closed || !session.direct) {
            throw failure(HttpStatus.CONFLICT, "Conecte ChatGPT y autorice el uso de su plan antes de continuar.");
        }
        if (!session.lock.tryLock()) throw failure(HttpStatus.CONFLICT, "Ya hay una solicitud ChatGPT en curso para este usuario.");
        if (!requests.tryAcquire()) {
            session.lock.unlock();
            throw failure(HttpStatus.TOO_MANY_REQUESTS, "Se alcanzó el límite de solicitudes ChatGPT simultáneas.");
        }
        try {
            ensureCurrent(session);
            return session;
        } catch (RuntimeException exception) {
            endRequest(session);
            throw exception;
        }
    }

    private void endRequest(Session session) {
        requests.release();
        session.lock.unlock();
    }

    private void finishDisconnect(Session session) {
        session.lock.lock();
        try {
            boolean confirmed = session.tokens != null && revoke(session.tokens.refreshToken, session.clientId);
            session.tokens = null;
            if (confirmed && !sessions.containsKey(session.ownerId)) notice(session.ownerId, "Cuenta desconectada de Exotic.");
        } finally {
            session.lock.unlock();
        }
    }

    private void ensureCurrent(Session session) {
        if (session.closed || sessions.get(session.ownerId) != session || !enabled()) {
            throw failure(HttpStatus.CONFLICT, "La cuenta ChatGPT se desconectó durante la solicitud.");
        }
    }

    private List<ModelChoice> models(Session session) {
        ensureCurrent(session);
        if (Instant.now().isBefore(session.modelsExpire)) return session.models;
        JsonReply reply = requestJson(HttpRequest.newBuilder(URI.create(RESOURCE + "/models"))
                .timeout(HTTP_TIMEOUT).header("Authorization", "Bearer " + accessToken(session))
                .header("Accept", "application/json").GET().build());
        if (reply.status != 200) throw upstream(reply);
        JsonNode catalog = reply.body.path("models");
        if (!catalog.isArray() || catalog.size() > 500) throw failure(HttpStatus.BAD_GATEWAY, "El catálogo ChatGPT tiene un formato inesperado.");
        Map<String, ModelChoice> choices = new LinkedHashMap<>();
        for (JsonNode item : catalog) {
            String id = item.path("slug").asText("");
            String name = item.path("display_name").asText("");
            if ("list".equals(item.path("visibility").asText()) && !id.isBlank() && id.length() <= 256
                    && !name.isBlank() && name.length() <= 256) choices.putIfAbsent(id, new ModelChoice(id, name));
        }
        ensureCurrent(session);
        session.models = List.copyOf(choices.values());
        session.modelsExpire = Instant.now().plusSeconds(60);
        return session.models;
    }

    // The caller holds this session's lock, including across rotating refresh-token replacement.
    private String accessToken(Session session) {
        ensureCurrent(session);
        TokenSet tokens = session.tokens;
        if (tokens == null) throw failure(HttpStatus.CONFLICT, "Vuelva a conectar ChatGPT.");
        if (tokens.expiresAt.isAfter(Instant.now().plusSeconds(60))) return tokens.accessToken;
        // Persist before contacting the token endpoint. An uncertain rotation must
        // never cause another process to retry the possibly spent old token.
        saveSession(session, tokens, true);
        try {
            JsonReply reply = postForm(TOKEN, Map.of("grant_type", "refresh_token", "client_id", session.clientId,
                    "refresh_token", tokens.refreshToken, "resource", RESOURCE), HTTP_TIMEOUT);
            if (reply.status != 200) throw upstream(reply);
            TokenSet replacement = tokens(reply.body, tokens.scopes);
            if (!replacement.idToken.isEmpty()) validateIdentity(replacement.idToken, session.clientId, null, session.subject);
            else replacement = new TokenSet(replacement.accessToken, replacement.refreshToken, replacement.expiresAt,
                    replacement.scopes, tokens.idToken);
            ensureCurrent(session);
            saveSession(session, replacement, false);
            session.tokens = replacement;
            session.direct = replacement.scopes.contains(DIRECT_SCOPE);
            if (!session.direct) throw failure(HttpStatus.FORBIDDEN, "La sesión renovada no autoriza el uso del plan ChatGPT.");
            return replacement.accessToken;
        } catch (RuntimeException ex) {
            session.closed = true;
            session.tokens = null;
            sessions.remove(session.ownerId, session);
            connectionId = UUID.randomUUID().toString();
            notice(session.ownerId, "La renovación no pudo completarse de forma segura. Reconecte desde desarrollo.");
            throw failure(HttpStatus.SERVICE_UNAVAILABLE, "Se necesita renovar la conexión desde desarrollo.");
        }
    }

    private void callback(HttpExchange exchange, Pending attempt) throws IOException {
        boolean consumed = false;
        TokenSet acquired = null;
        String acquiredClient = null;
        boolean installed = false;
        try {
            String host = exchange.getRequestHeaders().getFirst("Host");
            if (!"GET".equals(exchange.getRequestMethod()) || !"/auth/callback".equals(exchange.getRequestURI().getPath())
                    || !exchange.getRemoteAddress().getAddress().isLoopbackAddress()
                    || !URI.create(attempt.redirect).getAuthority().equals(host)) {
                callbackPage(exchange, 400, "Callback OAuth local no válido.");
                return;
            }
            Map<String, String> params = query(exchange.getRequestURI().getRawQuery());
            synchronized (this) {
                if (pending.get(attempt.ownerId) != attempt || attempt.consumed
                        || Instant.now().isAfter(attempt.expiresAt) || !enabled()
                        || !constantEquals(attempt.state, params.get("state"))) {
                    throw failure(HttpStatus.BAD_REQUEST, "El intento OAuth venció o no pudo verificarse. Vuelva a conectar desde Exotic.");
                }
                attempt.consumed = true;
                consumed = true;
            }
            if (params.containsKey("error")) throw failure(HttpStatus.BAD_REQUEST, "La conexión ChatGPT no fue autorizada. Puede intentarlo de nuevo desde Exotic.");
            String code = required(params.get("code"), 4096);
            String clientId = attempt.registration == null ? required(params.get("client_id"), 256) : attempt.registration.clientId;
            if (!validClient(clientId) || NEW_CLIENT.equals(clientId)
                    || (params.containsKey("client_id") && !clientId.equals(params.get("client_id")))) {
                throw failure(HttpStatus.BAD_REQUEST, "El registro OAuth no devolvió el cliente esperado.");
            }
            synchronized (this) {
                Metadata saved = metadata();
                if (closed) throw failure(HttpStatus.CONFLICT, "La conexión fue cancelada.");
                Map<Long, Registration> registrations = new LinkedHashMap<>(saved.registrations);
                registrations.put(attempt.ownerId, new Registration(clientId,
                        attempt.registration == null ? null : attempt.registration.subject));
                saveMetadata(new Metadata(saved.hostId, registrations));
            }
            JsonReply reply = postForm(TOKEN, Map.of("grant_type", "authorization_code", "client_id", clientId,
                    "code", code, "code_verifier", attempt.verifier, "redirect_uri", attempt.redirect, "resource", RESOURCE), HTTP_TIMEOUT);
            if (reply.status != 200) throw upstream(reply);
            TokenSet tokens = tokens(reply.body, Set.of());
            acquired = tokens;
            acquiredClient = clientId;
            String idToken = required(reply.body.path("id_token").asText(), 32_768);
            Claims identity = validateIdentity(idToken, clientId, attempt.nonce,
                    attempt.registration == null ? null : attempt.registration.subject);
            String subject = identity.getSubject();
            String email = identity.get("email") instanceof String text ? text : "ChatGPT";
            if (email.length() > 254 || email.chars().anyMatch(Character::isISOControl)) email = "ChatGPT";
            Session session = new Session(attempt.ownerId, clientId, subject,
                    email + " · " + clientId.substring(Math.max(0, clientId.length() - 8)), tokens);
            synchronized (this) {
                if (pending.get(attempt.ownerId) != attempt || !enabled() || Instant.now().isAfter(attempt.expiresAt)) {
                    throw failure(HttpStatus.CONFLICT, "La conexión fue cancelada antes de finalizar.");
                }
                Metadata saved = metadata();
                if (!saved.registrations.containsKey(attempt.ownerId) && saved.registrations.size() >= MAX_ACCOUNTS) {
                    throw failure(HttpStatus.TOO_MANY_REQUESTS, "Se alcanzó el límite local de cuentas ChatGPT registradas.");
                }
                Map<Long, Registration> registrations = new LinkedHashMap<>(saved.registrations);
                registrations.put(attempt.ownerId, new Registration(clientId, subject));
                saveMetadata(new Metadata(saved.hostId, registrations));
                saveSession(session, tokens, false);
                Session previous = sessions.put(attempt.ownerId, session);
                connectionId = UUID.randomUUID().toString();
                installed = true;
                if (previous != null) { previous.closed = true; close(previous.stream); previous.tokens = null; }
                notice(attempt.ownerId, session.direct
                        ? "Cuenta conectada. Credenciales conservadas en el perfil privado del operador."
                        : "Identidad ChatGPT verificada, pero falta el permiso de uso del plan. No se realizará inferencia.");
            }
            callbackPage(exchange, 200, session.direct ? "ChatGPT se conectó a Exotic. Puede cerrar esta ventana."
                    : "Identidad verificada, sin permiso para usar el plan. Regrese a Exotic para revisar la conexión.");
        } catch (Exception exception) {
            String message = exception instanceof ResponseStatusException status
                    ? status.getReason() : "No fue posible verificar la conexión ChatGPT. Vuelva a intentarlo desde Exotic.";
            synchronized (this) {
                if (pending.get(attempt.ownerId) == attempt) notice(attempt.ownerId, message);
            }
            callbackPage(exchange, 400, message);
        } finally {
            exchange.close();
            if (consumed) synchronized (this) { if (pending.get(attempt.ownerId) == attempt) stopPending(attempt.ownerId); }
            if (!installed && acquired != null) revoke(acquired.refreshToken, acquiredClient);
        }
    }

    private Claims validateIdentity(String token, String clientId, String nonce, String expectedSubject) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) throw new IllegalArgumentException();
            JsonNode header = mapper.readTree(Base64.getUrlDecoder().decode(parts[0]));
            String algorithm = header.path("alg").asText();
            if (!Set.of("RS256", "ES256").contains(algorithm) || header.has("crit")) throw new IllegalArgumentException();
            String kid = required(header.path("kid").asText(), 256);
            PublicKey key = signingKey(kid, algorithm, false);
            if (key == null) key = signingKey(kid, algorithm, true);
            if (key == null) throw new IllegalArgumentException();
            Claims claims = Jwts.parserBuilder().setSigningKey(key).requireIssuer(ISSUER)
                    .setAllowedClockSkewSeconds(5).build().parseClaimsJws(token).getBody();
            Object audience = claims.get("aud");
            boolean accepted = clientId.equals(audience) || audience instanceof List<?> values && values.contains(clientId);
            if (!accepted || claims.getExpiration() == null || claims.getIssuedAt() == null
                    || claims.getIssuedAt().toInstant().isAfter(Instant.now().plusSeconds(5))
                    || claims.getSubject() == null || claims.getSubject().isBlank() || claims.getSubject().length() > 512
                    || nonce != null && !constantEquals(nonce, claims.get("nonce") instanceof String value ? value : null)
                    || expectedSubject != null && !constantEquals(expectedSubject, claims.getSubject())) throw new IllegalArgumentException();
            if (audience instanceof List<?> values && values.size() > 1 && !clientId.equals(claims.get("azp"))) throw new IllegalArgumentException();
            if (claims.containsKey("azp") && !clientId.equals(claims.get("azp"))) throw new IllegalArgumentException();
            return claims;
        } catch (Exception exception) {
            throw failure(HttpStatus.BAD_GATEWAY, "No fue posible verificar la firma o identidad de la cuenta ChatGPT.");
        }
    }

    private synchronized PublicKey signingKey(String kid, String algorithm, boolean force) throws Exception {
        if (force || jwks == null || Instant.now().isAfter(jwksExpires)) {
            String uri = authUri(required(discovery().path("jwks_uri").asText(), 2048));
            JsonReply reply = requestJson(HttpRequest.newBuilder(URI.create(uri)).timeout(HTTP_TIMEOUT).GET().build());
            if (reply.status != 200 || !reply.body.path("keys").isArray()) throw new IllegalArgumentException();
            jwks = reply.body;
            jwksExpires = Instant.now().plusSeconds(600);
        }
        for (JsonNode key : jwks.path("keys")) {
            if (!kid.equals(key.path("kid").asText()) || key.has("use") && !"sig".equals(key.path("use").asText())
                    || key.has("alg") && !algorithm.equals(key.path("alg").asText())) continue;
            if ("RS256".equals(algorithm) && "RSA".equals(key.path("kty").asText())) {
                return KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(unsigned(key, "n"), unsigned(key, "e")));
            }
            if ("ES256".equals(algorithm) && "EC".equals(key.path("kty").asText()) && "P-256".equals(key.path("crv").asText())) {
                var parameters = java.security.AlgorithmParameters.getInstance("EC");
                parameters.init(new ECGenParameterSpec("secp256r1"));
                return KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(
                        new ECPoint(unsigned(key, "x"), unsigned(key, "y")), parameters.getParameterSpec(ECParameterSpec.class)));
            }
        }
        return null;
    }

    private synchronized JsonNode discovery() {
        if (discovery == null || Instant.now().isAfter(discoveryExpires)) {
            JsonReply reply = requestJson(HttpRequest.newBuilder(URI.create(ISSUER + "/.well-known/openid-configuration"))
                    .timeout(HTTP_TIMEOUT).GET().build());
            if (reply.status != 200 || !ISSUER.equals(reply.body.path("issuer").asText())
                    || !AUTHORIZE.equals(reply.body.path("authorization_endpoint").asText())
                    || !TOKEN.equals(reply.body.path("token_endpoint").asText())) {
                throw failure(HttpStatus.BAD_GATEWAY, "La configuración OpenID de OpenAI no coincide con el emisor esperado.");
            }
            authUri(reply.body.path("jwks_uri").asText());
            discovery = reply.body;
            discoveryExpires = Instant.now().plusSeconds(600);
        }
        return discovery;
    }

    private TokenSet tokens(JsonNode json, Set<String> previousScopes) {
        try {
            Set<String> scopes = json.has("scope") ? Set.copyOf(List.of(json.path("scope").asText().trim().split("\\s+"))) : previousScopes;
            String access = json.path("access_token").asText("");
            String refresh = json.path("refresh_token").asText("");
            long seconds = json.path("expires_in").asLong(0);
            if (scopes.contains(DIRECT_SCOPE)) {
                required(access, 65_536);
                required(refresh, 65_536);
                if (seconds < 1 || seconds > 86_400) throw new IllegalArgumentException();
            }
            if (access.length() > 65_536 || refresh.length() > 65_536
                    || !access.isEmpty() && !"Bearer".equalsIgnoreCase(json.path("token_type").asText())) throw new IllegalArgumentException();
            String idToken = json.path("id_token").asText("");
            if (idToken.length() > 32_768) throw new IllegalArgumentException();
            return new TokenSet(access, refresh, Instant.now().plusSeconds(Math.max(0, Math.min(86_400, seconds))), scopes, idToken);
        } catch (Exception exception) {
            throw failure(HttpStatus.BAD_GATEWAY, "OpenAI devolvió credenciales incompletas o no válidas.");
        }
    }

    private boolean revoke(String refresh, String clientId) {
        if (refresh == null || refresh.isBlank()) return false;
        try {
            String endpoint = authUri(discovery().path("revocation_endpoint").asText());
            for (int attempt = 0; attempt < 2; attempt++) {
                try {
                    JsonReply reply = postForm(endpoint, Map.of("token", refresh, "token_type_hint", "refresh_token", "client_id", clientId), Duration.ofSeconds(8));
                    if (reply.status == 200) return true;
                    if (reply.status < 500) return false;
                } catch (ResponseStatusException exception) { /* Retry a transport failure once; never log credentials. */ }
                if (attempt == 0) Thread.sleep(250);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (Exception exception) { /* Local sign-out must still finish. */ }
        return false;
    }

    private String readInferenceBody(String contentType, InputStream input) throws IOException {
        String media = contentType.split(";", 2)[0].strip().toLowerCase(java.util.Locale.ROOT);
        if (!media.isEmpty() && !media.equals("text/event-stream"))
            throw failure(HttpStatus.BAD_GATEWAY, "OpenAI no devolvió el flujo SSE esperado.");
        if (media.isEmpty()) {
            // The live direct route can omit Content-Type. Accept only recognizable
            // event framing, then apply the same bounded parser and completion checks.
            var inspected = new java.io.PushbackInputStream(input, 128);
            byte[] prefix = inspected.readNBytes(128);
            String opening = new String(prefix, StandardCharsets.UTF_8).stripLeading();
            if (!(opening.startsWith("data:") || opening.startsWith("event:") || opening.startsWith(":")))
                throw failure(HttpStatus.BAD_GATEWAY, "OpenAI no devolvió el flujo SSE esperado.");
            inspected.unread(prefix);
            input = inspected;
        }
        return readInference(input);
    }

    private String readInference(InputStream input) throws IOException {
        String completed = null;
        StringBuilder data = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new LimitedStream(input, MAX_STREAM_BYTES), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    if (!data.isEmpty()) {
                        String result = event(data.toString());
                        if (result != null) {
                            if (completed != null) throw new IOException("Multiple terminal events");
                            completed = result;
                        }
                        data.setLength(0);
                    }
                } else if (line.startsWith("data:")) {
                    if (!data.isEmpty()) data.append('\n');
                    data.append(line.substring(5).stripLeading());
                    if (data.length() > MAX_JSON_BYTES) throw new IOException("SSE event limit");
                }
            }
            if (!data.isEmpty()) {
                String result = event(data.toString());
                if (result != null) {
                    if (completed != null) throw new IOException("Multiple terminal events");
                    completed = result;
                }
            }
        }
        if (completed == null || completed.isBlank()) throw new IOException("Missing completed text response");
        return completed;
    }

    private String event(String data) throws IOException {
        if ("[DONE]".equals(data)) return null;
        JsonNode event;
        try (var parser = mapper.createParser(data)) {
            event = mapper.readTree(parser);
            if (event == null || !event.isObject() || parser.nextToken() != null)
                throw new IOException("Invalid SSE JSON event");
        }
        String type = event.path("type").asText();
        if ("error".equals(type) || "response.failed".equals(type) || "response.incomplete".equals(type)) {
            JsonNode detail = event.path("response").path("error");
            if (detail.isMissingNode()) detail = event;
            throw failure(HttpStatus.BAD_GATEWAY, "OpenAI interrumpió la respuesta (" + safeCode(detail.path("code").asText())
                    + "); no se usará contenido parcial.");
        }
        if (!"response.completed".equals(type)) return null;
        JsonNode response = event.path("response");
        if (!"completed".equals(response.path("status").asText()) || !response.path("output").isArray()) throw new IOException("Invalid completed response");
        StringBuilder result = new StringBuilder();
        for (JsonNode item : response.path("output")) {
            if (!"message".equals(item.path("type").asText())) continue;
            for (JsonNode content : item.path("content")) {
                if ("output_text".equals(content.path("type").asText())) {
                    result.append(content.path("text").asText());
                    if (result.length() > MAX_OUTPUT_CHARS) throw new IOException("Output limit");
                }
            }
        }
        return result.toString();
    }

    private JsonReply postForm(String uri, Map<String, String> values, Duration timeout) {
        return requestJson(HttpRequest.newBuilder(URI.create(uri)).timeout(timeout).header("Accept", "application/json")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form(values))).build());
    }

    private JsonReply requestJson(HttpRequest request) {
        try {
            long deadline = System.nanoTime() + request.timeout().orElse(HTTP_TIMEOUT).toNanos();
            HttpResponse<InputStream> reply = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
            var watchdog = timer.schedule(() -> close(reply.body()), Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
            try (InputStream input = reply.body()) {
                return new JsonReply(reply.statusCode(), parse(readLimited(input, MAX_JSON_BYTES)),
                        reply.headers().firstValue("x-request-id").orElse(reply.headers().firstValue("openai-request-id").orElse("")));
            } finally {
                watchdog.cancel(false);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw failure(HttpStatus.SERVICE_UNAVAILABLE, "Se interrumpió la comunicación con OpenAI.");
        } catch (Exception exception) {
            throw failure(HttpStatus.BAD_GATEWAY, "No se pudo completar la comunicación con OpenAI dentro de los límites locales.");
        }
    }

    private JsonNode parse(byte[] bytes) throws IOException {
        if (bytes.length == 0) return mapper.createObjectNode();
        try {
            JsonNode parsed = mapper.readTree(bytes);
            return parsed == null ? mapper.createObjectNode() : parsed;
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            // Preserve the upstream HTTP status, without exposing a non-JSON body
            // that could contain credentials or user data.
            return mapper.createObjectNode().put("non_json_body", true);
        }
    }

    private ResponseStatusException upstream(int status, JsonNode body, HttpResponse<?> response) {
        return upstream(new JsonReply(status, body, response.headers().firstValue("x-request-id")
                .orElse(response.headers().firstValue("openai-request-id").orElse(""))));
    }

    private ResponseStatusException upstream(JsonReply reply) {
        String code = safeCode(errorCode(reply.body));
        String requestId = reply.requestId.matches("[A-Za-z0-9_-]{1,128}") ? "; referencia " + reply.requestId : "";
        String recovery = switch (code) {
            case "subscription_sharing_usage_limit_exceeded" -> " Revise el uso del plan en los ajustes de ChatGPT.";
            case "subscription_sharing_user_not_eligible" -> " La cuenta o espacio no es elegible para usar el plan en esta integración.";
            case "subscription_sharing_unsupported_capability" -> " La solicitud incluye una capacidad no admitida.";
            default -> "";
        };
        HttpStatus status = reply.status == 429 ? HttpStatus.TOO_MANY_REQUESTS
                : reply.status == 503 ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.BAD_GATEWAY;
        return failure(status, "OpenAI rechazó la operación (HTTP " + reply.status + "; " + code + requestId + ")." + recovery);
    }

    private synchronized Metadata metadata() {
        if (metadata != null) return metadata;
        try {
            if (closed) throw new IOException("Gateway closed");
            store = profileOpener.open();
            profile = store.read();
            if (profile.isEmpty()) {
                Metadata created = new Metadata("urn:uuid:" + UUID.randomUUID(), new LinkedHashMap<>());
                saveMetadata(created);
                return metadata;
            }
            JsonNode json = profile;
            if (json.path("version").asInt() != 1) throw new IOException("Unsupported profile");
            String host = json.path("ext_agent_host_id").asText();
            if (!host.startsWith("urn:uuid:") || UUID.fromString(host.substring(9)).version() != 4) throw new IOException();
            JsonNode mappings = json.path("registrations");
            if (!mappings.isObject() || mappings.size() > MAX_ACCOUNTS) throw new IOException();
            Map<Long, Registration> registrations = new LinkedHashMap<>();
            var fields = mappings.fields();
            while (fields.hasNext()) {
                var field = fields.next();
                Long id = Long.valueOf(field.getKey());
                if (!ACCOUNT_SLOT.equals(id)) throw new IOException("Unknown account slot");
                String client = field.getValue().path("client_id").asText();
                String subject = field.getValue().path("sub").isNull() ? null : field.getValue().path("sub").asText(null);
                if (subject != null) required(subject, 512);
                if (!validClient(client) || NEW_CLIENT.equals(client)) throw new IOException();
                registrations.put(id, new Registration(client, subject));
            }
            metadata = new Metadata(host, registrations);
            restoreSession(json);
            return metadata;
        } catch (Exception exception) {
            if (store != null) try { store.close(); } catch (IOException ignored) { }
            store = null;
            metadata = null;
            profile = null;
            String message = exception instanceof AssistantCredentialStore.ProfileInUseException
                    ? exception.getMessage() : "No se pudo abrir el perfil privado del asistente. Revise su integridad y permisos desde desarrollo.";
            throw failure(HttpStatus.SERVICE_UNAVAILABLE, message);
        }
    }

    private synchronized void saveMetadata(Metadata value) throws IOException {
        if (closed) throw new IOException("Gateway closed");
        ObjectNode json = profile == null ? mapper.createObjectNode() : profile.deepCopy();
        json.put("version", 1).put("ext_agent_host_id", value.hostId);
        ObjectNode registrations = json.putObject("registrations");
        value.registrations.forEach((id, registration) -> registrations.putObject(id.toString())
                .put("client_id", registration.clientId).put("sub", registration.subject));
        store.write(json);
        profile = json;
        metadata = value;
    }

    private synchronized void saveSession(Session session, TokenSet tokens, boolean refreshPending) {
        try {
            if (closed) throw new IOException("Gateway closed");
            ObjectNode json = profile.deepCopy();
            ObjectNode stored = json.putObject("session").put("client_id", session.clientId)
                    .put("sub", session.subject).put("label", session.label)
                    .put("access_token", tokens.accessToken).put("refresh_token", tokens.refreshToken)
                    .put("id_token", tokens.idToken).put("expires_at", tokens.expiresAt.toString())
                    .put("refresh_pending", refreshPending);
            ArrayNode scopes = stored.putArray("scopes");
            tokens.scopes.stream().sorted().forEach(scopes::add);
            store.write(json);
            profile = json;
        } catch (IOException exception) {
            throw failure(HttpStatus.SERVICE_UNAVAILABLE, "No se pudo conservar la conexión de forma privada y atómica. Revise el perfil desde desarrollo.");
        }
    }

    private void restoreSession(JsonNode json) throws IOException {
        JsonNode value = json.path("session");
        if (value.isMissingNode()) return;
        if (!value.isObject()) throw new IOException("Invalid session");
        if (value.path("refresh_pending").asBoolean()) {
            notice(ACCOUNT_SLOT, "Hay una renovación interrumpida. Reconecte desde la herramienta de desarrollo.");
            return;
        }
        Registration registration = metadata.registrations.get(ACCOUNT_SLOT);
        if (registration == null || registration.subject == null || !registration.clientId.equals(value.path("client_id").asText())
                || !registration.subject.equals(value.path("sub").asText())) throw new IOException("Session identity mismatch");
        JsonNode scopeValues = value.path("scopes");
        if (!scopeValues.isArray() || scopeValues.size() > 16) throw new IOException("Invalid scopes");
        Set<String> scopes = new java.util.HashSet<>();
        for (JsonNode scope : scopeValues) scopes.add(required(scope.asText(), 200));
        String access = value.path("access_token").asText("");
        String refresh = value.path("refresh_token").asText("");
        if (scopes.contains(DIRECT_SCOPE)) { required(access, 65_536); required(refresh, 65_536); }
        String identity = required(value.path("id_token").asText(), 32_768);
        TokenSet tokens = new TokenSet(access, refresh, Instant.parse(value.path("expires_at").asText()), Set.copyOf(scopes), identity);
        sessions.put(ACCOUNT_SLOT, new Session(ACCOUNT_SLOT, registration.clientId, registration.subject,
                required(value.path("label").asText(), 512), tokens));
    }

    private synchronized void clearStoredSession() {
        try {
            ObjectNode json = profile.deepCopy();
            json.remove("session");
            store.write(json);
            profile = json;
        } catch (IOException exception) {
            throw failure(HttpStatus.SERVICE_UNAVAILABLE, "No se pudo retirar la sesión persistida. Revise el perfil desde desarrollo.");
        }
    }

    public synchronized boolean authorizationPending() {
        return pending.containsKey(ACCOUNT_SLOT);
    }

    private synchronized void expire(Pending attempt) {
        if (pending.get(attempt.ownerId) == attempt) {
            notice(attempt.ownerId, "El intento de conexión ChatGPT venció. Vuelva a iniciarlo desde Exotic.");
            stopPending(attempt.ownerId);
        }
    }

    private void stopPending(Long ownerId) {
        Pending attempt = pending.remove(ownerId);
        if (attempt != null) {
            if (attempt.expiration != null) attempt.expiration.cancel(false);
            attempt.server.stop(0);
        }
    }

    private synchronized void notice(Long ownerId, String message) {
        if (!notices.containsKey(ownerId) && notices.size() >= 64) {
            notices.keySet().stream().filter(id -> !sessions.containsKey(id) && !pending.containsKey(id))
                    .findFirst().ifPresent(notices::remove);
        }
        if (notices.containsKey(ownerId) || notices.size() < 64) notices.put(ownerId, message);
    }

    private static void callbackPage(HttpExchange exchange, int status, String message) throws IOException {
        byte[] bytes = message.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("Referrer-Policy", "no-referrer");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.getResponseHeaders().set("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    private static Map<String, String> query(String value) {
        if (value == null || value.length() > 8192) throw failure(HttpStatus.BAD_REQUEST, "Callback OAuth no válido.");
        Map<String, String> params = new HashMap<>();
        for (String pair : value.split("&")) {
            String[] fields = pair.split("=", 2);
            String key = URLDecoder.decode(fields[0], StandardCharsets.UTF_8);
            String text = URLDecoder.decode(fields.length == 2 ? fields[1] : "", StandardCharsets.UTF_8);
            if (params.putIfAbsent(key, text) != null) throw failure(HttpStatus.BAD_REQUEST, "Callback OAuth con parámetros duplicados.");
        }
        return params;
    }

    private static String form(Map<String, String> values) {
        return values.entrySet().stream().map(entry -> URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8)
                + "=" + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8)).collect(java.util.stream.Collectors.joining("&"));
    }

    private static String authUri(String value) {
        try {
            URI uri = URI.create(value);
            if (!"https".equals(uri.getScheme()) || !"auth.openai.com".equals(uri.getHost())
                    || uri.getPort() != -1 && uri.getPort() != 443 || uri.getRawUserInfo() != null
                    || uri.getFragment() != null || uri.getQuery() != null || uri.getPath().isBlank()) throw new IllegalArgumentException();
            return value;
        } catch (Exception exception) {
            throw failure(HttpStatus.BAD_GATEWAY, "OpenAI publicó un endpoint OpenID no válido para esta integración.");
        }
    }

    private String randomValue(int bytes) {
        byte[] value = new byte[bytes];
        random.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private static BigInteger unsigned(JsonNode key, String property) {
        return new BigInteger(1, Base64.getUrlDecoder().decode(required(key.path(property).asText(), 4096)));
    }

    private static String required(String value, int limit) {
        if (value == null || value.isBlank() || value.length() > limit || value.chars().anyMatch(Character::isISOControl)) throw new IllegalArgumentException();
        return value;
    }

    private static boolean validClient(String value) { return value != null && value.matches("[A-Za-z0-9_-]{8,256}"); }
    private static boolean constantEquals(String left, String right) {
        return left != null && right != null && MessageDigest.isEqual(left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8));
    }
    private static String safeCode(String value) { return value != null && value.matches("[a-zA-Z0-9_-]{1,100}") ? value : "sin_codigo"; }
    private static String errorCode(JsonNode body) { return body.path("error").isTextual() ? body.path("error").asText() : body.path("error").path("code").asText(); }
    private static byte[] readLimited(InputStream input, int limit) throws IOException {
        byte[] bytes = input.readNBytes(limit + 1);
        if (bytes.length > limit) throw new IOException("HTTP body limit");
        return bytes;
    }
    private void requireEnabled(Long id) {
        owner(id);
        if (!enabled()) throw failure(HttpStatus.SERVICE_UNAVAILABLE, "ChatGPT solo está disponible con el prototipo habilitado en entorno local.");
    }
    private static void owner(Long id) { if (id == null || id <= 0) throw failure(HttpStatus.BAD_REQUEST, "Usuario Exotic no válido."); }
    private static ResponseStatusException failure(HttpStatus status, String message) { return new ResponseStatusException(status, message); }
    private static Thread daemon(Runnable task, String name) { Thread thread = new Thread(task, name); thread.setDaemon(true); return thread; }
    private static ScheduledThreadPoolExecutor scheduler(String name) {
        ScheduledThreadPoolExecutor scheduler = new ScheduledThreadPoolExecutor(1, r -> daemon(r, name));
        scheduler.setRemoveOnCancelPolicy(true);
        return scheduler;
    }
    private static void close(InputStream input) { if (input != null) try { input.close(); } catch (IOException ignored) { } }

    @PreDestroy
    void shutdown() {
        synchronized (this) {
            closed = true;
            for (Long id : new ArrayList<>(pending.keySet())) stopPending(id);
            sessions.values().forEach(session -> { session.closed = true; close(session.stream); session.tokens = null; });
            sessions.clear();
        }
        callbacks.shutdownNow();
        authorizationExpirations.shutdownNow();
        boolean drained = false;
        try { drained = callbacks.awaitTermination(60, TimeUnit.SECONDS); }
        catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
        // Do not release the OS lock while a callback could still be exchanging
        // a code. Process termination remains the fallback owner of that lock.
        if (drained) synchronized (this) {
            timer.shutdownNow();
            if (store != null) try { store.close(); } catch (IOException ignored) { }
            store = null;
            profile = null;
        }
    }

    public record Status(boolean enabled, boolean connected, String accountLabel, String message) { }
    public record AuthStart(String authorizationUrl) { }
    public record ModelChoice(String id, String name) { }
    public record Turn(String role, String content) { }

    private record Registration(String clientId, String subject) { }
    @FunctionalInterface interface ProfileOpener { AssistantCredentialStore open() throws IOException; }
    private record Metadata(String hostId, Map<Long, Registration> registrations) { }
    private record JsonReply(int status, JsonNode body, String requestId) { }
    // Deliberately not a record: generated toString must never expose credentials.
    private static final class TokenSet {
        final String accessToken;
        final String refreshToken;
        final Instant expiresAt;
        final Set<String> scopes;
        final String idToken;
        TokenSet(String access, String refresh, Instant expires, Set<String> scopes, String idToken) {
            this.accessToken = access; this.refreshToken = refresh; this.expiresAt = expires; this.scopes = scopes; this.idToken = idToken;
        }
    }
    private static final class Session {
        final Long ownerId;
        final String clientId;
        final String subject;
        final String label;
        final ReentrantLock lock = new ReentrantLock();
        volatile TokenSet tokens;
        volatile boolean closed;
        volatile boolean direct;
        volatile InputStream stream;
        List<ModelChoice> models = List.of();
        Instant modelsExpire = Instant.EPOCH;
        Session(Long ownerId, String client, String subject, String label, TokenSet tokens) {
            this.ownerId = ownerId; this.clientId = client; this.subject = subject; this.label = label;
            this.tokens = tokens; this.direct = tokens.scopes.contains(DIRECT_SCOPE);
        }
    }
    private static final class Pending {
        final Long ownerId;
        final String state;
        final String nonce;
        final String verifier;
        final String redirect;
        final Registration registration;
        final Instant expiresAt;
        final HttpServer server;
        volatile ScheduledFuture<?> expiration;
        boolean consumed;
        Pending(Long ownerId, String state, String nonce, String verifier, String redirect,
                Registration registration, Instant expires, HttpServer server) {
            this.ownerId = ownerId; this.state = state; this.nonce = nonce; this.verifier = verifier;
            this.redirect = redirect; this.registration = registration; this.expiresAt = expires; this.server = server;
        }
    }
    private static final class LimitedStream extends FilterInputStream {
        private int remaining;
        LimitedStream(InputStream input, int limit) { super(input); remaining = limit; }
        @Override public int read() throws IOException {
            int value = in.read();
            if (value >= 0 && --remaining < 0) throw new IOException("SSE byte limit");
            return value;
        }
        @Override public int read(byte[] bytes, int offset, int length) throws IOException {
            int count = in.read(bytes, offset, Math.min(length, Math.max(1, remaining + 1)));
            if (count > 0 && (remaining -= count) < 0) throw new IOException("SSE byte limit");
            return count;
        }
    }
}
