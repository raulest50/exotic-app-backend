package exotic.app.planta.service.bi.inventario;

import exotic.app.planta.model.bi.dto.InformeInventarioDTO;
import exotic.app.planta.model.inventarios.Movimiento;
import exotic.app.planta.repo.inventarios.TransaccionAlmacenRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InformeInventarioServiceTest {
    private TransaccionAlmacenRepo movementRepo;
    private InventarioStockReader stockReader;
    private StockInventarioAssembler stockAssembler;
    private MovimientosInventarioAssembler movementAssembler;
    private PendientesInventarioAssembler pendingAssembler;
    private InformeInventarioService service;

    @BeforeEach
    void setUp() {
        movementRepo = mock(TransaccionAlmacenRepo.class);
        stockReader = mock(InventarioStockReader.class);
        stockAssembler = mock(StockInventarioAssembler.class);
        movementAssembler = mock(MovimientosInventarioAssembler.class);
        pendingAssembler = mock(PendientesInventarioAssembler.class);
        service = new InformeInventarioService(
                movementRepo,
                stockReader,
                stockAssembler,
                movementAssembler,
                pendingAssembler,
                Clock.fixed(
                        Instant.parse("2026-07-21T15:00:00Z"),
                        ZoneId.of("America/Bogota")));

        when(stockReader.readGeneralStock()).thenReturn(List.of());
        when(stockAssembler.assemble(List.of()))
                .thenReturn(InformeInventarioDTO.StockDTO.builder().build());
        when(pendingAssembler.buildPendingPurchaseOrders())
                .thenReturn(InformeInventarioDTO.OcmPendientesDTO.builder().build());
        when(pendingAssembler.buildOpenProductionOrderMaterial())
                .thenReturn(InformeInventarioDTO.MaterialDirectoOpDTO.builder().build());
    }

    @Test
    void singleDateUsesOnlyThatDayAndDoesNotBuildATimeSeries() {
        LocalDate date = LocalDate.of(2026, 7, 21);
        Movimiento movement = mock(Movimiento.class);
        List<Movimiento> movements = List.of(movement);
        InformeInventarioDTO.MovimientosDTO movementReport =
                InformeInventarioDTO.MovimientosDTO.builder()
                        .serieDiaria(List.of())
                        .build();
        when(movementRepo.findMovimientosBiByAlmacenAndRango(
                Movimiento.Almacen.GENERAL,
                date.atStartOfDay(),
                date.atTime(LocalTime.MAX)))
                .thenReturn(movements);
        when(movementAssembler.assemble(movements, List.of(), date, date))
                .thenReturn(movementReport);

        InformeInventarioDTO report = service.getReport(date, date);

        verify(movementAssembler).assemble(movements, List.of(), date, date);
        assertEquals(3, report.versionContrato());
        assertEquals(report.periodo(), report.periodoTendencia());
        assertEquals(List.of(), report.movimientos().serieDiaria());
    }

    @Test
    void rangeUsesTheExactPeriodForSummariesAndDailySeries() {
        LocalDate startDate = LocalDate.of(2026, 7, 1);
        LocalDate endDate = LocalDate.of(2026, 7, 21);
        List<Movimiento> movements = List.of(mock(Movimiento.class));
        when(movementRepo.findMovimientosBiByAlmacenAndRango(
                Movimiento.Almacen.GENERAL,
                startDate.atStartOfDay(),
                endDate.atTime(LocalTime.MAX)))
                .thenReturn(movements);
        when(movementAssembler.assemble(
                movements,
                movements,
                startDate,
                endDate))
                .thenReturn(InformeInventarioDTO.MovimientosDTO.builder()
                        .serieDiaria(List.of())
                        .build());

        InformeInventarioDTO report = service.getReport(startDate, endDate);

        verify(movementAssembler).assemble(
                movements,
                movements,
                startDate,
                endDate);
        assertEquals(startDate, report.periodo().fechaDesde());
        assertEquals(endDate, report.periodo().fechaHasta());
        assertEquals(report.periodo(), report.periodoTendencia());
    }
}
