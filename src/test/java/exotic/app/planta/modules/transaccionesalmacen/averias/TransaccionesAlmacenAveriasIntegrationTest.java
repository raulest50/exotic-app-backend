package exotic.app.planta.modules.transaccionesalmacen.averias;

import exotic.app.planta.model.inventarios.dto.ReporteAveriaAlmacenDTO;
import exotic.app.planta.model.inventarios.dto.ReporteAveriaAlmacenItemDTO;
import exotic.app.planta.model.inventarios.dto.ReporteAveriaDTO;
import exotic.app.planta.model.inventarios.dto.ReporteAveriaItemDTO;
import exotic.app.planta.modules.transaccionesalmacen.support.AbstractTransaccionesAlmacenIntegrationTest;
import exotic.app.planta.modules.transaccionesalmacen.support.TransaccionesAlmacenFixtureFactory.ModuleFixture;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TransaccionesAlmacenAveriasIntegrationTest extends AbstractTransaccionesAlmacenIntegrationTest {

    @Test
    void searchEndpoints_coverAveriasAndSupportQueries() throws Exception {
        ModuleFixture fixture = fixtureFactory.seedModuleFixture();

        mockMvc.perform(get("/productos/search_mprima")
                        .with(bearerToken())
                        .param("page", "0")
                        .param("size", "10")
                        .param("search", "Acido")
                        .param("tipoBusqueda", "NOMBRE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].productoId").value(fixture.materialPrincipal().getProductoId()));

        mockMvc.perform(post("/api/areas-produccion/search_by_name")
                        .with(bearerToken())
                        .contentType("application/json")
                        .content(readJsonResource("json/transacciones_almacen/areas/search_area_mezclado.json")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nombre").value("mezclado"));

        mockMvc.perform(get("/api/averias/search_orden_by_lote")
                        .with(bearerToken())
                        .param("loteAsignado", "LOT-PT")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].ordenId").value(fixture.ordenAbierta().getOrdenId()));
    }

    @Test
    void itemsDispensadosYHistorial_reflectOperationalDamageTrail() throws Exception {
        ModuleFixture fixture = fixtureFactory.seedModuleFixture();
        fixtureFactory.createDispensacionTransaccion(
                fixture.ordenAbierta(),
                fixture.materialPrincipal(),
                fixture.loteMateriaPrima(),
                5.0,
                fixture.masterUser(),
                fixture.mezclado()
        );
        fixtureFactory.createReporteAveriaTransaccion(
                fixture.ordenAbierta(),
                fixture.materialPrincipal(),
                fixture.loteMateriaPrima(),
                1.0,
                fixture.masterUser(),
                fixture.mezclado()
        );

        mockMvc.perform(get("/api/averias/orden/{ordenProduccionId}/items-dispensados", fixture.ordenAbierta().getOrdenId())
                        .with(bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].productoId").value(fixture.materialPrincipal().getProductoId()))
                .andExpect(jsonPath("$[0].loteId").value(fixture.loteMateriaPrima().getId()));

        mockMvc.perform(get("/api/averias/orden/{ordenProduccionId}/historial", fixture.ordenAbierta().getOrdenId())
                        .with(bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].items[0].productoId").value(fixture.materialPrincipal().getProductoId()));

        mockMvc.perform(get("/api/averias/almacen/search-material-by-lote")
                        .with(bearerToken())
                        .param("batchNumber", fixture.loteMateriaPrima().getBatchNumber()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].productoId").value(fixture.materialPrincipal().getProductoId()));
    }

    @Test
    void registrarAverias_createsProductionAndWarehouseDamageTransactions() throws Exception {
        ModuleFixture fixture = fixtureFactory.seedModuleFixture();
        fixtureFactory.createDispensacionTransaccion(
                fixture.ordenAbierta(),
                fixture.materialPrincipal(),
                fixture.loteMateriaPrima(),
                4.0,
                fixture.masterUser(),
                fixture.mezclado()
        );

        ReporteAveriaDTO produccionPayload = new ReporteAveriaDTO(
                fixture.ordenAbierta().getOrdenId(),
                fixture.mezclado().getAreaId(),
                "Averia produccion",
                "master",
                List.of(new ReporteAveriaItemDTO(
                        fixture.materialPrincipal().getProductoId(),
                        fixture.loteMateriaPrima().getId(),
                        1.0
                ))
        );

        mockMvc.perform(post("/api/averias/registrar")
                        .with(bearerToken())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsBytes(produccionPayload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipoEntidadCausante").value("RA"));

        ReporteAveriaAlmacenDTO almacenPayload = new ReporteAveriaAlmacenDTO(
                "Averia en almacen",
                "master",
                List.of(new ReporteAveriaAlmacenItemDTO(
                        fixture.materialPrincipal().getProductoId(),
                        fixture.loteMateriaPrima().getId(),
                        1.0
                ))
        );

        mockMvc.perform(post("/api/averias/almacen/registrar")
                        .with(bearerToken())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsBytes(almacenPayload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipoEntidadCausante").value("RAA"))
                .andExpect(jsonPath("$.movimientosTransaccion.length()").value(2));
    }
}
