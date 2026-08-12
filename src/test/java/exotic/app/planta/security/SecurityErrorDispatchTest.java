package exotic.app.planta.security;

import exotic.app.planta.config.CorsConfig;
import exotic.app.planta.config.SecurityConfig;
import exotic.app.planta.config.runtime.ApplicationRuntimeEnvironmentResolver;
import exotic.app.planta.service.users.UserDetailsServiceImpl;
import jakarta.servlet.DispatcherType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SecurityErrorDispatchProbeController.class)
@Import({
        SecurityConfig.class,
        CorsConfig.class,
        RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class
})
class SecurityErrorDispatchTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private MigrationAuthenticationProvider migrationAuthenticationProvider;

    @MockBean
    private PasswordEncoder passwordEncoder;

    @MockBean
    private ApplicationRuntimeEnvironmentResolver runtimeEnvironmentResolver;

    @MockBean
    private UserDetailsServiceImpl userDetailsService;

    @Test
    void errorDispatchBypassesAuthenticationAndPreservesInternalServerError() throws Exception {
        mockMvc.perform(get("/security-test/error-dispatch")
                        .with(request -> {
                            request.setDispatcherType(DispatcherType.ERROR);
                            return request;
                        }))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void regularProtectedRequestWithoutAuthenticationRemainsUnauthorized() throws Exception {
        mockMvc.perform(get("/security-test/protected"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(RestAuthenticationEntryPoint.AUTHENTICATION_REQUIRED));
    }

}

@RestController
class SecurityErrorDispatchProbeController {

    @GetMapping("/security-test/error-dispatch")
    ResponseEntity<Void> errorDispatch() {
        return ResponseEntity.internalServerError().build();
    }

    @GetMapping("/security-test/protected")
    ResponseEntity<Void> protectedEndpoint() {
        return ResponseEntity.noContent().build();
    }
}
