-- =====================================================================
-- V014: Renombrar area_produccion a area_operativa
-- =====================================================================
-- Fecha: 2026-03-25
-- Descripción: Refactoring de nomenclatura para reflejar que las áreas
--              operativas abarcan más que solo producción (ej: almacén,
--              mármitas, envasado, etc.). Se renombra la tabla principal
--              y todas las columnas FK que la referencian.
--
-- Contexto:
-- - Migración arquitectónica: modelo de producción → modelo organizacional
-- - Paquete Java: producto.manufacturing.procesos → organizacion
-- - Entidad JPA: AreaProduccion → AreaOperativa
--
-- Impacto:
-- - Tabla principal: area_produccion → area_operativa
-- - FK en movimientos: area_produccion_id → area_operativa_id
-- - FK en procesos_produccion: area_produccion_id → area_operativa_id
-- =====================================================================

DO $$
BEGIN
    -- ===================================================================
    -- 1. Renombrar tabla principal
    -- ===================================================================
    IF EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = 'public'
          AND table_name = 'area_produccion'
    ) THEN
        ALTER TABLE area_produccion RENAME TO area_operativa;
        RAISE NOTICE 'Tabla area_produccion renombrada a area_operativa';
    ELSE
        RAISE NOTICE 'Tabla area_produccion no existe (posiblemente ya renombrada)';
    END IF;


    -- ===================================================================
    -- 2. Actualizar FK en tabla movimientos
    -- ===================================================================

    -- 2.1. Eliminar constraint existente
    IF EXISTS (
        SELECT 1
        FROM information_schema.table_constraints
        WHERE table_schema = 'public'
          AND constraint_name = 'fk_movimientos_area_produccion'
    ) THEN
        ALTER TABLE movimientos
        DROP CONSTRAINT fk_movimientos_area_produccion;
        RAISE NOTICE 'Constraint fk_movimientos_area_produccion eliminada';
    ELSE
        RAISE NOTICE 'Constraint fk_movimientos_area_produccion no existe, omitiendo';
    END IF;

    -- 2.2. Renombrar columna
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'movimientos'
          AND column_name = 'area_produccion_id'
    ) THEN
        ALTER TABLE movimientos
        RENAME COLUMN area_produccion_id TO area_operativa_id;
        RAISE NOTICE 'Columna movimientos.area_produccion_id renombrada a area_operativa_id';
    ELSE
        RAISE NOTICE 'Columna movimientos.area_produccion_id no existe, omitiendo';
    END IF;

    -- 2.3. Crear nueva constraint
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.table_constraints
        WHERE table_schema = 'public'
          AND constraint_name = 'fk_movimientos_area_operativa'
    ) THEN
        ALTER TABLE movimientos
        ADD CONSTRAINT fk_movimientos_area_operativa
        FOREIGN KEY (area_operativa_id)
        REFERENCES area_operativa(area_id);
        RAISE NOTICE 'Constraint fk_movimientos_area_operativa creada';
    ELSE
        RAISE NOTICE 'Constraint fk_movimientos_area_operativa ya existe, omitiendo';
    END IF;

    -- 2.4. Renombrar índice
    IF EXISTS (
        SELECT 1
        FROM pg_indexes
        WHERE schemaname = 'public'
          AND indexname = 'idx_movimientos_area_produccion'
    ) THEN
        ALTER INDEX idx_movimientos_area_produccion
        RENAME TO idx_movimientos_area_operativa;
        RAISE NOTICE 'Índice idx_movimientos_area_produccion renombrado';
    ELSE
        RAISE NOTICE 'Índice idx_movimientos_area_produccion no existe, omitiendo';
    END IF;


    -- ===================================================================
    -- 3. Actualizar FK en tabla procesos_produccion
    -- ===================================================================

    -- 3.1. Eliminar constraint existente (nombre inferido por Hibernate)
    IF EXISTS (
        SELECT 1
        FROM information_schema.table_constraints tc
        JOIN information_schema.constraint_column_usage ccu
          ON tc.constraint_name = ccu.constraint_name
        WHERE tc.table_schema = 'public'
          AND tc.table_name = 'procesos_produccion'
          AND ccu.column_name = 'area_produccion_id'
          AND tc.constraint_type = 'FOREIGN KEY'
    ) THEN
        -- Obtener el nombre dinámico del constraint generado por Hibernate
        DECLARE
            constraint_name_var VARCHAR;
        BEGIN
            SELECT tc.constraint_name INTO constraint_name_var
            FROM information_schema.table_constraints tc
            JOIN information_schema.constraint_column_usage ccu
              ON tc.constraint_name = ccu.constraint_name
            WHERE tc.table_schema = 'public'
              AND tc.table_name = 'procesos_produccion'
              AND ccu.column_name = 'area_produccion_id'
              AND tc.constraint_type = 'FOREIGN KEY'
            LIMIT 1;

            IF constraint_name_var IS NOT NULL THEN
                EXECUTE format('ALTER TABLE procesos_produccion DROP CONSTRAINT %I', constraint_name_var);
                RAISE NOTICE 'Constraint % eliminado de procesos_produccion', constraint_name_var;
            END IF;
        END;
    ELSE
        RAISE NOTICE 'No se encontró FK de area_produccion_id en procesos_produccion, omitiendo';
    END IF;

    -- 3.2. Renombrar columna
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'procesos_produccion'
          AND column_name = 'area_produccion_id'
    ) THEN
        ALTER TABLE procesos_produccion
        RENAME COLUMN area_produccion_id TO area_operativa_id;
        RAISE NOTICE 'Columna procesos_produccion.area_produccion_id renombrada a area_operativa_id';
    ELSE
        RAISE NOTICE 'Columna procesos_produccion.area_produccion_id no existe, omitiendo';
    END IF;

    -- 3.3. Crear nueva constraint
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.table_constraints
        WHERE table_schema = 'public'
          AND constraint_name = 'fk_procesos_produccion_area_operativa'
    ) THEN
        ALTER TABLE procesos_produccion
        ADD CONSTRAINT fk_procesos_produccion_area_operativa
        FOREIGN KEY (area_operativa_id)
        REFERENCES area_operativa(area_id);
        RAISE NOTICE 'Constraint fk_procesos_produccion_area_operativa creada';
    ELSE
        RAISE NOTICE 'Constraint fk_procesos_produccion_area_operativa ya existe, omitiendo';
    END IF;

END $$;
