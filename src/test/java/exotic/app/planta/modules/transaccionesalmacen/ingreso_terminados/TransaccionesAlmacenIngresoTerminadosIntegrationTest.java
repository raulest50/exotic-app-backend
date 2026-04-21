package exotic.app.planta.modules.transaccionesalmacen.ingreso_terminados;

import exotic.app.planta.model.inventarios.dto.IngresoMasivoRequestDTO;
import exotic.app.planta.model.inventarios.dto.IngresoTerminadoRequestDTO;
import exotic.app.planta.modules.transaccionesalmacen.support.AbstractTransaccionesAlmacenIntegrationTest;
import exotic.app.planta.modules.transaccionesalmacen.support.TransaccionesAlmacenFixtureFactory.ModuleFixture;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TransaccionesAlmacenIngresoTerminadosIntegrationTest extends AbstractTransaccionesAlmacenIntegrationTest {

    @Test
    void buscarOpPorLoteYPlantilla_returnOperationalData() throws Exception {
        ModuleFixture fixture = fixtureFactory.seedModuleFixture();

        mockMvc.perform(get("/ingresos_terminados_almacen/buscar-op-por-lote")
                        .with(bearerToken())
                        .param("loteAsignado", fixture.ordenAbierta().getLoteAsignado()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ordenProduccionId").value(fixture.ordenAbierta().getOrdenId()))
                .andExpect(jsonPath("$.productoId").value(fixture.terminado().getProductoId()));

        mockMvc.perform(get("/ingresos_terminados_almacen/plantilla")
                        .with(bearerToken()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("plantilla_ingreso_terminados_")));
    }

    @Test
    void registrarIngresoTerminado_createsBackflushAndClosesOrder() throws Exception {
        ModuleFixture fixture = fixtureFactory.seedModuleFixture();

        IngresoTerminadoRequestDTO payload = new IngresoTerminadoRequestDTO(
                "master",
                fixture.ordenAbierta().getOrdenId(),
                8,
                LocalDate.now().plusMonths(12),
                "Ingreso terminado desde test"
        );

        mockMvc.perform(post("/ingresos_terminados_almacen/registrar")
                        .with(bearerToken())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsBytes(payload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipoEntidadCausante").value("OP"))
                .andExpect(jsonPath("$.movimientosTransaccion[0].tipoMovimiento").value("BACKFLUSH"));
    }

    @Test
    void registrarMasivo_returnsMultiStatusWhenOneOrderFails() throws Exception {
        ModuleFixture fixture = fixtureFactory.seedModuleFixture();

        IngresoMasivoRequestDTO payload = new IngresoMasivoRequestDTO(
                "master",
                List.of(
                        new IngresoMasivoRequestDTO.IngresoItemDTO(
                                fixture.ordenMasivo().getOrdenId(),
                                5,
                                LocalDate.now().plusMonths(9)
                        ),
                        new IngresoMasivoRequestDTO.IngresoItemDTO(
                                fixture.ordenCancelada().getOrdenId(),
                                5,
                                LocalDate.now().plusMonths(9)
                        )
                )
        );

        mockMvc.perform(post("/ingresos_terminados_almacen/registrar-masivo")
                        .with(bearerToken())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsBytes(payload)))
                .andExpect(status().is(207))
                .andExpect(jsonPath("$.totalProcesados").value(2))
                .andExpect(jsonPath("$.exitosos").value(1))
                .andExpect(jsonPath("$.fallidos").value(1));
    }
}
