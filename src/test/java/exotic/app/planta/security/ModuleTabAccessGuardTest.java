package exotic.app.planta.security;

import exotic.app.planta.model.users.ModuloAcceso;
import exotic.app.planta.model.users.ModuloSistema;
import exotic.app.planta.model.users.TabAcceso;
import exotic.app.planta.model.users.User;
import exotic.app.planta.repo.usuarios.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

class ModuleTabAccessGuardTest {

    private UserRepository userRepository;
    private ModuleTabAccessGuard guard;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        guard = new ModuleTabAccessGuard(userRepository);
    }

    @Test
    void requireTabAccess_doesNotBorrowLevelFromAnotherTab() {
        User user = userWithTabs(
                "editor_mision",
                tab("ORGANIGRAMA", 1),
                tab("MISION_VISION", 4)
        );
        when(userRepository.findByUsername(user.getUsername())).thenReturn(Optional.of(user));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> guard.requireTabAccess(
                        auth(user.getUsername()),
                        ModuloSistema.ORGANIGRAMA,
                        "ORGANIGRAMA",
                        2,
                        "Sin permiso"
                )
        );

        assertEquals(FORBIDDEN, exception.getStatusCode());
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 3, 4})
    void requireTabAccess_allowsLevelTwoOrHigher(int level) {
        User user = userWithTabs("editor_" + level, tab("MISION_VISION", level));
        when(userRepository.findByUsername(user.getUsername())).thenReturn(Optional.of(user));

        User result = guard.requireTabAccess(
                auth(user.getUsername()),
                ModuloSistema.ORGANIGRAMA,
                "MISION_VISION",
                2,
                "Sin permiso"
        );

        assertSame(user, result);
    }

    @ParameterizedTest
    @ValueSource(strings = {"master", "super_master"})
    void requireTabAccess_allowsMasterLikeWithoutTabs(String username) {
        User user = userWithTabs(username);
        when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));

        assertSame(user, guard.requireTabAccess(
                auth(username),
                ModuloSistema.ORGANIGRAMA,
                "MISION_VISION",
                2,
                "Sin permiso"
        ));
    }

    @Test
    void requireTabAccess_rejectsUnauthenticatedRequest() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> guard.requireTabAccess(
                        null,
                        ModuloSistema.ORGANIGRAMA,
                        "MISION_VISION",
                        1,
                        "Sin permiso"
                )
        );

        assertEquals(UNAUTHORIZED, exception.getStatusCode());
    }

    private static Authentication auth(String username) {
        return new UsernamePasswordAuthenticationToken(username, null, List.of());
    }

    private static User userWithTabs(String username, TabAcceso... tabs) {
        User user = new User();
        user.setId(1L);
        user.setUsername(username);
        if (tabs.length == 0) return user;

        ModuloAcceso module = ModuloAcceso.builder()
                .user(user)
                .modulo(ModuloSistema.ORGANIGRAMA)
                .tabs(new HashSet<>(List.of(tabs)))
                .build();
        for (TabAcceso tab : tabs) {
            tab.setModuloAcceso(module);
        }
        user.setModuloAccesos(new HashSet<>(List.of(module)));
        return user;
    }

    private static TabAcceso tab(String id, int level) {
        return TabAcceso.builder().tabId(id).nivel(level).build();
    }
}
