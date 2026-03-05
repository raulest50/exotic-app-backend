-- Migración: Agregar RAA (Reporte de Avería de Almacén) a tipo_entidad_causante
-- Fecha: 2026-03-05
-- Descripción: Actualiza el constraint check de transaccion_almacen para permitir
--              el nuevo valor RAA (ordinal 7) en tipo_entidad_causante.
--
--              Valores del enum TipoEntidadCausante:
--              0 = OCM (Orden de Compra de Materiales)
--              1 = OP  (Orden de Producción)
--              2 = OTA (Orden de Transferencia de Almacén)
--              3 = OAA (Orden de Ajuste de Almacén)
--              4 = OD  (Orden de Dispensación)
--              5 = CM  (Carga Masiva)
--              6 = RA  (Reporte de Avería - producción)
--              7 = RAA (Reporte de Avería de Almacén) -- NUEVO

-- Eliminar el constraint existente
ALTER TABLE transaccion_almacen
DROP CONSTRAINT IF EXISTS transaccion_almacen_tipo_entidad_causante_check;

-- Crear el nuevo constraint que permite valores de 0 a 7
ALTER TABLE transaccion_almacen
ADD CONSTRAINT transaccion_almacen_tipo_entidad_causante_check
CHECK (tipo_entidad_causante >= 0 AND tipo_entidad_causante <= 7);
