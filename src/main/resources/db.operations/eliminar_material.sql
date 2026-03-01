-- Script de eliminación en cascada para materiales
-- Declaración de variable para el ID del material a eliminar
-- Reemplazar 'ID_DEL_MATERIAL_A_ELIMINAR' con el ID del material a eliminar
DO $$
DECLARE
    v_material_id VARCHAR(50) := 'ID_DEL_MATERIAL_A_ELIMINAR';
BEGIN

-- 1. Eliminar registros de InsumoEmpaque relacionados con este material
DELETE FROM insumos_empaque
WHERE material_id = v_material_id;

-- 2. Eliminar movimientos relacionados con este material
DELETE FROM movimientos
WHERE producto_id = v_material_id;

-- 3. Eliminar lotes relacionados con órdenes de compra que contienen este material
DELETE FROM lote
WHERE orden_compra_id IN (
    SELECT orden_compra_id FROM orden_compra
    WHERE orden_compra_id IN (
        SELECT orden_compra_id FROM item_orden_compra
        WHERE producto_id = v_material_id
    )
);

-- 4. Eliminar items de factura de compra relacionados con este material
DELETE FROM items_factura_compra
WHERE materia_prima_id = v_material_id;

-- 5. Eliminar items de orden de compra relacionados con este material
DELETE FROM item_orden_compra
WHERE producto_id = v_material_id;

-- 6. Eliminar órdenes de compra que solo contienen este material
-- (solo si no tienen otros items después de eliminar los items del material específico)
DELETE FROM orden_compra
WHERE orden_compra_id NOT IN (
    SELECT DISTINCT orden_compra_id FROM item_orden_compra
);

-- 7. Eliminar referencias en insumos (como input)
DELETE FROM insumos
WHERE input_producto_id = v_material_id;

-- 8. Finalmente eliminar el material (que es un producto con tipo_producto = 'M')
DELETE FROM productos
WHERE producto_id = v_material_id AND tipo_producto = 'M';

END $$;
