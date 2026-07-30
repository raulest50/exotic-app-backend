package exotic.app.planta.service.bi.inventario;

import exotic.app.planta.model.bi.dto.CoberturaMaterialesDTO;
import exotic.app.planta.model.bi.dto.FuenteDemandaCobertura;
import exotic.app.planta.model.bi.dto.PaginaInformeInventarioDTO;
import exotic.app.planta.model.inventarios.CausaAjusteInventario;
import exotic.app.planta.model.inventarios.Movimiento;
import exotic.app.planta.model.inventarios.TransaccionAlmacen;
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
import java.util.Locale;
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
    private static final int MAX_SEARCH_LENGTH = 100;
    private static final Set<Integer> ALLOWED_PAGE_SIZES = Set.of(10, 20);

    private final TransaccionAlmacenRepo movementRepo;
    private final InventarioStockReader stockReader;
    private final BootstrapDemandIntervalCalculator intervalCalculator;
    private final Clock applicationClock;

    public CoberturaMaterialesDTO calculate(int windowDays) {
        return calculate(windowDays, FuenteDemandaCobertura.SOLO_DISPENSACIONES);
    }

    public CoberturaMaterialesDTO calculate(
            int windowDays,
            FuenteDemandaCobertura demandSource
    ) {
        return calculate(
                windowDays,
                demandSource,
                "TODOS",
                "TODOS",
                null,
                "AGOTAMIENTO",
                null,
                0,
                10);
    }

    public CoberturaMaterialesDTO calculate(
            int windowDays,
            FuenteDemandaCobertura demandSource,
            String rawHorizon,
            String rawGroup,
            String rawUnit,
            String rawOrder,
            String rawSearch,
            int page,
            int size
    ) {
        validateWindow(windowDays);
        validateDemandSource(demandSource);
        validatePage(page, size);
        CoverageHorizon horizon = CoverageHorizon.parse(rawHorizon);
        MaterialGroup group = MaterialGroup.parse(rawGroup);
        CoverageOrder order = CoverageOrder.parse(rawOrder);
        String unit = normalizeOptional(rawUnit);
        String search = normalizeSearch(rawSearch);
        if (order == CoverageOrder.MAYOR_DEMANDA && unit.isBlank()) {
            throw new IllegalArgumentException(
                    "Debe seleccionar una unidad para ordenar por demanda.");
        }
        LocalDate cutoffDate = LocalDate.now(applicationClock);
        LocalDate startDate = cutoffDate.minusDays(windowDays - 1L);

        List<ProductoStockSnapshot> materialStock = stockReader.readGeneralStock().stream()
                .filter(snapshot -> snapshot.producto() instanceof Material)
                .toList();
        List<Movimiento> movements = loadMovements(startDate, cutoffDate);
        List<Movimiento> dispensations = movements.stream()
                .filter(this::isMaterialDispensation)
                .toList();
        List<Movimiento> contingencyAdjustments = movements.stream()
                .filter(this::isMaterialNegativeAdjustment)
                .filter(this::isContingencyAdjustment)
                .toList();
        long unclassifiedNegativeAdjustments = movements.stream()
                .filter(this::isMaterialNegativeAdjustment)
                .filter(this::isUnclassifiedAdjustment)
                .count();

        Map<String, double[]> operativeDemandByMaterial = groupDailyDemand(
                dispensations,
                startDate,
                windowDays);
        Map<String, double[]> contingencyDemandByMaterial = groupDailyDemand(
                contingencyAdjustments,
                startDate,
                windowDays);
        Map<String, Integer> contingencyMovementsByMaterial = groupMovementCount(
                contingencyAdjustments);
        boolean includeContingencies = demandSource.incluyeContingencias();
        List<Movimiento> includedDemandMovements = new ArrayList<>(dispensations);
        if (includeContingencies) {
            includedDemandMovements.addAll(contingencyAdjustments);
        }

        List<CoberturaMaterialesDTO.EstimacionMaterialDTO> estimates = materialStock.stream()
                .map(snapshot -> estimateMaterial(
                        snapshot,
                        operativeDemandByMaterial.getOrDefault(
                                snapshot.producto().getProductoId(),
                                new double[windowDays]),
                        includeContingencies
                                ? contingencyDemandByMaterial.getOrDefault(
                                        snapshot.producto().getProductoId(),
                                        new double[windowDays])
                                : new double[windowDays],
                        includeContingencies
                                ? contingencyMovementsByMaterial.getOrDefault(
                                        snapshot.producto().getProductoId(),
                                        0)
                                : 0,
                        cutoffDate,
                        windowDays))
                .filter(Objects::nonNull)
                .sorted(coverageComparator())
                .toList();

        CoberturaMaterialesDTO.EstimacionMaterialDTO criticalEstimate =
                estimates.isEmpty() ? null : estimates.get(0);
        List<String> lowConfidenceReasons = criticalEstimate == null
                ? List.of()
                : criticalEstimate.motivosConfianzaBaja();
        List<CoberturaMaterialesDTO.EstimacionMaterialDTO> filtered =
                estimates.stream()
                        .filter(estimate -> horizon.matches(
                                estimate.diasHastaAgotamiento()))
                        .filter(estimate -> group.matches(estimate.grupo()))
                        .filter(estimate -> unit.isBlank()
                                || unit.equals(normalizeOptional(
                                        estimate.unidadMedida())))
                        .filter(estimate -> matchesSearch(estimate, search))
                        .sorted(comparator(order))
                        .toList();

        return CoberturaMaterialesDTO.builder()
                .ventanaDias(windowDays)
                .fechaDesde(startDate)
                .fechaHasta(cutoffDate)
                .fechaHoraCorteStock(LocalDateTime.now(applicationClock))
                .fuenteDemanda(demandSource)
                .escenarioExploratorio(includeContingencies)
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
                .diasConDemanda(distinctMovementDays(includedDemandMovements))
                .materialesAnalizados(materialStock.size())
                .materialesConDemanda(estimates.size())
                .resumenFuentesDemanda(
                        CoberturaMaterialesDTO.ResumenFuentesDemandaDTO.builder()
                                .movimientosDispensacionIncluidos(dispensations.size())
                                .ajustesContingenciaDisponibles(
                                        contingencyAdjustments.size())
                                .ajustesContingenciaIncluidos(
                                        includeContingencies
                                                ? contingencyAdjustments.size()
                                                : 0)
                                .ajustesNegativosSinClasificarExcluidos(
                                        Math.toIntExact(unclassifiedNegativeAdjustments))
                                .build())
                .estimaciones(estimates.stream().limit(MAX_ESTIMATES).toList())
                .facetas(CoberturaMaterialesDTO.FacetasCoberturaDTO.builder()
                        .gruposDisponibles(estimates.stream()
                                .map(CoberturaMaterialesDTO.EstimacionMaterialDTO::grupo)
                                .distinct()
                                .sorted()
                                .toList())
                        .unidadesDisponibles(estimates.stream()
                                .map(CoberturaMaterialesDTO.EstimacionMaterialDTO::unidadMedida)
                                .distinct()
                                .sorted()
                                .toList())
                        .build())
                .pagina(toPage(filtered, page, size))
                .build();
    }

    private List<Movimiento> loadMovements(LocalDate startDate, LocalDate cutoffDate) {
        return movementRepo.findMovimientosBiByAlmacenAndRango(
                        Movimiento.Almacen.GENERAL,
                        startDate.atStartOfDay(),
                        cutoffDate.atTime(LocalTime.MAX));
    }

    private boolean isMaterialDispensation(Movimiento movement) {
        return movement.getProducto() instanceof Material
                && movement.getCantidad() < 0
                && movement.getTipoMovimiento() == Movimiento.TipoMovimiento.DISPENSACION;
    }

    private boolean isMaterialNegativeAdjustment(Movimiento movement) {
        return movement.getProducto() instanceof Material
                && movement.getCantidad() < 0
                && movement.getTipoMovimiento() == Movimiento.TipoMovimiento.AJUSTE_NEGATIVO;
    }

    private boolean isContingencyAdjustment(Movimiento movement) {
        if (movement.getTransaccionAlmacen() == null) return false;
        if (movement.getTransaccionAlmacen().getTipoEntidadCausante()
                != TransaccionAlmacen.TipoEntidadCausante.OAA) {
            return false;
        }
        CausaAjusteInventario cause = movement.getTransaccionAlmacen().getCausaAjuste();
        return cause != null && cause.isElegibleComoDemanda();
    }

    private boolean isUnclassifiedAdjustment(Movimiento movement) {
        if (movement.getTransaccionAlmacen() == null) return true;
        return movement.getTransaccionAlmacen().getTipoEntidadCausante()
                == TransaccionAlmacen.TipoEntidadCausante.OAA
                && movement.getTransaccionAlmacen().getCausaAjuste() == null;
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

    private Map<String, Integer> groupMovementCount(List<Movimiento> movements) {
        Map<String, Integer> countByMaterial = new HashMap<>();
        for (Movimiento movement : movements) {
            countByMaterial.merge(
                    movement.getProducto().getProductoId(),
                    1,
                    Integer::sum);
        }
        return countByMaterial;
    }

    private CoberturaMaterialesDTO.EstimacionMaterialDTO estimateMaterial(
            ProductoStockSnapshot snapshot,
            double[] operativeDailyDemand,
            double[] contingencyDailyDemand,
            int includedContingencyMovements,
            LocalDate cutoffDate,
            int windowDays
    ) {
        double[] dailyDemand = combineDailyDemand(
                operativeDailyDemand,
                contingencyDailyDemand);
        double meanDemand = Arrays.stream(dailyDemand).average().orElse(0);
        if (meanDemand <= 0) return null;

        double operativeMeanDemand = Arrays.stream(operativeDailyDemand)
                .average()
                .orElse(0);
        double contingencyMeanDemand = Arrays.stream(contingencyDailyDemand)
                .average()
                .orElse(0);
        int activeDays = Math.toIntExact(Arrays.stream(dailyDemand)
                .filter(demand -> demand > 0)
                .count());
        int dispensationDays = Math.toIntExact(Arrays.stream(operativeDailyDemand)
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
        List<String> confidenceReasons = lowConfidenceReasons(
                windowDays,
                activeDays,
                includedContingencyMovements,
                exhaustion.earliestDate(),
                exhaustion.latestDate());

        return CoberturaMaterialesDTO.EstimacionMaterialDTO.builder()
                .productoId(snapshot.producto().getProductoId())
                .nombre(snapshot.producto().getNombre())
                .grupo(InventarioBiUtils.inventoryTypeOf(snapshot.producto()))
                .unidadMedida(InventarioBiUtils.unitOf(snapshot.producto()))
                .stockActual(snapshot.stockGeneral())
                .demandaMediaDiaria(meanDemand)
                .demandaMediaDiariaOperativa(operativeMeanDemand)
                .demandaMediaDiariaContingencia(contingencyMeanDemand)
                .diasConDispensacion(dispensationDays)
                .diasConDemanda(activeDays)
                .ajustesContingenciaIncluidos(includedContingencyMovements)
                .diasHastaAgotamiento(exhaustion.daysUntilExhaustion())
                .fechaAgotamiento(exhaustion.estimatedDate())
                .intervaloFechaMin(exhaustion.earliestDate())
                .intervaloFechaMax(exhaustion.latestDate())
                .confianzaBaja(!confidenceReasons.isEmpty())
                .motivosConfianzaBaja(confidenceReasons)
                .build();
    }

    private double[] combineDailyDemand(double[] operative, double[] contingency) {
        double[] combined = Arrays.copyOf(operative, operative.length);
        for (int i = 0; i < combined.length; i++) {
            combined[i] += contingency[i];
        }
        return combined;
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
            int activeDays,
            int includedContingencyMovements,
            LocalDate earliestDate,
            LocalDate latestDate
    ) {
        List<String> reasons = new ArrayList<>();
        if (windowDays < MIN_OBSERVED_DAYS) {
            reasons.add("Se observaron menos de 30 días.");
        }
        if (activeDays < MIN_ACTIVE_DAYS) {
            reasons.add("El material tuvo menos de 5 días con demanda.");
        }
        if (includedContingencyMovements > 0) {
            reasons.add(
                    "El material incluye salidas de producción registradas como contingencia.");
        }
        if (latestDate == null) {
            reasons.add("El límite máximo del intervalo no es estimable.");
        } else if (earliestDate != null
                && ChronoUnit.DAYS.between(
                        earliestDate,
                        latestDate) > MAX_INTERVAL_WIDTH_DAYS) {
            reasons.add("El intervalo de fechas supera 30 días.");
        }
        return List.copyOf(reasons);
    }

    private Comparator<CoberturaMaterialesDTO.EstimacionMaterialDTO>
    comparator(CoverageOrder order) {
        return switch (order) {
            case AGOTAMIENTO -> coverageComparator();
            case MAYOR_DEMANDA -> Comparator
                    .comparingDouble(
                            CoberturaMaterialesDTO.EstimacionMaterialDTO::demandaMediaDiaria)
                    .reversed()
                    .thenComparing(coverageComparator());
            case NOMBRE -> Comparator
                    .comparing(
                            (CoberturaMaterialesDTO.EstimacionMaterialDTO estimate) ->
                                    normalizeOptional(estimate.nombre()),
                            String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(
                            CoberturaMaterialesDTO.EstimacionMaterialDTO::productoId,
                            String.CASE_INSENSITIVE_ORDER);
        };
    }

    private Comparator<CoberturaMaterialesDTO.EstimacionMaterialDTO>
    coverageComparator() {
        return Comparator
                .comparing(
                        CoberturaMaterialesDTO.EstimacionMaterialDTO::fechaAgotamiento,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(
                        CoberturaMaterialesDTO.EstimacionMaterialDTO::diasHastaAgotamiento,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(
                        CoberturaMaterialesDTO.EstimacionMaterialDTO::productoId,
                        String.CASE_INSENSITIVE_ORDER);
    }

    private boolean matchesSearch(
            CoberturaMaterialesDTO.EstimacionMaterialDTO estimate,
            String search
    ) {
        if (search.isBlank()) return true;
        return normalizeOptional(estimate.productoId()).contains(search)
                || normalizeOptional(estimate.nombre()).contains(search);
    }

    private String normalizeSearch(String value) {
        String search = normalizeOptional(value);
        if (search.length() > MAX_SEARCH_LENGTH) {
            throw new IllegalArgumentException(
                    "La busqueda no puede superar 100 caracteres.");
        }
        return search;
    }

    private String normalizeOptional(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private void validatePage(int page, int size) {
        if (page < 0) {
            throw new IllegalArgumentException("La pagina no puede ser negativa.");
        }
        if (!ALLOWED_PAGE_SIZES.contains(size)) {
            throw new IllegalArgumentException(
                    "El tamaño de pagina debe ser 10 o 20.");
        }
    }

    private PaginaInformeInventarioDTO<
            CoberturaMaterialesDTO.EstimacionMaterialDTO> toPage(
            List<CoberturaMaterialesDTO.EstimacionMaterialDTO> items,
            int requestedPage,
            int size
    ) {
        int totalElements = items.size();
        int totalPages = totalElements == 0
                ? 0
                : (int) Math.ceil(totalElements / (double) size);
        int page = totalPages == 0
                ? 0
                : Math.min(requestedPage, totalPages - 1);
        int from = Math.min(page * size, totalElements);
        int to = Math.min(from + size, totalElements);

        return new PaginaInformeInventarioDTO<>(
                items.subList(from, to),
                page,
                size,
                totalElements,
                totalPages,
                page == 0,
                totalPages == 0 || page >= totalPages - 1);
    }

    private int distinctDispensationDays(List<Movimiento> dispensations) {
        return distinctMovementDays(dispensations);
    }

    private int distinctMovementDays(List<Movimiento> movements) {
        return Math.toIntExact(movements.stream()
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

    private void validateDemandSource(FuenteDemandaCobertura demandSource) {
        if (demandSource == null) {
            throw new IllegalArgumentException("La fuente de demanda es obligatoria.");
        }
    }

    private enum CoverageHorizon {
        TODOS,
        AGOTADO,
        HASTA_7_DIAS,
        DE_8_A_30_DIAS,
        MAS_DE_30_DIAS;

        boolean matches(Double rawDays) {
            if (this == TODOS) return true;
            if (rawDays == null) return false;
            double days = rawDays;
            return switch (this) {
                case TODOS -> true;
                case AGOTADO -> days <= 0;
                case HASTA_7_DIAS -> days > 0 && days <= 7;
                case DE_8_A_30_DIAS -> days > 7 && days <= 30;
                case MAS_DE_30_DIAS -> days > 30;
            };
        }

        static CoverageHorizon parse(String rawValue) {
            try {
                return valueOf(normalizeEnum(rawValue));
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException(
                        "El horizonte de cobertura no es valido.");
            }
        }
    }

    private enum MaterialGroup {
        TODOS,
        MATERIA_PRIMA,
        EMPAQUE,
        OTROS;

        boolean matches(String value) {
            return this == TODOS || name().equals(value);
        }

        static MaterialGroup parse(String rawValue) {
            try {
                return valueOf(normalizeEnum(rawValue));
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException(
                        "El grupo de material no es valido.");
            }
        }
    }

    private enum CoverageOrder {
        AGOTAMIENTO,
        MAYOR_DEMANDA,
        NOMBRE;

        static CoverageOrder parse(String rawValue) {
            try {
                return valueOf(normalizeEnum(rawValue));
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException(
                        "El orden de cobertura no es valido.");
            }
        }
    }

    private static String normalizeEnum(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            throw new IllegalArgumentException("El filtro es obligatorio.");
        }
        return rawValue.trim().toUpperCase(Locale.ROOT);
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
