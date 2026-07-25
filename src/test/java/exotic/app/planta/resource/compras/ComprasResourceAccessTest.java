package exotic.app.planta.resource.compras;

import exotic.app.planta.model.compras.OrdenCompraMateriales;
import exotic.app.planta.model.compras.dto.UpdateEstadoOrdenCompraRequest;
import exotic.app.planta.model.users.ModuloAcceso;
import exotic.app.planta.model.users.ModuloSistema;
import exotic.app.planta.model.users.TabAcceso;
import exotic.app.planta.model.users.User;
import exotic.app.planta.repo.usuarios.UserRepository;
import exotic.app.planta.service.compras.ComprasService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

class ComprasResourceAccessTest {

    private ComprasService comprasService;
    private UserRepository userRepository;
    private ComprasResource resource;

    @BeforeEach
    void setUp() {
        comprasService = mock(ComprasService.class);
        userRepository = mock(UserRepository.class);
        resource = new ComprasResource(comprasService, userRepository);
    }

    @Test
    void createOrder_passesAuthenticatedUserToService() {
        User user = userWithComprasTabs("creador");
        OrdenCompraMateriales request = new OrdenCompraMateriales();
        OrdenCompraMateriales saved = new OrdenCompraMateriales();
        saved.setOrdenCompraId(101);
        when(userRepository.findByUsername(user.getUsername())).thenReturn(Optional.of(user));
        when(comprasService.saveOrdenCompra(request, user)).thenReturn(saved);

        ResponseEntity<OrdenCompraMateriales> response = resource.saveOrdenCompra(
                request,
                auth(user.getUsername())
        );

        assertEquals(CREATED, response.getStatusCode());
        verify(comprasService).saveOrdenCompra(request, user);
    }

    @Test
    void createOrder_blocksUnauthenticatedRequest() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> resource.saveOrdenCompra(new OrdenCompraMateriales(), null)
        );

        assertEquals(UNAUTHORIZED, exception.getStatusCode());
        verifyNoInteractions(comprasService, userRepository);
    }

    @Test
    void releaseOrder_blocksReportesLevelOne() {
        User user = userWithComprasTabs(
                "compras_nivel_1",
                tab("REPORTES_ORDENES_COMPRA", 1)
        );
        when(userRepository.findByUsername(user.getUsername())).thenReturn(Optional.of(user));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> releaseOrderAs(user.getUsername())
        );

        assertEquals(FORBIDDEN, exception.getStatusCode());
        verifyNoInteractions(comprasService);
    }

    @Test
    void releaseOrder_doesNotUseLevelFromAnotherComprasTab() {
        User user = userWithComprasTabs(
                "creador_nivel_2",
                tab("CREAR_OCM", 2),
                tab("REPORTES_ORDENES_COMPRA", 1)
        );
        when(userRepository.findByUsername(user.getUsername())).thenReturn(Optional.of(user));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> releaseOrderAs(user.getUsername())
        );

        assertEquals(FORBIDDEN, exception.getStatusCode());
        verifyNoInteractions(comprasService);
    }

    @Test
    void releaseOrder_blocksUserWithoutReportesTab() {
        User user = userWithComprasTabs(
                "sin_reportes",
                tab("CREAR_OCM", 4)
        );
        when(userRepository.findByUsername(user.getUsername())).thenReturn(Optional.of(user));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> releaseOrderAs(user.getUsername())
        );

        assertEquals(FORBIDDEN, exception.getStatusCode());
        verifyNoInteractions(comprasService);
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 3, 4})
    void releaseOrder_allowsReportesLevelTwoOrHigher(int nivel) {
        User user = userWithComprasTabs(
                "reportes_nivel_" + nivel,
                tab("REPORTES_ORDENES_COMPRA", nivel)
        );
        UpdateEstadoOrdenCompraRequest request = releaseRequest();
        when(userRepository.findByUsername(user.getUsername())).thenReturn(Optional.of(user));
        when(comprasService.updateEstadoOrdenCompra(101, request, user))
                .thenReturn(new OrdenCompraMateriales());

        ResponseEntity<?> response = resource.updateEstadoOrdenCompra(
                101,
                request,
                null,
                auth(user.getUsername())
        );

        assertEquals(OK, response.getStatusCode());
        verify(comprasService).updateEstadoOrdenCompra(101, request, user);
    }

    @ParameterizedTest
    @ValueSource(strings = {"master", "super_master"})
    void releaseOrder_allowsMasterLikeUsers(String username) {
        User user = userWithComprasTabs(username);
        UpdateEstadoOrdenCompraRequest request = releaseRequest();
        when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));
        when(comprasService.updateEstadoOrdenCompra(101, request, user))
                .thenReturn(new OrdenCompraMateriales());

        ResponseEntity<?> response = resource.updateEstadoOrdenCompra(
                101,
                request,
                null,
                auth(username)
        );

        assertEquals(OK, response.getStatusCode());
        verify(comprasService).updateEstadoOrdenCompra(101, request, user);
    }

    @Test
    void releaseOrder_blocksUnauthenticatedRequest() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> resource.updateEstadoOrdenCompra(101, releaseRequest(), null, null)
        );

        assertEquals(UNAUTHORIZED, exception.getStatusCode());
        verifyNoInteractions(comprasService, userRepository);
    }

    private ResponseEntity<?> releaseOrderAs(String username) {
        return resource.updateEstadoOrdenCompra(101, releaseRequest(), null, auth(username));
    }

    private static UpdateEstadoOrdenCompraRequest releaseRequest() {
        UpdateEstadoOrdenCompraRequest request = new UpdateEstadoOrdenCompraRequest();
        request.setNewEstado(1);
        return request;
    }

    private static Authentication auth(String username) {
        return new UsernamePasswordAuthenticationToken(username, null, List.of());
    }

    private static User userWithComprasTabs(String username, TabAcceso... tabs) {
        User user = new User();
        user.setId(1L);
        user.setUsername(username);

        if (tabs.length > 0) {
            ModuloAcceso compras = ModuloAcceso.builder()
                    .user(user)
                    .modulo(ModuloSistema.COMPRAS)
                    .tabs(new HashSet<>(List.of(tabs)))
                    .build();
            for (TabAcceso tab : tabs) {
                tab.setModuloAcceso(compras);
            }
            user.setModuloAccesos(new HashSet<>(List.of(compras)));
        }

        return user;
    }

    private static TabAcceso tab(String tabId, int nivel) {
        return TabAcceso.builder()
                .tabId(tabId)
                .nivel(nivel)
                .build();
    }
}
