package exotic.app.planta.modules.transaccionesalmacen.ajustes;

import exotic.app.planta.model.inventarios.Movimiento;
import exotic.app.planta.model.inventarios.dto.AjusteInventarioDTO;
import exotic.app.planta.model.inventarios.dto.AjusteItemDTO;
import exotic.app.planta.modules.transaccionesalmacen.support.AbstractTransaccionesAlmacenIntegrationTest;
import exotic.app.planta.modules.transaccionesalmacen.support.TransaccionesAlmacenFixtureFactory.ModuleFixture;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TransaccionesAlmacenAjustesIntegrationTest extends AbstractTransaccionesAlmacenIntegrationTest {

    @Test
    void consultaProductosAndLotesSupportAjustesFlow() throws Exception {
        ModuleFixture fixture = fixtureFactory.seedModuleFixture();

        mockMvc.perform(post("/productos/consulta1")
                        .with(bearerToken())
                        .contentType("application/json")
                        .content(readJsonResource("json/transacciones_almacen/ajustes/consulta_productos_materiales.json")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].productoId").value(fixture.materialPrincipal().getProductoId()));

        mockMvc.perform(get("/movimientos/ajustes/lotes-disponibles")
                        .with(bearerToken())
                        .param("productoId", fixture.materialPrincipal().getProductoId())
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productoId").value(fixture.materialPrincipal().getProductoId()))
                .andExpect(jsonPath("$.lotes[0].loteId").value(fixture.loteMateriaPrima().getId()));

        mockMvc.perform(get("/movimientos/ajustes/lotes-existentes")
                        .with(bearerToken())
                        .param("productoId", fixture.materialPrincipal().getProductoId())
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lotes[0].batchNumber").value(fixture.loteMateriaPrima().getBatchNumber()));
    }

    @Test
    void saveAjusteInventario_createsWarehouseAdjustment() throws Exception {
        ModuleFixture fixture = fixtureFactory.seedModuleFixture();

        AjusteInventarioDTO payload = new AjusteInventarioDTO(
                "master",
                "Ajuste correctivo desde test",
                "fixture://ajuste",
                List.of(new AjusteItemDTO(
                        fixture.materialPrincipal().getProductoId(),
                        -2.0,
                        Movimiento.Almacen.GENERAL,
                        fixture.loteMateriaPrima().getId().intValue(),
                        "AJUSTE_NEGATIVO"
                ))
        );

        mockMvc.perform(post("/movimientos/ajustes")
                        .with(bearerToken())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsBytes(payload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipoEntidadCausante").value("OAA"))
                .andExpect(jsonPath("$.movimientosTransaccion[0].tipoMovimiento").value("AJUSTE_NEGATIVO"));
    }
}
