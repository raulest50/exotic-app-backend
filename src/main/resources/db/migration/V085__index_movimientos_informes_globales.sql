CREATE INDEX IF NOT EXISTS idx_movimientos_almacen_producto
    ON movimientos (almacen, producto_id);

CREATE INDEX IF NOT EXISTS idx_movimientos_almacen_fecha_producto
    ON movimientos (almacen, fecha_movimiento, producto_id);

CREATE INDEX IF NOT EXISTS idx_transaccion_causante
    ON transaccion_almacen (
        tipo_entidad_causante,
        id_entidad_causante,
        transaccion_id
    );

CREATE INDEX IF NOT EXISTS idx_movimientos_transaccion_producto_tipo
    ON movimientos (
        transaccion_id,
        producto_id,
        tipo_movimiento
    );
