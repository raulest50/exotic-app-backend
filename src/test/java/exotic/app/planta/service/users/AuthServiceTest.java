package exotic.app.planta.service.users;

import exotic.app.planta.config.runtime.ApplicationRuntimeEnvironmentResolver;
import exotic.app.planta.model.users.User;
import exotic.app.planta.model.users.auth.PasswordResetToken;
import exotic.app.planta.repo.usuarios.PasswordResetTokenRepository;
import exotic.app.planta.repo.usuarios.UserRepository;
import exotic.app.planta.security.JwtTokenProvider;
import exotic.app.planta.service.commons.EmailService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AuthServiceTest {

    @Test
    void authenticateUser_inactiveUser_throwsException() {
        UserRepository userRepo = Mockito.mock(UserRepository.class);
        AuthenticationManager authManager = Mockito.mock(AuthenticationManager.class);
        JwtTokenProvider tokenProvider = Mockito.mock(JwtTokenProvider.class);
        PasswordResetTokenRepository tokenRepo = Mockito.mock(PasswordResetTokenRepository.class);
        EmailService emailService = Mockito.mock(EmailService.class);
        ApplicationRuntimeEnvironmentResolver environmentResolver =
                Mockito.mock(ApplicationRuntimeEnvironmentResolver.class);

        AuthService service = new AuthService(
                userRepo,
                authManager,
                tokenProvider,
                tokenRepo,
                emailService,
                environmentResolver
        );

        User inactive = new User();
        inactive.setEstado(2);
        when(userRepo.findByUsername("user")).thenReturn(Optional.of(inactive));

        assertThrows(AuthenticationException.class, () -> service.authenticateUser("user", "pass"));
        verify(authManager, never()).authenticate(any());
    }

    @Test
    void authenticateUser_masterInactive_allowsLogin() {
        UserRepository userRepo = Mockito.mock(UserRepository.class);
        AuthenticationManager authManager = Mockito.mock(AuthenticationManager.class);
        JwtTokenProvider tokenProvider = Mockito.mock(JwtTokenProvider.class);
        PasswordResetTokenRepository tokenRepo = Mockito.mock(PasswordResetTokenRepository.class);
        EmailService emailService = Mockito.mock(EmailService.class);
        ApplicationRuntimeEnvironmentResolver environmentResolver =
                Mockito.mock(ApplicationRuntimeEnvironmentResolver.class);

        AuthService service = new AuthService(
                userRepo,
                authManager,
                tokenProvider,
                tokenRepo,
                emailService,
                environmentResolver
        );

        User master = new User();
        master.setEstado(2); // inactive but should still authenticate
        master.setUsername("master");
        when(userRepo.findByUsername("master")).thenReturn(Optional.of(master));

        Authentication auth = Mockito.mock(Authentication.class);
        when(authManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(auth);
        when(tokenProvider.generateToken(auth)).thenReturn("jwt");

        Map<String, String> result = service.authenticateUser("master", "pass");

        assertEquals("jwt", result.get("token"));
        verify(authManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
    }

    @Test
    void requestPasswordReset_inactiveUser_returnsFalse() throws jakarta.mail.MessagingException {
        UserRepository userRepo = Mockito.mock(UserRepository.class);
        AuthenticationManager authManager = Mockito.mock(AuthenticationManager.class);
        JwtTokenProvider tokenProvider = Mockito.mock(JwtTokenProvider.class);
        PasswordResetTokenRepository tokenRepo = Mockito.mock(PasswordResetTokenRepository.class);
        EmailService emailService = Mockito.mock(EmailService.class);
        ApplicationRuntimeEnvironmentResolver environmentResolver =
                Mockito.mock(ApplicationRuntimeEnvironmentResolver.class);

        AuthService service = new AuthService(
                userRepo,
                authManager,
                tokenProvider,
                tokenRepo,
                emailService,
                environmentResolver
        );

        User inactive = new User();
        inactive.setEstado(2);
        inactive.setUsername("user");
        inactive.setNombreCompleto("User Test");
        inactive.setEmail("user@test.com");
        when(userRepo.findByEmail("user@test.com")).thenReturn(Optional.of(inactive));

        boolean result = service.requestPasswordReset("user@test.com");

        assertFalse(result);
        verify(tokenRepo, never()).save(any(PasswordResetToken.class));
        verify(emailService, never()).sendHtmlEmail(anyString(), anyString(), anyString());
    }

    @Test
    void getDomain_usesFrontendUrlForEachRuntimeEnvironment() {
        UserRepository userRepo = Mockito.mock(UserRepository.class);
        AuthenticationManager authManager = Mockito.mock(AuthenticationManager.class);
        JwtTokenProvider tokenProvider = Mockito.mock(JwtTokenProvider.class);
        PasswordResetTokenRepository tokenRepo = Mockito.mock(PasswordResetTokenRepository.class);
        EmailService emailService = Mockito.mock(EmailService.class);
        ApplicationRuntimeEnvironmentResolver environmentResolver =
                Mockito.mock(ApplicationRuntimeEnvironmentResolver.class);

        AuthService service = new AuthService(
                userRepo,
                authManager,
                tokenProvider,
                tokenRepo,
                emailService,
                environmentResolver
        );

        when(environmentResolver.getCurrentEnvironment())
                .thenReturn(ApplicationRuntimeEnvironmentResolver.ApplicationRuntimeEnvironment.LOCAL);
        assertEquals("http://localhost:5173", service.getDomain());

        when(environmentResolver.getCurrentEnvironment())
                .thenReturn(ApplicationRuntimeEnvironmentResolver.ApplicationRuntimeEnvironment.STAGING);
        assertEquals("https://exotic-app-frontend-staging.onrender.com", service.getDomain());

        when(environmentResolver.getCurrentEnvironment())
                .thenReturn(ApplicationRuntimeEnvironmentResolver.ApplicationRuntimeEnvironment.PRODUCTION);
        assertEquals("https://lac-manufacture-frontend.onrender.com", service.getDomain());
    }

    @Test
    void requestPasswordReset_stagingUsesStagingFrontendUrl() throws jakarta.mail.MessagingException {
        UserRepository userRepo = Mockito.mock(UserRepository.class);
        AuthenticationManager authManager = Mockito.mock(AuthenticationManager.class);
        JwtTokenProvider tokenProvider = Mockito.mock(JwtTokenProvider.class);
        PasswordResetTokenRepository tokenRepo = Mockito.mock(PasswordResetTokenRepository.class);
        EmailService emailService = Mockito.mock(EmailService.class);
        ApplicationRuntimeEnvironmentResolver environmentResolver =
                Mockito.mock(ApplicationRuntimeEnvironmentResolver.class);

        AuthService service = new AuthService(
                userRepo,
                authManager,
                tokenProvider,
                tokenRepo,
                emailService,
                environmentResolver
        );

        User active = new User();
        active.setEstado(1);
        active.setUsername("staging-user");
        active.setNombreCompleto("Staging User");
        active.setEmail("staging-user@test.com");

        when(userRepo.findByEmail("staging-user@test.com")).thenReturn(Optional.of(active));
        when(environmentResolver.getCurrentEnvironment())
                .thenReturn(ApplicationRuntimeEnvironmentResolver.ApplicationRuntimeEnvironment.STAGING);

        assertTrue(service.requestPasswordReset("staging-user@test.com"));

        ArgumentCaptor<String> contentCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendHtmlEmail(
                eq("staging-user@test.com"),
                eq("Solicitud de Restablecimiento de Contraseña"),
                contentCaptor.capture()
        );
        assertTrue(contentCaptor.getValue().contains(
                "href='https://exotic-app-frontend-staging.onrender.com/reset-password?token="
        ));
    }
}
