-- Migración: Agregar RA (Reporte de Avería) a tipo_entidad_causante
-- Fecha: 2026-03-01
-- Descripción: Actualiza el constraint check de transaccion_almacen para permitir
--              el nuevo valor RA (ordinal 6) en tipo_entidad_causante.
--
--              Valores del enum TipoEntidadCausante:
--              0 = OCM (Orden de Compra de Materiales)
--              1 = OP  (Orden de Producción)
--              2 = OTA (Orden de Transferencia de Almacén)
--              3 = OAA (Orden de Ajuste de Almacén)
--              4 = OD  (Orden de Dispensación)
--              5 = CM  (Carga Masiva)
--              6 = RA  (Reporte de Avería) -- NUEVO

-- Eliminar el constraint existente
ALTER TABLE transaccion_almacen
DROP CONSTRAINT IF EXISTS transaccion_almacen_tipo_entidad_causante_check;

-- Crear el nuevo constraint que permite valores de 0 a 6
ALTER TABLE transaccion_almacen
ADD CONSTRAINT transaccion_almacen_tipo_entidad_causante_check
CHECK (tipo_entidad_causante >= 0 AND tipo_entidad_causante <= 6);
