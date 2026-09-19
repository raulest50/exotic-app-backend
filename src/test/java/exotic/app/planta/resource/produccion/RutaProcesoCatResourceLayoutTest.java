package exotic.app.planta.resource.produccion;

import exotic.app.planta.model.users.ModuloSistema;
import exotic.app.planta.security.ModuleTabAccessGuard;
import exotic.app.planta.service.produccion.RutaProcesoCatService;
import exotic.app.planta.service.produccion.RutaProcesoCatService.RutaProcesoCatDTO;
import exotic.app.planta.service.produccion.RutaProcesoCatService.RutaProcesoLayoutUpdateDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class RutaProcesoCatResourceLayoutTest {

    @Mock private RutaProcesoCatService service;
    @Mock private ModuleTabAccessGuard accessGuard;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new RutaProcesoCatResource(service, accessGuard))
                .build();
    }

    @Test
    void actualizaLayoutConPermisoNivelTresYDevuelveContratoAuditado() throws Exception {
        Authentication authentication = authentication("diagramador");
        RutaProcesoCatDTO response = new RutaProcesoCatDTO();
        response.setCategoriaId(7);
        response.setVersionId(31L);
        response.setVersionNumber(4);
        response.setEstado("VIGENTE");
        response.setLayoutRevision(3);
        response.setLayoutActualizadoPor("diagramador");
        when(service.updateLayout(eq(7), eq(31L), any(RutaProcesoLayoutUpdateDTO.class), eq("diagramador")))
                .thenReturn(response);

        mockMvc.perform(patch("/api/ruta-proceso-cat/7/versiones/31/layout")
                        .principal(authentication)
                        .contentType("application/json")
                        .content("""
                                {
                                  "layoutRevision": 2,
                                  "nodes": [
                                    {"id": "almacen", "posicionX": 10.12, "posicionY": 20.34},
                                    {"id": "mezcla", "posicionX": 30.56, "posicionY": 40.78}
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versionId").value(31))
                .andExpect(jsonPath("$.versionNumber").value(4))
                .andExpect(jsonPath("$.layoutRevision").value(3))
                .andExpect(jsonPath("$.layoutActualizadoPor").value("diagramador"));

        verify(accessGuard).requireTabAccess(
                eq(authentication),
                eq(ModuloSistema.PRODUCCION),
                eq("PARAMETROS_POR_CATEGORIA"),
                eq(3),
                anyString());
        ArgumentCaptor<RutaProcesoLayoutUpdateDTO> captor =
                ArgumentCaptor.forClass(RutaProcesoLayoutUpdateDTO.class);
        verify(service).updateLayout(eq(7), eq(31L), captor.capture(), eq("diagramador"));
        assertThat(captor.getValue().getLayoutRevision()).isEqualTo(2);
        assertThat(captor.getValue().getNodes()).hasSize(2);
        assertThat(captor.getValue().getNodes().get(0).getPosicionX()).isEqualTo(10.12);
    }

    @Test
    void payloadInvalidoDevuelveBadRequest() throws Exception {
        Authentication authentication = authentication("diagramador");
        when(service.updateLayout(eq(7), eq(31L), any(RutaProcesoLayoutUpdateDTO.class), eq("diagramador")))
                .thenThrow(new IllegalArgumentException("Las coordenadas deben ser números finitos."));

        mockMvc.perform(patch("/api/ruta-proceso-cat/7/versiones/31/layout")
                        .principal(authentication)
                        .contentType("application/json")
                        .content("""
                                {"layoutRevision": 2, "nodes": [{"id": "almacen"}]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Disposición visual inválida"));
    }

    @Test
    void concurrenciaDevuelveConflict() throws Exception {
        Authentication authentication = authentication("diagramador");
        when(service.updateLayout(eq(7), eq(31L), any(RutaProcesoLayoutUpdateDTO.class), eq("diagramador")))
                .thenThrow(new IllegalStateException("La revisión visual cambió."));

        mockMvc.perform(patch("/api/ruta-proceso-cat/7/versiones/31/layout")
                        .principal(authentication)
                        .contentType("application/json")
                        .content("""
                                {"layoutRevision": 1, "nodes": [{"id": "almacen", "posicionX": 1, "posicionY": 2}]}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Conflicto al actualizar disposición"));
    }

    @Test
    void permisoInsuficienteDevuelveForbiddenAntesDelServicio() throws Exception {
        Authentication authentication = mock(Authentication.class);
        when(accessGuard.requireTabAccess(
                eq(authentication),
                eq(ModuloSistema.PRODUCCION),
                eq("PARAMETROS_POR_CATEGORIA"),
                eq(3),
                anyString()))
                .thenThrow(new ResponseStatusException(FORBIDDEN, "Sin permiso"));

        mockMvc.perform(patch("/api/ruta-proceso-cat/7/versiones/31/layout")
                        .principal(authentication)
                        .contentType("application/json")
                        .content("""
                                {"layoutRevision": 2, "nodes": [{"id": "almacen", "posicionX": 1, "posicionY": 2}]}
                                """))
                .andExpect(status().isForbidden());

        verifyNoInteractions(service);
    }

    private static Authentication authentication(String username) {
        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn(username);
        when(authentication.isAuthenticated()).thenReturn(true);
        return authentication;
    }
}
