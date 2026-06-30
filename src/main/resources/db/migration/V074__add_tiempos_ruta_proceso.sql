ALTER TABLE ruta_proceso_node
    ADD COLUMN IF NOT EXISTS duracion_estimada_minutos INTEGER NOT NULL DEFAULT 0;

ALTER TABLE ruta_proceso_node
    ADD COLUMN IF NOT EXISTS requiere_jornada_laboral BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE ruta_proceso_node
    DROP CONSTRAINT IF EXISTS chk_ruta_proceso_node_duracion_estimada;

ALTER TABLE ruta_proceso_node
    ADD CONSTRAINT chk_ruta_proceso_node_duracion_estimada
        CHECK (duracion_estimada_minutos >= 0);

ALTER TABLE seguimiento_orden_area
    ADD COLUMN IF NOT EXISTS duracion_estimada_minutos INTEGER NOT NULL DEFAULT 0;

ALTER TABLE seguimiento_orden_area
    ADD COLUMN IF NOT EXISTS requiere_jornada_laboral BOOLEAN NOT NULL DEFAULT TRUE;

UPDATE seguimiento_orden_area seguimiento
SET duracion_estimada_minutos = COALESCE(node.duracion_estimada_minutos, 0),
    requiere_jornada_laboral = COALESCE(node.requiere_jornada_laboral, TRUE)
FROM ruta_proceso_node node
WHERE seguimiento.ruta_proceso_node_id = node.id;

ALTER TABLE seguimiento_orden_area
    DROP CONSTRAINT IF EXISTS chk_seguimiento_orden_area_duracion_estimada;

ALTER TABLE seguimiento_orden_area
    ADD CONSTRAINT chk_seguimiento_orden_area_duracion_estimada
        CHECK (duracion_estimada_minutos >= 0);

ALTER TABLE ordenes_produccion
    ADD COLUMN IF NOT EXISTS jornada_laboral_version_id BIGINT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_ordenes_produccion_jornada_laboral_version'
    ) THEN
        ALTER TABLE ordenes_produccion
            ADD CONSTRAINT fk_ordenes_produccion_jornada_laboral_version
            FOREIGN KEY (jornada_laboral_version_id)
            REFERENCES jornada_laboral_version (id);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_ordenes_produccion_jornada_laboral_version
    ON ordenes_produccion (jornada_laboral_version_id);
