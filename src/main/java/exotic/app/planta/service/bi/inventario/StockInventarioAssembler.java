package exotic.app.planta.service.bi.inventario;

import exotic.app.planta.model.bi.dto.InformeInventarioDTO;
import exotic.app.planta.model.producto.Material;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
class StockInventarioAssembler {
    private static final double STOCK_EPSILON = 1e-6;
    private static final int MAX_ALERTS = 10;
    private static final List<String> INVENTORY_TYPES = List.of(
            "MATERIA_PRIMA",
            "EMPAQUE",
            "TERMINADO",
            "OTROS"
    );
    private static final List<String> ABC_CLASSES = List.of("A", "B", "C");

    private final AlertasInventarioClassifier alertClassifier;

    InformeInventarioDTO.StockDTO assemble(List<ProductoStockSnapshot> snapshots) {
        double totalValue = snapshots.stream()
                .mapToDouble(InventarioBiUtils::estimatedValue)
                .sum();
        long positiveStockReferences = snapshots.stream()
                .filter(snapshot -> snapshot.stockGeneral() > STOCK_EPSILON)
                .count();
        long valuedReferences = snapshots.stream()
                .filter(this::isValuedReference)
                .count();
        long negativeStockReferences = snapshots.stream()
                .filter(snapshot -> snapshot.stockGeneral() < -STOCK_EPSILON)
                .count();

        return InformeInventarioDTO.StockDTO.builder()
                .resumen(buildSummary(
                        snapshots,
                        totalValue,
                        positiveStockReferences,
                        valuedReferences,
                        negativeStockReferences))
                .porUnidad(aggregateStockByUnit(snapshots))
                .materialesPorTipo(buildMaterialStockByType(snapshots))
                .composicion(buildComposition(snapshots, totalValue))
                .abc(buildAbc(snapshots, totalValue, positiveStockReferences, valuedReferences))
                .alertas(buildAlerts(snapshots))
                .build();
    }

    private InformeInventarioDTO.ResumenStockDTO buildSummary(
            List<ProductoStockSnapshot> snapshots,
            double totalValue,
            long positiveStockReferences,
            long valuedReferences,
            long negativeStockReferences
    ) {
        Double costCoverage = positiveStockReferences == 0
                ? null
                : InventarioBiUtils.percentage(valuedReferences, positiveStockReferences);

        return InformeInventarioDTO.ResumenStockDTO.builder()
                .valorEstimado(totalValue)
                .referenciasConStock(Math.toIntExact(positiveStockReferences))
                .referenciasValorizadas(Math.toIntExact(valuedReferences))
                .coberturaCostosPct(costCoverage)
                .valorizacion(buildValuation(snapshots))
                .coberturaCostosDetalle(buildCostCoverageDetail(
                        snapshots,
                        positiveStockReferences,
                        valuedReferences))
                .referenciasNegativas(Math.toIntExact(negativeStockReferences))
                .build();
    }

    private InformeInventarioDTO.ValorizacionInventarioDTO buildValuation(
            List<ProductoStockSnapshot> snapshots
    ) {
        Map<String, Double> valuesByType = new LinkedHashMap<>();
        INVENTORY_TYPES.forEach(type -> valuesByType.put(type, 0d));

        for (ProductoStockSnapshot snapshot : snapshots) {
            double value = InventarioBiUtils.estimatedValue(snapshot);
            if (value <= 0) continue;
            String type = InventarioBiUtils.inventoryTypeOf(snapshot.producto());
            valuesByType.computeIfPresent(type, (ignored, current) -> current + value);
        }

        double rawMaterialValue = valuesByType.get("MATERIA_PRIMA");
        double packagingValue = valuesByType.get("EMPAQUE");

        return InformeInventarioDTO.ValorizacionInventarioDTO.builder()
                .materiales(InformeInventarioDTO.ValorizacionMaterialesDTO.builder()
                        .total(rawMaterialValue + packagingValue)
                        .materiaPrima(rawMaterialValue)
                        .empaque(packagingValue)
                        .build())
                .terminados(valuesByType.get("TERMINADO"))
                .build();
    }

