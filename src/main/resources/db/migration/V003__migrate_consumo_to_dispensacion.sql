-- Migración: CONSUMO → DISPENSACION
-- Fecha: 2025-03-01
-- Descripción: Migra todos los registros históricos de tipo_movimiento 'CONSUMO' a 'DISPENSACION'
--              para reflejar correctamente que son salidas de almacén para órdenes de producción.
--              El tipo CONSUMO se mantiene en el enum para uso futuro.

-- Migrar registros históricos
-- CONSUMO estaba en posición ordinal 2 en el enum anterior
-- DISPENSACION está en posición ordinal 3 en el enum actual
UPDATE movimientos
SET tipo_movimiento = 3
WHERE tipo_movimiento = 2;
