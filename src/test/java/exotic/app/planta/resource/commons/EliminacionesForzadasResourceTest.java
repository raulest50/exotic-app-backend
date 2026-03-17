package exotic.app.planta.resource.commons;

import exotic.app.planta.model.commons.dto.eliminaciones.EliminacionTerminadosBatchResultDTO;
import exotic.app.planta.service.commons.DangerousOperationGuard;
import exotic.app.planta.service.commons.EliminacionesForzadasService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(EliminacionesForzadasResource.class)
@AutoConfigureMockMvc(addFilters = false)
class EliminacionesForzadasResourceTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EliminacionesForzadasService eliminacionesForzadasService;

    @MockBean
    private DangerousOperationGuard dangerousOperationGuard;

    @Test
    void ejecutarPurgaTotalTerminados_returnsSummary() throws Exception {
        EliminacionTerminadosBatchResultDTO dto = new EliminacionTerminadosBatchResultDTO();
        dto.setPermitted(true);
        dto.setExecuted(true);
        dto.setMessage("Purga total de terminados completada.");
        dto.setTotalTerminados(3);
        dto.setEliminados(3);
        dto.setFallidos(0);

        doNothing().when(dangerousOperationGuard).assertNotProduction("La purga total de terminados");
        when(eliminacionesForzadasService.ejecutarEliminacionTodosLosTerminados()).thenReturn(dto);

        mockMvc.perform(delete("/api/eliminaciones-forzadas/terminados")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.permitted").value(true))
                .andExpect(jsonPath("$.executed").value(true))
                .andExpect(jsonPath("$.totalTerminados").value(3))
                .andExpect(jsonPath("$.eliminados").value(3));
    }

    @Test
    void ejecutarPurgaTotalTerminados_blocksProductionEnvironment() throws Exception {
        doThrow(new IllegalStateException("La purga total de terminados está bloqueada en producción."))
                .when(dangerousOperationGuard)
                .assertNotProduction("La purga total de terminados");

        mockMvc.perform(delete("/api/eliminaciones-forzadas/terminados")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.permitted").value(false))
                .andExpect(jsonPath("$.executed").value(false))
                .andExpect(jsonPath("$.message").value("La purga total de terminados está bloqueada en producción."));
    }
}
