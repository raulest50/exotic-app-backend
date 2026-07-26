CREATE TABLE receta_costos_revision (
    id SMALLINT PRIMARY KEY,
    version BIGINT NOT NULL,
    actualizado_en TIMESTAMP NOT NULL,
    CONSTRAINT ck_receta_costos_revision_singleton CHECK (id = 1),
    CONSTRAINT ck_receta_costos_revision_version CHECK (version >= 1)
);

INSERT INTO receta_costos_revision (id, version, actualizado_en)
VALUES (1, 1, CURRENT_TIMESTAMP);

CREATE OR REPLACE FUNCTION incrementar_receta_costos_revision()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    UPDATE receta_costos_revision
       SET version = version + 1,
           actualizado_en = CURRENT_TIMESTAMP
     WHERE id = 1;
    RETURN NULL;
END;
$$;

CREATE TRIGGER trg_insumos_receta_costos_revision
AFTER INSERT OR UPDATE OR DELETE OR TRUNCATE ON insumos
FOR EACH STATEMENT
EXECUTE FUNCTION incrementar_receta_costos_revision();

CREATE INDEX IF NOT EXISTS idx_insumos_input_producto
    ON insumos (input_producto_id);

CREATE INDEX IF NOT EXISTS idx_insumos_output_producto
    ON insumos (output_producto_id);

ALTER TABLE carga_costos_lote
    DROP CONSTRAINT ck_carga_costos_lote_estado;

ALTER TABLE carga_costos_lote
    ADD CONSTRAINT ck_carga_costos_lote_estado CHECK (
        estado IN (
            'PREPARADO',
            'EJECUTADO',
            'EXPIRADO',
            'BLOQUEADO',
            'CANCELADO',
            'INVALIDADO'
        )
    ),
    ADD COLUMN receta_revision BIGINT,
    ADD COLUMN algoritmo_version VARCHAR(40),
    ADD COLUMN propagacion_sha256 VARCHAR(64),
    ADD COLUMN invalidado_en TIMESTAMP,
    ADD COLUMN invalidacion_codigo VARCHAR(60),
    ADD COLUMN total_dependencias INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN total_semiterminados INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN total_terminados INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN total_dependencias_actualizadas INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN total_dependencias_sin_cambio INTEGER NOT NULL DEFAULT 0;

UPDATE carga_costos_lote
   SET estado = 'INVALIDADO',
       token_hash = NULL,
       token_expira_en = NULL,
       invalidado_en = CURRENT_TIMESTAMP,
       invalidacion_codigo = 'MIGRACION_PROPAGACION'
 WHERE estado = 'PREPARADO';

ALTER TABLE carga_costos_lote
    ADD CONSTRAINT ck_carga_costos_lote_propagacion CHECK (
        estado <> 'PREPARADO'
        OR (
            receta_revision IS NOT NULL
            AND receta_revision >= 1
            AND algoritmo_version IS NOT NULL
            AND propagacion_sha256 IS NOT NULL
        )
    ),
    ADD CONSTRAINT ck_carga_costos_lote_dependencias CHECK (
        total_dependencias >= 0
        AND total_semiterminados >= 0
        AND total_terminados >= 0
        AND total_dependencias_actualizadas >= 0
        AND total_dependencias_sin_cambio >= 0
        AND total_dependencias = total_semiterminados + total_terminados
        AND total_dependencias = total_dependencias_actualizadas + total_dependencias_sin_cambio
    );

CREATE TABLE carga_costos_propagacion_item (
    id BIGSERIAL PRIMARY KEY,
    lote_id UUID NOT NULL REFERENCES carga_costos_lote(id) ON DELETE CASCADE,
    producto_id VARCHAR(255) NOT NULL,
    producto_nombre VARCHAR(200),
    tipo_producto VARCHAR(1) NOT NULL,
    nivel INTEGER NOT NULL,
    costo_anterior NUMERIC(19, 6) NOT NULL,
    costo_nuevo NUMERIC(19, 6) NOT NULL,
    costo_version_anterior BIGINT NOT NULL,
    CONSTRAINT ck_carga_costos_propagacion_item_valores CHECK (
        tipo_producto IN ('S', 'T')
        AND nivel >= 1
        AND costo_anterior >= 0
        AND costo_nuevo >= 0
        AND costo_version_anterior >= 1
    ),
    CONSTRAINT uq_carga_costos_propagacion_lote_producto
        UNIQUE (lote_id, producto_id)
);

CREATE INDEX idx_carga_costos_propagacion_lote_nivel
    ON carga_costos_propagacion_item (lote_id, nivel, producto_id);
