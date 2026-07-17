CREATE INDEX IF NOT EXISTS idx_movimientos_tipo_fecha_producto
    ON movimientos (tipo_movimiento, fecha_movimiento, producto_id);
