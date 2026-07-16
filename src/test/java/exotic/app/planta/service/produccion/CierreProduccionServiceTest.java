package exotic.app.planta.service.produccion;

import exotic.app.planta.model.inventarios.Lote;
import exotic.app.planta.model.inventarios.Movimiento;
import exotic.app.planta.model.inventarios.TransaccionAlmacen;
import exotic.app.planta.model.producto.Terminado;
import exotic.app.planta.model.produccion.CierreProduccion;
import exotic.app.planta.model.produccion.OrdenProduccion;
import exotic.app.planta.model.produccion.ReporteProduccionLote;
import exotic.app.planta.model.produccion.SeguimientoOrdenArea;
import exotic.app.planta.model.produccion.dto.CierreProduccionRequestDTO;
import exotic.app.planta.model.produccion.dto.CierreProduccionResponseDTO;
import exotic.app.planta.model.users.User;
import exotic.app.planta.repo.inventarios.LoteRepo;
import exotic.app.planta.repo.inventarios.TransaccionAlmacenHeaderRepo;
import exotic.app.planta.repo.produccion.CierreProduccionRepo;
import exotic.app.planta.repo.produccion.OrdenProduccionRepo;
import exotic.app.planta.repo.produccion.ReporteProduccionLoteRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CierreProduccionServiceTest {

    private CierreProduccionRepo cierreRepo;
    private ReporteProduccionLoteRepo reporteRepo;
    private OrdenProduccionRepo ordenRepo;
    private LoteRepo loteRepo;
    private TransaccionAlmacenHeaderRepo transaccionRepo;
    private ProduccionCierreLockService cierreLockService;
    private CierreProduccionService service;

    @BeforeEach
    void setUp() {
        cierreRepo = mock(CierreProduccionRepo.class);
        reporteRepo = mock(ReporteProduccionLoteRepo.class);
        ordenRepo = mock(OrdenProduccionRepo.class);
        loteRepo = mock(LoteRepo.class);
        transaccionRepo = mock(TransaccionAlmacenHeaderRepo.class);
        cierreLockService = mock(ProduccionCierreLockService.class);
        service = new CierreProduccionService(
                cierreRepo,
                reporteRepo,
                ordenRepo,
                loteRepo,
                transaccionRepo,
                cierreLockService,
                Clock.fixed(Instant.parse("2026-07-16T14:00:00Z"), ZoneId.of("America/Bogota"))
        );
    }

    @Test
    void confirmar_createsOneClosureAndOnePendingWarehouseTransactionPerOrder() {
        LocalDate fechaProduccion = LocalDate.of(2026, 7, 15);
        User actor = new User();
        ReporteProduccionLote reporte = reporte(51L, 2L, fechaProduccion, 1001, "LOT-1001");
        CierreProduccionRequestDTO request = request(
                fechaProduccion,
                UUID.fromString("72e1cad1-b95d-4d0d-bd79-d9da54afebae"),
                item(51L, 2L, "95.5", "Merma validada")
        );
        AtomicReference<CierreProduccion> cierreGuardado = new AtomicReference<>();

        when(cierreRepo.findByIdempotencyKey(request.getIdempotencyKey()))
                .thenAnswer(invocation -> Optional.ofNullable(cierreGuardado.get()));
        when(cierreRepo.save(any())).thenAnswer(invocation -> {
            CierreProduccion cierre = invocation.getArgument(0);
            cierre.setId(700L);
            cierreGuardado.set(cierre);
            return cierre;
        });
        when(reporteRepo.findPendientesByFechaForUpdate(
                fechaProduccion, ReporteProduccionLote.Estado.PENDIENTE)).thenReturn(List.of(reporte));
        when(reporteRepo.findByCierreProduccion_IdOrderByIdAsc(700L)).thenReturn(List.of(reporte));
        when(ordenRepo.findByIdForUpdate(1001)).thenReturn(Optional.of(reporte.getOrdenProduccion()));
        when(transaccionRepo.countByTipoEntidadCausanteAndIdEntidadCausante(
                TransaccionAlmacen.TipoEntidadCausante.OP, 1001)).thenReturn(0L);
        when(transaccionRepo.save(any())).thenAnswer(invocation -> {
            TransaccionAlmacen transaccion = invocation.getArgument(0);
            transaccion.setTransaccionId(800);
            return transaccion;
        });

        CierreProduccionResponseDTO primera = service.confirmar(actor, request);
        CierreProduccionResponseDTO repetida = service.confirmar(actor, request);

        assertEquals(700L, primera.getCierreId());
        assertEquals(primera.getCierreId(), repetida.getCierreId());
        assertEquals(new BigDecimal("95.5"), reporte.getCantidadConfirmada());
        assertEquals(ReporteProduccionLote.Estado.CONFIRMADO, reporte.getEstado());
        assertEquals(2, reporte.getOrdenProduccion().getEstadoOrden());
        assertEquals(fechaProduccion, reporte.getLote().getProductionDate());
        assertSame(cierreGuardado.get(), reporte.getCierreProduccion());
        assertEquals(TransaccionAlmacen.EstadoContable.PENDIENTE,
                reporte.getTransaccionAlmacen().getEstadoContable());
        Movimiento movimiento = reporte.getTransaccionAlmacen().getMovimientosTransaccion().get(0);
        assertEquals(Movimiento.TipoMovimiento.BACKFLUSH, movimiento.getTipoMovimiento());
        assertEquals(Movimiento.Almacen.GENERAL, movimiento.getAlmacen());
        assertEquals(95.5, movimiento.getCantidad());
        verify(transaccionRepo, times(1)).save(any());
    }

    @Test
    void confirmar_rejectsSnapshotThatDoesNotContainEveryPendingReport() {
        LocalDate fecha = LocalDate.of(2026, 7, 15);
        ReporteProduccionLote primero = reporte(51L, 0L, fecha, 1001, "LOT-1001");
        ReporteProduccionLote segundo = reporte(52L, 0L, fecha, 1002, "LOT-1002");
        CierreProduccionRequestDTO request = request(fecha, UUID.randomUUID(), item(51L, 0L, "100", null));

        when(cierreRepo.findByIdempotencyKey(request.getIdempotencyKey())).thenReturn(Optional.empty());
        when(reporteRepo.findPendientesByFechaForUpdate(fecha, ReporteProduccionLote.Estado.PENDIENTE))
                .thenReturn(List.of(primero, segundo));

        assertThrows(CierreProduccionConflictException.class, () -> service.confirmar(new User(), request));
    }

    private ReporteProduccionLote reporte(
            long id,
            long version,
            LocalDate fecha,
            int ordenId,
            String batchNumber
    ) {
        Terminado producto = new Terminado();
        producto.setProductoId("PT-" + ordenId);

        OrdenProduccion orden = new OrdenProduccion();
        orden.setOrdenId(ordenId);
        orden.setEstadoOrden(3);
        orden.setProducto(producto);

        Lote lote = new Lote();
        lote.setId((long) ordenId);
        lote.setBatchNumber(batchNumber);
        lote.setOrdenProduccion(orden);

        ReporteProduccionLote reporte = new ReporteProduccionLote();
        reporte.setId(id);
        reporte.setVersion(version);
        reporte.setOrdenProduccion(orden);
        reporte.setLote(lote);
        reporte.setSeguimientoOrdenArea(new SeguimientoOrdenArea());
        reporte.setCantidadReportada(new BigDecimal("100"));
        reporte.setFechaProduccion(fecha);
        reporte.setEstado(ReporteProduccionLote.Estado.PENDIENTE);
        return reporte;
    }

    private CierreProduccionRequestDTO request(
            LocalDate fecha,
            UUID idempotencyKey,
            CierreProduccionRequestDTO.ItemDTO... items
    ) {
        CierreProduccionRequestDTO request = new CierreProduccionRequestDTO();
        request.setFechaProduccion(fecha);
        request.setIdempotencyKey(idempotencyKey);
        request.setReportes(List.of(items));
        return request;
    }

    private CierreProduccionRequestDTO.ItemDTO item(
            long reporteId,
            long version,
            String cantidad,
            String motivo
    ) {
        CierreProduccionRequestDTO.ItemDTO item = new CierreProduccionRequestDTO.ItemDTO();
        item.setReporteId(reporteId);
        item.setVersion(version);
        item.setCantidadConfirmada(new BigDecimal(cantidad));
        item.setMotivoCorreccion(motivo);
        return item;
    }
}
