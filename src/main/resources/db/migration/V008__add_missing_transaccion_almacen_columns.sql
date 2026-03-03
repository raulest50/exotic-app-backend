-- =====================================================================
-- V008: Agregar columnas faltantes en transaccion_almacen (si no existen)
-- =====================================================================
-- Fecha: 2026-03-03
-- Descripción: Agrega columnas que fueron introducidas ANTES de migrar
--              a Flyway, cuando se usaba Hibernate ddl-auto=update.
--
-- IMPORTANTE: Esta migración es completamente idempotente y autocontenida.
--             Usa IF NOT EXISTS para detectar y aplicar solo lo necesario:
--             - Si las columnas ya existen (Hibernate las creó): omite
--             - Si faltan: las agrega
--
-- Contexto:
-- - usuario_aprobador_id: Usuario que aprueba una dispensación
-- - estado_contable: Estado de contabilización de la transacción
-- - asiento_contable_id: Referencia al asiento contable generado
-- - Tabla join: Para relación ManyToMany con usuarios responsables
-- =====================================================================

DO $$
BEGIN
    -- ===================================================================
    -- 1. Agregar usuario_aprobador_id (si no existe)
    -- ===================================================================
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'transaccion_almacen'
          AND column_name = 'usuario_aprobador_id'
    ) THEN
        ALTER TABLE transaccion_almacen
        ADD COLUMN usuario_aprobador_id BIGINT;

        ALTER TABLE transaccion_almacen
        ADD CONSTRAINT fk_transaccion_usuario_aprobador
        FOREIGN KEY (usuario_aprobador_id)
        REFERENCES users(id);

        CREATE INDEX idx_transaccion_aprobador
        ON transaccion_almacen(usuario_aprobador_id);

        RAISE NOTICE 'Columna usuario_aprobador_id agregada exitosamente';
    ELSE
        RAISE NOTICE 'Columna usuario_aprobador_id ya existe, omitiendo';
    END IF;


    -- ===================================================================
    -- 2. Agregar estado_contable (si no existe)
    -- ===================================================================
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'transaccion_almacen'
          AND column_name = 'estado_contable'
    ) THEN
        ALTER TABLE transaccion_almacen
        ADD COLUMN estado_contable VARCHAR(20) DEFAULT 'PENDIENTE';

        -- Actualizar registros existentes con el valor por defecto
        UPDATE transaccion_almacen
        SET estado_contable = 'PENDIENTE'
        WHERE estado_contable IS NULL;

        -- Agregar NOT NULL constraint después de actualizar
        ALTER TABLE transaccion_almacen
        ALTER COLUMN estado_contable SET NOT NULL;

        -- Agregar CHECK constraint para valores válidos del enum
        ALTER TABLE transaccion_almacen
        ADD CONSTRAINT transaccion_almacen_estado_contable_check
        CHECK (estado_contable IN ('PENDIENTE', 'CONTABILIZADA', 'NO_APLICA'));

        RAISE NOTICE 'Columna estado_contable agregada exitosamente';
    ELSE
        RAISE NOTICE 'Columna estado_contable ya existe, omitiendo';
    END IF;


    -- ===================================================================
    -- 3. Agregar asiento_contable_id (si no existe)
    -- ===================================================================
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'transaccion_almacen'
          AND column_name = 'asiento_contable_id'
    ) THEN
        ALTER TABLE transaccion_almacen
        ADD COLUMN asiento_contable_id BIGINT;

        ALTER TABLE transaccion_almacen
        ADD CONSTRAINT fk_transaccion_asiento_contable
        FOREIGN KEY (asiento_contable_id)
        REFERENCES asiento_contable(id);

        CREATE INDEX idx_transaccion_asiento
        ON transaccion_almacen(asiento_contable_id);

        RAISE NOTICE 'Columna asiento_contable_id agregada exitosamente';
    ELSE
        RAISE NOTICE 'Columna asiento_contable_id ya existe, omitiendo';
    END IF;


    -- ===================================================================
    -- 4. Crear tabla join para usuarios responsables (si no existe)
    -- ===================================================================
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = 'public'
          AND table_name = 'transaccion_almacen_usuarios_realizadores'
    ) THEN
        CREATE TABLE transaccion_almacen_usuarios_realizadores (
            transaccion_id INTEGER NOT NULL,
            usuario_id BIGINT NOT NULL,
            PRIMARY KEY (transaccion_id, usuario_id),
            CONSTRAINT fk_tar_transaccion
                FOREIGN KEY (transaccion_id)
                REFERENCES transaccion_almacen(transaccion_id)
                ON DELETE CASCADE,
            CONSTRAINT fk_tar_usuario
                FOREIGN KEY (usuario_id)
                REFERENCES users(id)
                ON DELETE CASCADE
        );

        CREATE INDEX idx_tar_transaccion
        ON transaccion_almacen_usuarios_realizadores(transaccion_id);

        CREATE INDEX idx_tar_usuario
        ON transaccion_almacen_usuarios_realizadores(usuario_id);

        RAISE NOTICE 'Tabla transaccion_almacen_usuarios_realizadores creada exitosamente';
    ELSE
        RAISE NOTICE 'Tabla transaccion_almacen_usuarios_realizadores ya existe, omitiendo';
    END IF;

END $$;
