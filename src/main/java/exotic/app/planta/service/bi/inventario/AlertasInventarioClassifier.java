package exotic.app.planta.service.bi.inventario;

import exotic.app.planta.model.bi.dto.InformeInventarioDTO;
import exotic.app.planta.model.producto.Material;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Component
class AlertasInventarioClassifier {
    private static final double STOCK_EPSILON = 1e-6;

    List<InformeInventarioDTO.AlertaStockDTO> classify(
            List<ProductoStockSnapshot> snapshots
    ) {
        return snapshots.stream()
                .filter(snapshot -> snapshot.producto() instanceof Material)
                .map(this::classifyMaterial)
                .filter(Objects::nonNull)
                .sorted(priorityComparator())
                .toList();
    }

    InformeInventarioDTO.AlertasDTO summarize(
            List<InformeInventarioDTO.AlertaStockDTO> alerts,
            int itemLimit
    ) {
        return InformeInventarioDTO.AlertasDTO.builder()
                .total(alerts.size())
                .negativas(countType(alerts, "STOCK_NEGATIVO"))
                .agotadas(countType(alerts, "AGOTADO"))
                .bajoUmbral(countType(alerts, "BAJO_UMBRAL"))
                .sinCosto(countType(alerts, "SIN_COSTO"))
                .items(alerts.stream().limit(itemLimit).toList())
                .build();
    }

    Comparator<InformeInventarioDTO.AlertaStockDTO> priorityComparator() {
        return Comparator
                .comparingInt(InformeInventarioDTO.AlertaStockDTO::prioridad)
                .thenComparingDouble(InformeInventarioDTO.AlertaStockDTO::stock)
                .thenComparing(
                        InformeInventarioDTO.AlertaStockDTO::productoId,
                        String.CASE_INSENSITIVE_ORDER);
    }

    private InformeInventarioDTO.AlertaStockDTO classifyMaterial(
            ProductoStockSnapshot snapshot
    ) {
        Material material = (Material) snapshot.producto();
        ProductThreshold threshold = thresholdFor(material);
        String type;
        int priority;

        if (snapshot.stockGeneral() < -STOCK_EPSILON) {
            type = "STOCK_NEGATIVO";
            priority = 1;
        } else if (Math.abs(snapshot.stockGeneral()) <= STOCK_EPSILON) {
            type = "AGOTADO";
            priority = 2;
        } else if (threshold.isConfiguredAndReachedBy(snapshot.stockGeneral())) {
            type = "BAJO_UMBRAL";
            priority = 3;
        } else if (!InventarioBiUtils.hasValidCost(material)) {
            type = "SIN_COSTO";
            priority = 4;
        } else {
            return null;
        }

        Double effectiveThreshold = threshold.effectiveValue();
        Double thresholdGap = effectiveThreshold == null
                ? null
                : Math.max(effectiveThreshold - snapshot.stockGeneral(), 0);
        Double thresholdGapPct = effectiveThreshold == null
                ? null
                : thresholdGap * 100 / effectiveThreshold;

        return InformeInventarioDTO.AlertaStockDTO.builder()
                .tipo(type)
                .prioridad(priority)
                .productoId(material.getProductoId())
                .productoNombre(material.getNombre())
                .grupo(InventarioBiUtils.inventoryTypeOf(material))
                .unidadMedida(InventarioBiUtils.unitOf(material))
                .stock(snapshot.stockGeneral())
                .umbral(effectiveThreshold)
                .stockMinimo(material.getStockMinimo())
                .puntoReorden(material.getPuntoReorden())
                .brechaUmbral(thresholdGap)
                .brechaPct(thresholdGapPct)
                .costoVigente(InventarioBiUtils.hasValidCost(material))
                .umbralesIncumplidos(
                        threshold.reachedThresholds(snapshot.stockGeneral()))
                .build();
    }

    private ProductThreshold thresholdFor(Material material) {
        return new ProductThreshold(
                material.getStockMinimo(),
                material.getPuntoReorden());
    }

    private int countType(
            List<InformeInventarioDTO.AlertaStockDTO> alerts,
            String type
    ) {
        return Math.toIntExact(alerts.stream()
                .filter(alert -> type.equals(alert.tipo()))
                .count());
    }

    private record ProductThreshold(double minimumStock, double reorderPoint) {
        Double effectiveValue() {
            double effective = Math.max(
                    Math.max(minimumStock, 0),
                    Math.max(reorderPoint, 0));
            return effective > 0 ? effective : null;
        }

        boolean isConfiguredAndReachedBy(double stock) {
            Double effective = effectiveValue();
            return effective != null && stock <= effective;
        }

        List<String> reachedThresholds(double stock) {
            List<String> reached = new ArrayList<>();
            if (minimumStock > 0 && stock <= minimumStock) {
                reached.add("STOCK_MINIMO");
            }
            if (reorderPoint > 0 && stock <= reorderPoint) {
                reached.add("PUNTO_REORDEN");
            }
            return List.copyOf(reached);
        }
    }
}
