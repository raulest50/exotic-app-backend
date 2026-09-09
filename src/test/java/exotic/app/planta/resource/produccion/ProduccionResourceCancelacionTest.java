package exotic.app.planta.resource.produccion;

import exotic.app.planta.model.produccion.dto.OrdenProduccionDTO;
import exotic.app.planta.model.users.ModuloSistema;
import exotic.app.planta.model.users.User;
import exotic.app.planta.repo.inventarios.LoteRepo;
import exotic.app.planta.repo.usuarios.UserRepository;
import exotic.app.planta.security.ModuleTabAccessGuard;
import exotic.app.planta.service.produccion.ProduccionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ProduccionResourceCancelacionTest {

    @Mock private ProduccionService produccionService;
    @Mock private LoteRepo loteRepo;
    @Mock private UserRepository userRepository;
    @Mock private ModuleTabAccessGuard accessGuard;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ProduccionResource resource = new ProduccionResource(
                produccionService, loteRepo, userRepository, accessGuard);
        mockMvc = MockMvcBuilders.standaloneSetup(resource).build();
    }

    @Test
    void cancelabilidadUsaPermisoExactoYContratoCancelable() throws Exception {
        Authentication authentication = authentication("supervisor");
        User actor = User.builder().username("supervisor").build();
        when(accessGuard.requireTabAccess(
                eq(authentication), eq(ModuloSistema.PRODUCCION), eq("HISTORIAL"), eq(2), anyString()))
                .thenReturn(actor);
        when(produccionService.isOrdenProduccionCancelable(21)).thenReturn(true);

        mockMvc.perform(get("/produccion/orden_produccion/21/is_deletable")
                        .principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cancelable").value(true));
    }

    @Test
    void cancelacionUsaElActorAutorizadoYDevuelveLaAuditoria() throws Exception {
        Authentication authentication = authentication("supervisor");
        User actor = User.builder().id(7L).username("supervisor").build();
        LocalDateTime canceladaEn = LocalDateTime.of(2026, 9, 9, 10, 30);
        OrdenProduccionDTO dto = new OrdenProduccionDTO();
        dto.setOrdenId(21);
        dto.setEstadoOrden(-1);
        dto.setCanceladaEn(canceladaEn);
        dto.setCanceladaPorUsername("supervisor");
        dto.setCanceladaPorNombreCompleto("Supervisora de Producción");
        when(accessGuard.requireTabAccess(
                eq(authentication), eq(ModuloSistema.PRODUCCION), eq("HISTORIAL"), eq(2), anyString()))
                .thenReturn(actor);
        when(produccionService.cancelarOrdenProduccion(21, actor)).thenReturn(dto);

        mockMvc.perform(put("/produccion/orden_produccion/21/cancel")
                        .principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estadoOrden").value(-1))
                .andExpect(jsonPath("$.canceladaPorUsername").value("supervisor"))
                .andExpect(jsonPath("$.canceladaPorNombreCompleto")
                        .value("Supervisora de Producción"));

        verify(produccionService).cancelarOrdenProduccion(21, actor);
    }

    @Test
    void nivelInsuficienteRecibeForbiddenAntesDeConsultarElServicio() throws Exception {
        Authentication authentication = authentication("lector");
        when(accessGuard.requireTabAccess(
                eq(authentication), eq(ModuloSistema.PRODUCCION), eq("HISTORIAL"), eq(2), anyString()))
                .thenThrow(new ResponseStatusException(FORBIDDEN, "Sin permiso"));

        mockMvc.perform(get("/produccion/orden_produccion/21/is_deletable")
                        .principal(authentication))
                .andExpect(status().isForbidden());
    }

    private static Authentication authentication(String username) {
        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn(username);
        return authentication;
    }
}
