package exotic.app.planta.modules.transaccionesalmacen.ingreso_ocm;

import exotic.app.planta.model.inventarios.Lote;
import exotic.app.planta.model.inventarios.Movimiento;
import exotic.app.planta.model.inventarios.TransaccionAlmacen;
import exotic.app.planta.model.inventarios.dto.IngresoOCM_DTA;
import exotic.app.planta.model.master.configs.MasterDirective;
import exotic.app.planta.model.master.configs.MasterDirectiveKeys;
import exotic.app.planta.modules.transaccionesalmacen.support.AbstractTransaccionesAlmacenIntegrationTest;
import exotic.app.planta.modules.transaccionesalmacen.support.TransaccionesAlmacenFixtureFactory.ModuleFixture;
import exotic.app.planta.repo.compras.OrdenCompraRepo;
import exotic.app.planta.repo.compras.ProveedorRepo;
import exotic.app.planta.repo.inventarios.LoteRepo;
import exotic.app.planta.repo.inventarios.TransaccionAlmacenHeaderRepo;
import exotic.app.planta.repo.master.configs.MasterDirectiveRepo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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

    @Autowired
    private ProveedorRepo proveedorRepo;

    @Autowired
    private LoteRepo loteRepo;

    @Autowired
    private OrdenCompraRepo ordenCompraRepo;

    @Test
    void consultaOcmsPendientes_returnsSeededOrder() throws Exception {
        setLimiteRecepcionesParcialesOcm("2");
        ModuleFixture fixture = fixtureFactory.seedModuleFixture();

        mockMvc.perform(get("/ingresos_almacen/ocms_pendientes_ingreso")
                        .with(bearerToken())
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].ordenCompraId").value(fixture.ordenCompra().getOrdenCompraId()))
                .andExpect(jsonPath("$.content[0].proveedor.id").value(fixture.proveedor().getId()))
                .andExpect(jsonPath("$.content[0].limiteRecepcionesParcialesEfectivo").value(2))
                .andExpect(jsonPath("$.content[0].porcentajeRecibido").value(100.0));
    }

    @Test
    void consultaOcmsPendientes_exposesProviderReceptionLimitWithoutApplyingGlobalCap() throws Exception {
        setLimiteRecepcionesParcialesOcm("4");
        ModuleFixture fixture = fixtureFactory.seedModuleFixture();
        setLimiteRecepcionesProveedor(fixture, 2);

        mockMvc.perform(get("/ingresos_almacen/ocms_pendientes_ingreso")
                        .with(bearerToken())
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].ordenCompraId").value(fixture.ordenCompra().getOrdenCompraId()))
                .andExpect(jsonPath("$.content[0].limiteRecepcionesParcialesEfectivo").value(2));

        setLimiteRecepcionesProveedor(fixture, 6);

        mockMvc.perform(get("/ingresos_almacen/ocms_pendientes_ingreso")
                        .with(bearerToken())
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].ordenCompraId").value(fixture.ordenCompra().getOrdenCompraId()))
                .andExpect(jsonPath("$.content[0].limiteRecepcionesParcialesEfectivo").value(6));
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
        setLimiteRecepcionesProveedor(fixture, 3);

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
    void saveDocIngresoOc_usesProviderLimitWhenItIsLowerThanGlobalLimit() throws Exception {
        setLimiteRecepcionesParcialesOcm("3");
        ModuleFixture fixture = fixtureFactory.seedModuleFixture();
        setLimiteRecepcionesProveedor(fixture, 2);

        mockMvc.perform(multipart("/movimientos/save_doc_ingreso_oc")
                        .file(jsonPart("docIngresoDTA", buildIngresoPayload(fixture, "MPLOT-PROV-002", 1.0)))
                        .file(classpathFile("files/transacciones_almacen/ingreso_ocm_soporte.txt", MediaType.TEXT_PLAIN_VALUE))
                        .with(bearerToken()))
                .andExpect(status().isOk());

        assertThat(countRecepcionesOcm(fixture)).isEqualTo(2);

        mockMvc.perform(multipart("/movimientos/save_doc_ingreso_oc")
                        .file(jsonPart("docIngresoDTA", buildIngresoPayload(fixture, "MPLOT-PROV-003", 1.0)))
                        .file(classpathFile("files/transacciones_almacen/ingreso_ocm_soporte.txt", MediaType.TEXT_PLAIN_VALUE))
                        .with(bearerToken()))
                .andExpect(status().isConflict());

        assertThat(countRecepcionesOcm(fixture)).isEqualTo(2);
    }

    @Test
    void saveDocIngresoOc_usesPersistedProviderLimitWhenPayloadOmitsProviderLimit() throws Exception {
        setLimiteRecepcionesParcialesOcm("3");
        ModuleFixture fixture = fixtureFactory.seedModuleFixture();
        setLimiteRecepcionesProveedor(fixture, 2);

        mockMvc.perform(multipart("/movimientos/save_doc_ingreso_oc")
                        .file(jsonPart("docIngresoDTA", buildIngresoPayload(fixture, "MPLOT-STALE-002", 1.0)))
                        .file(classpathFile("files/transacciones_almacen/ingreso_ocm_soporte.txt", MediaType.TEXT_PLAIN_VALUE))
                        .with(bearerToken()))
                .andExpect(status().isOk());

        assertThat(countRecepcionesOcm(fixture)).isEqualTo(2);

        IngresoOCM_DTA stalePayload = buildIngresoPayload(fixture, "MPLOT-STALE-003", 1.0);
        stalePayload.getOrdenCompraMateriales().getProveedor().setLimiteRecepcionesParcialesOcm(null);

        mockMvc.perform(multipart("/movimientos/save_doc_ingreso_oc")
                        .file(jsonPart("docIngresoDTA", stalePayload))
                        .file(classpathFile("files/transacciones_almacen/ingreso_ocm_soporte.txt", MediaType.TEXT_PLAIN_VALUE))
                        .with(bearerToken()))
                .andExpect(status().isConflict());

        assertThat(countRecepcionesOcm(fixture)).isEqualTo(2);
    }

    @Test
    void saveDocIngresoOc_rejectsUnknownOrdenCompraBeforeCreatingInventoryData() throws Exception {
        ModuleFixture fixture = fixtureFactory.seedModuleFixture();
        long transaccionesBefore = transaccionAlmacenHeaderRepo.count();
        long lotesBefore = loteRepo.count();

        IngresoOCM_DTA payload = buildIngresoPayload(fixture, "MPLOT-UNKNOWN-OCM", 1.0);
        payload.getOrdenCompraMateriales().setOrdenCompraId(999999);

        mockMvc.perform(multipart("/movimientos/save_doc_ingreso_oc")
                        .file(jsonPart("docIngresoDTA", payload))
                        .file(classpathFile("files/transacciones_almacen/ingreso_ocm_soporte.txt", MediaType.TEXT_PLAIN_VALUE))
                        .with(bearerToken()))
                .andExpect(status().isNotFound());

        assertThat(transaccionAlmacenHeaderRepo.count()).isEqualTo(transaccionesBefore);
        assertThat(loteRepo.count()).isEqualTo(lotesBefore);
    }

    @Test
    void saveDocIngresoOc_rejectsOrdenCompraWhenItIsNotRecepcionableBeforeCreatingInventoryData() throws Exception {
        ModuleFixture fixture = fixtureFactory.seedModuleFixture();
        fixture.ordenCompra().setEstado(3);
        ordenCompraRepo.save(fixture.ordenCompra());
        long transaccionesBefore = transaccionAlmacenHeaderRepo.count();
        long lotesBefore = loteRepo.count();

        mockMvc.perform(multipart("/movimientos/save_doc_ingreso_oc")
                        .file(jsonPart("docIngresoDTA", buildIngresoPayload(fixture, "MPLOT-CLOSED-OCM", 1.0)))
                        .file(classpathFile("files/transacciones_almacen/ingreso_ocm_soporte.txt", MediaType.TEXT_PLAIN_VALUE))
                        .with(bearerToken()))
                .andExpect(status().isConflict());

        assertThat(transaccionAlmacenHeaderRepo.count()).isEqualTo(transaccionesBefore);
        assertThat(loteRepo.count()).isEqualTo(lotesBefore);
    }

    @Test
    void saveDocIngresoOc_serializesConcurrentReceptionsForSameOrdenCompra() throws Exception {
        setLimiteRecepcionesParcialesOcm("2");
        ModuleFixture fixture = fixtureFactory.seedModuleFixture();
        assertThat(countRecepcionesOcm(fixture)).isEqualTo(1);

        String token = loginAsMaster();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            Future<Integer> first = executor.submit(() -> {
                start.await();
                return performSaveDocIngreso(token, fixture, "MPLOT-CONCURRENT-002-A");
            });
            Future<Integer> second = executor.submit(() -> {
                start.await();
                return performSaveDocIngreso(token, fixture, "MPLOT-CONCURRENT-002-B");
            });

            start.countDown();

            List<Integer> statuses = List.of(
                    first.get(30, TimeUnit.SECONDS),
                    second.get(30, TimeUnit.SECONDS)
            );

            assertThat(statuses).containsExactlyInAnyOrder(
                    HttpStatus.OK.value(),
                    HttpStatus.CONFLICT.value()
            );
            assertThat(countRecepcionesOcm(fixture)).isEqualTo(2);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void saveDocIngresoOc_usesProviderLimitEvenWhenItIsHigherThanGlobalLimit() throws Exception {
        setLimiteRecepcionesParcialesOcm("3");
        ModuleFixture fixture = fixtureFactory.seedModuleFixture();
        setLimiteRecepcionesProveedor(fixture, 5);

        mockMvc.perform(multipart("/movimientos/save_doc_ingreso_oc")
                        .file(jsonPart("docIngresoDTA", buildIngresoPayload(fixture, "MPLOT-CAP-002", 1.0)))
                        .file(classpathFile("files/transacciones_almacen/ingreso_ocm_soporte.txt", MediaType.TEXT_PLAIN_VALUE))
                        .with(bearerToken()))
                .andExpect(status().isOk());

        mockMvc.perform(multipart("/movimientos/save_doc_ingreso_oc")
                        .file(jsonPart("docIngresoDTA", buildIngresoPayload(fixture, "MPLOT-CAP-003", 1.0)))
                        .file(classpathFile("files/transacciones_almacen/ingreso_ocm_soporte.txt", MediaType.TEXT_PLAIN_VALUE))
                        .with(bearerToken()))
                .andExpect(status().isOk());

        assertThat(countRecepcionesOcm(fixture)).isEqualTo(3);

        mockMvc.perform(multipart("/movimientos/save_doc_ingreso_oc")
                        .file(jsonPart("docIngresoDTA", buildIngresoPayload(fixture, "MPLOT-CAP-004", 1.0)))
                        .file(classpathFile("files/transacciones_almacen/ingreso_ocm_soporte.txt", MediaType.TEXT_PLAIN_VALUE))
                        .with(bearerToken()))
                .andExpect(status().isOk());

        mockMvc.perform(multipart("/movimientos/save_doc_ingreso_oc")
                        .file(jsonPart("docIngresoDTA", buildIngresoPayload(fixture, "MPLOT-CAP-005", 1.0)))
                        .file(classpathFile("files/transacciones_almacen/ingreso_ocm_soporte.txt", MediaType.TEXT_PLAIN_VALUE))
                        .with(bearerToken()))
                .andExpect(status().isOk());

        assertThat(countRecepcionesOcm(fixture)).isEqualTo(5);

        mockMvc.perform(multipart("/movimientos/save_doc_ingreso_oc")
                        .file(jsonPart("docIngresoDTA", buildIngresoPayload(fixture, "MPLOT-CAP-006", 1.0)))
                        .file(classpathFile("files/transacciones_almacen/ingreso_ocm_soporte.txt", MediaType.TEXT_PLAIN_VALUE))
                        .with(bearerToken()))
                .andExpect(status().isConflict());

        assertThat(countRecepcionesOcm(fixture)).isEqualTo(5);
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

    private int performSaveDocIngreso(String token, ModuleFixture fixture, String batchNumber) throws Exception {
        MvcResult result = mockMvc.perform(multipart("/movimientos/save_doc_ingreso_oc")
                        .file(jsonPart("docIngresoDTA", buildIngresoPayload(fixture, batchNumber, 1.0)))
                        .file(classpathFile("files/transacciones_almacen/ingreso_ocm_soporte.txt", MediaType.TEXT_PLAIN_VALUE))
                        .header("Authorization", "Bearer " + token))
                .andReturn();

        return result.getResponse().getStatus();
    }

    private void setLimiteRecepcionesProveedor(ModuleFixture fixture, Integer limite) {
        fixture.proveedor().setLimiteRecepcionesParcialesOcm(limite);
        fixture.ordenCompra().getProveedor().setLimiteRecepcionesParcialesOcm(limite);
        proveedorRepo.save(fixture.proveedor());
    }
}
