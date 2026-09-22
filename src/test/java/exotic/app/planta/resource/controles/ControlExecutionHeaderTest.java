package exotic.app.planta.resource.controles;

import exotic.app.planta.resource.calidad.CalidadControlUnificadoResource;
import exotic.app.planta.resource.produccion.ProcesoControlResource;
import exotic.app.planta.security.ModuleTabAccessGuard;
import exotic.app.planta.service.controles.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ControlExecutionHeaderTest {
    private final ControlExecutionService executionService = mock(ControlExecutionService.class);
    private final ControlIdempotencyService idempotencyService = mock(ControlIdempotencyService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        var plans = mock(ControlPlanService.class);
        var deviations = mock(ControlDeviationService.class);
        var guard = mock(ModuleTabAccessGuard.class);
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new CalidadControlUnificadoResource(plans, executionService, deviations, idempotencyService, guard),
                        new ProcesoControlResource(plans, executionService, deviations, idempotencyService, guard))
                .setControllerAdvice(new ControlApiExceptionHandler())
                .build();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/calidad/controles-calidad/ejecuciones", "/api/produccion/controles-proceso/ejecuciones"})
    void laCabeceraSigueSiendoObligatoriaYElErrorEsExplicito(String path) throws Exception {
        mockMvc.perform(post(path).contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("IDEMPOTENCY_KEY_REQUIRED"))
                .andExpect(jsonPath("$.message").value(
                        "No se pudo identificar la solicitud de guardado. Actualice la página e intente nuevamente."));
        verifyNoInteractions(executionService, idempotencyService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/calidad/controles-calidad/ejecuciones", "/api/produccion/controles-proceso/ejecuciones"})
    void agregarLaCabeceraNoEvitaLasValidacionesDelContenido(String path) throws Exception {
        mockMvc.perform(post(path).contentType("application/json")
                        .header(ControlIdempotencyService.HEADER, "registro-de-prueba")
                        .content("{\"controlRequeridoId\":11,\"muestras\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.field").value("muestras"));
        verifyNoInteractions(executionService, idempotencyService);
    }
}
