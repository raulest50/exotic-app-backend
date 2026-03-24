package exotic.app.planta.model.commons.notificaciones;

import exotic.app.planta.model.producto.Material;

/**
 * Material con stock agregado (suma de movimientos), usado para alertas de punto de reorden.
 */
public record MaterialStockRow(Material material, double stock) {}