    private InformeInventarioDTO.CoberturaCostosDetalleDTO buildCostCoverageDetail(
            List<ProductoStockSnapshot> snapshots,
            long positiveStockReferences,
            long valuedReferences
    ) {
        long positiveMaterialReferences = countPositiveReferences(
                snapshots,
                List.of("MATERIA_PRIMA", "EMPAQUE"));
        long valuedMaterialReferences = countValuedReferences(
                snapshots,
                List.of("MATERIA_PRIMA", "EMPAQUE"));
        long positiveFinishedReferences = countPositiveReferences(
                snapshots,
                List.of("TERMINADO"));
        long valuedFinishedReferences = countValuedReferences(
                snapshots,
                List.of("TERMINADO"));

        return InformeInventarioDTO.CoberturaCostosDetalleDTO.builder()
                .globalPct(coveragePercentage(valuedReferences, positiveStockReferences))
                .materialesPct(coveragePercentage(
                        valuedMaterialReferences,
                        positiveMaterialReferences))
                .terminadosPct(coveragePercentage(
                        valuedFinishedReferences,
                        positiveFinishedReferences))
                .build();
    }

    private long countPositiveReferences(
            List<ProductoStockSnapshot> snapshots,
            List<String> includedTypes
    ) {
        return snapshots.stream()
                .filter(snapshot -> snapshot.stockGeneral() > STOCK_EPSILON)
                .filter(snapshot -> includedTypes.contains(
                        InventarioBiUtils.inventoryTypeOf(snapshot.producto())))
                .count();
    }

    private long countValuedReferences(
            List<ProductoStockSnapshot> snapshots,
            List<String> includedTypes
    ) {
        return snapshots.stream()
                .filter(this::isValuedReference)
                .filter(snapshot -> includedTypes.contains(
                        InventarioBiUtils.inventoryTypeOf(snapshot.producto())))
                .count();
    }

    private Double coveragePercentage(long valuedReferences, long positiveStockReferences) {
        return positiveStockReferences == 0
                ? null
                : InventarioBiUtils.percentage(valuedReferences, positiveStockReferences);
    }

    private InformeInventarioDTO.MaterialesPorTipoDTO buildMaterialStockByType(
            List<ProductoStockSnapshot> snapshots
    ) {
        List<ProductoStockSnapshot> rawMaterials = snapshots.stream()
                .filter(snapshot -> snapshot.producto() instanceof Material material
                        && material.getTipoMaterial() == 1)
                .toList();
        List<ProductoStockSnapshot> packagingMaterials = snapshots.stream()
                .filter(snapshot -> snapshot.producto() instanceof Material material
                        && material.getTipoMaterial() == 2)
                .toList();

        return InformeInventarioDTO.MaterialesPorTipoDTO.builder()
                .materiaPrima(aggregateStockByUnit(rawMaterials))
                .empaque(aggregateStockByUnit(packagingMaterials))
                .build();
    }

    private List<InformeInventarioDTO.StockUnidadDTO> aggregateStockByUnit(
            List<ProductoStockSnapshot> snapshots
    ) {
        Map<String, UnitStockAccumulator> byUnit = new LinkedHashMap<>();
        for (ProductoStockSnapshot snapshot : snapshots) {
            String unit = InventarioBiUtils.unitOf(snapshot.producto());
            byUnit.computeIfAbsent(unit, UnitStockAccumulator::new).add(snapshot.stockGeneral());
        }
        return byUnit.values().stream()
                .filter(UnitStockAccumulator::hasData)
                .sorted(Comparator.comparing(UnitStockAccumulator::unit))
                .map(UnitStockAccumulator::toDto)
                .toList();
    }

    private List<InformeInventarioDTO.ComposicionDTO> buildComposition(
            List<ProductoStockSnapshot> snapshots,
            double totalValue
    ) {
        Map<String, ValueAccumulator> byType = initializedValueMap(INVENTORY_TYPES);
        for (ProductoStockSnapshot snapshot : snapshots) {
            double value = InventarioBiUtils.estimatedValue(snapshot);
            if (value <= 0) continue;
            byType.get(InventarioBiUtils.inventoryTypeOf(snapshot.producto())).add(value);
        }

        return byType.entrySet().stream()
                .filter(entry -> entry.getValue().references() > 0)
                .map(entry -> InformeInventarioDTO.ComposicionDTO.builder()
                        .tipo(entry.getKey())
                        .referencias(entry.getValue().references())
                        .valorEstimado(entry.getValue().value())
                        .participacionPct(InventarioBiUtils.percentage(
                                entry.getValue().value(),
                                totalValue))
                        .build())
                .toList();
    }

