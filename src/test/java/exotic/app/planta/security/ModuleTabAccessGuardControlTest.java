package exotic.app.planta.security;

import exotic.app.planta.model.users.*;
import exotic.app.planta.repo.usuarios.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.http.HttpStatus.FORBIDDEN;

class ModuleTabAccessGuardControlTest {

    @Test
    void nuevosControles_soloSuperMasterTieneBypassInicial() {
        UserRepository repo = mock(UserRepository.class);
        ModuleTabAccessGuard guard = new ModuleTabAccessGuard(repo);
        User master = usuario("master");
        User superMaster = usuario("super_master");
        when(repo.findByUsername("master")).thenReturn(Optional.of(master));
        when(repo.findByUsername("super_master")).thenReturn(Optional.of(superMaster));

        ResponseStatusException denied = assertThrows(ResponseStatusException.class,
                () -> guard.requireTabAccessWithSuperMasterBypass(auth("master"),
                        ModuloSistema.PRODUCCION, "PLANES_CONTROL_PROCESO", 3, "Sin permiso"));
        assertEquals(FORBIDDEN, denied.getStatusCode());
        assertSame(superMaster, guard.requireTabAccessWithSuperMasterBypass(auth("super_master"),
                ModuloSistema.PRODUCCION, "PLANES_CONTROL_PROCESO", 3, "Sin permiso"));
    }

    @Test
    void disposicionRegulada_noTieneBypassPorNombre() {
        UserRepository repo = mock(UserRepository.class);
        ModuleTabAccessGuard guard = new ModuleTabAccessGuard(repo);
        for (String username : List.of("master", "super_master")) {
            when(repo.findByUsername(username)).thenReturn(Optional.of(usuario(username)));
            ResponseStatusException denied = assertThrows(ResponseStatusException.class,
                    () -> guard.requireTabAccessWithoutMasterBypass(auth(username),
                            ModuloSistema.CALIDAD, "DESVIACIONES_CONTROL_CALIDAD", 3, "Sin permiso"));
            assertEquals(FORBIDDEN, denied.getStatusCode());
        }
    }

    @Test
    void masterConAsignacionExplicita_puedeAdministrarPlanes() {
        UserRepository repo = mock(UserRepository.class);
        ModuleTabAccessGuard guard = new ModuleTabAccessGuard(repo);
        User master = usuario("master");
        ModuloAcceso modulo = ModuloAcceso.builder()
                .user(master).modulo(ModuloSistema.PRODUCCION).tabs(new HashSet<>()).build();
        TabAcceso tab = TabAcceso.builder().tabId("PLANES_CONTROL_PROCESO").nivel(3).build();
        tab.setModuloAcceso(modulo);
        modulo.getTabs().add(tab);
        master.getModuloAccesos().add(modulo);
        when(repo.findByUsername("master")).thenReturn(Optional.of(master));

        assertSame(master, guard.requireTabAccessWithSuperMasterBypass(auth("master"),
                ModuloSistema.PRODUCCION, "PLANES_CONTROL_PROCESO", 3, "Sin permiso"));
    }

    private static UsernamePasswordAuthenticationToken auth(String username) {
        return new UsernamePasswordAuthenticationToken(username, null, List.of());
    }

    private static User usuario(String username) {
        User user = new User();
        user.setId(1L);
        user.setUsername(username);
        user.setModuloAccesos(new HashSet<>());
        return user;
    }
}
