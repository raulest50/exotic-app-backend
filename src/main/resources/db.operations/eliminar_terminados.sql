-- Script de eliminación en cascada para productos

-- 1. Primero, romper la referencia circular entre productos y procesos_produccion
UPDATE productos
SET proceso_prod_id = NULL
WHERE producto_id IN ('403007', '403008');

-- 2. Eliminar registros de lote relacionados con órdenes de producción de estos productos
DELETE FROM lote
WHERE orden_produccion_id IN (
    SELECT orden_id FROM ordenes_produccion
    WHERE producto_id IN ('403007', '403008')
);

-- 3. Eliminar registros de seguimiento de órdenes relacionados
DELETE FROM ordenes_seguimiento
WHERE orden_prod_id IN (
    SELECT orden_id FROM ordenes_produccion
    WHERE producto_id IN ('403007', '403008')
);

-- 4. Eliminar planificaciones de producción relacionadas
DELETE FROM planificacion_produccion
WHERE id IN (
    SELECT orden_id FROM ordenes_produccion
    WHERE producto_id IN ('403007', '403008')
);

-- 5. Eliminar las órdenes de producción relacionadas
DELETE FROM ordenes_produccion
WHERE producto_id IN ('403007', '403008');

-- 6. Eliminar nodos de proceso de producción relacionados
DELETE FROM proceso_produccion_node
WHERE proceso_completo_id IN (
    SELECT proceso_completo_id FROM procesos_produccion
    WHERE producto_id IN ('403007', '403008')
);

-- 7. Eliminar procesos de producción relacionados
DELETE FROM procesos_produccion
WHERE producto_id IN ('403007', '403008');

-- 8. Eliminar referencias en insumos (como input)
DELETE FROM insumos
WHERE input_producto_id IN ('403007', '403008');

-- 9. Eliminar referencias en insumos (como output)
DELETE FROM insumos
WHERE output_producto_id IN ('403007', '403008');

-- 10. Eliminar case packs relacionados
DELETE FROM case_pack
WHERE id IN (
    SELECT case_pack_id FROM productos
    WHERE producto_id IN ('403007', '403008')
);

-- 11. Finalmente eliminar los productos
DELETE FROM productos
WHERE producto_id IN ('403007', '403008');