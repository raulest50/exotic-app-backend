package exotic.app.planta.service.bi.inventario;

import exotic.app.planta.model.bi.dto.InformeInventarioDTO;
import exotic.app.planta.model.inventarios.Movimiento;
import exotic.app.planta.model.inventarios.TransaccionAlmacen;
import exotic.app.planta.model.producto.Material;
import exotic.app.planta.model.producto.Producto;
import exotic.app.planta.model.producto.Terminado;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

@Component
class MovimientosInventarioAssembler {

    InformeInventarioDTO.MovimientosDTO assemble(
            List<Movimiento> periodMovements,
            List<Movimiento> trendMovements,
            LocalDate trendStartDate,
            LocalDate trendEndDate
    ) {
        Map<FlowType, FlowSummaryAccumulator> summaryByFlow = emptyFlowSummary();
        Map<String, UnitFlowAccumulator> flowsByUnit = new LinkedHashMap<>();

        for (Movimiento movement : periodMovements) {
            FlowType flowType = classify(movement);
            if (flowType == null) continue;

            double quantity = Math.abs(movement.getCantidad());
            double estimatedValue = estimatedValue(movement.getProducto(), quantity);
            summaryByFlow.get(flowType).add(
                    movement.getProducto().getProductoId(),
                    estimatedValue);
            String unit = InventarioBiUtils.unitOf(movement.getProducto());
            flowsByUnit.computeIfAbsent(unit, UnitFlowAccumulator::new)
                    .add(flowType, quantity);
        }

        return InformeInventarioDTO.MovimientosDTO.builder()
                .resumen(toSummary(summaryByFlow))
                .porUnidad(flowsByUnit.values().stream()
                        .sorted(Comparator.comparing(UnitFlowAccumulator::unit))
                        .map(UnitFlowAccumulator::toDto)
                        .toList())
                .serieDiaria(buildDailySeries(
                        trendMovements,
                        trendStartDate,
                        trendEndDate))
                .build();
    }

    private List<InformeInventarioDTO.SerieMovimientoDTO> buildDailySeries(
            List<Movimiento> movements,
            LocalDate startDate,
            LocalDate endDate
    ) {
        Map<SeriesKey, DailySeriesAccumulator> series = new LinkedHashMap<>();
        Set<String> units = movements.stream()
                .filter(movement -> classify(movement) != null)
                .map(Movimiento::getProducto)
                .map(InventarioBiUtils::unitOf)
                .collect(Collectors.toCollection(TreeSet::new));

        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            for (String unit : units) {
                SeriesKey key = new SeriesKey(date, unit);
                series.put(key, new DailySeriesAccumulator(date, unit));
            }
        }

        for (Movimiento movement : movements) {
            FlowType flowType = classify(movement);
            if (flowType == null || movement.getFechaMovimiento() == null) continue;

            LocalDate date = movement.getFechaMovimiento().toLocalDate();
            String unit = InventarioBiUtils.unitOf(movement.getProducto());
            double quantity = Math.abs(movement.getCantidad());
            double estimatedValue = estimatedValue(movement.getProducto(), quantity);
            SeriesKey key = new SeriesKey(date, unit);
            series.computeIfAbsent(key, ignored -> new DailySeriesAccumulator(date, unit))
                    .add(flowType, quantity, estimatedValue);
        }

