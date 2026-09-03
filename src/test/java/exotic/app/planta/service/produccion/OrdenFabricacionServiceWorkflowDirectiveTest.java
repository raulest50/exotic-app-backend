package exotic.app.planta.service.produccion;

import exotic.app.planta.model.inventarios.EstadoCalidadLote;
import exotic.app.planta.model.inventarios.Lote;
import exotic.app.planta.model.produccion.OrdenProduccion;
import exotic.app.planta.model.produccion.batchrecord.BatchRecord;
import exotic.app.planta.model.produccion.dto.OrdenFabricacionDTOs;
import exotic.app.planta.model.produccion.fabricacion.OrdenFabricacion;
import exotic.app.planta.model.producto.SemiTerminado;
import exotic.app.planta.model.producto.manufacturing.snapshots.ManufacturingVersions;
import exotic.app.planta.model.users.User;
import exotic.app.planta.repo.inventarios.LoteRepo;
import exotic.app.planta.repo.inventarios.TransaccionAlmacenHeaderRepo;
import exotic.app.planta.repo.producto.SemiTerminadoRepo;
import exotic.app.planta.repo.producto.manufacturing.snapshots.ManufacturingVersionRepo;
import exotic.app.planta.repo.produccion.batchrecord.BatchRecordRepo;
import exotic.app.planta.repo.produccion.fabricacion.OrdenFabricacionOperacionEventoRepo;
import exotic.app.planta.repo.produccion.fabricacion.OrdenFabricacionRepo;
import exotic.app.planta.repo.usuarios.UserRepository;
import exotic.app.planta.service.master.configs.MasterDirectiveService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrdenFabricacionServiceWorkflowDirectiveTest {

    private OrdenFabricacionRepo ordenRepo;
    private ManufacturingVersionRepo manufacturingVersionRepo;
    private LoteRepo loteRepo;
    private BatchRecordService batchRecordService;
    private MasterDirectiveService directiveService;
    private OrdenFabricacionOperacionService operacionService;
    private LoteManufacturaNumeroService loteNumeroService;
    private OrdenFabricacionService service;
    private SemiTerminado semi;
    private ManufacturingVersions version;
    private User actor;

    @BeforeEach
    void setUp() {
        ordenRepo = mock(OrdenFabricacionRepo.class);
        SemiTerminadoRepo semiRepo = mock(SemiTerminadoRepo.class);
        manufacturingVersionRepo = mock(ManufacturingVersionRepo.class);
        loteRepo = mock(LoteRepo.class);
        batchRecordService = mock(BatchRecordService.class);
        directiveService = mock(MasterDirectiveService.class);
        operacionService = mock(OrdenFabricacionOperacionService.class);
        loteNumeroService = mock(LoteManufacturaNumeroService.class);
        service = new OrdenFabricacionService(
                ordenRepo,
                semiRepo,
                manufacturingVersionRepo,
                loteRepo,
                mock(UserRepository.class),
                mock(BatchRecordRepo.class),
                mock(VencimientoLoteService.class),
                batchRecordService,
                directiveService,
                operacionService,
                mock(OrdenFabricacionOperacionEventoRepo.class),
                mock(TransaccionAlmacenHeaderRepo.class),
                loteNumeroService,
                Clock.fixed(Instant.parse("2026-09-02T15:00:00Z"), ZoneOffset.UTC));

        semi = new SemiTerminado();
        semi.setProductoId("ST-1");
        semi.setNombre("Base de prueba");
        semi.setTipoUnidades("kg");
        semi.setRequiereOrdenFabricacion(true);
        version = new ManufacturingVersions();
        version.setId(20L);
        version.setVersionNumber(3);
        actor = new User();
        actor.setId(7L);
        actor.setUsername("responsable");
        actor.setCedula(1007L);

        when(semiRepo.findById("ST-1")).thenReturn(Optional.of(semi));
        when(manufacturingVersionRepo.findTopByProductoOrderByVersionNumberDesc(semi))
                .thenReturn(Optional.of(version));
        when(ordenRepo.saveAndFlush(any(OrdenFabricacion.class))).thenAnswer(invocation -> {
            OrdenFabricacion orden = invocation.getArgument(0);
            orden.setOrdenFabricacionId(10L);
            return orden;
        });
        when(loteRepo.saveAndFlush(any(Lote.class))).thenAnswer(invocation -> {
            Lote lote = invocation.getArgument(0);
            lote.setId(30L);
            return lote;
        });
        when(operacionService.listar(10L)).thenReturn(List.of());
    }

    @Test
    void directivaActivaCreaExpedienteYDejaLoteDeOfEnCuarentena() {
        when(directiveService.lockBatchRecordWorkflowForNewOrder()).thenReturn(true);
        BatchRecord record = new BatchRecord();
        record.setId(40L);
        record.setCodigo("BR-OF-10");
        when(batchRecordService.crearParaOrdenFabricacion(
                any(OrdenFabricacion.class), any(Lote.class), any(User.class)))
                .thenReturn(record);

        OrdenFabricacionDTOs.Response response = service.crear(request("OF-LOTE-1"), actor);

        assertEquals(EstadoCalidadLote.CUARENTENA, response.getEstadoCalidadLote());
        assertEquals(40L, response.getBatchRecordId());
        verify(operacionService).inicializar(any(OrdenFabricacion.class), any(BatchRecord.class));
        verify(batchRecordService).materializarRequisitos(any(OrdenFabricacion.class));
    }

    @Test
    void directivaInactivaMantieneOfOperativaSinExpediente() {
        when(directiveService.lockBatchRecordWorkflowForNewOrder()).thenReturn(false);

        OrdenFabricacionDTOs.Response response = service.crear(request("OF-LOTE-2"), actor);

        assertEquals(EstadoCalidadLote.SIN_CLASIFICAR, response.getEstadoCalidadLote());
        assertNull(response.getBatchRecordId());
        assertNull(response.getBatchRecordCodigo());
        verify(batchRecordService, never()).crearParaOrdenFabricacion(any(), any(), any());
        verify(batchRecordService, never()).materializarRequisitos(any(OrdenFabricacion.class));
        verify(operacionService).inicializar(any(OrdenFabricacion.class), isNull());
    }

    @Test
    void generacionAutomaticaRespetaDirectivaInactivaSinSuprimirLaOf() {
        when(directiveService.lockBatchRecordWorkflowForNewOrder()).thenReturn(false);
        when(loteNumeroService.siguiente("ST-1")).thenReturn("ST-0001");
        OrdenProduccion origen = new OrdenProduccion();
        origen.setOrdenId(99);
        origen.setFechaFinalPlanificada(LocalDateTime.of(2026, 9, 10, 12, 0));

        OrdenFabricacionDTOs.Response response = service.crearAutomatica(
                semi, BigDecimal.valueOf(12), origen, actor);

        assertEquals("ST-0001", response.getLote());
        assertEquals(EstadoCalidadLote.SIN_CLASIFICAR, response.getEstadoCalidadLote());
        assertNull(response.getBatchRecordId());
        verify(ordenRepo).saveAndFlush(any(OrdenFabricacion.class));
        verify(operacionService).inicializar(any(OrdenFabricacion.class), isNull());
    }

    private OrdenFabricacionDTOs.CreateRequest request(String lote) {
        OrdenFabricacionDTOs.CreateRequest request = new OrdenFabricacionDTOs.CreateRequest();
        request.setSemiTerminadoId("ST-1");
        request.setCantidadPlanificada(BigDecimal.TEN);
        request.setLote(lote);
        return request;
    }
}
