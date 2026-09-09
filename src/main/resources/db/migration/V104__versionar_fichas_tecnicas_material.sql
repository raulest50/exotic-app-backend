CREATE TABLE material_ficha_tecnica_version (
    id BIGSERIAL PRIMARY KEY,
    material_id VARCHAR(255) NOT NULL,
    version INTEGER NOT NULL,
    estado VARCHAR(20) NOT NULL,
    nombre_archivo_original VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    tamano_bytes BIGINT NULL,
    sha256 VARCHAR(64) NULL,
    storage_key VARCHAR(500) NOT NULL,
    vigente_desde TIMESTAMP NOT NULL,
    vigente_hasta TIMESTAMP NULL,
    creado_en TIMESTAMP NOT NULL,
    creado_por VARCHAR(120) NULL,
    motivo_cambio TEXT NOT NULL,
    CONSTRAINT fk_material_ficha_tecnica_material
        FOREIGN KEY (material_id)
        REFERENCES productos (producto_id)
        ON DELETE CASCADE,
    CONSTRAINT uk_material_ficha_tecnica_version
        UNIQUE (material_id, version),
    CONSTRAINT chk_material_ficha_tecnica_estado
        CHECK (estado IN ('VIGENTE', 'RETIRADA')),
    CONSTRAINT chk_material_ficha_tecnica_version_positiva
        CHECK (version > 0),
    CONSTRAINT chk_material_ficha_tecnica_tamano
        CHECK (tamano_bytes IS NULL OR (tamano_bytes > 0 AND tamano_bytes <= 10485760)),
    CONSTRAINT chk_material_ficha_tecnica_sha256
        CHECK (sha256 IS NULL OR char_length(sha256) = 64)
);

CREATE UNIQUE INDEX uq_material_ficha_tecnica_vigente
    ON material_ficha_tecnica_version (material_id)
    WHERE estado = 'VIGENTE';

CREATE INDEX idx_material_ficha_tecnica_historial
    ON material_ficha_tecnica_version (material_id, version DESC);

INSERT INTO material_ficha_tecnica_version (
    material_id,
    version,
    estado,
    nombre_archivo_original,
    content_type,
    tamano_bytes,
    sha256,
    storage_key,
    vigente_desde,
    vigente_hasta,
    creado_en,
    creado_por,
    motivo_cambio
)
SELECT
    producto_id,
    1,
    'VIGENTE',
    CONCAT('ficha-tecnica-', producto_id, '.pdf'),
    'application/pdf',
    NULL,
    NULL,
    ficha_tecnica_url,
    COALESCE(fecha_creacion, CURRENT_TIMESTAMP),
    NULL,
    COALESCE(fecha_creacion, CURRENT_TIMESTAMP),
    NULL,
    'Migracion de ficha tecnica existente'
FROM productos
WHERE tipo_producto = 'M'
  AND ficha_tecnica_url IS NOT NULL
  AND BTRIM(ficha_tecnica_url) <> '';

ALTER TABLE productos DROP COLUMN ficha_tecnica_url;
