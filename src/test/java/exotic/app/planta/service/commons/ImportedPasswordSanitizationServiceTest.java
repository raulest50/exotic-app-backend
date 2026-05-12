package exotic.app.planta.service.commons;

import exotic.app.planta.config.PasswordConfig;
import exotic.app.planta.model.users.User;
import exotic.app.planta.repo.usuarios.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ImportedPasswordSanitizationServiceTest {

    private UserRepository userRepository;
    private DangerousOperationGuard dangerousOperationGuard;
    private ImportedPasswordSanitizationService service;

    @BeforeEach
    void setUp() {
        userRepository = Mockito.mock(UserRepository.class);
        dangerousOperationGuard = Mockito.mock(DangerousOperationGuard.class);
        service = new ImportedPasswordSanitizationService(userRepository, dangerousOperationGuard);
    }

    @Test
    void sanitizeImportedUserPasswords_updatesActiveAndInactiveNonPrivilegedUsersOnly() {
        User master = user("master", "master-old-password", 1);
        User superMaster = user("SUPER_MASTER", "super-master-old-password", 1);
        User activeUser = user("operator", "operator-old-password", 1);
        User inactiveUser = user("inactive_operator", "inactive-old-password", 2);
        User nullUsername = user(null, "null-username-old-password", 1);
        User blankUsername = user("   ", "blank-username-old-password", 1);
        when(userRepository.findAll()).thenReturn(List.of(
                master,
                superMaster,
                activeUser,
                inactiveUser,
                nullUsername,
                blankUsername
        ));

        ImportedPasswordSanitizationService.PasswordSanitizationResult result =
                service.sanitizeNonPrivilegedUserPasswords();

        assertEquals(2, result.sanitizedUsers());
        assertEquals(2, result.privilegedUsersSkipped());
        assertEquals(2, result.invalidUsersSkipped());

        PasswordEncoder passwordEncoder = new PasswordConfig.Argon2PasswordEncoder();
        assertTrue(passwordEncoder.matches("staging1234", activeUser.getPassword()));
        assertTrue(passwordEncoder.matches("staging1234", inactiveUser.getPassword()));
        assertEquals("master-old-password", master.getPassword());
        assertEquals("super-master-old-password", superMaster.getPassword());
        assertEquals("null-username-old-password", nullUsername.getPassword());
        assertEquals("blank-username-old-password", blankUsername.getPassword());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<User>> savedUsersCaptor = ArgumentCaptor.forClass(Iterable.class);
        verify(userRepository).saveAll(savedUsersCaptor.capture());

        List<User> savedUsers = new ArrayList<>();
        savedUsersCaptor.getValue().forEach(savedUsers::add);
        assertEquals(List.of(activeUser, inactiveUser), savedUsers);
    }

    @Test
    void sanitizeImportedUserPasswords_whenEnvironmentIsBlocked_throwsBeforeTouchingUsers() {
        doThrow(new UnsupportedOperationException("blocked"))
                .when(dangerousOperationGuard)
                .assertLocalOrStagingOnly(anyString());

        assertThrows(
                UnsupportedOperationException.class,
                () -> service.sanitizeNonPrivilegedUserPasswords()
        );

        verify(dangerousOperationGuard).assertLocalOrStagingOnly(anyString());
        verifyNoInteractions(userRepository);
    }

    private User user(String username, String password, int estado) {
        User user = new User();
        user.setUsername(username);
        user.setPassword(password);
        user.setEstado(estado);
        return user;
    }
}
