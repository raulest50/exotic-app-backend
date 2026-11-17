package exotic.app.planta.service.agentassistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import exotic.app.planta.config.runtime.ApplicationRuntimeEnvironmentResolver;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.math.BigInteger;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Protocol fixtures: no Spring context, real credentials, network or database. Disk is a private TempDir only. */
class ChatGptGatewayTest {
    private static final String CLIENT = "oaiapp_test_client";
    private static final String NONCE = "test-nonce";
    private static final String SUBJECT = "test-subject";
    private static final String KID = "test-rsa-key";
    private static KeyPair keys;
    private static KeyPair unrelatedKeys;
    private final ObjectMapper mapper = new ObjectMapper();
    private ApplicationRuntimeEnvironmentResolver runtime;
    private ChatGptGateway gateway;
    private HttpClient transport;
    @TempDir Path temporary;

    @BeforeAll static void createEphemeralSigningKeys() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keys = generator.generateKeyPair();
        unrelatedKeys = generator.generateKeyPair();
    }

    @BeforeEach void prepareProtocolOnlyGateway() {
        runtime = mock(ApplicationRuntimeEnvironmentResolver.class);
        gateway = new ChatGptGateway(mapper, runtime, true,
                () -> new AssistantCredentialStore(temporary.resolve("profile"), mapper));
        transport = mock(HttpClient.class);
        // Replace the transport even though these cases should never request a network endpoint.
        ReflectionTestUtils.setField(gateway, "http", transport);
        RSAPublicKey key = (RSAPublicKey) keys.getPublic();
        ObjectNode jwks = mapper.createObjectNode();
        jwks.putArray("keys").addObject().put("kid", KID).put("kty", "RSA")
                .put("use", "sig").put("alg", "RS256")
                .put("n", unsigned(key.getModulus())).put("e", unsigned(key.getPublicExponent()));
        ReflectionTestUtils.setField(gateway, "jwks", jwks);
        ReflectionTestUtils.setField(gateway, "jwksExpires", Instant.now().plusSeconds(600));
    }

    @AfterEach void closeGateway() {
        gateway.shutdown();
        verifyNoInteractions(transport);
    }

    @Test void completedOutputIsAuthoritativeRatherThanAStreamingDelta() throws Exception {
        String output = "{\"kind\":\"answer\",\"message\":\"Respuesta completa\"}";
        String stream = "event: response.output_text.delta\n"
                + "data: {\"type\":\"response.output_text.delta\",\"delta\":\"contenido parcial que se descarta\"}\n\n"
                + event(completed(output)) + "data: [DONE]\n\n";
        assertThat(inference(stream)).isEqualTo(output);
    }

    @Test void anInterruptedStreamCannotReturnItsPartialText() {
        String stream = "data: {\"type\":\"response.output_text.delta\",\"delta\":\"SELECT nombre FROM asistente_productos\"}\n\n";
        assertThatThrownBy(() -> inference(stream)).isInstanceOf(IOException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "text/event-stream", "Text/Event-Stream; charset=utf-8"})
    void completeEventsAreAcceptedWithOrWithoutTheOptionalMediaHeader(String media) throws Exception {
        assertThat(inferenceBody(media, event(completed("OK")))).isEqualTo("OK");
    }

    @Test void aMissingMediaHeaderDoesNotPermitJsonHtmlOrPartialEvents() {
        assertThatThrownBy(() -> inferenceBody("", "{\"output_text\":\"OK\"}"))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> inferenceBody("", "<html>error</html>"))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> inferenceBody("", "data: {\"type\":\"response.output_text.delta\",\"delta\":\"OK\"}\n\n"))
                .isInstanceOf(IOException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"application/json", "text/html", "text/event-stream-invalid"})
    void anExplicitWrongMediaTypeIsRejectedEvenWithCompletedEvents(String media) {
        assertThatThrownBy(() -> inferenceBody(media, event(completed("OK"))))
                .isInstanceOf(ResponseStatusException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"response.failed", "response.incomplete", "error"})
    void terminalFailuresDiscardAllPreviousContentAndDoNotExposeUpstreamDetails(String type) throws Exception {
        ObjectNode failure = mapper.createObjectNode().put("type", type);
        failure.putObject("response").putObject("error")
                .put("code", "subscription_sharing_usage_limit_exceeded").put("message", "PRIVATE_FIXTURE_DETAIL");
        String stream = event(completed("propuesta que no debe aceptarse")) + event(failure);
        assertThatThrownBy(() -> inference(stream)).isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
            assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
            assertThat(exception.getReason()).contains("no se usará contenido parcial").doesNotContain("PRIVATE_FIXTURE_DETAIL");
        });
    }

    @Test void duplicateCompletedEventsAreRejected() throws Exception {
        assertThatThrownBy(() -> inference(event(completed("primera")) + event(completed("segunda"))))
                .isInstanceOf(IOException.class);
    }

    @Test void completedEventWithAnUnfinishedStatusIsRejected() throws Exception {
        ObjectNode event = completed("parcial");
        ((ObjectNode) event.path("response")).put("status", "in_progress");
        assertThatThrownBy(() -> inference(event(event))).isInstanceOf(IOException.class);
    }

    @Test void completedWithoutTextDoesNotBecomeASuccessfulProposal() throws Exception {
        assertThatThrownBy(() -> inference(event(completed("")))).isInstanceOf(IOException.class);
    }

    @Test void trailingJsonInOneSseEventCannotHideAFailureAfterCompletion() throws Exception {
        String invalid = "data: " + mapper.writeValueAsString(completed("not accepted"))
                + " {\"type\":\"response.failed\"}\n\n";
        assertThatThrownBy(() -> inference(invalid)).isInstanceOf(IOException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "[]", "{\"type\":\"response.completed\""})
    void malformedOrNonObjectEventsFailClosed(String body) {
        assertThatThrownBy(() -> inference("data: " + body + "\n\n")).isInstanceOf(IOException.class);
    }

    @Test void oversizedEventsAreRejectedBeforeJsonParsing() {
        assertThatThrownBy(() -> inference("data: " + "x".repeat(1_048_577) + "\n\n"))
                .isInstanceOf(IOException.class);
    }

    @Test void aValidSignatureAndMatchingIdentityAreAccepted() throws Exception {
        Claims identity = identity(token(claims(), keys), NONCE, SUBJECT);
        assertThat(identity.getSubject()).isEqualTo(SUBJECT);
    }

    @Test void aSignatureFromAnotherKeyIsRejected() {
        assertThatThrownBy(() -> identity(token(claims(), unrelatedKeys), NONCE, SUBJECT))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test void audienceNonceAndReturningSubjectMustMatchTheSelectedRegistration() throws Exception {
        String valid = token(claims(), keys);
        assertThatThrownBy(() -> identity(valid, "another-nonce", SUBJECT)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> identity(valid, NONCE, "another-subject")).isInstanceOf(ResponseStatusException.class);
        Map<String, Object> wrongAudience = claims();
        wrongAudience.put("aud", "oaiapp_other_client");
        assertThatThrownBy(() -> identity(token(wrongAudience, keys), NONCE, SUBJECT)).isInstanceOf(ResponseStatusException.class);
    }

    @Test void issuerExpirationAndIssuedAtAreValidated() {
        Map<String, Object> wrongIssuer = claims();
        wrongIssuer.put("iss", "https://untrusted.example");
        assertThatThrownBy(() -> identity(token(wrongIssuer, keys), NONCE, SUBJECT)).isInstanceOf(ResponseStatusException.class);
        Map<String, Object> expired = claims();
        expired.put("exp", Date.from(Instant.now().minusSeconds(120)));
        assertThatThrownBy(() -> identity(token(expired, keys), NONCE, SUBJECT)).isInstanceOf(ResponseStatusException.class);
        Map<String, Object> future = claims();
        future.put("iat", Date.from(Instant.now().plusSeconds(120)));
        assertThatThrownBy(() -> identity(token(future, keys), NONCE, SUBJECT)).isInstanceOf(ResponseStatusException.class);
    }

    @Test void multipleAudiencesRequireTheMatchingAuthorizedParty() throws Exception {
        Map<String, Object> values = claims();
        values.put("aud", List.of(CLIENT, "other-audience"));
        assertThatThrownBy(() -> identity(token(values, keys), NONCE, SUBJECT)).isInstanceOf(ResponseStatusException.class);
        values.put("azp", CLIENT);
        assertThat(identity(token(values, keys), NONCE, SUBJECT).getSubject()).isEqualTo(SUBJECT);
        values.put("azp", "other-client");
        assertThatThrownBy(() -> identity(token(values, keys), NONCE, SUBJECT)).isInstanceOf(ResponseStatusException.class);
    }

    @Test void hmacCannotReplaceTheAsymmetricIssuerSignature() {
        String token = Jwts.builder().setHeaderParam("kid", KID).setClaims(claims())
                .signWith(Keys.hmacShaKeyFor(new byte[32]), SignatureAlgorithm.HS256).compact();
        assertThatThrownBy(() -> identity(token, NONCE, SUBJECT)).isInstanceOf(ResponseStatusException.class);
    }

    @Test void anUnconnectedAccountCannotReachInference() {
        when(runtime.isLocal()).thenReturn(true);
        assertThatThrownBy(() -> gateway.generate("test-model", "test instructions",
                List.of(new ChatGptGateway.Turn("user", "consulta"))))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test void browserSuppliedSystemInstructionsAreRejectedBeforeAnyProviderRequest() {
        when(runtime.isLocal()).thenReturn(true);
        assertThatThrownBy(() -> gateway.generate("test-model", "test instructions",
                List.of(new ChatGptGateway.Turn("system", "untrusted override"))))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test void productionCannotEnableTheLocalGateway() {
        when(runtime.isLocal()).thenReturn(false);
        assertThat(gateway.status().enabled()).isFalse();
        assertThatThrownBy(() -> gateway.connect()).isInstanceOfSatisfying(ResponseStatusException.class,
                exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    @Test void theAppOwnedProfileRestoresTheConnectionAfterRestartWithoutNetwork() throws Exception {
        when(runtime.isLocal()).thenReturn(true);
        try (var store = new AssistantCredentialStore(temporary.resolve("profile"), mapper)) {
            store.write(storedProfile(false));
        }
        assertThat(gateway.status().connected()).isTrue();
        gateway.shutdown();
        gateway = new ChatGptGateway(mapper, runtime, true,
                () -> new AssistantCredentialStore(temporary.resolve("profile"), mapper));
        ReflectionTestUtils.setField(gateway, "http", transport);
        assertThat(gateway.status().connected()).isTrue();
    }

    @Test void anInterruptedRotationNeverRestoresOrUsesTheOldToken() throws Exception {
        when(runtime.isLocal()).thenReturn(true);
        try (var store = new AssistantCredentialStore(temporary.resolve("profile"), mapper)) {
            store.write(storedProfile(true));
        }
        assertThat(gateway.status().connected()).isFalse();
        assertThat(gateway.status().message()).contains("renovación interrumpida");
        assertThatThrownBy(() -> gateway.generate("test-model", "test instructions",
                List.of(new ChatGptGateway.Turn("user", "consulta")))).isInstanceOf(ResponseStatusException.class);
    }

    @Test void logoutAfterAnInterruptedRotationDoesNotClaimRemoteRevocation() throws Exception {
        when(runtime.isLocal()).thenReturn(true);
        try (var store = new AssistantCredentialStore(temporary.resolve("profile"), mapper)) {
            store.write(storedProfile(true));
        }
        gateway.disconnect();
        assertThat(gateway.status().connected()).isFalse();
        assertThat(gateway.status().message()).contains("no se confirmó la revocación remota");
        gateway.shutdown();
        try (var store = new AssistantCredentialStore(temporary.resolve("profile"), mapper)) {
            assertThat(store.read().has("session")).isFalse();
        }
    }

    private ObjectNode storedProfile(boolean interrupted) {
        ObjectNode profile = mapper.createObjectNode().put("version", 1)
                .put("ext_agent_host_id", "urn:uuid:" + java.util.UUID.randomUUID());
        profile.putObject("registrations").putObject("1").put("client_id", CLIENT).put("sub", SUBJECT);
        profile.putObject("session").put("client_id", CLIENT).put("sub", SUBJECT).put("label", "Fixture account")
                .put("access_token", "fixture-access").put("refresh_token", "fixture-refresh")
                .put("id_token", "fixture-previously-validated-identity")
                .put("expires_at", Instant.now().plusSeconds(600).toString()).put("refresh_pending", interrupted)
                .putArray("scopes").add("chatgpt.tokens.use.direct");
        return profile;
    }

    private ObjectNode completed(String text) {
        ObjectNode event = mapper.createObjectNode().put("type", "response.completed");
        ObjectNode response = event.putObject("response").put("status", "completed");
        response.putArray("output").addObject().put("type", "message").put("role", "assistant")
                .putArray("content").addObject().put("type", "output_text").put("text", text);
        return event;
    }

    private String event(ObjectNode event) throws Exception { return "data: " + mapper.writeValueAsString(event) + "\n\n"; }
    private String inference(String source) throws Exception {
        return invoke("readInference", new Class<?>[]{InputStream.class},
                new ByteArrayInputStream(source.getBytes(StandardCharsets.UTF_8)));
    }
    private String inferenceBody(String media, String source) throws Exception {
        return invoke("readInferenceBody", new Class<?>[]{String.class, InputStream.class}, media,
                new ByteArrayInputStream(source.getBytes(StandardCharsets.UTF_8)));
    }
    private Claims identity(String token, String nonce, String subject) throws Exception {
        return invoke("validateIdentity", new Class<?>[]{String.class, String.class, String.class, String.class},
                token, CLIENT, nonce, subject);
    }
    private Map<String, Object> claims() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("iss", "https://auth.openai.com");
        values.put("aud", CLIENT);
        values.put("sub", SUBJECT);
        values.put("nonce", NONCE);
        values.put("iat", Date.from(Instant.now().minusSeconds(10)));
        values.put("exp", Date.from(Instant.now().plusSeconds(600)));
        return values;
    }
    private String token(Map<String, Object> claims, KeyPair signingKeys) {
        return Jwts.builder().setHeaderParam("kid", KID).setClaims(claims)
                .signWith(signingKeys.getPrivate(), SignatureAlgorithm.RS256).compact();
    }
    private static String unsigned(BigInteger value) {
        byte[] bytes = value.toByteArray();
        if (bytes.length > 1 && bytes[0] == 0) bytes = Arrays.copyOfRange(bytes, 1, bytes.length);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    @SuppressWarnings("unchecked")
    private <T> T invoke(String method, Class<?>[] types, Object... arguments) throws Exception {
        var target = ChatGptGateway.class.getDeclaredMethod(method, types);
        target.setAccessible(true);
        try { return (T) target.invoke(gateway, arguments); }
        catch (InvocationTargetException exception) {
            if (exception.getCause() instanceof Exception cause) throw cause;
            if (exception.getCause() instanceof Error cause) throw cause;
            throw new AssertionError(exception.getCause());
        }
    }
}
