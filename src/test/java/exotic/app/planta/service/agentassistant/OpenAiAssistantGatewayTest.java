package exotic.app.planta.service.agentassistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Flow;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OpenAiAssistantGatewayTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final AssistantUsageService usage = mock(AssistantUsageService.class);
    private final HttpClient http = mock(HttpClient.class);
    private final OpenAiAssistantGateway gateway = new OpenAiAssistantGateway(mapper, usage, true,
            "test-only-placeholder", "gpt-5.4-mini-2026-03-17", http);

    @SuppressWarnings("unchecked")
    private void respond(int status, String body) throws Exception {
        HttpResponse<byte[]> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(body.getBytes(StandardCharsets.UTF_8));
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
    }

    private String response(String status, String output) {
        return """
                {"id":"resp_example","model":"example-model","status":"%s",
                 "usage":{"input_tokens":100,"input_tokens_details":{"cached_tokens":20},"output_tokens":30},
                 "output":%s}
                """.formatted(status, output);
    }

    @Test void sendsAnAuthenticatedResponsesRequestWithNoStorageOrDatabaseTools() throws Exception {
        respond(200, response("completed", """
                [{"type":"reasoning"},{"type":"message","content":[{"type":"output_text","text":"hello"}]}]
                """));
        assertThat(gateway.generate("instructions", List.of(new AssistantAiGateway.Turn("user", "hola")))).isEqualTo("hello");
        var request = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http).send(request.capture(), any());
        assertThat(request.getValue().uri().toString()).isEqualTo("https://api.openai.com/v1/responses");
        var collected = new java.io.ByteArrayOutputStream();
        request.getValue().bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<>() {
            public void onSubscribe(Flow.Subscription subscription) { subscription.request(Long.MAX_VALUE); }
            public void onNext(ByteBuffer item) { while (item.hasRemaining()) collected.write(item.get()); }
            public void onError(Throwable error) { throw new AssertionError(error); }
            public void onComplete() { }
        });
        var payload = mapper.readTree(collected.toByteArray());
        assertThat(payload.path("store").asBoolean()).isFalse();
        assertThat(payload.has("tools")).isFalse();
        assertThat(payload.path("text").path("format").path("strict").asBoolean()).isTrue();
        assertThat(payload.path("input").get(0).path("content").asText()).isEqualTo("hola");
        verify(usage).record("resp_example", "example-model", 100, 20, 30);
    }

    @Test void countsIncompleteResponsesEvenWhenTheirContentCannotBeReturned() throws Exception {
        respond(200, response("incomplete", "[]"));
        assertThatThrownBy(() -> gateway.generate("instructions", List.of())).isInstanceOf(ResponseStatusException.class);
        verify(usage).record("resp_example", "example-model", 100, 20, 30);
    }

    @Test void providerErrorsDoNotExposeCredentialsOrErrorBodies() throws Exception {
        respond(401, "sensitive diagnostic test-only-placeholder");
        assertThatThrownBy(() -> gateway.generate("instructions", List.of()))
                .isInstanceOf(ResponseStatusException.class).hasMessageNotContaining("sensitive")
                .hasMessageNotContaining("test-only-placeholder");
        verify(usage, never()).record(any(), any(), anyLong(), anyLong(), anyLong());
    }

    @Test void noAllowanceMeansNoNetworkCall() {
        doThrow(new ResponseStatusException(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS))
                .when(usage).requireAllowance();
        assertThatThrownBy(() -> gateway.generate("instructions", List.of())).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(http);
    }
}
