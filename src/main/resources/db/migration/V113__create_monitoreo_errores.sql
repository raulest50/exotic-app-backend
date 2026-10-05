-- Estructura inicial: grupos y evidencia. La captura y la consulta se implementan aparte.
CREATE TABLE monitoreo_grupo_error (
    id UUID PRIMARY KEY,
    firma VARCHAR(64) NOT NULL,
    titulo VARCHAR(500) NOT NULL,
    estado VARCHAR(20) NOT NULL DEFAULT 'ABIERTO',
    severidad VARCHAR(10) NOT NULL DEFAULT 'MEDIA',
    total_eventos BIGINT NOT NULL DEFAULT 0,
    primera_aparicion TIMESTAMP WITH TIME ZONE,
    ultima_aparicion TIMESTAMP WITH TIME ZONE,
    version_correccion VARCHAR(120),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_monitoreo_grupo_error_firma UNIQUE (firma),
    CONSTRAINT chk_monitoreo_grupo_firma CHECK (firma ~ '^[0-9a-f]{64}$'),
    CONSTRAINT chk_monitoreo_grupo_estado CHECK (estado IN ('ABIERTO', 'EN_INVESTIGACION', 'RESUELTO')),
    CONSTRAINT chk_monitoreo_grupo_severidad CHECK (severidad IN ('BAJA', 'MEDIA', 'ALTA', 'CRITICA')),
    CONSTRAINT chk_monitoreo_grupo_total CHECK (total_eventos >= 0),
    CONSTRAINT chk_monitoreo_grupo_fechas CHECK (
        (primera_aparicion IS NULL AND ultima_aparicion IS NULL)
        OR (primera_aparicion IS NOT NULL AND ultima_aparicion IS NOT NULL
            AND ultima_aparicion >= primera_aparicion)
    )
);

CREATE TABLE monitoreo_evento_error (
    id UUID PRIMARY KEY,
    grupo_error_id UUID NOT NULL REFERENCES monitoreo_grupo_error(id) ON DELETE RESTRICT,
    origen VARCHAR(10) NOT NULL,
    modulo VARCHAR(100),
    tab VARCHAR(128),
    paso VARCHAR(160),
    accion VARCHAR(200),
    usuario_id BIGINT,
    ocurrido_en TIMESTAMP WITH TIME ZONE NOT NULL,
    recibido_en TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ambiente VARCHAR(10) NOT NULL,
    version_aplicacion VARCHAR(120),
    correlacion_id VARCHAR(128),
    tipo_error VARCHAR(500) NOT NULL,
    mensaje TEXT,
    archivo TEXT,
    linea INTEGER,
    stack_trace TEXT,
    solicitud JSONB,
    payloads JSONB,
    entidades JSONB,
    acciones_previas JSONB,
    logs_relacionados JSONB,
    CONSTRAINT chk_monitoreo_evento_origen CHECK (origen IN ('FRONTEND', 'BACKEND')),
    CONSTRAINT chk_monitoreo_evento_ambiente CHECK (ambiente IN ('LOCAL', 'STAGING', 'PRODUCCION')),
    CONSTRAINT chk_monitoreo_evento_linea CHECK (linea IS NULL OR linea > 0),
    CONSTRAINT chk_monitoreo_evento_solicitud CHECK (jsonb_typeof(solicitud) = 'object'),
    CONSTRAINT chk_monitoreo_evento_payloads CHECK (jsonb_typeof(payloads) = 'array'),
    CONSTRAINT chk_monitoreo_evento_entidades CHECK (jsonb_typeof(entidades) = 'array'),
    CONSTRAINT chk_monitoreo_evento_acciones CHECK (jsonb_typeof(acciones_previas) = 'array'),
    CONSTRAINT chk_monitoreo_evento_logs CHECK (jsonb_typeof(logs_relacionados) = 'array')
);

CREATE INDEX idx_monitoreo_grupo_estado_fecha
    ON monitoreo_grupo_error (estado, ultima_aparicion DESC, id);
CREATE INDEX idx_monitoreo_evento_grupo_fecha
    ON monitoreo_evento_error (grupo_error_id, ocurrido_en DESC, id);
CREATE INDEX idx_monitoreo_evento_modulo_grupo
    ON monitoreo_evento_error (modulo, grupo_error_id, ocurrido_en DESC);
CREATE INDEX idx_monitoreo_evento_correlacion
    ON monitoreo_evento_error (correlacion_id) WHERE correlacion_id IS NOT NULL;
CREATE INDEX idx_monitoreo_evento_recibido
    ON monitoreo_evento_error (recibido_en);

COMMENT ON TABLE monitoreo_grupo_error IS
    'Problemas agrupados por firma; sus modulos y ambientes se identifican mediante los eventos.';
COMMENT ON COLUMN monitoreo_grupo_error.total_eventos IS
    'Acumulado historico de eventos unicos; preservar al purgar evidencia antigua.';
COMMENT ON TABLE monitoreo_evento_error IS
    'Evidencia de frontend o backend; depurar datos sensibles antes de persistir payloads, trazas y logs.';
COMMENT ON COLUMN monitoreo_evento_error.usuario_id IS
    'Identificador historico opcional, sin FK para no bloquear la eliminacion del usuario.';
COMMENT ON COLUMN monitoreo_evento_error.modulo IS
    'Identificador funcional, independiente del catalogo de permisos; NULL si no se conoce el modulo.';
