package exotic.app.planta.resource.users;

import exotic.app.planta.service.users.AgentAssistantAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Solo expone las capacidades del usuario actual; no recibe ni ejecuta preguntas. */
@RestController
@RequestMapping("/agent-assistant")
@RequiredArgsConstructor
public class AgentAssistantResource {

    private final AgentAssistantAccessService accessService;

    @GetMapping("/access")
    public ResponseEntity<AgentAssistantAccessService.Access> getAccess(Authentication authentication) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(accessService.getAccess(authentication));
    }
}
