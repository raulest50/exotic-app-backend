package exotic.app.planta.modules.transaccionesalmacen.ingreso_ocm;

import exotic.app.planta.model.inventarios.Lote;
import exotic.app.planta.model.inventarios.Movimiento;
import exotic.app.planta.model.inventarios.TransaccionAlmacen;
import exotic.app.planta.model.inventarios.dto.IngresoOCM_DTA;
import exotic.app.planta.model.master.configs.MasterDirective;
import exotic.app.planta.model.master.configs.MasterDirectiveKeys;
import exotic.app.planta.modules.transaccionesalmacen.support.AbstractTransaccionesAlmacenIntegrationTest;
import exotic.app.planta.modules.transaccionesalmacen.support.TransaccionesAlmacenFixtureFactory.ModuleFixture;
import exotic.app.planta.repo.inventarios.TransaccionAlmacenHeaderRepo;
import exotic.app.planta.repo.master.configs.MasterDirectiveRepo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TransaccionesAlmacenIngresoOcmIntegrationTest extends AbstractTransaccionesAlmacenIntegrationTest {

    @Autowired
    private MasterDirectiveRepo masterDirectiveRepo;

    @Autowired
    private TransaccionAlmacenHeaderRepo transaccionAlmacenHeaderRepo;

    @Test
    void consultaOcmsPendientes_returnsSeededOrder() throws Exception {
        ModuleFixture fixture = fixtureFactory.seedModuleFixture();

        mockMvc.perform(get("/ingresos_almacen/ocms_pendientes_ingreso")
                        .with(bearerToken())
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].ordenCompraId").value(fixture.ordenCompra().getOrdenCompraId()))
                .andExpect(jsonPath("$.content[0].proveedor.id").value(fixture.proveedor().getId()))
                .andExpect(jsonPath("$.content[0].porcentajeRecibido").value(100.0));
    }

    @Test
    void consultaTransaccionesMovimientosYConsolidado_returnStoredWarehouseData() throws Exception {
        ModuleFixture fixture = fixtureFactory.seedModuleFixture();

        mockMvc.perform(get("/ingresos_almacen/consultar_transin_de_ocm")
                        .with(bearerToken())
                        .param("page", "0")
                        .param("size", "10")
                        .param("ordenCompraId", String.valueOf(fixture.ordenCompra().getOrdenCompraId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].transaccionId").value(fixture.ingresoOcm().getTransaccionId()));

        mockMvc.perform(get("/ingresos_almacen/transaccion/{transaccionId}/movimientos", fixture.ingresoOcm().getTransaccionId())
                        .with(bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].productoId").value(fixture.materialPrincipal().getProductoId()))
                .andExpect(jsonPath("$[0].batchNumber").value(fixture.loteMateriaPrima().getBatchNumber()));

        mockMvc.perform(get("/ingresos_almacen/ocm/{ordenCompraId}/consolidado-materiales", fixture.ordenCompra().getOrdenCompraId())
                        .with(bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ordenCompraId").value(fixture.ordenCompra().getOrdenCompraId()))
                .andExpect(jsonPath("$.totalTransacciones").value(1))
                .andExpect(jsonPath("$.materiales[0].productoId").value(fixture.materialPrincipal().getProductoId()))
                .andExpect(jsonPath("$.materiales[0].lotes[0].batchNumber").value(fixture.loteMateriaPrima().getBatchNumber()));
    }

    @Test
    void saveDocIngresoOc_createsNewTransactionWithMultipartPayload() throws Exception {
        ModuleFixture fixture = fixtureFactory.seedModuleFixture();

        IngresoOCM_DTA payload = buildIngresoPayload(fixture, "MPLOT-NEW-001", 5.0);
        MockMultipartFile soporte = classpathFile("files/transacciones_almacen/ingreso_ocm_soporte.txt", MediaType.TEXT_PLAIN_VALUE);

        mockMvc.perform(multipart("/movimientos/save_doc_ingreso_oc")
                        .file(jsonPart("docIngresoDTA", payload))
                        .file(soporte)
                        .with(bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idEntidadCausante").value(fixture.ordenCompra().getOrdenCompraId()))
                .andExpect(jsonPath("$.movimientosTransaccion[0].producto.productoId").value(fixture.materialPrincipal().getProductoId()));
    }

    @Test
    void saveDocIngresoOc_rejectsThirdReceptionWhenConfiguredLimitIsTwo() throws Exception {
        setLimiteRecepcionesParcialesOcm("2");
        ModuleFixture fixture = fixtureFactory.seedModuleFixture();

        mockMvc.perform(multipart("/movimientos/save_doc_ingreso_oc")
                        .file(jsonPart("docIngresoDTA", buildIngresoPayload(fixture, "MPLOT-LIMIT-002", 1.0)))
                        .file(classpathFile("files/transacciones_almacen/ingreso_ocm_soporte.txt", MediaType.TEXT_PLAIN_VALUE))
                        .with(bearerToken()))
                .andExpect(status().isOk());

        assertThat(countRecepcionesOcm(fixture)).isEqualTo(2);

        mockMvc.perform(multipart("/movimientos/save_doc_ingreso_oc")
                        .file(jsonPart("docIngresoDTA", buildIngresoPayload(fixture, "MPLOT-LIMIT-003", 1.0)))
                        .file(classpathFile("files/transacciones_almacen/ingreso_ocm_soporte.txt", MediaType.TEXT_PLAIN_VALUE))
                        .with(bearerToken()))
                .andExpect(status().isConflict());

        assertThat(countRecepcionesOcm(fixture)).isEqualTo(2);
    }

    @Test
    void saveDocIngresoOc_allowsThirdReceptionWhenConfiguredLimitIsThreeAndRejectsFourth() throws Exception {
        setLimiteRecepcionesParcialesOcm("3");
        ModuleFixture fixture = fixtureFactory.seedModuleFixture();

        mockMvc.perform(multipart("/movimientos/save_doc_ingreso_oc")
                        .file(jsonPart("docIngresoDTA", buildIngresoPayload(fixture, "MPLOT-LIMIT3-002", 1.0)))
                        .file(classpathFile("files/transacciones_almacen/ingreso_ocm_soporte.txt", MediaType.TEXT_PLAIN_VALUE))
                        .with(bearerToken()))
                .andExpect(status().isOk());

        mockMvc.perform(multipart("/movimientos/save_doc_ingreso_oc")
                        .file(jsonPart("docIngresoDTA", buildIngresoPayload(fixture, "MPLOT-LIMIT3-003", 1.0)))
                        .file(classpathFile("files/transacciones_almacen/ingreso_ocm_soporte.txt", MediaType.TEXT_PLAIN_VALUE))
                        .with(bearerToken()))
                .andExpect(status().isOk());

        assertThat(countRecepcionesOcm(fixture)).isEqualTo(3);

        mockMvc.perform(multipart("/movimientos/save_doc_ingreso_oc")
                        .file(jsonPart("docIngresoDTA", buildIngresoPayload(fixture, "MPLOT-LIMIT3-004", 1.0)))
                        .file(classpathFile("files/transacciones_almacen/ingreso_ocm_soporte.txt", MediaType.TEXT_PLAIN_VALUE))
                        .with(bearerToken()))
                .andExpect(status().isConflict());

        assertThat(countRecepcionesOcm(fixture)).isEqualTo(3);
    }

    @Test
    void closeOrdenCompra_movesOrderToClosedStateWhenThereIsInventoryHistory() throws Exception {
        ModuleFixture fixture = fixtureFactory.seedModuleFixture();

        mockMvc.perform(put("/compras/orden_compra/{ordenCompraId}/close", fixture.ordenCompra().getOrdenCompraId())
                        .with(bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value(3));
    }

    @Test
    void protectedIngresoOcmEndpoints_rejectAnonymousAccess() throws Exception {
        mockMvc.perform(get("/ingresos_almacen/ocms_pendientes_ingreso")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().is4xxClientError());
    }

    private IngresoOCM_DTA buildIngresoPayload(ModuleFixture fixture, String batchNumber, double cantidad) {
        Lote lote = new Lote();
        lote.setBatchNumber(batchNumber);
        lote.setProductionDate(LocalDate.now());
        lote.setExpirationDate(LocalDate.now().plusMonths(9));

        Movimiento movimiento = new Movimiento();
        movimiento.setCantidad(cantidad);
        movimiento.setProducto(fixture.materialPrincipal());
        movimiento.setTipoMovimiento(Movimiento.TipoMovimiento.COMPRA);
        movimiento.setAlmacen(Movimiento.Almacen.GENERAL);
        movimiento.setLote(lote);

        TransaccionAlmacen transaccion = new TransaccionAlmacen();
        transaccion.setMovimientosTransaccion(List.of(movimiento));

        return new IngresoOCM_DTA(
                transaccion,
                fixture.ordenCompra(),
                String.valueOf(masterUserId()),
                "Ingreso OCM desde test"
        );
    }

    private MasterDirective setLimiteRecepcionesParcialesOcm(String valor) {
        MasterDirective directive = masterDirectiveRepo.findByNombre(MasterDirectiveKeys.LIMITE_RECEPCIONES_PARCIALES_OCM)
                .orElseGet(() -> {
                    MasterDirective created = new MasterDirective();
                    created.setNombre(MasterDirectiveKeys.LIMITE_RECEPCIONES_PARCIALES_OCM);
                    created.setTipoDato(MasterDirective.TipoDato.NUMERO);
                    created.setGrupo(MasterDirective.GRUPO.COMPRAS_ALMACEN);
                    created.setResumen("Limite de recepciones parciales permitidas por OCM");
                    created.setAyuda("Fixture de prueba");
                    return created;
                });
        directive.setValor(valor);
        return masterDirectiveRepo.save(directive);
    }

    private long countRecepcionesOcm(ModuleFixture fixture) {
        return transaccionAlmacenHeaderRepo.countByTipoEntidadCausanteAndIdEntidadCausante(
                TransaccionAlmacen.TipoEntidadCausante.OCM,
                fixture.ordenCompra().getOrdenCompraId()
        );
    }
}
