package exotic.app.planta.modules.transaccionesalmacen.dispensacion;

import exotic.app.planta.model.inventarios.dto.DispensacionDTO;
import exotic.app.planta.model.inventarios.dto.DispensacionItemDTO;
import exotic.app.planta.modules.transaccionesalmacen.support.AbstractTransaccionesAlmacenIntegrationTest;
import exotic.app.planta.modules.transaccionesalmacen.support.TransaccionesAlmacenFixtureFactory.ModuleFixture;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TransaccionesAlmacenDispensacionIntegrationTest extends AbstractTransaccionesAlmacenIntegrationTest {

    @Test
    void consultaBusquedaResumenYLotes_coverOperationalReadFlow() throws Exception {
        ModuleFixture fixture = fixtureFactory.seedModuleFixture();

        mockMvc.perform(get("/produccion/dispensacion_odp_consulta")
                        .with(bearerToken())
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].ordenId").value(fixture.ordenAbierta().getOrdenId()));

        mockMvc.perform(get("/produccion/dispensacion_odp_busqueda_lote")
                        .with(bearerToken())
                        .param("loteAsignado", "LOT-PT")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].loteAsignado").value(fixture.ordenAbierta().getLoteAsignado()));

        mockMvc.perform(get("/salidas_almacen/orden-produccion/{ordenProduccionId}/insumos-desglosados", fixture.ordenAbierta().getOrdenId())
                        .with(bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.insumosReceta[0].productoId").value(fixture.materialPrincipal().getProductoId()));

        mockMvc.perform(get("/salidas_almacen/orden-produccion/{ordenProduccionId}/dispensacion-resumen", fixture.ordenAbierta().getOrdenId())
                        .with(bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.insumosReceta[0].productoId").value(fixture.materialPrincipal().getProductoId()));

        mockMvc.perform(get("/salidas_almacen/lotes-disponibles")
                        .with(bearerToken())
                        .param("productoId", fixture.materialPrincipal().getProductoId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productoId").value(fixture.materialPrincipal().getProductoId()))
                .andExpect(jsonPath("$.lotesDisponibles[0].batchNumber").value(fixture.loteMateriaPrima().getBatchNumber()));
    }

    @Test
    void createDispensacion_registersMovementAndUpdatesOrderState() throws Exception {
        ModuleFixture fixture = fixtureFactory.seedModuleFixture();

        DispensacionDTO payload = new DispensacionDTO(
                fixture.ordenAbierta().getOrdenId(),
                null,
                "Dispensacion desde test",
                (int) masterUserId(),
                null,
                null,
                List.of(new DispensacionItemDTO(
                        fixture.materialPrincipal().getProductoId(),
                        4.0,
                        fixture.loteMateriaPrima().getId().intValue()
                ))
        );

        mockMvc.perform(post("/salidas_almacen/dispensacion")
                        .with(bearerToken())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsBytes(payload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipoEntidadCausante").value("OD"))
                .andExpect(jsonPath("$.movimientosTransaccion[0].cantidad").value(-4.0));

        mockMvc.perform(get("/produccion/dispensacion_odp_consulta")
                        .with(bearerToken())
                        .param("ordenId", String.valueOf(fixture.ordenAbierta().getOrdenId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].estadoOrden").value(11));
    }

    @Test
    void createDispensacionReposicionAveria_acceptsPendingReplacement() throws Exception {
        ModuleFixture fixture = fixtureFactory.seedModuleFixture();
        fixtureFactory.createDispensacionTransaccion(
                fixture.ordenAbierta(),
                fixture.materialPrincipal(),
                fixture.loteMateriaPrima(),
                6.0,
                fixture.masterUser(),
                fixture.mezclado()
        );
        fixtureFactory.createReporteAveriaTransaccion(
                fixture.ordenAbierta(),
                fixture.materialPrincipal(),
                fixture.loteMateriaPrima(),
                2.0,
                fixture.masterUser(),
                fixture.mezclado()
        );

        DispensacionDTO payload = new DispensacionDTO(
                fixture.ordenAbierta().getOrdenId(),
                null,
                "Reposicion averia",
                (int) masterUserId(),
                null,
                null,
                List.of(new DispensacionItemDTO(
                        fixture.materialPrincipal().getProductoId(),
                        2.0,
                        fixture.loteMateriaPrima().getId().intValue()
                ))
        );

        mockMvc.perform(post("/salidas_almacen/dispensacion-reposicion-averia")
                        .with(bearerToken())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsBytes(payload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipoEntidadCausante").value("OD_RA"));
    }

    @Test
    void historialDispensacionAndSeguimientoRemainQueryable() throws Exception {
        ModuleFixture fixture = fixtureFactory.seedModuleFixture();
        fixtureFactory.createDispensacionTransaccion(
                fixture.ordenAbierta(),
                fixture.materialPrincipal(),
                fixture.loteMateriaPrima(),
                3.0,
                fixture.masterUser(),
                fixture.mezclado()
        );

        String filterJson = readJsonResource("json/transacciones_almacen/dispensacion/historial_filter.json")
                .replace("\"ordenProduccionId\": 0", "\"ordenProduccionId\": " + fixture.ordenAbierta().getOrdenId());

        mockMvc.perform(post("/salidas_almacen/historial_dispensacion_filter")
                        .with(bearerToken())
                        .contentType("application/json")
                        .content(filterJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].idEntidadCausante").value(fixture.ordenAbierta().getOrdenId()));

        mockMvc.perform(get("/api/seguimiento-orden-area/orden/{ordenId}/progreso", fixture.ordenAbierta().getOrdenId())
                        .with(bearerToken()))
                .andExpect(status().isOk());
    }

    @Test
    void protectedDispensacionEndpoints_rejectAnonymousAccess() throws Exception {
        mockMvc.perform(get("/produccion/dispensacion_odp_consulta")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().is4xxClientError());
    }
}
