-- ============================================================================
-- V047: Normalizar persistencia de MPS semanal de programacion
-- ============================================================================
-- El MPS semanal deja de persistirse como snapshot_json y pasa a modelarse como:
-- MPS -> dia -> item de terminado -> lote planificado -> ODP/Lote real.
-- La limpieza de datos MPS existentes es intencional.
-- ============================================================================

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'ordenes_produccion'
          AND column_name = 'mps_id'
    ) THEN
        CREATE TEMP TABLE tmp_v047_mps_ops ON COMMIT DROP AS
        SELECT orden_id
        FROM ordenes_produccion
        WHERE mps_id IS NOT NULL;

        CREATE TEMP TABLE tmp_v047_mps_tx ON COMMIT DROP AS
        SELECT transaccion_id
        FROM transaccion_almacen
        WHERE id_entidad_causante IN (SELECT orden_id FROM tmp_v047_mps_ops)
          AND tipo_entidad_causante IN (1, 4);

        DELETE FROM transaccion_almacen_usuarios_realizadores
        WHERE transaccion_id IN (SELECT transaccion_id FROM tmp_v047_mps_tx);

        DELETE FROM movimientos
        WHERE transaccion_id IN (SELECT transaccion_id FROM tmp_v047_mps_tx);

        DELETE FROM movimientos
        WHERE lote_id IN (
            SELECT id
            FROM lote
            WHERE orden_produccion_id IN (SELECT orden_id FROM tmp_v047_mps_ops)
        );

        DELETE FROM transaccion_almacen
        WHERE transaccion_id IN (SELECT transaccion_id FROM tmp_v047_mps_tx);

        DELETE FROM lote
        WHERE orden_produccion_id IN (SELECT orden_id FROM tmp_v047_mps_ops);

        DELETE FROM seguimiento_orden_area_evento
        WHERE seguimiento_orden_area_id IN (
            SELECT id
            FROM seguimiento_orden_area
            WHERE orden_produccion_id IN (SELECT orden_id FROM tmp_v047_mps_ops)
        );

        DELETE FROM seguimiento_orden_area
        WHERE orden_produccion_id IN (SELECT orden_id FROM tmp_v047_mps_ops);

        DELETE FROM ordenes_produccion
        WHERE orden_id IN (SELECT orden_id FROM tmp_v047_mps_ops);
    END IF;
END $$;

DELETE FROM mps_semanal_observacion;
DELETE FROM master_production_schedule_semanal;

ALTER TABLE ordenes_produccion
    DROP CONSTRAINT IF EXISTS uk_ordenes_produccion_mps_block_lote;

ALTER TABLE ordenes_produccion
    DROP COLUMN IF EXISTS mps_block_id;

ALTER TABLE ordenes_produccion
    DROP COLUMN IF EXISTS mps_lote_ordinal;

ALTER TABLE master_production_schedule_semanal
    DROP COLUMN IF EXISTS snapshot_json;

CREATE TABLE IF NOT EXISTS mps_semanal_dia (
    id BIGSERIAL PRIMARY KEY,
    mps_id INTEGER NOT NULL,
    fecha DATE NOT NULL,
    day_index INTEGER NOT NULL,
    display_order INTEGER NOT NULL,
    CONSTRAINT fk_mps_sem_dia_mps
        FOREIGN KEY (mps_id)
        REFERENCES master_production_schedule_semanal(mps_id)
        ON DELETE CASCADE,
    CONSTRAINT uk_mps_sem_dia_fecha UNIQUE (mps_id, fecha),
    CONSTRAINT uk_mps_sem_dia_day_index UNIQUE (mps_id, day_index),
    CONSTRAINT ck_mps_sem_dia_day_index CHECK (day_index BETWEEN 0 AND 5)
);

