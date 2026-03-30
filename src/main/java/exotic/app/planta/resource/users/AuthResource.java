package exotic.app.planta.resource.users;

import exotic.app.planta.model.users.ModuloAcceso;
import exotic.app.planta.model.users.TabAcceso;
import exotic.app.planta.model.users.User;
import exotic.app.planta.model.users.dto.UserAssignmentStatusDTO;
import exotic.app.planta.repo.usuarios.UserRepository;
import exotic.app.planta.service.users.AuthService;
import exotic.app.planta.service.users.UserOperationalCompatibilityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthResource {

    private final AuthService authService;
    private final UserRepository userRepository;
    private final UserOperationalCompatibilityService userOperationalCompatibilityService;

    @PostMapping("/login")
    public ResponseEntity<?> authenticateUser(@RequestBody LoginRequest loginRequest) {
        try {
            Map<String, String> response = authService.authenticateUser(
                    loginRequest.getUsername(),
                    loginRequest.getPassword()
            );

            return ResponseEntity.ok(response);
        } catch (AuthenticationException e) {
            log.error("Authentication error: {}", e.getMessage());
            return ResponseEntity.badRequest().body("Authentication failed: " + e.getMessage());
        }
    }

    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(Authentication authentication) {
        String username = requireAuthenticatedUsername(authentication);
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
        UserAssignmentStatusDTO assignmentStatus = userOperationalCompatibilityService.buildAssignmentStatus(user.getId(), null);

        Map<String, Object> userData = new HashMap<>();
        userData.put("id", user.getId());
        userData.put("cedula", user.getCedula());
        userData.put("username", user.getUsername());
        userData.put("nombreCompleto", user.getNombreCompleto());
        userData.put("email", user.getEmail());
        userData.put("cel", user.getCel());
        userData.put("direccion", user.getDireccion());
        userData.put("fechaNacimiento", user.getFechaNacimiento());
        userData.put("estado", user.getEstado());

        Map<String, Object> response = new HashMap<>();
        response.put("user", userData);
        response.put("isMasterLike", isMasterLike(user.getUsername()));
        // El frontend necesita este snapshot para bifurcar la ruta raiz entre Home y el panel de area operativa.
        response.put("isAreaResponsable", assignmentStatus.isAreaResponsable());
        response.put("areaResponsable", buildAreaResponsablePayload(assignmentStatus));
        response.put("accesos", buildModuloAccesosPayload(user));

        return ResponseEntity.ok(response);
    }

    private static Map<String, Object> buildAreaResponsablePayload(UserAssignmentStatusDTO assignmentStatus) {
        if (!assignmentStatus.isAreaResponsable()) {
            return null;
        }
        Map<String, Object> areaResponsable = new HashMap<>();
        areaResponsable.put("areaId", assignmentStatus.getAreaResponsableId());
        areaResponsable.put("nombre", assignmentStatus.getAreaResponsableNombre());
        return areaResponsable;
    }

    private static List<Map<String, Object>> buildModuloAccesosPayload(User user) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (ModuloAcceso ma : user.getModuloAccesos()) {
            Map<String, Object> mo = new HashMap<>();
            mo.put("id", ma.getId());
            mo.put("modulo", ma.getModulo().name());
            List<Map<String, Object>> tabs = new ArrayList<>();
            for (TabAcceso t : ma.getTabs()) {
                Map<String, Object> tm = new HashMap<>();
                tm.put("id", t.getId());
                tm.put("tabId", t.getTabId());
                tm.put("nivel", t.getNivel());
                tabs.add(tm);
            }
            mo.put("tabs", tabs);
            list.add(mo);
        }
        return list;
    }

    private static String requireAuthenticatedUsername(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        return authentication.getName();
    }

    private static boolean isMasterLike(String username) {
        if (username == null) return false;
        String normalized = username.toLowerCase();
        return "master".equals(normalized) || "super_master".equals(normalized);
    }

    @PostMapping("/request_reset_passw")
    public ResponseEntity<?> requestPasswordReset(@RequestBody EmailRequest request) {
        authService.requestPasswordReset(request.getEmail());
        return ResponseEntity.ok(Map.of("message", "If an account with that email exists, a password reset link has been sent."));
    }

    @PostMapping("/set_new_passw")
    public ResponseEntity<?> setNewPassword(@RequestBody PasswordResetRequest request) {
        boolean success = authService.setNewPassword(request.getToken(), request.getNewPassword());

        if (success) {
            return ResponseEntity.ok(Map.of("message", "Password has been reset successfully."));
        } else {
            return ResponseEntity.badRequest().body(Map.of("message", "Invalid or expired token."));
        }
    }

    public static class EmailRequest {
        @JsonProperty("email")
        private String email;

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }
    }

    public static class PasswordResetRequest {
        @JsonProperty("token")
        private String token;

        @JsonProperty("newPassword")
        private String newPassword;

        public String getToken() {
            return token;
        }

        public void setToken(String token) {
            this.token = token;
        }

        public String getNewPassword() {
            return newPassword;
        }

        public void setNewPassword(String newPassword) {
            this.newPassword = newPassword;
        }
    }

    public static class LoginRequest {
        private String username;
        private String password;

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }
}
