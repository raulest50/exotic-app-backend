package exotic.app.planta.model.commons.notificaciones;

import java.util.List;

/**
 * Resultado de evaluar materiales frente a punto de reorden
 * para correo programado y modal de stock.
 */
public record PuntoReordenEvaluacionResult(
        List<MaterialEnPuntoReordenDTO> pendientesOrdenar,
        List<MaterialEnPuntoReordenConOcmDTO> pendientesIngresoAlmacen,
        List<MaterialEnPuntoReordenDTO> sinPuntoReorden,
        long totalPendientesOrdenar,
        long totalPendientesIngresoAlmacen,
        long totalSinPuntoReorden,
        long totalEnAlerta
) {}
