-- Vincula cada etapa de la ruta con el proceso que ejecuta y congela el POE
-- aplicable en el seguimiento de cada orden. Las columnas permanecen nulas para
-- conservar rutas y ordenes historicas sin reinterpretar su evidencia.

ALTER TABLE ruta_proceso_node
    ADD COLUMN IF NOT EXISTS proceso_produccion_id INTEGER NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_ruta_proceso_node_proceso_produccion'
    ) THEN
        ALTER TABLE ruta_proceso_node
            ADD CONSTRAINT fk_ruta_proceso_node_proceso_produccion
            FOREIGN KEY (proceso_produccion_id)
            REFERENCES proceso_produccion (proceso_id)
            ON DELETE RESTRICT;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_ruta_proceso_node_proceso_produccion
    ON ruta_proceso_node (proceso_produccion_id);

ALTER TABLE seguimiento_orden_area
    ADD COLUMN IF NOT EXISTS poe_documento_version_id BIGINT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_seguimiento_orden_area_poe_documento_version'
    ) THEN
        ALTER TABLE seguimiento_orden_area
            ADD CONSTRAINT fk_seguimiento_orden_area_poe_documento_version
            FOREIGN KEY (poe_documento_version_id)
            REFERENCES proceso_produccion_documento_version (id)
            ON DELETE RESTRICT;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_seguimiento_orden_area_poe_documento_version
    ON seguimiento_orden_area (poe_documento_version_id);
