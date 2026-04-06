-- =====================================================================
-- V023: Consolidar histórico de lotes en un lote INIT por producto
-- =====================================================================
-- Descripción:
--   Para cada producto_id que tiene al menos un movimiento, asegura un lote
--   sintético con batch_number = 'INIT-' || producto_id (FKs OCM/OP en NULL)
--   y reasigna TODOS los movimientos de ese producto a ese lote.
--
-- Alcance:
--   Todos los producto_id distintos presentes en movimientos (M, S, T).
--
-- Advertencia (pre-chequeo / verify-almacen):
--   Las consultas de stock por lote en la aplicación agregan movimientos sin
--   filtrar por almacén GENERAL; la dispensación registra salidas típicamente
--   en GENERAL. Si el descuadre era por ubicación y no por buckets de lote,
--   esta migración no lo corrige. Ejecutar preferiblemente con la aplicación
--   en mantenimiento para evitar movimientos concurrentes a mitad del script.
--
-- Post-migración opcional:
--   Elimina filas de lote que ya no están referenciadas por movimientos.
-- =====================================================================

-- 1) Crear lotes INIT (idempotente: no inserta si el batch_number ya existe)
INSERT INTO lote (batch_number, production_date, expiration_date, orden_compra_id, orden_produccion_id)
SELECT DISTINCT
    ('INIT-' || m.producto_id) AS batch_number,
    CURRENT_DATE              AS production_date,
    NULL                      AS expiration_date,
    NULL                      AS orden_compra_id,
    NULL                      AS orden_produccion_id
FROM movimientos m
WHERE NOT EXISTS (
    SELECT 1
    FROM lote l
    WHERE l.batch_number = ('INIT-' || m.producto_id)
);

-- 2) Apuntar todo el histórico de cada producto a su lote INIT (incluye lote_id previo NULL)
UPDATE movimientos m
SET lote_id = l.id
FROM lote l
WHERE l.batch_number = ('INIT-' || m.producto_id);

-- 3) Opcional: purgar lotes huérfanos (ya no referenciados por ningún movimiento)
DELETE FROM lote l
WHERE NOT EXISTS (
    SELECT 1
    FROM movimientos m
    WHERE m.lote_id = l.id
);