        return series.values().stream()
                .sorted(Comparator
                        .comparing(DailySeriesAccumulator::date)
                        .thenComparing(DailySeriesAccumulator::unit))
                .map(DailySeriesAccumulator::toDto)
                .toList();
    }

    private FlowType classify(Movimiento movement) {
        TransaccionAlmacen transaction = movement.getTransaccionAlmacen();
        Producto product = movement.getProducto();

        if (isOcmReceipt(movement, transaction, product)) {
            return FlowType.OCM_RECEIPT;
        }
        if (isMaterialDispensation(movement, product)) {
            return FlowType.DISPENSATION;
        }
        if (isFinishedProductReceipt(movement, product)) {
            return FlowType.FINISHED_PRODUCT;
        }
        if (isOtherIncome(movement, transaction)) {
            return FlowType.OTHER_INCOME;
        }
        return null;
    }

    private boolean isOcmReceipt(
            Movimiento movement,
            TransaccionAlmacen transaction,
            Producto product
    ) {
        return product instanceof Material
                && movement.getCantidad() > 0
                && movement.getTipoMovimiento() == Movimiento.TipoMovimiento.COMPRA
                && transaction != null
                && transaction.getTipoEntidadCausante()
                == TransaccionAlmacen.TipoEntidadCausante.OCM;
    }

    private boolean isMaterialDispensation(Movimiento movement, Producto product) {
        return product instanceof Material
                && movement.getCantidad() < 0
                && movement.getTipoMovimiento() == Movimiento.TipoMovimiento.DISPENSACION;
    }

    private boolean isFinishedProductReceipt(Movimiento movement, Producto product) {
        return product instanceof Terminado
                && movement.getCantidad() > 0
                && movement.getTipoMovimiento() == Movimiento.TipoMovimiento.BACKFLUSH;
    }

    private boolean isOtherIncome(
            Movimiento movement,
            TransaccionAlmacen transaction
    ) {
        if (movement.getCantidad() <= 0) return false;

        if (movement.getTipoMovimiento() == Movimiento.TipoMovimiento.AJUSTE_POSITIVO
                || movement.getTipoMovimiento() == Movimiento.TipoMovimiento.TRANSFERENCIA) {
            return true;
        }
        return movement.getTipoMovimiento() == Movimiento.TipoMovimiento.COMPRA
                && (transaction == null
                || transaction.getTipoEntidadCausante()
                != TransaccionAlmacen.TipoEntidadCausante.OCM);
    }

    private double estimatedValue(Producto product, double quantity) {
        return InventarioBiUtils.hasValidCost(product)
                ? quantity * InventarioBiUtils.costAsDouble(product)
                : 0;
    }

    private Map<FlowType, FlowSummaryAccumulator> emptyFlowSummary() {
        Map<FlowType, FlowSummaryAccumulator> summary = new EnumMap<>(FlowType.class);
        for (FlowType type : FlowType.values()) {
            summary.put(type, new FlowSummaryAccumulator());
        }
        return summary;
    }

    private InformeInventarioDTO.ResumenMovimientosDTO toSummary(
            Map<FlowType, FlowSummaryAccumulator> summary
    ) {
        return InformeInventarioDTO.ResumenMovimientosDTO.builder()
                .recepcionesOcm(summary.get(FlowType.OCM_RECEIPT).toDto())
                .dispensaciones(summary.get(FlowType.DISPENSATION).toDto())
                .productoTerminado(summary.get(FlowType.FINISHED_PRODUCT).toDto())
                .otrosIngresos(summary.get(FlowType.OTHER_INCOME).toDto())
                .build();
    }

    private enum FlowType {
        OCM_RECEIPT,
        DISPENSATION,
        FINISHED_PRODUCT,
        OTHER_INCOME
    }

    private record SeriesKey(LocalDate date, String unit) {
    }

    private static final class FlowSummaryAccumulator {
        private int movements;
        private final Set<String> productIds = new HashSet<>();
        private double estimatedValue;

        void add(String productId, double value) {
            movements++;
            productIds.add(productId);
            estimatedValue += value;
        }

        InformeInventarioDTO.FlujoDTO toDto() {
            return InformeInventarioDTO.FlujoDTO.builder()
                    .movimientos(movements)
                    .referencias(productIds.size())
                    .valorEstimado(estimatedValue)
                    .build();
        }
    }

    private static final class UnitFlowAccumulator {
        private final String unit;
        private double ocmReceipts;
        private double dispensations;
        private double finishedProducts;
        private double otherIncome;

        private UnitFlowAccumulator(String unit) {
            this.unit = unit;
        }

        void add(FlowType type, double quantity) {
            switch (type) {
                case OCM_RECEIPT -> ocmReceipts += quantity;
                case DISPENSATION -> dispensations += quantity;
                case FINISHED_PRODUCT -> finishedProducts += quantity;
                case OTHER_INCOME -> otherIncome += quantity;
            }
        }

        String unit() {
            return unit;
        }

        InformeInventarioDTO.FlujoUnidadDTO toDto() {
            return InformeInventarioDTO.FlujoUnidadDTO.builder()
                    .unidadMedida(unit)
                    .recepcionesOcm(ocmReceipts)
                    .dispensaciones(dispensations)
                    .productoTerminado(finishedProducts)
                    .otrosIngresos(otherIncome)
                    .build();
        }
    }

    private static final class DailySeriesAccumulator {
        private final LocalDate date;
        private final String unit;
        private final UnitFlowAccumulator quantities;
        private final UnitFlowAccumulator values;

        private DailySeriesAccumulator(LocalDate date, String unit) {
            this.date = date;
            this.unit = unit;
            this.quantities = new UnitFlowAccumulator(unit);
            this.values = new UnitFlowAccumulator("COP");
        }

        void add(FlowType type, double quantity, double value) {
            quantities.add(type, quantity);
            values.add(type, value);
        }

        LocalDate date() {
            return date;
        }

        String unit() {
            return unit;
        }

        InformeInventarioDTO.SerieMovimientoDTO toDto() {
            return InformeInventarioDTO.SerieMovimientoDTO.builder()
                    .fecha(date)
                    .unidadMedida(unit)
                    .recepcionesOcm(quantities.ocmReceipts)
                    .dispensaciones(quantities.dispensations)
                    .productoTerminado(quantities.finishedProducts)
                    .otrosIngresos(quantities.otherIncome)
                    .valorRecepcionesOcm(values.ocmReceipts)
                    .valorDispensaciones(values.dispensations)
                    .valorProductoTerminado(values.finishedProducts)
                    .valorOtrosIngresos(values.otherIncome)
                    .build();
        }
    }
}
