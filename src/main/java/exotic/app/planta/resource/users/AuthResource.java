package exotic.app.planta.resource.users;

import exotic.app.planta.model.users.ModuloAcceso;
import exotic.app.planta.model.users.TabAcceso;
import exotic.app.planta.model.users.User;
import exotic.app.planta.repo.usuarios.UserRepository;
import exotic.app.planta.service.users.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
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

    @GetMapping("/whoami")
    public ResponseEntity<?> whoAmI(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String username = authentication.getName();

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        Map<String, Object> response = new HashMap<>();
        response.put("name", username);
        response.put("authenticated", authentication.isAuthenticated());
        response.put("credentials", authentication.getCredentials());
        response.put("details", authentication.getDetails());

        List<Map<String, Object>> authoritiesWithLevel = buildAuthoritiesWithMaxNivel(user);
        response.put("authorities", authoritiesWithLevel);
        response.put("accesos", buildModuloAccesosPayload(user));

        Map<String, Object> principalInfo = new HashMap<>();
        principalInfo.put("username", username);
        principalInfo.put("password", "");
        principalInfo.put("authorities", authoritiesWithLevel);
        principalInfo.put("accountNonExpired", true);
        principalInfo.put("accountNonLocked", true);
        principalInfo.put("credentialsNonExpired", true);
        principalInfo.put("enabled", true);

        response.put("principal", principalInfo);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String username = authentication.getName();

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

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

        List<Map<String, Object>> accesosList = buildModuloAccesosPayload(user);
        List<Map<String, Object>> authoritiesWithLevel = buildAuthoritiesWithMaxNivel(user);

        Map<String, Object> response = new HashMap<>();
        response.put("user", userData);
        response.put("accesos", accesosList);
        response.put("authorities", authoritiesWithLevel);

        return ResponseEntity.ok(response);
    }

    /**
     * Una entrada por módulo; nivel = máximo entre tabs (compatibilidad con clientes que leen un solo nivel).
     */
    private static List<Map<String, Object>> buildAuthoritiesWithMaxNivel(User user) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (ModuloAcceso ma : user.getModuloAccesos()) {
            int maxNivel = ma.getTabs().stream().mapToInt(TabAcceso::getNivel).max().orElse(1);
            Map<String, Object> row = new HashMap<>();
            row.put("authority", "ACCESO_" + ma.getModulo().name());
            row.put("nivel", String.valueOf(maxNivel));
            out.add(row);
        }
        return out;
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
