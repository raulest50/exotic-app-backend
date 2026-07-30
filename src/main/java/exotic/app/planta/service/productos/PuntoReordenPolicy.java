package exotic.app.planta.service.productos;

/**
 * Reglas de dominio compartidas para el punto de reorden de materiales.
 */
public final class PuntoReordenPolicy {

    public static final double IGNORAR_ALERTAS = -1.0d;

    private PuntoReordenPolicy() {
    }

    public static boolean isValid(double value) {
        return Double.isFinite(value)
                && (Double.compare(value, IGNORAR_ALERTAS) == 0 || value >= 0.0d);
    }

    public static void validate(double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("puntoReorden debe ser un número finito");
        }
        if (Double.compare(value, IGNORAR_ALERTAS) != 0 && value < 0.0d) {
            throw new IllegalArgumentException(
                    "puntoReorden debe ser -1 (ignorar alertas) o mayor o igual a 0");
        }
    }
}
