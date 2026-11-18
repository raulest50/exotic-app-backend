package exotic.app.planta.service.agentassistant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Paid API provider owned exclusively by the backend. No OAuth, browser credentials or database tools. */
@Component
public class OpenAiAssistantGateway implements AssistantAiGateway {
    private static final URI RESPONSES = URI.create("https://api.openai.com/v1/responses");
    private static final int BODY_LIMIT = 262_144;
    private final ObjectMapper mapper;
    private final AssistantUsageService usage;
    private final boolean configuredEnabled;
    private final String apiKey;
    private final String model;
    private final HttpClient http;
    private final String connectionId = UUID.randomUUID().toString();

    @Autowired
    public OpenAiAssistantGateway(ObjectMapper mapper, AssistantUsageService usage,
            @Value("${app.agent-assistant.api.enabled:false}") boolean enabled,
            @Value("${app.agent-assistant.api.key:}") String apiKey,
            @Value("${app.agent-assistant.api.model:gpt-5.4-mini-2026-03-17}") String model) {
        this(mapper, usage, enabled, apiKey, model, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build());
    }

    OpenAiAssistantGateway(ObjectMapper mapper, AssistantUsageService usage, boolean enabled,
                           String apiKey, String model, HttpClient http) {
        this.mapper = mapper;
        this.usage = usage;
        this.configuredEnabled = enabled;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model;
        this.http = http;
    }

    @Override public boolean enabled() {
        return configuredEnabled && !apiKey.isBlank() && model != null && !model.isBlank();
    }
    @Override public boolean available() {
        if (!enabled()) return false;
        var remaining = usage.view();
        return remaining.configured() && remaining.remainingPercent() > 0;
    }
    @Override public String connectionId() { return connectionId; }
    @Override public Usage usage() { return enabled() ? usage.view() : new Usage(false, null); }

    @Override public String generate(String instructions, List<Turn> turns) {
        if (!enabled()) throw failure(HttpStatus.SERVICE_UNAVAILABLE);
        usage.requireAllowance();
        try {
            Map<String, Object> schema = Map.of("type", "object", "additionalProperties", false,
                    "required", List.of("kind", "message", "sql"), "properties", Map.of(
                            "kind", Map.of("type", "string", "enum", List.of("answer", "query", "unsupported_write")),
                            "message", Map.of("type", "string"), "sql", Map.of("type", "string")));
            var payload = Map.of("model", model, "instructions", instructions, "input", turns,
                    "store", false, "stream", false, "max_output_tokens", 1600,
                    "service_tier", "default", "reasoning", Map.of("effort", "none"),
                    "text", Map.of("format", Map.of("type", "json_schema", "name", "exotic_assistant",
                            "strict", true, "schema", schema)));
            var request = HttpRequest.newBuilder(RESPONSES).timeout(Duration.ofSeconds(90))
                    .header("Authorization", "Bearer " + apiKey).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload))).build();
            var response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
            // Never expose or log provider error bodies, which can include request data.
            if (response.statusCode() == 429) throw failure(HttpStatus.TOO_MANY_REQUESTS);
            if (response.statusCode() != 200) throw failure(HttpStatus.SERVICE_UNAVAILABLE);
            byte[] bytes = response.body();
            if (bytes.length > BODY_LIMIT) throw failure(HttpStatus.BAD_GATEWAY);
            JsonNode root = mapper.readTree(bytes);
            JsonNode tokens = root.path("usage");
            usage.record(root.path("id").asText(), root.path("model").asText(),
                    count(tokens.path("input_tokens")),
                    tokens.path("input_tokens_details").has("cached_tokens")
                            ? count(tokens.path("input_tokens_details").path("cached_tokens")) : 0,
                    count(tokens.path("output_tokens")));
            if (!root.path("status").asText().equals("completed")) throw failure(HttpStatus.BAD_GATEWAY);
            StringBuilder text = new StringBuilder();
            for (JsonNode item : root.path("output")) {
                if (!item.path("type").asText().equals("message")) continue;
                for (JsonNode content : item.path("content")) {
                    if (content.path("type").asText().equals("refusal")) throw failure(HttpStatus.BAD_GATEWAY);
                    if (content.path("type").asText().equals("output_text")) text.append(content.path("text").asText());
                }
            }
            if (text.isEmpty() || text.length() > 32_000) throw failure(HttpStatus.BAD_GATEWAY);
            return text.toString();
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw failure(HttpStatus.SERVICE_UNAVAILABLE);
        } catch (Exception ex) {
            throw failure(HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    private static long count(JsonNode node) {
        return node.isIntegralNumber() && node.canConvertToLong() ? node.longValue() : -1;
    }

    private static ResponseStatusException failure(HttpStatus status) {
        return new ResponseStatusException(status, "No se pudo obtener una respuesta del servicio de IA.");
    }
}
