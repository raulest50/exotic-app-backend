-- Firma visual opcional y versionada para usuarios.
-- La imagen es una representación documental; no sustituye la autenticación
-- ni la evidencia electrónica de las acciones realizadas por el usuario.

CREATE TABLE firma_visual_usuario_version (
    id BIGSERIAL PRIMARY KEY,
    usuario_id BIGINT NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    version INTEGER NOT NULL,
    estado VARCHAR(20) NOT NULL,
    nombre_archivo_original VARCHAR(255) NOT NULL,
    content_type VARCHAR(80) NOT NULL,
    tamano_bytes BIGINT NOT NULL,
    ancho_px INTEGER NOT NULL,
    alto_px INTEGER NOT NULL,
    sha256 VARCHAR(64) NOT NULL,
    contenido BYTEA NOT NULL,
    vigente_desde TIMESTAMP NOT NULL,
    vigente_hasta TIMESTAMP NULL,
    creado_en TIMESTAMP NOT NULL,
    configurada_por_id BIGINT NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    configurada_por_username VARCHAR(255) NOT NULL,
    configurada_por_nombre VARCHAR(255) NOT NULL,
    motivo_cambio TEXT NOT NULL,
    retirada_por_id BIGINT NULL REFERENCES users(id) ON DELETE RESTRICT,
    retirada_por_username VARCHAR(255) NULL,
    retirada_por_nombre VARCHAR(255) NULL,
    motivo_retiro TEXT NULL,
    CONSTRAINT uq_firma_visual_usuario_version UNIQUE (usuario_id, version),
    CONSTRAINT chk_firma_visual_usuario_version_positiva CHECK (version > 0),
    CONSTRAINT chk_firma_visual_usuario_estado CHECK (estado IN ('VIGENTE', 'RETIRADA')),
    CONSTRAINT chk_firma_visual_usuario_png CHECK (content_type = 'image/png'),
    CONSTRAINT chk_firma_visual_usuario_tamano CHECK (
        tamano_bytes > 0
        AND tamano_bytes <= 1048576
        AND octet_length(contenido) = tamano_bytes
    ),
    CONSTRAINT chk_firma_visual_usuario_dimensiones CHECK (
        ancho_px BETWEEN 50 AND 2000
        AND alto_px BETWEEN 20 AND 1000
    ),
    CONSTRAINT chk_firma_visual_usuario_hash CHECK (sha256 ~ '^[0-9A-Fa-f]{64}$'),
    CONSTRAINT chk_firma_visual_usuario_vigencia CHECK (
        (estado = 'VIGENTE'
            AND vigente_hasta IS NULL
            AND retirada_por_id IS NULL
            AND retirada_por_username IS NULL
            AND retirada_por_nombre IS NULL
            AND motivo_retiro IS NULL)
        OR
        (estado = 'RETIRADA'
            AND vigente_hasta IS NOT NULL
            AND vigente_hasta >= vigente_desde
            AND retirada_por_id IS NOT NULL
            AND retirada_por_username IS NOT NULL
            AND retirada_por_nombre IS NOT NULL
            AND motivo_retiro IS NOT NULL)
    )
);

CREATE UNIQUE INDEX uq_firma_visual_usuario_vigente
    ON firma_visual_usuario_version(usuario_id)
    WHERE estado = 'VIGENTE';

CREATE INDEX idx_firma_visual_usuario_historial
    ON firma_visual_usuario_version(usuario_id, version DESC);
