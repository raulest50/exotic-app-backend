package exotic.app.planta.resource.users;

import exotic.app.planta.service.users.AgentAssistantAccessService;
import exotic.app.planta.service.agentassistant.AgentAssistantService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Authenticated assistant endpoints. Authorization is repeated by the service. */
@RestController
@RequestMapping("/agent-assistant")
@RequiredArgsConstructor
public class AgentAssistantResource {

    private final AgentAssistantAccessService accessService;
    private final AgentAssistantService assistantService;

    @GetMapping("/access")
    public ResponseEntity<AgentAssistantAccessService.Access> getAccess(Authentication authentication) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(accessService.getAccess(authentication));
    }

    @GetMapping("/status")
    public ResponseEntity<AgentAssistantService.AssistantStatus> status(Authentication authentication) {
        return noStore(assistantService.status(authentication));
    }

    @PostMapping("/chat")
    public ResponseEntity<AgentAssistantService.ChatResponse> chat(Authentication authentication,
            @RequestBody AgentAssistantService.ChatRequest request) {
        return noStore(assistantService.chat(authentication, request));
    }

    @PostMapping("/proposals/{id}/execute")
    public ResponseEntity<AgentAssistantService.Operation> execute(Authentication authentication, @PathVariable String id) {
        return noStore(assistantService.execute(authentication, id));
    }

    @GetMapping("/operations/{id}")
    public ResponseEntity<AgentAssistantService.Operation> operation(Authentication authentication, @PathVariable String id) {
        return noStore(assistantService.operation(authentication, id));
    }

    private static <T> ResponseEntity<T> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ProblemDetail> assistantFailure(ResponseStatusException failure) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(failure.getStatusCode(),
                failure.getReason() == null ? "No se pudo completar la solicitud del asistente." : failure.getReason());
        if (failure instanceof AgentAssistantService.BackupPermissionRequired) body.setProperty("code", "BACKUP_PERMISSION_REQUIRED");
        return ResponseEntity.status(failure.getStatusCode()).cacheControl(CacheControl.noStore()).body(body);
    }
}
