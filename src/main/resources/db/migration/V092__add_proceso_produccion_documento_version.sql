CREATE TABLE IF NOT EXISTS proceso_produccion_documento_version (
    id BIGSERIAL PRIMARY KEY,
    proceso_id INTEGER NOT NULL,
    version INTEGER NOT NULL,
    estado VARCHAR(20) NOT NULL,
    nombre_archivo_original VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    tamano_bytes BIGINT NOT NULL,
    sha256 VARCHAR(64) NOT NULL,
    storage_provider VARCHAR(30) NOT NULL DEFAULT 'LOCAL_DISK',
    storage_key VARCHAR(500) NOT NULL,
    vigente_desde TIMESTAMP NOT NULL,
    vigente_hasta TIMESTAMP NULL,
    creado_en TIMESTAMP NOT NULL,
    creado_por VARCHAR(120) NULL,
    motivo_cambio TEXT NOT NULL,
    CONSTRAINT fk_proceso_documento_proceso
        FOREIGN KEY (proceso_id)
        REFERENCES proceso_produccion (proceso_id)
        ON DELETE RESTRICT,
    CONSTRAINT uk_proceso_documento_version
        UNIQUE (proceso_id, version),
    CONSTRAINT uk_proceso_documento_storage_key
        UNIQUE (storage_key),
    CONSTRAINT chk_proceso_documento_estado
        CHECK (estado IN ('VIGENTE', 'RETIRADA')),
    CONSTRAINT chk_proceso_documento_storage_provider
        CHECK (storage_provider IN ('LOCAL_DISK')),
    CONSTRAINT chk_proceso_documento_version_positiva
        CHECK (version > 0),
    CONSTRAINT chk_proceso_documento_tamano
        CHECK (tamano_bytes > 0 AND tamano_bytes <= 2097152)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_proceso_documento_vigente
    ON proceso_produccion_documento_version (proceso_id)
    WHERE estado = 'VIGENTE';

CREATE INDEX IF NOT EXISTS idx_proceso_documento_historial
    ON proceso_produccion_documento_version (proceso_id, version DESC);
