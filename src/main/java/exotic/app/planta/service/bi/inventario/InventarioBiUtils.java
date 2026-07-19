package exotic.app.planta.service.bi.inventario;

import exotic.app.planta.model.producto.Material;
import exotic.app.planta.model.producto.Producto;
import exotic.app.planta.model.producto.Terminado;

import java.util.Locale;

final class InventarioBiUtils {
    private static final double STOCK_EPSILON = 1e-6;

    private InventarioBiUtils() {
    }

    static boolean hasValidCost(Producto producto) {
        return producto.getCosto() != null && producto.getCosto().signum() > 0;
    }

    static double costAsDouble(Producto producto) {
        return producto.getCosto() == null ? 0 : producto.getCosto().doubleValue();
    }

    static double estimatedValue(ProductoStockSnapshot snapshot) {
        if (snapshot.stockGeneral() <= STOCK_EPSILON
                || !hasValidCost(snapshot.producto())) {
            return 0;
        }
        return snapshot.stockGeneral() * costAsDouble(snapshot.producto());
    }

    static String unitOf(Producto producto) {
        String unit = producto.getTipoUnidades();
        return unit == null || unit.isBlank()
                ? "SIN UNIDAD"
                : unit.trim().toUpperCase(Locale.ROOT);
    }

    static String inventoryTypeOf(Producto producto) {
        if (producto instanceof Material material) {
            return switch (material.getTipoMaterial()) {
                case 1 -> "MATERIA_PRIMA";
                case 2 -> "EMPAQUE";
                default -> "OTROS";
            };
        }
        return producto instanceof Terminado ? "TERMINADO" : "OTROS";
    }

    static double percentage(double numerator, double denominator) {
        return denominator <= 0 ? 0 : numerator * 100d / denominator;
    }

    static double numberValue(Object value) {
        return value instanceof Number number ? number.doubleValue() : 0;
    }
}
