CREATE INDEX IF NOT EXISTS idx_bi_ocm_pendientes_fecha
    ON orden_compra (fecha_emision, orden_compra_id)
    WHERE estado = 2;

CREATE INDEX IF NOT EXISTS idx_bi_item_ocm_orden_producto
    ON item_orden_compra (orden_compra_id, producto_id, item_id);

CREATE INDEX IF NOT EXISTS idx_bi_op_abierta_antiguedad
    ON ordenes_produccion ((COALESCE(fecha_inicio, fecha_creacion)), orden_id)
    WHERE estado_orden <> 2 AND estado_orden <> -1;
