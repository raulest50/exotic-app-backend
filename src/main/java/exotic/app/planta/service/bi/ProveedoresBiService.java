package exotic.app.planta.service.bi;

import exotic.app.planta.config.AppTime;
import exotic.app.planta.model.bi.dto.LeadTimeProveedorMaterialDTO;
import exotic.app.planta.model.bi.dto.LeadTimeProveedorMaterialPageRowDTO;
import exotic.app.planta.model.bi.dto.LeadTimeStatsDTO;
import exotic.app.planta.model.bi.dto.ProveedorMaterialOrdenHistRowDTO;
import exotic.app.planta.model.bi.dto.ProveedorMaterialRecepcionRowDTO;
import exotic.app.planta.model.bi.dto.PuntoReordenEstimadoDTO;
import exotic.app.planta.model.compras.Proveedor;
import exotic.app.planta.model.inventarios.Movimiento;
import exotic.app.planta.model.inventarios.TransaccionAlmacen;
import exotic.app.planta.model.producto.Material;
import exotic.app.planta.repo.compras.ItemOrdenCompraRepo;
import exotic.app.planta.repo.compras.ProveedorRepo;
import exotic.app.planta.repo.inventarios.TransaccionAlmacenRepo;
import exotic.app.planta.repo.producto.MaterialRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProveedoresBiService {

    private static final int DEFAULT_WINDOW_DAYS = 365;
    private static final int FULL_STATISTICAL_MIN_DAYS = 90;
    private static final int FULL_STATISTICAL_MIN_LT_OBS = 4;
    private static final double SERVICE_LEVEL_Z = 1.65d;
    private static final double EPSILON = 1e-9d;
    private static final double MAX_CV_FOR_VARIABILITY_SCORE = 1.50d;
    private static final Set<Movimiento.TipoMovimiento> DEMAND_MOVEMENT_TYPES = Set.of(
            Movimiento.TipoMovimiento.DISPENSACION,
            Movimiento.TipoMovimiento.CONSUMO
    );

    private final ProveedorRepo proveedorRepo;
    private final MaterialRepo materialRepo;
    private final ItemOrdenCompraRepo itemOrdenCompraRepo;
    private final TransaccionAlmacenRepo transaccionAlmacenRepo;

    public LeadTimeProveedorMaterialDTO calcularLeadTimeProveedorMaterial(
            String proveedorId,
            String materialId,
            LocalDate fechaCorte,
            int ventanaDias
    ) {
        Proveedor proveedor = proveedorRepo.findById(proveedorId)
                .orElseThrow(() -> new NoSuchElementException("Proveedor no encontrado: " + proveedorId));
        Material material = materialRepo.findById(materialId)
                .orElseThrow(() -> new NoSuchElementException("Material no encontrado: " + materialId));

        EffectiveWindow window = resolveWindow(fechaCorte, ventanaDias);
        HistoricalContext context = loadHistoricalContext(materialId, proveedorId, window);
        LeadTimePair leadTimePair = buildLeadTimePair(context.orders(), context.receiptsByOrder());

        return new LeadTimeProveedorMaterialDTO(
                proveedor.getId(),
                proveedor.getNombre(),
                material.getProductoId(),
                material.getNombre(),
                window.fechaCorte(),
                window.ventanaDias(),
                context.orders().size(),
                leadTimePair.firstReceipt(),
                leadTimePair.completeReceipt()
        );
    }

    public Page<LeadTimeProveedorMaterialPageRowDTO> listarLeadTimesPorMaterial(
            String materialId,
            LocalDate fechaCorte,
            int ventanaDias,
            int page,
            int size,
            String direction
    ) {
        Material material = materialRepo.findById(materialId)
                .orElseThrow(() -> new NoSuchElementException("Material no encontrado: " + materialId));

        String normalizedDirection = normalizeDirection(direction);
        EffectiveWindow window = resolveWindow(fechaCorte, ventanaDias);
        HistoricalContext context = loadHistoricalContext(materialId, null, window);

        Map<String, List<OrderAggregate>> ordersByProveedor = new LinkedHashMap<>();
        for (OrderAggregate order : context.orders()) {
            ordersByProveedor.computeIfAbsent(order.proveedorId(), ignored -> new ArrayList<>()).add(order);
        }

        List<LeadTimeProveedorMaterialPageRowDTO> rows = new ArrayList<>();
        for (Map.Entry<String, List<OrderAggregate>> entry : ordersByProveedor.entrySet()) {
            LeadTimePair pair = buildLeadTimePair(entry.getValue(), context.receiptsByOrder());
            int firstObs = pair.firstReceipt().getValidObservations() != null ? pair.firstReceipt().getValidObservations() : 0;
            int completeObs = pair.completeReceipt().getValidObservations() != null ? pair.completeReceipt().getValidObservations() : 0;
            if (firstObs == 0 && completeObs == 0) {
                continue;
            }

            OrderAggregate sample = entry.getValue().get(0);
            Double representativeFirst = pair.firstReceipt().getRepresentativeLeadTimeDays();
            Integer firstConfidence = pair.firstReceipt().getConfidenceScore();
            Double adjustedLeadTimeDays = representativeFirst == null || firstConfidence == null
                    ? null
                    : round2(representativeFirst * (1.0d + (1.0d - (firstConfidence / 100.0d)) * 0.50d));

            rows.add(new LeadTimeProveedorMaterialPageRowDTO(
                    sample.proveedorId(),
                    sample.proveedorNombre(),
                    material.getProductoId(),
                    material.getNombre(),
                    representativeFirst,
                    pair.completeReceipt().getRepresentativeLeadTimeDays(),
                    firstConfidence,
                    pair.completeReceipt().getConfidenceScore(),
                    firstObs,
                    completeObs,
                    entry.getValue().size(),
                    adjustedLeadTimeDays
            ));
        }

        Comparator<LeadTimeProveedorMaterialPageRowDTO> comparator = Comparator.comparing(
                LeadTimeProveedorMaterialPageRowDTO::getAdjustedLeadTimeDays,
                Comparator.nullsLast(Double::compareTo)
        ).thenComparing(
                LeadTimeProveedorMaterialPageRowDTO::getRepresentativeFirstReceiptLeadTimeDays,
                Comparator.nullsLast(Double::compareTo)
        ).thenComparing(
                LeadTimeProveedorMaterialPageRowDTO::getProveedorNombre,
                Comparator.nullsLast(String::compareToIgnoreCase)
        );

        if ("desc".equals(normalizedDirection)) {
            comparator = comparator.reversed();
        }

        rows.sort(comparator);

        int safePage = Math.max(page, 0);
        int safeSize = size > 0 ? size : 10;
        int startIndex = Math.min(safePage * safeSize, rows.size());
        int endIndex = Math.min(startIndex + safeSize, rows.size());
        List<LeadTimeProveedorMaterialPageRowDTO> slice = rows.subList(startIndex, endIndex);

        return new PageImpl<>(slice, PageRequest.of(safePage, safeSize), rows.size());
    }

    public PuntoReordenEstimadoDTO estimarPuntoReorden(
            String materialId,
            LocalDate fechaCorte,
            int ventanaDias
    ) {
        Material material = materialRepo.findById(materialId)
                .orElseThrow(() -> new NoSuchElementException("Material no encontrado: " + materialId));

        EffectiveWindow window = resolveWindow(fechaCorte, ventanaDias);
        if (!material.isInventareable()) {
            return buildNoDataRop(material, window, "El material no es inventareable.");
        }

        HistoricalContext leadTimeContext = loadHistoricalContext(materialId, null, window);
        LeadTimePair aggregateLeadTimes = buildLeadTimePair(leadTimeContext.orders(), leadTimeContext.receiptsByOrder());
        LeadTimeStatsDTO firstReceiptStats = aggregateLeadTimes.firstReceipt();

        if (firstReceiptStats.getRepresentativeLeadTimeDays() == null || firstReceiptStats.getValidObservations() == null
                || firstReceiptStats.getValidObservations() <= 0) {
            return buildNoDataRop(
                    material,
                    window,
                    "No existen observaciones suficientes de lead time para este material en la ventana consultada."
            );
        }

        List<Double> demandaDiaria = buildDailyDemandSeries(
                materialId,
                window.startDateTime(),
                window.endDateTime(),
                window.ventanaDias()
        );
        double totalDemand = demandaDiaria.stream().mapToDouble(Double::doubleValue).sum();
        if (totalDemand <= EPSILON) {
            return buildNoDataRop(
                    material,
                    window,
                    "No existe demanda historica consumible para este material en la ventana consultada."
            );
        }

        double avgDemand = mean(demandaDiaria);
        double stdDemand = standardDeviation(demandaDiaria, avgDemand);
        double representativeLeadTime = firstReceiptStats.getRepresentativeLeadTimeDays();
        double avgLeadTime = firstReceiptStats.getAverageLeadTimeDays() != null
                ? firstReceiptStats.getAverageLeadTimeDays()
                : representativeLeadTime;
        double stdLeadTime = firstReceiptStats.getStandardDeviationLeadTimeDays() != null
                ? firstReceiptStats.getStandardDeviationLeadTimeDays()
                : 0.0d;
        int leadTimeObs = firstReceiptStats.getValidObservations();

        String metodo;
        double puntoReorden;

        if (window.ventanaDias() >= FULL_STATISTICAL_MIN_DAYS
                && leadTimeObs >= FULL_STATISTICAL_MIN_LT_OBS
                && stdLeadTime > EPSILON) {
            metodo = "FULL_STATISTICAL";
            puntoReorden = avgDemand * representativeLeadTime
                    + SERVICE_LEVEL_Z * Math.sqrt(
                    representativeLeadTime * Math.pow(stdDemand, 2)
                            + Math.pow(avgDemand, 2) * Math.pow(stdLeadTime, 2)
            );
        } else if (window.ventanaDias() >= FULL_STATISTICAL_MIN_DAYS) {
            metodo = "DEMAND_ONLY_STATISTICAL";
            puntoReorden = avgDemand * representativeLeadTime
                    + SERVICE_LEVEL_Z * stdDemand * Math.sqrt(representativeLeadTime);
        } else {
            metodo = "DETERMINISTIC";
            puntoReorden = avgDemand * representativeLeadTime;
        }

        int nonZeroDemandDays = (int) demandaDiaria.stream().filter(v -> v > EPSILON).count();
        int proveedoresObservados = (int) leadTimeContext.orders().stream()
                .map(OrderAggregate::proveedorId)
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .count();

        return new PuntoReordenEstimadoDTO(
                material.getProductoId(),
                material.getNombre(),
                window.fechaCorte(),
                window.ventanaDias(),
                metodo,
                null,
                round2(Math.max(puntoReorden, 0.0d)),
                round4(avgDemand),
                round4(stdDemand),
                round4(representativeLeadTime),
                round4(avgLeadTime),
                round4(stdLeadTime),
                round4(totalDemand),
                window.ventanaDias(),
                leadTimeObs,
                proveedoresObservados,
                computeRopConfidence(firstReceiptStats.getConfidenceScore(), window.ventanaDias(), nonZeroDemandDays)
        );
    }

    private PuntoReordenEstimadoDTO buildNoDataRop(Material material, EffectiveWindow window, String reason) {
        return new PuntoReordenEstimadoDTO(
                material.getProductoId(),
                material.getNombre(),
                window.fechaCorte(),
                window.ventanaDias(),
                "NO_DATA",
                reason,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                window.ventanaDias(),
                0,
                0,
                0
        );
    }

    private HistoricalContext loadHistoricalContext(String materialId, String proveedorId, EffectiveWindow window) {
        List<ProveedorMaterialOrdenHistRowDTO> orderRows = itemOrdenCompraRepo.findLeadTimeOrderHistory(
                materialId,
                proveedorId,
                window.startDateTime(),
                window.endDateTime()
        );
        List<ProveedorMaterialRecepcionRowDTO> receiptRows = transaccionAlmacenRepo.findLeadTimeReceiptHistory(
                materialId,
                proveedorId,
                window.startDateTime(),
                window.endDateTime(),
                TransaccionAlmacen.TipoEntidadCausante.OCM,
                Movimiento.TipoMovimiento.COMPRA
        );

        Map<Integer, OrderAggregate> orders = new LinkedHashMap<>();
        for (ProveedorMaterialOrdenHistRowDTO row : orderRows) {
            orders.compute(row.getOrdenCompraId(), (ignored, existing) -> {
                if (existing == null) {
                    return new OrderAggregate(
                            row.getOrdenCompraId(),
                            row.getProveedorId(),
                            row.getProveedorNombre(),
                            row.getMaterialId(),
                            row.getMaterialNombre(),
                            row.getFechaEmision(),
                            safeInt(row.getCantidadOrdenada())
                    );
                }
                return existing.withAdditionalOrderedQuantity(safeInt(row.getCantidadOrdenada()));
            });
        }

        Map<Integer, ReceiptAggregate> receipts = new HashMap<>();
        for (ProveedorMaterialRecepcionRowDTO row : receiptRows) {
            if (row.getFechaMovimiento() == null) {
                continue;
            }
            receipts.compute(row.getOrdenCompraId(), (ignored, existing) -> {
                if (existing == null) {
                    return new ReceiptAggregate(
                            row.getOrdenCompraId(),
                            safeDouble(row.getCantidadRecibida()),
                            row.getFechaMovimiento(),
                            row.getFechaMovimiento()
                    );
                }
                return existing.merge(safeDouble(row.getCantidadRecibida()), row.getFechaMovimiento());
            });
        }

        return new HistoricalContext(new ArrayList<>(orders.values()), receipts);
    }

    private LeadTimePair buildLeadTimePair(List<OrderAggregate> orders, Map<Integer, ReceiptAggregate> receiptsByOrder) {
        if (orders.isEmpty()) {
            LeadTimeStatsDTO empty = emptyStats("No se registran ordenes del material en la ventana consultada.", 0);
            return new LeadTimePair(empty, empty);
        }

        List<Double> firstReceiptLeadTimes = new ArrayList<>();
        List<Double> completeReceiptLeadTimes = new ArrayList<>();
        List<LocalDateTime> firstReceiptObservedAt = new ArrayList<>();
        List<LocalDateTime> completeReceiptObservedAt = new ArrayList<>();

        int totalOrders = orders.size();
        boolean hasAnyReceipt = false;
        boolean hasAnyCompleteReceipt = false;

        for (OrderAggregate order : orders) {
            if (order.fechaEmision() == null) {
                continue;
            }

            ReceiptAggregate receipt = receiptsByOrder.get(order.ordenCompraId());
            if (receipt == null || receipt.firstReceiptAt() == null) {
                continue;
            }

            double firstReceiptLeadTime = daysBetween(order.fechaEmision(), receipt.firstReceiptAt());
            if (firstReceiptLeadTime >= 0.0d) {
                firstReceiptLeadTimes.add(firstReceiptLeadTime);
                firstReceiptObservedAt.add(receipt.firstReceiptAt());
                hasAnyReceipt = true;
            }

            if (receipt.totalCantidadRecibida() + EPSILON >= order.cantidadOrdenada() && receipt.lastReceiptAt() != null) {
                double completeReceiptLeadTime = daysBetween(order.fechaEmision(), receipt.lastReceiptAt());
                if (completeReceiptLeadTime >= 0.0d) {
                    completeReceiptLeadTimes.add(completeReceiptLeadTime);
                    completeReceiptObservedAt.add(receipt.lastReceiptAt());
                    hasAnyCompleteReceipt = true;
                }
            }
        }

        LeadTimeStatsDTO firstReceiptStats = buildStats(
                firstReceiptLeadTimes,
                totalOrders,
                hasAnyReceipt ? null : "No se registran movimientos COMPRA del material relacionados con la consulta.",
                firstReceiptObservedAt
        );
        LeadTimeStatsDTO completeReceiptStats = buildStats(
                completeReceiptLeadTimes,
                totalOrders,
                hasAnyCompleteReceipt
                        ? null
                        : "No existe ninguna orden con recepcion completa del material en la ventana consultada.",
                completeReceiptObservedAt
        );

        return new LeadTimePair(firstReceiptStats, completeReceiptStats);
    }

    private LeadTimeStatsDTO buildStats(
            List<Double> observations,
            int totalOrders,
            String emptyReason,
            List<LocalDateTime> receiptObservedAt
    ) {
        if (observations.isEmpty()) {
            return emptyStats(emptyReason, totalOrders);
        }

        List<Double> sorted = new ArrayList<>(observations);
        sorted.sort(Double::compareTo);

        double average = mean(sorted);
        double median = median(sorted);
        double min = sorted.get(0);
        double max = sorted.get(sorted.size() - 1);
        double std = standardDeviation(sorted, average);
        int confidence = computeConfidence(sorted, totalOrders, average, std);
        LocalDateTime lastObserved = receiptObservedAt.stream()
                .max(LocalDateTime::compareTo)
                .orElse(null);

        return new LeadTimeStatsDTO(
                true,
                null,
                round4(sorted.size() >= 3 ? median : average),
                round4(average),
                round4(median),
                round4(min),
                round4(max),
                round4(std),
                sorted.size(),
                totalOrders,
                confidence,
                lastObserved
        );
    }

    private LeadTimeStatsDTO emptyStats(String reason, int totalOrders) {
        return new LeadTimeStatsDTO(
                false,
                reason,
                null,
                null,
                null,
                null,
                null,
                null,
                0,
                totalOrders,
                0,
                null
        );
    }

    private List<Double> buildDailyDemandSeries(
            String materialId,
            LocalDateTime start,
            LocalDateTime end,
            int windowDays
    ) {
        List<Movimiento> movimientos = transaccionAlmacenRepo
                .findByProducto_ProductoIdAndFechaMovimientoBetweenOrderByFechaMovimientoAscMovimientoIdAsc(
                        materialId,
                        start,
                        end
                );

        Map<LocalDate, Double> demandByDate = new HashMap<>();
        for (Movimiento movimiento : movimientos) {
            if (movimiento.getTipoMovimiento() == null || !DEMAND_MOVEMENT_TYPES.contains(movimiento.getTipoMovimiento())) {
                continue;
            }
            if (movimiento.getFechaMovimiento() == null) {
                continue;
            }
            LocalDate date = movimiento.getFechaMovimiento().toLocalDate();
            demandByDate.merge(date, Math.abs(movimiento.getCantidad()), Double::sum);
        }

        List<Double> dailyDemand = new ArrayList<>(windowDays);
        LocalDate current = start.toLocalDate();
        for (int i = 0; i < windowDays; i++) {
            dailyDemand.add(round4(demandByDate.getOrDefault(current, 0.0d)));
            current = current.plusDays(1);
        }
        return dailyDemand;
    }

    private EffectiveWindow resolveWindow(LocalDate fechaCorte, int ventanaDias) {
        LocalDate effectiveFechaCorte = fechaCorte != null ? fechaCorte : AppTime.today();
        int effectiveWindowDays = ventanaDias > 0 ? ventanaDias : DEFAULT_WINDOW_DAYS;
        LocalDate startDate = effectiveFechaCorte.minusDays(effectiveWindowDays - 1L);
        return new EffectiveWindow(
                effectiveFechaCorte,
                effectiveWindowDays,
                startDate.atStartOfDay(),
                effectiveFechaCorte.atTime(LocalTime.MAX)
        );
    }

    private String normalizeDirection(String direction) {
        String normalized = direction == null ? "asc" : direction.trim().toLowerCase(Locale.ROOT);
        if (!"asc".equals(normalized) && !"desc".equals(normalized)) {
            throw new IllegalArgumentException("direction debe ser 'asc' o 'desc'.");
        }
        return normalized;
    }

    private int computeConfidence(List<Double> observations, int totalOrders, double mean, double std) {
        double coverageScore = totalOrders <= 0 ? 0.0d : clamp01(observations.size() / (double) totalOrders);
        double sampleScore = clamp01(observations.size() / 6.0d);
        double variabilityScore;
        if (observations.size() < 2 || mean <= EPSILON) {
            variabilityScore = 0.0d;
        } else {
            double cv = std / mean;
            variabilityScore = clamp01(1.0d - Math.min(cv, MAX_CV_FOR_VARIABILITY_SCORE) / MAX_CV_FOR_VARIABILITY_SCORE);
        }
        return (int) Math.round(100.0d * (0.40d * coverageScore + 0.35d * sampleScore + 0.25d * variabilityScore));
    }

    private int computeRopConfidence(Integer leadTimeConfidence, int totalDays, int nonZeroDemandDays) {
        double leadTimeScore = leadTimeConfidence != null ? clamp01(leadTimeConfidence / 100.0d) : 0.0d;
        double windowScore = clamp01(totalDays / 90.0d);
        double demandSpreadScore = clamp01(nonZeroDemandDays / 30.0d);
        return (int) Math.round(100.0d * (0.45d * windowScore + 0.20d * demandSpreadScore + 0.35d * leadTimeScore));
    }

    private double daysBetween(LocalDateTime start, LocalDateTime end) {
        return Duration.between(start, end).toMinutes() / 1440.0d;
    }

    private double mean(Collection<Double> values) {
        if (values.isEmpty()) {
            return 0.0d;
        }
        return values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0d);
    }

    private double median(List<Double> sorted) {
        int size = sorted.size();
        if (size == 0) {
            return 0.0d;
        }
        if (size % 2 == 1) {
            return sorted.get(size / 2);
        }
        return (sorted.get((size / 2) - 1) + sorted.get(size / 2)) / 2.0d;
    }

    private double standardDeviation(List<Double> values, double mean) {
        if (values.isEmpty()) {
            return 0.0d;
        }
        double variance = values.stream()
                .mapToDouble(value -> Math.pow(value - mean, 2))
                .average()
                .orElse(0.0d);
        return Math.sqrt(variance);
    }

    private static double clamp01(double value) {
        return Math.max(0.0d, Math.min(1.0d, value));
    }

    private static int safeInt(Integer value) {
        return value != null ? value : 0;
    }

    private static double safeDouble(Double value) {
        return value != null ? value : 0.0d;
    }

    private static Double round2(Double value) {
        if (value == null) {
            return null;
        }
        return Math.round(value * 100.0d) / 100.0d;
    }

    private static Double round4(Double value) {
        if (value == null) {
            return null;
        }
        return Math.round(value * 10000.0d) / 10000.0d;
    }

    private record EffectiveWindow(
            LocalDate fechaCorte,
            int ventanaDias,
            LocalDateTime startDateTime,
            LocalDateTime endDateTime
    ) {
    }

    private record HistoricalContext(
            List<OrderAggregate> orders,
            Map<Integer, ReceiptAggregate> receiptsByOrder
    ) {
    }

    private record OrderAggregate(
            Integer ordenCompraId,
            String proveedorId,
            String proveedorNombre,
            String materialId,
            String materialNombre,
            LocalDateTime fechaEmision,
            int cantidadOrdenada
    ) {
        private OrderAggregate withAdditionalOrderedQuantity(int extraQty) {
            return new OrderAggregate(
                    ordenCompraId,
                    proveedorId,
                    proveedorNombre,
                    materialId,
                    materialNombre,
                    fechaEmision,
                    cantidadOrdenada + extraQty
            );
        }
    }

    private record ReceiptAggregate(
            Integer ordenCompraId,
            double totalCantidadRecibida,
            LocalDateTime firstReceiptAt,
            LocalDateTime lastReceiptAt
    ) {
        private ReceiptAggregate merge(double extraQty, LocalDateTime receiptAt) {
            LocalDateTime first = firstReceiptAt == null || receiptAt.isBefore(firstReceiptAt) ? receiptAt : firstReceiptAt;
            LocalDateTime last = lastReceiptAt == null || receiptAt.isAfter(lastReceiptAt) ? receiptAt : lastReceiptAt;
            return new ReceiptAggregate(
                    ordenCompraId,
                    totalCantidadRecibida + extraQty,
                    first,
                    last
            );
        }
    }

    private record LeadTimePair(
            LeadTimeStatsDTO firstReceipt,
            LeadTimeStatsDTO completeReceipt
    ) {
    }
}
