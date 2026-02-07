-- ============================================================
-- Migración: Incluir CM (Carga Masiva) en check de tipo_entidad_causante
-- ============================================================
-- Contexto: Se añadió TipoEntidadCausante.CM (ordinal 5) para carga masiva
-- de inventario. El check constraint existente solo permitía 0-4.
-- Este script elimina el constraint anterior y crea uno nuevo con (0,1,2,3,4,5).
--
-- Ejecutar en: BD local, test remoto y producción.
-- ============================================================

-- Paso 1: Eliminar el constraint existente (búsqueda dinámica por nombre)
DO $$
DECLARE
    constraint_name_var TEXT;
BEGIN
    SELECT con.conname INTO constraint_name_var
    FROM pg_constraint con
    JOIN pg_class rel ON rel.oid = con.conrelid
    WHERE rel.relname = 'transaccion_almacen'
      AND con.contype = 'c'
      AND pg_get_constraintdef(con.oid) LIKE '%tipo_entidad_causante%'
    LIMIT 1;

    IF constraint_name_var IS NOT NULL THEN
        EXECUTE format('ALTER TABLE transaccion_almacen DROP CONSTRAINT %I', constraint_name_var);
        RAISE NOTICE 'Constraint eliminado: %', constraint_name_var;
    ELSE
        RAISE NOTICE 'No existe constraint previo sobre tipo_entidad_causante.';
    END IF;
END $$;

-- Paso 2: Crear nuevo constraint incluyendo CM (ordinal 5)
-- OCM=0, OP=1, OTA=2, OAA=3, OD=4, CM=5
ALTER TABLE transaccion_almacen
    ADD CONSTRAINT transaccion_almacen_tipo_entidad_causante_check
    CHECK (tipo_entidad_causante IN (0, 1, 2, 3, 4, 5));
