package exotic.app.planta.service.produccion;

import exotic.app.planta.model.inventarios.Lote;
import exotic.app.planta.model.produccion.OrdenProduccion;
import exotic.app.planta.model.produccion.ReporteProduccionLote;
import exotic.app.planta.model.produccion.SeguimientoOrdenArea;
import exotic.app.planta.model.users.User;
import exotic.app.planta.repo.inventarios.LoteRepo;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReporteProduccionLoteServiceTest {

    private ReporteProduccionLoteRepo reporteRepo;
    private LoteRepo loteRepo;
    private OrdenProduccionRepo ordenRepo;
    private ProduccionCierreLockService cierreLockService;
    private ReporteProduccionLoteService service;

    @BeforeEach
    void setUp() {
        reporteRepo = mock(ReporteProduccionLoteRepo.class);
        loteRepo = mock(LoteRepo.class);
        ordenRepo = mock(OrdenProduccionRepo.class);
        cierreLockService = mock(ProduccionCierreLockService.class);
        Clock clock = Clock.fixed(
                Instant.parse("2026-07-15T15:30:00Z"),
                ZoneId.of("America/Bogota")
        );
        service = new ReporteProduccionLoteService(
                reporteRepo, loteRepo, ordenRepo, cierreLockService, clock);
        when(reporteRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void registrarPendiente_createsReportAndMovesOrderToStateThree() {
        OrdenProduccion orden = orden(10, 1, "LOT-10");
        SeguimientoOrdenArea seguimiento = seguimiento(orden);
        Lote lote = lote(orden, "LOT-10");
        User actor = new User();

        when(reporteRepo.existsByOrdenProduccion_OrdenIdAndEstadoNot(
                10, ReporteProduccionLote.Estado.ANULADO)).thenReturn(false);
        when(loteRepo.findByOrdenProduccion_OrdenId(10)).thenReturn(List.of(lote));

        ReporteProduccionLote reporte = service.registrarPendiente(
                seguimiento, actor, new BigDecimal("125.2500"));

        assertEquals(ReporteProduccionLote.Estado.PENDIENTE, reporte.getEstado());
        assertEquals(new BigDecimal("125.25"), reporte.getCantidadReportada());
        assertEquals(LocalDate.of(2026, 7, 15), reporte.getFechaProduccion());
        assertEquals(1, reporte.getEstadoOrdenAnterior());
        assertEquals(3, orden.getEstadoOrden());
        assertNotNull(orden.getFechaFinal());
    }

    @Test
    void anularPendiente_restoresPreviousOrderStateAndClearsPhysicalEnd() {
        OrdenProduccion orden = orden(10, 3, "LOT-10");
        orden.setFechaFinal(java.time.LocalDateTime.of(2026, 7, 15, 10, 30));
        SeguimientoOrdenArea seguimiento = seguimiento(orden);
        seguimiento.setId(25L);
        ReporteProduccionLote reporte = new ReporteProduccionLote();
        reporte.setOrdenProduccion(orden);
        reporte.setEstado(ReporteProduccionLote.Estado.PENDIENTE);
        reporte.setEstadoOrdenAnterior(1);
        User actor = new User();

        when(reporteRepo.findFirstBySeguimientoOrdenArea_IdAndEstado(
                25L, ReporteProduccionLote.Estado.PENDIENTE)).thenReturn(Optional.of(reporte));

        service.anularPendientePorSeguimiento(seguimiento, actor, "Correccion de prueba");

        assertEquals(ReporteProduccionLote.Estado.ANULADO, reporte.getEstado());
        assertEquals(1, orden.getEstadoOrden());
        assertNull(orden.getFechaFinal());
        assertEquals(actor, reporte.getAnuladoPor());
        assertEquals("Correccion de prueba", reporte.getMotivoAnulacion());
    }

    @Test
    void registrarPendiente_rejectsNonPositiveQuantity() {
        assertThrows(IllegalArgumentException.class, () -> service.registrarPendiente(
                seguimiento(orden(10, 1, "LOT-10")), new User(), BigDecimal.ZERO));
    }

    @Test
    void anularPendiente_rejectsCorrectionAfterConfirmedClosure() {
        SeguimientoOrdenArea seguimiento = seguimiento(orden(10, 2, "LOT-10"));
        seguimiento.setId(25L);
        when(reporteRepo.findFirstBySeguimientoOrdenArea_IdAndEstado(
                25L, ReporteProduccionLote.Estado.PENDIENTE)).thenReturn(Optional.empty());
        when(reporteRepo.existsBySeguimientoOrdenArea_IdAndEstado(
                25L, ReporteProduccionLote.Estado.CONFIRMADO)).thenReturn(true);

        assertThrows(CierreProduccionConflictException.class, () ->
                service.anularPendientePorSeguimiento(seguimiento, new User(), "No permitido"));
    }

    private OrdenProduccion orden(int id, int estado, String loteAsignado) {
        OrdenProduccion orden = new OrdenProduccion();
        orden.setOrdenId(id);
        orden.setEstadoOrden(estado);
        orden.setLoteAsignado(loteAsignado);
        return orden;
    }

    private SeguimientoOrdenArea seguimiento(OrdenProduccion orden) {
        SeguimientoOrdenArea seguimiento = new SeguimientoOrdenArea();
        seguimiento.setOrdenProduccion(orden);
        return seguimiento;
    }

    private Lote lote(OrdenProduccion orden, String batchNumber) {
        Lote lote = new Lote();
        lote.setOrdenProduccion(orden);
        lote.setBatchNumber(batchNumber);
        return lote;
    }
}
