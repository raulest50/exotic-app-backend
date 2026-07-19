package exotic.app.planta.service.bi.inventario;

import exotic.app.planta.model.bi.dto.InformeInventarioDTO;
import exotic.app.planta.model.inventarios.Movimiento;
import exotic.app.planta.repo.inventarios.TransaccionAlmacenRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InformeInventarioService {
    private static final int CONTRACT_VERSION = 2;
    private static final Set<Integer> VALID_TREND_WINDOWS = Set.of(7, 30, 90);

    private final TransaccionAlmacenRepo movementRepo;
    private final InventarioStockReader stockReader;
    private final StockInventarioAssembler stockAssembler;
    private final MovimientosInventarioAssembler movementAssembler;
    private final PendientesInventarioAssembler pendingAssembler;
    private final Clock applicationClock;

    public InformeInventarioDTO getReport(
            LocalDate startDate,
            LocalDate endDate,
            int trendWindowDays
    ) {
        validateDates(startDate, endDate);
        validateTrendWindow(trendWindowDays);

        boolean singleDate = startDate.equals(endDate);
        LocalDate trendStartDate = singleDate
                ? endDate.minusDays(trendWindowDays - 1L)
                : startDate;
        LocalDate queryStartDate = startDate.isBefore(trendStartDate)
                ? startDate
                : trendStartDate;
        List<ProductoStockSnapshot> stock = stockReader.readGeneralStock();
        List<Movimiento> loadedMovements = loadMovements(queryStartDate, endDate);
        List<Movimiento> periodMovements = loadedMovements.stream()
                .filter(movement -> isWithinPeriod(movement, startDate, endDate))
                .toList();
        List<Movimiento> trendMovements = loadedMovements.stream()
                .filter(movement -> isWithinPeriod(movement, trendStartDate, endDate))
                .toList();

        return InformeInventarioDTO.builder()
                .versionContrato(CONTRACT_VERSION)
                .periodo(toPeriod(startDate, endDate))
                .periodoTendencia(toPeriod(trendStartDate, endDate))
                .fechaHoraCorteStock(LocalDateTime.now(applicationClock))
                .stock(stockAssembler.assemble(stock))
                .movimientos(movementAssembler.assemble(
                        periodMovements,
                        trendMovements,
                        trendStartDate,
                        endDate))
                .ocmPendientes(pendingAssembler.buildPendingPurchaseOrders())
                .materialDirectoOp(pendingAssembler.buildOpenProductionOrderMaterial())
                .notas(buildNotes())
                .build();
    }

    private List<Movimiento> loadMovements(LocalDate startDate, LocalDate endDate) {
        return movementRepo.findMovimientosBiByAlmacenAndRango(
                Movimiento.Almacen.GENERAL,
                startDate.atStartOfDay(),
                endDate.atTime(LocalTime.MAX));
    }

    private boolean isWithinPeriod(
            Movimiento movement,
            LocalDate startDate,
            LocalDate endDate
    ) {
        if (movement.getFechaMovimiento() == null) return false;
        LocalDate movementDate = movement.getFechaMovimiento().toLocalDate();
        return !movementDate.isBefore(startDate) && !movementDate.isAfter(endDate);
    }

    private InformeInventarioDTO.PeriodoDTO toPeriod(
            LocalDate startDate,
            LocalDate endDate
    ) {
        return InformeInventarioDTO.PeriodoDTO.builder()
                .fechaDesde(startDate)
                .fechaHasta(endDate)
                .modoFecha(startDate.equals(endDate) ? "FECHA_UNICA" : "RANGO")
                .dias(Math.toIntExact(ChronoUnit.DAYS.between(startDate, endDate) + 1))
                .build();
    }

    private List<InformeInventarioDTO.NotaDTO> buildNotes() {
        return List.of(
                note(
                        "INFO",
                        "El stock corresponde al saldo presente del almacén General, "
                                + "incluso si el periodo consultado es histórico."),
                note(
                        "INFO",
                        "Toda valoración es estimada con costo maestro actual."),
                note(
                        "INFO",
                        "Las cantidades se presentan por unidad para no sumar magnitudes "
                                + "incompatibles."));
    }

    private InformeInventarioDTO.NotaDTO note(String type, String message) {
        return InformeInventarioDTO.NotaDTO.builder()
                .tipo(type)
                .mensaje(message)
                .build();
    }

    private void validateDates(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null || startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("Rango de fechas invalido.");
        }
    }

    private void validateTrendWindow(int trendWindowDays) {
        if (!VALID_TREND_WINDOWS.contains(trendWindowDays)) {
            throw new IllegalArgumentException("La ventana debe ser 7, 30 o 90 dias.");
        }
    }
}
