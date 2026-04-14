-- ============================================================================
-- V026: Corregir timestamps historicos operativos afectados por timezone (+5h)
-- ============================================================================
-- Fecha: 2026-04-14
-- Descripcion:
--   Corrige timestamps historicos guardados con desfase de +5 horas antes de la
--   correccion global del backend. La migracion aplica solo a entidades
--   operativas criticas de compras, produccion y almacen.
--
--   IMPORTANTE:
--   - Solo se corrigen columnas autogeneradas por backend/SQL.
--   - No se tocan fechas de negocio capturadas o planificadas por usuarios.
--   - La correccion consiste en restar exactamente 5 horas.
-- ============================================================================

UPDATE orden_compra
SET fecha_emision = fecha_emision - INTERVAL '5 hours'
WHERE fecha_emision IS NOT NULL;

UPDATE facturas_compras
SET fecha_compra = fecha_compra - INTERVAL '5 hours'
WHERE fecha_compra IS NOT NULL;

UPDATE ordenes_produccion
SET fecha_creacion = fecha_creacion - INTERVAL '5 hours'
WHERE fecha_creacion IS NOT NULL;

UPDATE ordenes_produccion
SET fecha_final = fecha_final - INTERVAL '5 hours'
WHERE fecha_final IS NOT NULL;

UPDATE transaccion_almacen
SET fecha_transaccion = fecha_transaccion - INTERVAL '5 hours'
WHERE fecha_transaccion IS NOT NULL;

UPDATE movimientos
SET fecha_movimiento = fecha_movimiento - INTERVAL '5 hours'
WHERE fecha_movimiento IS NOT NULL;

UPDATE seguimiento_orden_area
SET fecha_creacion = fecha_creacion - INTERVAL '5 hours'
WHERE fecha_creacion IS NOT NULL;

UPDATE seguimiento_orden_area
SET fecha_visible = fecha_visible - INTERVAL '5 hours'
WHERE fecha_visible IS NOT NULL;

UPDATE seguimiento_orden_area
SET fecha_completado = fecha_completado - INTERVAL '5 hours'
WHERE fecha_completado IS NOT NULL;
