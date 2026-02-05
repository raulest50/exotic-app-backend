-- ============================================================
-- SCRIPT PARA VERIFICAR Y ACTUALIZAR RESTRICCIÓN DE tipo_entidad_causante
-- ============================================================

-- 1. VERIFICAR SI EXISTE LA RESTRICCIÓN
-- ============================================================
SELECT
    con.conname AS nombre_restriccion,
    pg_get_constraintdef(con.oid) AS definicion
FROM pg_constraint con
         JOIN pg_class rel ON rel.oid = con.conrelid
         JOIN pg_namespace nsp ON nsp.oid = rel.relnamespace
WHERE rel.relname = 'transaccion_almacen'
  AND con.contype = 'c'  -- 'c' indica CHECK constraint
  AND pg_get_constraintdef(con.oid) LIKE '%tipo_entidad_causante%';

-- Si el resultado muestra algo, existe una restricción


-- 2. ELIMINAR LA RESTRICCIÓN SI EXISTE
-- ============================================================
-- Primero necesitamos el nombre exacto de la restricción
-- Por lo general Hibernate/JPA genera nombres como: transaccion_almacen_tipo_entidad_causante_check
-- O puede ser un nombre generado automáticamente

-- Opción A: Si conoces el nombre exacto (reemplaza <nombre_restriccion>)
-- ALTER TABLE transaccion_almacen DROP CONSTRAINT IF EXISTS <nombre_restriccion>;

-- Opción B: Script dinámico para eliminar cualquier restricción sobre tipo_entidad_causante
DO $$
DECLARE
constraint_name TEXT;
BEGIN
SELECT con.conname INTO constraint_name
FROM pg_constraint con
         JOIN pg_class rel ON rel.oid = con.conrelid
WHERE rel.relname = 'transaccion_almacen'
  AND con.contype = 'c'
  AND pg_get_constraintdef(con.oid) LIKE '%tipo_entidad_causante%';

IF constraint_name IS NOT NULL THEN
        EXECUTE 'ALTER TABLE transaccion_almacen DROP CONSTRAINT ' || constraint_name;
        RAISE NOTICE 'Restricción eliminada: %', constraint_name;
ELSE
        RAISE NOTICE 'No se encontró restricción sobre tipo_entidad_causante';
END IF;
END $$;


-- 3. CREAR NUEVA RESTRICCIÓN INCLUYENDO CM
-- ============================================================
ALTER TABLE transaccion_almacen
    ADD CONSTRAINT transaccion_almacen_tipo_entidad_causante_check
        CHECK (tipo_entidad_causante IN ('OCM', 'OP', 'OTA', 'OAA', 'OD', 'CM'));

-- Verificar que la restricción se creó correctamente
SELECT
    con.conname AS nombre_restriccion,
    pg_get_constraintdef(con.oid) AS definicion
FROM pg_constraint con
         JOIN pg_class rel ON rel.oid = con.conrelid
WHERE rel.relname = 'transaccion_almacen'
  AND con.contype = 'c'
  AND pg_get_constraintdef(con.oid) LIKE '%tipo_entidad_causante%';