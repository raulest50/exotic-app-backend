package exotic.app.planta.model.commons.notificaciones;

import java.util.List;

/**
 * Resultado de evaluar materiales frente a punto de reorden (misma regla que el correo programado).
 */
public record PuntoReordenEvaluacionResult(
        List<MaterialStockRow> enReorden,
        List<MaterialStockRow> sinPunto
) {}
