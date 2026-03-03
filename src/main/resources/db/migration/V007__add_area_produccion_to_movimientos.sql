-- =====================================================================
-- V007: Agregar columna area_produccion_id a tabla movimientos
-- =====================================================================
-- Fecha: 2026-03-03
-- Descripción: Agrega relación opcional entre movimientos y áreas de
--              producción para soportar reporte de averías por área.
--
-- Contexto:
-- - La columna es nullable porque solo aplica para movimientos tipo AVERIA
-- - Permite rastrear de qué área operativa se reportó una avería
-- - Usa IF NOT EXISTS porque algunos ambientes ya tienen esta columna
--   creada por Hibernate ddl-auto=update antes de migrar a Flyway
-- =====================================================================

DO $$
BEGIN
    -- ===================================================================
    -- 1. Agregar columna area_produccion_id (si no existe)
    -- ===================================================================
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'movimientos'
          AND column_name = 'area_produccion_id'
    ) THEN
        ALTER TABLE movimientos
        ADD COLUMN area_produccion_id INTEGER;

        RAISE NOTICE 'Columna area_produccion_id agregada exitosamente';
    ELSE
        RAISE NOTICE 'Columna area_produccion_id ya existe, omitiendo';
    END IF;


    -- ===================================================================
    -- 2. Agregar foreign key (si no existe)
    -- ===================================================================
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.table_constraints
        WHERE table_schema = 'public'
          AND constraint_name = 'fk_movimientos_area_produccion'
    ) THEN
        ALTER TABLE movimientos
        ADD CONSTRAINT fk_movimientos_area_produccion
        FOREIGN KEY (area_produccion_id)
        REFERENCES area_produccion(area_id);

        RAISE NOTICE 'Foreign key fk_movimientos_area_produccion agregada exitosamente';
    ELSE
        RAISE NOTICE 'Foreign key fk_movimientos_area_produccion ya existe, omitiendo';
    END IF;


    -- ===================================================================
    -- 3. Agregar índice (si no existe)
    -- ===================================================================
    IF NOT EXISTS (
        SELECT 1
        FROM pg_indexes
        WHERE schemaname = 'public'
          AND tablename = 'movimientos'
          AND indexname = 'idx_movimientos_area_produccion'
    ) THEN
        CREATE INDEX idx_movimientos_area_produccion
        ON movimientos(area_produccion_id);

        RAISE NOTICE 'Índice idx_movimientos_area_produccion agregado exitosamente';
    ELSE
        RAISE NOTICE 'Índice idx_movimientos_area_produccion ya existe, omitiendo';
    END IF;

END $$;
