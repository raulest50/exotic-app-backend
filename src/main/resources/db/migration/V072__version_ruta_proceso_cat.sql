-- V072: Versionar rutas de proceso por categoria
-- Mantiene estables los nodos ya referenciados por seguimiento_orden_area.

CREATE TABLE IF NOT EXISTS ruta_proceso_cat_version (
    id BIGSERIAL PRIMARY KEY,
    ruta_proceso_cat_id BIGINT NOT NULL,
    version_number INTEGER NOT NULL,
    estado VARCHAR(20) NOT NULL,
    vigente_desde TIMESTAMP NOT NULL,
    vigente_hasta TIMESTAMP NULL,
    creado_en TIMESTAMP NOT NULL,
    creado_por VARCHAR(120) NULL,
    motivo_cambio TEXT NULL,
    CONSTRAINT chk_ruta_proceso_cat_version_estado
        CHECK (estado IN ('VIGENTE', 'RETIRADA')),
    CONSTRAINT uq_ruta_proceso_cat_version_number
        UNIQUE (ruta_proceso_cat_id, version_number)
);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_ruta_proceso_cat_version_ruta_cat'
    ) THEN
        ALTER TABLE ruta_proceso_cat_version
            ADD CONSTRAINT fk_ruta_proceso_cat_version_ruta_cat
            FOREIGN KEY (ruta_proceso_cat_id)
            REFERENCES ruta_proceso_cat (id)
            ON DELETE CASCADE;
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS uq_ruta_proceso_cat_version_vigente
    ON ruta_proceso_cat_version (ruta_proceso_cat_id)
    WHERE estado = 'VIGENTE';

CREATE INDEX IF NOT EXISTS idx_ruta_proceso_cat_version_ruta
    ON ruta_proceso_cat_version (ruta_proceso_cat_id);

INSERT INTO ruta_proceso_cat_version (
    ruta_proceso_cat_id,
    version_number,
    estado,
    vigente_desde,
    creado_en,
    creado_por,
    motivo_cambio
)
SELECT
    rpc.id,
    1,
    'VIGENTE',
    COALESCE(rpc.fecha_modificacion, rpc.fecha_creacion, CURRENT_TIMESTAMP),
    COALESCE(rpc.fecha_creacion, CURRENT_TIMESTAMP),
    'system',
    'Version inicial creada por migracion V072'
FROM ruta_proceso_cat rpc
WHERE NOT EXISTS (
    SELECT 1
    FROM ruta_proceso_cat_version rpcv
    WHERE rpcv.ruta_proceso_cat_id = rpc.id
);

ALTER TABLE ruta_proceso_node
    ADD COLUMN IF NOT EXISTS ruta_proceso_cat_version_id BIGINT NULL;

UPDATE ruta_proceso_node node
SET ruta_proceso_cat_version_id = version.id
FROM ruta_proceso_cat_version version
WHERE node.ruta_proceso_cat_version_id IS NULL
  AND node.ruta_proceso_cat_id = version.ruta_proceso_cat_id
  AND version.version_number = 1;

ALTER TABLE ruta_proceso_node
    ALTER COLUMN ruta_proceso_cat_version_id SET NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_ruta_proceso_node_version'
    ) THEN
        ALTER TABLE ruta_proceso_node
            ADD CONSTRAINT fk_ruta_proceso_node_version
            FOREIGN KEY (ruta_proceso_cat_version_id)
            REFERENCES ruta_proceso_cat_version (id)
            ON DELETE CASCADE;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_ruta_proceso_node_version
    ON ruta_proceso_node (ruta_proceso_cat_version_id);

ALTER TABLE ruta_proceso_edge
    ADD COLUMN IF NOT EXISTS ruta_proceso_cat_version_id BIGINT NULL;

UPDATE ruta_proceso_edge edge
SET ruta_proceso_cat_version_id = version.id
FROM ruta_proceso_cat_version version
WHERE edge.ruta_proceso_cat_version_id IS NULL
  AND edge.ruta_proceso_cat_id = version.ruta_proceso_cat_id
  AND version.version_number = 1;

ALTER TABLE ruta_proceso_edge
    ALTER COLUMN ruta_proceso_cat_version_id SET NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_ruta_proceso_edge_version'
    ) THEN
        ALTER TABLE ruta_proceso_edge
            ADD CONSTRAINT fk_ruta_proceso_edge_version
            FOREIGN KEY (ruta_proceso_cat_version_id)
            REFERENCES ruta_proceso_cat_version (id)
            ON DELETE CASCADE;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_ruta_proceso_edge_version
    ON ruta_proceso_edge (ruta_proceso_cat_version_id);

ALTER TABLE ordenes_produccion
    ADD COLUMN IF NOT EXISTS ruta_proceso_cat_version_id BIGINT NULL;

UPDATE ordenes_produccion orden
SET ruta_proceso_cat_version_id = inferred.version_id
FROM (
    SELECT
        seguimiento.orden_produccion_id,
        MIN(node.ruta_proceso_cat_version_id) AS version_id
    FROM seguimiento_orden_area seguimiento
    JOIN ruta_proceso_node node
        ON node.id = seguimiento.ruta_proceso_node_id
    WHERE node.ruta_proceso_cat_version_id IS NOT NULL
    GROUP BY seguimiento.orden_produccion_id
) inferred
WHERE orden.orden_id = inferred.orden_produccion_id
  AND orden.ruta_proceso_cat_version_id IS NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_ordenes_produccion_ruta_proceso_cat_version'
    ) THEN
        ALTER TABLE ordenes_produccion
            ADD CONSTRAINT fk_ordenes_produccion_ruta_proceso_cat_version
            FOREIGN KEY (ruta_proceso_cat_version_id)
            REFERENCES ruta_proceso_cat_version (id);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_ordenes_produccion_ruta_version
    ON ordenes_produccion (ruta_proceso_cat_version_id);

ALTER TABLE ruta_proceso_node
    DROP CONSTRAINT IF EXISTS fk_ruta_proceso_node_ruta_cat;

ALTER TABLE ruta_proceso_edge
    DROP CONSTRAINT IF EXISTS fk_ruta_proceso_edge_ruta_cat;

DROP INDEX IF EXISTS idx_ruta_proceso_node_ruta_cat;
DROP INDEX IF EXISTS idx_ruta_proceso_edge_ruta_cat;

ALTER TABLE ruta_proceso_node
    DROP COLUMN IF EXISTS ruta_proceso_cat_id;

ALTER TABLE ruta_proceso_edge
    DROP COLUMN IF EXISTS ruta_proceso_cat_id;
