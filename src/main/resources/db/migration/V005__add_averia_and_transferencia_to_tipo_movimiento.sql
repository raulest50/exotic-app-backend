-- Migración: Agregar AVERIA y TRANSFERENCIA a tipo_movimiento
-- Fecha: 2026-03-01
-- Descripción: Actualiza el constraint check de movimientos para permitir
--              los nuevos valores AVERIA (ordinal 8) y TRANSFERENCIA (ordinal 9)
--              en tipo_movimiento.
--
--              Valores del enum TipoMovimiento:
--              0 = COMPRA
--              1 = BACKFLUSH
--              2 = AJUSTE_POSITIVO
--              3 = CONSUMO
--              4 = VENTA
--              5 = DISPENSACION
--              6 = BAJA
--              7 = AJUSTE_NEGATIVO
--              8 = AVERIA             -- NUEVO (para reportes de avería)
--              9 = TRANSFERENCIA      -- NUEVO (para transferencias entre almacenes)

-- Eliminar el constraint existente
ALTER TABLE movimientos
DROP CONSTRAINT IF EXISTS movimientos_tipo_movimiento_check;

-- Crear el nuevo constraint que permite valores de 0 a 9
ALTER TABLE movimientos
ADD CONSTRAINT movimientos_tipo_movimiento_check
CHECK (tipo_movimiento >= 0 AND tipo_movimiento <= 9);
