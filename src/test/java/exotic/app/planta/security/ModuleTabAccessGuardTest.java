package exotic.app.planta.security;

import exotic.app.planta.model.users.ModuloAcceso;
import exotic.app.planta.model.users.ModuloSistema;
import exotic.app.planta.model.users.TabAcceso;
import exotic.app.planta.model.users.User;
import exotic.app.planta.repo.usuarios.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ModuleTabAccessGuardTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private Authentication authentication;

    private ModuleTabAccessGuard guard;

    @BeforeEach
    void setUp() {
        guard = new ModuleTabAccessGuard(userRepository);
        when(authentication.isAuthenticated()).thenReturn(true);
    }

    @Test
    void masterAccedeSinPermisosExplicitosAlNivelMaximo() {
        User master = authenticate("master");

        User result = guard.requireTabAccess(
                authentication, ModuloSistema.CALIDAD, "REVISION_LIBERACION_LOTES", 3, "Sin acceso");

        assertSame(master, result);
    }

    @Test
    void superMasterAccedeSinPermisosExplicitosAlNivelMaximo() {
        User superMaster = authenticate("super_master");

        User result = guard.requireTabAccess(
                authentication, ModuloSistema.CALIDAD, "DESVIACIONES_CONTROL_CALIDAD", 3, "Sin acceso");

        assertSame(superMaster, result);
    }

    @Test
    void usuarioOrdinarioSinPermisoExplicitoRecibeForbidden() {
        authenticate("analista_calidad");

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () ->
                guard.requireTabAccess(
                        authentication, ModuloSistema.CALIDAD,
                        "REGISTRAR_CONTROL_CALIDAD", 1, "Sin acceso"));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
    }

    @Test
    void usuarioOrdinarioConservaElNivelExplicito() {
        User analyst = authenticate("analista_calidad");
        addTab(analyst, ModuloSistema.CALIDAD, "REGISTRAR_CONTROL_CALIDAD", 2);

        assertSame(analyst, guard.requireTabAccess(
                authentication, ModuloSistema.CALIDAD,
                "REGISTRAR_CONTROL_CALIDAD", 2, "Sin acceso"));
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () ->
                guard.requireTabAccess(
                        authentication, ModuloSistema.CALIDAD,
                        "REGISTRAR_CONTROL_CALIDAD", 3, "Sin acceso"));
        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
    }

    @Test
    void masterAccedeAComprobacionDeCualquieraDeLosTabs() {
        User master = authenticate("master");

        User result = guard.requireAnyTabAccess(
                authentication,
                ModuloSistema.CALIDAD,
                Map.of("REGISTRAR_CONTROL_CALIDAD", 1, "PLANES_CONTROL_CALIDAD", 3),
                "Sin acceso");

        assertSame(master, result);
    }

    @Test
    void recursoCompartidoAceptaUnPermisoExplicitoDeCualquieraDeLosModulos() {
        User planner = authenticate("planeador");
        addTab(planner, ModuloSistema.PRODUCCION, "PLANES_CONTROL_PROCESO", 3);

        User result = guard.requireAnyTabAccess(
                authentication,
                Map.of(
                        ModuloSistema.PRODUCCION, Map.of("PLANES_CONTROL_PROCESO", 3),
                        ModuloSistema.CALIDAD, Map.of("PLANES_CONTROL_CALIDAD", 3)),
                "Sin acceso");

        assertSame(planner, result);
    }

    private User authenticate(String username) {
        User user = User.builder().username(username).build();
        when(authentication.getName()).thenReturn(username);
        when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));
        return user;
    }

    private void addTab(User user, ModuloSistema modulo, String tabId, int nivel) {
        ModuloAcceso moduloAcceso = ModuloAcceso.builder()
                .user(user)
                .modulo(modulo)
                .build();
        TabAcceso tabAcceso = TabAcceso.builder()
                .moduloAcceso(moduloAcceso)
                .tabId(tabId)
                .nivel(nivel)
                .build();
        moduloAcceso.getTabs().add(tabAcceso);
        user.getModuloAccesos().add(moduloAcceso);
    }
}
