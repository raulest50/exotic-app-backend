package exotic.app.planta.resource.master.configs;

import exotic.app.planta.model.master.configs.SuperMasterConfig;
import exotic.app.planta.service.master.configs.SuperMasterOpsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/super-master-ops")
@RequiredArgsConstructor
@Slf4j
public class SuperMasterOpsResource {

    private static final String SUPER_MASTER_USERNAME = "super_master";

    private final SuperMasterOpsService superMasterOpsService;

    private boolean isSuperMaster(Authentication auth) {
        return auth != null && auth.isAuthenticated() && SUPER_MASTER_USERNAME.equals(auth.getName());
    }

    /**
     * Get Super Master config (read-only). Any authenticated user can read for tab visibility.
     */
    @GetMapping("/config")
    public ResponseEntity<?> getConfig(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return superMasterOpsService.getConfig()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Update Super Master config. Only super_master can update.
     */
    @PutMapping("/config")
    public ResponseEntity<?> updateConfig(@RequestBody SuperMasterConfig config, Authentication authentication) {
        if (!isSuperMaster(authentication)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Only super_master can update config"));
        }
        try {
            SuperMasterConfig updated = superMasterOpsService.updateConfig(config);
            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            log.error("Error updating Super Master config: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * Send 6-digit verification code to email. Only super_master.
     */
    @PostMapping("/send-verification-code")
    public ResponseEntity<?> sendVerificationCode(@RequestBody Map<String, String> body, Authentication authentication) {
        if (!isSuperMaster(authentication)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Only super_master can send verification code"));
        }
        String email = body != null ? body.get("email") : null;
        if (email == null || email.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "email is required"));
        }
        try {
            superMasterOpsService.sendVerificationCode(email.trim());
            return ResponseEntity.ok(Map.of("message", "Verification code sent"));
        } catch (Exception e) {
            log.error("Error sending verification code: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * Complete super_master profile (email + password) after code verification. Only super_master.
     */
    @PostMapping("/complete-profile")
    public ResponseEntity<?> completeProfile(@RequestBody CompleteProfileRequest request, Authentication authentication) {
        if (!isSuperMaster(authentication)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Only super_master can complete profile"));
        }
        if (request == null || request.getEmail() == null || request.getCode() == null || request.getNewPassword() == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "email, code and newPassword are required"));
        }
        boolean success = superMasterOpsService.completeProfile(request.getEmail(), request.getCode(), request.getNewPassword());
        if (success) {
            return ResponseEntity.ok(Map.of("message", "Profile completed"));
        }
        return ResponseEntity.badRequest().body(Map.of("message", "Invalid or expired code"));
    }

    public static class CompleteProfileRequest {
        private String email;
        private String code;
        private String newPassword;

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
        public String getNewPassword() { return newPassword; }
        public void setNewPassword(String newPassword) { this.newPassword = newPassword; }
    }
}