    private InformeInventarioDTO.AbcDTO buildAbc(
            List<ProductoStockSnapshot> snapshots,
            double totalValue,
            long positiveStockReferences,
            long valuedReferences
    ) {
        List<ProductoStockSnapshot> valuedSnapshots = snapshots.stream()
                .filter(this::isValuedReference)
                .sorted(Comparator
                        .comparingDouble(InventarioBiUtils::estimatedValue)
                        .reversed()
                        .thenComparing(
                                snapshot -> valueOrEmpty(snapshot.producto().getNombre()),
                                String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(
                                snapshot -> valueOrEmpty(snapshot.producto().getProductoId()),
                                String.CASE_INSENSITIVE_ORDER))
                .toList();

        Map<String, ValueAccumulator> byClass = initializedValueMap(ABC_CLASSES);
        double accumulatedValue = 0;
        for (ProductoStockSnapshot snapshot : valuedSnapshots) {
            String abcClass = abcClassBeforeAdding(accumulatedValue, totalValue);
            double referenceValue = InventarioBiUtils.estimatedValue(snapshot);
            byClass.get(abcClass).add(referenceValue);
            accumulatedValue += referenceValue;
        }

        List<InformeInventarioDTO.ClaseAbcDTO> classes = byClass.entrySet().stream()
                .map(entry -> InformeInventarioDTO.ClaseAbcDTO.builder()
                        .clase(entry.getKey())
                        .referencias(entry.getValue().references())
                        .valorEstimado(entry.getValue().value())
                        .participacionPct(InventarioBiUtils.percentage(
                                entry.getValue().value(),
                                totalValue))
                        .build())
                .toList();

        return InformeInventarioDTO.AbcDTO.builder()
                .clases(classes)
                .referenciasExcluidasSinCosto(Math.toIntExact(
                        positiveStockReferences - valuedReferences))
                .build();
    }

    private String abcClassBeforeAdding(double accumulatedValue, double totalValue) {
        if (accumulatedValue < totalValue * 0.80) return "A";
        if (accumulatedValue < totalValue * 0.95) return "B";
        return "C";
    }

    private InformeInventarioDTO.AlertasDTO buildAlerts(
            List<ProductoStockSnapshot> snapshots
    ) {
        return alertClassifier.summarize(
                alertClassifier.classify(snapshots),
                MAX_ALERTS);
    }

    private boolean isValuedReference(ProductoStockSnapshot snapshot) {
        return snapshot.stockGeneral() > STOCK_EPSILON
                && InventarioBiUtils.hasValidCost(snapshot.producto());
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private Map<String, ValueAccumulator> initializedValueMap(List<String> keys) {
        Map<String, ValueAccumulator> values = new LinkedHashMap<>();
        keys.forEach(key -> values.put(key, new ValueAccumulator()));
        return values;
    }

    private static final class UnitStockAccumulator {
        private final String unit;
        private double netQuantity;
        private double positiveQuantity;
        private double negativeQuantity;
        private int positiveReferences;

        private UnitStockAccumulator(String unit) {
            this.unit = unit;
        }

        void add(double quantity) {
            netQuantity += quantity;
            if (quantity > STOCK_EPSILON) {
                positiveQuantity += quantity;
                positiveReferences++;
            } else if (quantity < -STOCK_EPSILON) {
                negativeQuantity += quantity;
            }
        }

        String unit() {
            return unit;
        }

        boolean hasData() {
            return Math.abs(netQuantity) > STOCK_EPSILON
                    || positiveQuantity > STOCK_EPSILON
                    || negativeQuantity < -STOCK_EPSILON;
        }

        InformeInventarioDTO.StockUnidadDTO toDto() {
            return InformeInventarioDTO.StockUnidadDTO.builder()
                    .unidadMedida(unit)
                    .cantidadNeta(netQuantity)
                    .cantidadPositiva(positiveQuantity)
                    .cantidadNegativa(negativeQuantity)
                    .referenciasConStock(positiveReferences)
                    .build();
        }
    }

    private static final class ValueAccumulator {
        private int references;
        private double value;

        void add(double referenceValue) {
            references++;
            value += referenceValue;
        }

        int references() {
            return references;
        }

        double value() {
            return value;
        }
    }

}