CREATE TABLE IF NOT EXISTS mps_semanal_item (
    id BIGSERIAL PRIMARY KEY,
    mps_id INTEGER NOT NULL,
    mps_dia_id BIGINT NOT NULL,
    terminado_id VARCHAR(255) NOT NULL,
    terminado_nombre VARCHAR(200) NOT NULL,
    categoria_id INTEGER,
    categoria_nombre VARCHAR(200),
    lote_size INTEGER NOT NULL,
    tiempo_dias_fabricacion INTEGER NOT NULL,
    numero_lotes INTEGER NOT NULL,
    cantidad_total DOUBLE PRECISION NOT NULL,
    fecha_lanzamiento DATE NOT NULL,
    fecha_final_planificada DATE NOT NULL,
    observacion TEXT,
    warning TEXT,
    display_order INTEGER NOT NULL,
    CONSTRAINT fk_mps_sem_item_mps
        FOREIGN KEY (mps_id)
        REFERENCES master_production_schedule_semanal(mps_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_mps_sem_item_dia
        FOREIGN KEY (mps_dia_id)
        REFERENCES mps_semanal_dia(id)
        ON DELETE CASCADE,
    CONSTRAINT fk_mps_sem_item_terminado
        FOREIGN KEY (terminado_id)
        REFERENCES productos(producto_id),
    CONSTRAINT uk_mps_sem_item_dia_terminado UNIQUE (mps_dia_id, terminado_id),
    CONSTRAINT ck_mps_sem_item_numero_lotes CHECK (numero_lotes > 0),
    CONSTRAINT ck_mps_sem_item_cantidad_total CHECK (cantidad_total > 0),
    CONSTRAINT ck_mps_sem_item_lote_size CHECK (lote_size > 0),
    CONSTRAINT ck_mps_sem_item_tiempo_fabricacion CHECK (tiempo_dias_fabricacion >= 0)
);

CREATE TABLE IF NOT EXISTS mps_semanal_lote_planificado (
    id BIGSERIAL PRIMARY KEY,
    mps_item_id BIGINT NOT NULL,
    lote_ordinal INTEGER NOT NULL,
    cantidad_planificada DOUBLE PRECISION NOT NULL,
    estado VARCHAR(30) NOT NULL DEFAULT 'PENDIENTE_ODP',
    CONSTRAINT fk_mps_sem_lote_item
        FOREIGN KEY (mps_item_id)
        REFERENCES mps_semanal_item(id)
        ON DELETE CASCADE,
    CONSTRAINT uk_mps_sem_lote_item_ordinal UNIQUE (mps_item_id, lote_ordinal),
    CONSTRAINT ck_mps_sem_lote_ordinal CHECK (lote_ordinal > 0),
    CONSTRAINT ck_mps_sem_lote_cantidad CHECK (cantidad_planificada > 0),
    CONSTRAINT ck_mps_sem_lote_estado
        CHECK (estado IN ('PENDIENTE_ODP', 'ODP_GENERADA', 'CANCELADO'))
);

ALTER TABLE ordenes_produccion
    ADD COLUMN IF NOT EXISTS mps_lote_planificado_id BIGINT;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'uk_ordenes_produccion_mps_lote_planificado'
    ) THEN
        ALTER TABLE ordenes_produccion
            ADD CONSTRAINT uk_ordenes_produccion_mps_lote_planificado
            UNIQUE (mps_lote_planificado_id);
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_ordenes_produccion_mps_lote_planificado'
    ) THEN
        ALTER TABLE ordenes_produccion
            ADD CONSTRAINT fk_ordenes_produccion_mps_lote_planificado
            FOREIGN KEY (mps_lote_planificado_id)
            REFERENCES mps_semanal_lote_planificado(id)
            ON DELETE SET NULL;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_mps_sem_dia_mps_id
    ON mps_semanal_dia (mps_id);

CREATE INDEX IF NOT EXISTS idx_mps_sem_dia_fecha
    ON mps_semanal_dia (fecha);

CREATE INDEX IF NOT EXISTS idx_mps_sem_item_mps_id
    ON mps_semanal_item (mps_id);

CREATE INDEX IF NOT EXISTS idx_mps_sem_item_dia_id
    ON mps_semanal_item (mps_dia_id);

CREATE INDEX IF NOT EXISTS idx_mps_sem_item_terminado
    ON mps_semanal_item (terminado_id);

CREATE INDEX IF NOT EXISTS idx_mps_sem_lote_item_id
    ON mps_semanal_lote_planificado (mps_item_id);

CREATE INDEX IF NOT EXISTS idx_mps_sem_lote_estado
    ON mps_semanal_lote_planificado (estado);

CREATE INDEX IF NOT EXISTS idx_ordenes_produccion_mps_lote_planificado
    ON ordenes_produccion (mps_lote_planificado_id);
