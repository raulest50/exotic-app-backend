package exotic.app.planta.service.bi.inventario;

import exotic.app.planta.model.bi.dto.CoberturaMaterialesDTO;
import exotic.app.planta.model.inventarios.Movimiento;
import exotic.app.planta.model.producto.Material;
import exotic.app.planta.repo.inventarios.TransaccionAlmacenRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CoberturaMaterialesService {
    private static final Set<Integer> VALID_WINDOWS = Set.of(7, 30, 90);
    private static final int MIN_OBSERVED_DAYS = 30;
    private static final int MIN_ACTIVE_DAYS = 5;
    private static final int MAX_INTERVAL_WIDTH_DAYS = 30;
    private static final int MAX_ESTIMATES = 10;

    private final TransaccionAlmacenRepo movementRepo;
    private final InventarioStockReader stockReader;
    private final BootstrapDemandIntervalCalculator intervalCalculator;
    private final Clock applicationClock;

    public CoberturaMaterialesDTO calculate(int windowDays) {
        validateWindow(windowDays);
        LocalDate cutoffDate = LocalDate.now(applicationClock);
        LocalDate startDate = cutoffDate.minusDays(windowDays - 1L);

        List<ProductoStockSnapshot> materialStock = stockReader.readGeneralStock().stream()
                .filter(snapshot -> snapshot.producto() instanceof Material)
                .toList();
        List<Movimiento> dispensations = loadDispensations(startDate, cutoffDate);
        Map<String, double[]> demandByMaterial = groupDailyDemand(
                dispensations,
                startDate,
                windowDays);

        List<CoberturaMaterialesDTO.EstimacionMaterialDTO> estimates = materialStock.stream()
                .map(snapshot -> estimateMaterial(
                        snapshot,
                        demandByMaterial.getOrDefault(
                                snapshot.producto().getProductoId(),
                                new double[windowDays]),
                        cutoffDate,
                        windowDays))
                .filter(Objects::nonNull)
                .sorted(Comparator
                        .comparing(CoberturaMaterialesDTO.EstimacionMaterialDTO::fechaAgotamiento)
                        .thenComparing(CoberturaMaterialesDTO.EstimacionMaterialDTO::productoId))
                .toList();

        CoberturaMaterialesDTO.EstimacionMaterialDTO criticalEstimate =
                estimates.isEmpty() ? null : estimates.get(0);
        List<String> lowConfidenceReasons = lowConfidenceReasons(
                windowDays,
                criticalEstimate);

        return CoberturaMaterialesDTO.builder()
                .ventanaDias(windowDays)
                .fechaDesde(startDate)
                .fechaHasta(cutoffDate)
                .fechaHoraCorteStock(LocalDateTime.now(applicationClock))
                .estado(criticalEstimate == null
                        ? CoberturaMaterialesDTO.EstadoCobertura.SIN_CONSUMO
                        : CoberturaMaterialesDTO.EstadoCobertura.ESTIMADO)
                .fechaPrimerAgotamiento(valueOrNull(
                        criticalEstimate,
                        CoberturaMaterialesDTO.EstimacionMaterialDTO::fechaAgotamiento))
                .materialCriticoId(valueOrNull(
                        criticalEstimate,
                        CoberturaMaterialesDTO.EstimacionMaterialDTO::productoId))
                .materialCriticoNombre(valueOrNull(
                        criticalEstimate,
                        CoberturaMaterialesDTO.EstimacionMaterialDTO::nombre))
                .intervaloFechaMin(valueOrNull(
                        criticalEstimate,
                        CoberturaMaterialesDTO.EstimacionMaterialDTO::intervaloFechaMin))
                .intervaloFechaMax(valueOrNull(
                        criticalEstimate,
                        CoberturaMaterialesDTO.EstimacionMaterialDTO::intervaloFechaMax))
                .confianzaBaja(criticalEstimate != null && !lowConfidenceReasons.isEmpty())
                .motivosConfianzaBaja(lowConfidenceReasons)
                .diasObservados(windowDays)
                .diasConDispensacion(distinctDispensationDays(dispensations))
                .materialesAnalizados(materialStock.size())
                .materialesConDemanda(estimates.size())
                .estimaciones(estimates.stream().limit(MAX_ESTIMATES).toList())
                .build();
    }

    private List<Movimiento> loadDispensations(LocalDate startDate, LocalDate cutoffDate) {
        return movementRepo.findMovimientosBiByAlmacenAndRango(
                        Movimiento.Almacen.GENERAL,
                        startDate.atStartOfDay(),
                        cutoffDate.atTime(LocalTime.MAX))
                .stream()
                .filter(this::isMaterialDispensation)
                .toList();
    }

    private boolean isMaterialDispensation(Movimiento movement) {
        return movement.getProducto() instanceof Material
                && movement.getCantidad() < 0
                && movement.getTipoMovimiento() == Movimiento.TipoMovimiento.DISPENSACION;
    }

    private Map<String, double[]> groupDailyDemand(
            List<Movimiento> dispensations,
            LocalDate startDate,
            int windowDays
    ) {
        Map<String, double[]> demandByMaterial = new HashMap<>();
        for (Movimiento dispensation : dispensations) {
            if (dispensation.getFechaMovimiento() == null) continue;

            int dayIndex = Math.toIntExact(ChronoUnit.DAYS.between(
                    startDate,
                    dispensation.getFechaMovimiento().toLocalDate()));
            if (dayIndex < 0 || dayIndex >= windowDays) continue;

            demandByMaterial.computeIfAbsent(
                            dispensation.getProducto().getProductoId(),
                            ignored -> new double[windowDays])[dayIndex]
                    += Math.abs(dispensation.getCantidad());
        }
        return demandByMaterial;
    }

    private CoberturaMaterialesDTO.EstimacionMaterialDTO estimateMaterial(
            ProductoStockSnapshot snapshot,
            double[] dailyDemand,
            LocalDate cutoffDate,
            int windowDays
    ) {
        double meanDemand = Arrays.stream(dailyDemand).average().orElse(0);
        if (meanDemand <= 0) return null;

        int activeDays = Math.toIntExact(Arrays.stream(dailyDemand)
                .filter(demand -> demand > 0)
                .count());
        ExhaustionEstimate exhaustion = snapshot.stockGeneral() <= 0
                ? ExhaustionEstimate.exhaustedToday(cutoffDate)
                : estimatePositiveStock(
                        snapshot,
                        dailyDemand,
                        meanDemand,
                        cutoffDate,
                        windowDays);

        return CoberturaMaterialesDTO.EstimacionMaterialDTO.builder()
                .productoId(snapshot.producto().getProductoId())
                .nombre(snapshot.producto().getNombre())
                .unidadMedida(InventarioBiUtils.unitOf(snapshot.producto()))
                .stockActual(snapshot.stockGeneral())
                .demandaMediaDiaria(meanDemand)
                .diasConDispensacion(activeDays)
                .diasHastaAgotamiento(exhaustion.daysUntilExhaustion())
                .fechaAgotamiento(exhaustion.estimatedDate())
                .intervaloFechaMin(exhaustion.earliestDate())
                .intervaloFechaMax(exhaustion.latestDate())
                .build();
    }

    private ExhaustionEstimate estimatePositiveStock(
            ProductoStockSnapshot snapshot,
            double[] dailyDemand,
            double meanDemand,
            LocalDate cutoffDate,
            int windowDays
    ) {
        double daysUntilExhaustion = snapshot.stockGeneral() / meanDemand;
        LocalDate estimatedDate = addRoundedUpDays(cutoffDate, daysUntilExhaustion);
        long seed = deterministicSeed(
                snapshot.producto().getProductoId(),
                cutoffDate,
                windowDays);
        var demandInterval = intervalCalculator.calculate(dailyDemand, seed);

        LocalDate earliestDate = demandInterval.upperMean() > 0
                ? addRoundedUpDays(
                        cutoffDate,
                        snapshot.stockGeneral() / demandInterval.upperMean())
                : null;
        LocalDate latestDate = demandInterval.lowerMean() > 0
                ? addRoundedUpDays(
                        cutoffDate,
                        snapshot.stockGeneral() / demandInterval.lowerMean())
                : null;

        return new ExhaustionEstimate(
                daysUntilExhaustion,
                estimatedDate,
                earliestDate,
                latestDate);
    }

    private List<String> lowConfidenceReasons(
            int windowDays,
            CoberturaMaterialesDTO.EstimacionMaterialDTO criticalEstimate
    ) {
        if (criticalEstimate == null) return List.of();

        List<String> reasons = new ArrayList<>();
        if (windowDays < MIN_OBSERVED_DAYS) {
            reasons.add("Se observaron menos de 30 días.");
        }
        if (criticalEstimate.diasConDispensacion() < MIN_ACTIVE_DAYS) {
            reasons.add("El material crítico tuvo menos de 5 días con dispensación.");
        }
        if (criticalEstimate.intervaloFechaMax() == null) {
            reasons.add("El límite máximo del intervalo no es estimable.");
        } else if (criticalEstimate.intervaloFechaMin() != null
                && ChronoUnit.DAYS.between(
                        criticalEstimate.intervaloFechaMin(),
                        criticalEstimate.intervaloFechaMax()) > MAX_INTERVAL_WIDTH_DAYS) {
            reasons.add("El intervalo de fechas supera 30 días.");
        }
        return List.copyOf(reasons);
    }

    private int distinctDispensationDays(List<Movimiento> dispensations) {
        return Math.toIntExact(dispensations.stream()
                .map(Movimiento::getFechaMovimiento)
                .filter(Objects::nonNull)
                .map(LocalDateTime::toLocalDate)
                .distinct()
                .count());
    }

    private LocalDate addRoundedUpDays(LocalDate date, double days) {
        return date.plusDays((long) Math.ceil(days));
    }

    private long deterministicSeed(String productId, LocalDate cutoffDate, int windowDays) {
        return 31L * (31L * productId.hashCode() + cutoffDate.toEpochDay()) + windowDays;
    }

    private void validateWindow(int windowDays) {
        if (!VALID_WINDOWS.contains(windowDays)) {
            throw new IllegalArgumentException("La ventana debe ser 7, 30 o 90 dias.");
        }
    }

    private <T> T valueOrNull(
            CoberturaMaterialesDTO.EstimacionMaterialDTO estimate,
            Function<CoberturaMaterialesDTO.EstimacionMaterialDTO, T> getter
    ) {
        return estimate == null ? null : getter.apply(estimate);
    }

    private record ExhaustionEstimate(
            double daysUntilExhaustion,
            LocalDate estimatedDate,
            LocalDate earliestDate,
            LocalDate latestDate
    ) {
        static ExhaustionEstimate exhaustedToday(LocalDate cutoffDate) {
            return new ExhaustionEstimate(0, cutoffDate, cutoffDate, cutoffDate);
        }
    }
}
