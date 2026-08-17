-- Primera activación funcional del expediente digital de fabricación.
-- Los PDF no se almacenan. La tabla batch_record_documento de V095 se conserva
-- únicamente por compatibilidad y no tiene entidad ni escrituras nuevas.

ALTER TABLE batch_record
    DROP CONSTRAINT IF EXISTS chk_batch_record_estado;

ALTER TABLE batch_record
    ADD CONSTRAINT chk_batch_record_estado CHECK (
        estado IN (
            'BORRADOR',
            'EN_EJECUCION',
            'PENDIENTE_REVISION',
            'DEVUELTO_PRODUCCION',
            'APROBADO',
            'RECHAZADO',
            'CERRADO',
            'ANULADO'
        )
    );

-- PENDIENTE_REVISION se persiste y se fotografía dentro de la misma
-- transacción; el hash queda asignado antes del commit. Permitir ese estado
-- transitorio evita una dependencia circular entre el snapshot y su hash.
ALTER TABLE batch_record
    DROP CONSTRAINT IF EXISTS chk_batch_record_revision_contenido;

ALTER TABLE batch_record
    ADD CONSTRAINT chk_batch_record_revision_contenido CHECK (
        estado NOT IN ('APROBADO', 'RECHAZADO', 'CERRADO')
        OR (cantidad_obtenida IS NOT NULL AND contenido_sha256 IS NOT NULL)
    );

ALTER TABLE batch_record_etapa
    ADD COLUMN seguimiento_orden_area_id BIGINT NULL
        REFERENCES seguimiento_orden_area(id),
    ADD COLUMN control_proceso_plantilla_id BIGINT NULL
        REFERENCES calidad_control_proceso_plantilla(id);

CREATE UNIQUE INDEX uq_batch_record_etapa_seguimiento
    ON batch_record_etapa(seguimiento_orden_area_id)
    WHERE seguimiento_orden_area_id IS NOT NULL;

CREATE INDEX idx_batch_record_etapa_control_plantilla
    ON batch_record_etapa(control_proceso_plantilla_id)
    WHERE control_proceso_plantilla_id IS NOT NULL;

ALTER TABLE batch_record_consumo
    ADD COLUMN tipo VARCHAR(30) NOT NULL DEFAULT 'DISPENSACION';

ALTER TABLE batch_record_consumo
    ALTER COLUMN tipo DROP DEFAULT,
    DROP CONSTRAINT IF EXISTS chk_batch_record_consumo_cantidad;

ALTER TABLE batch_record_consumo
    ADD CONSTRAINT chk_batch_record_consumo_tipo CHECK (
        tipo IN ('DISPENSACION', 'REPOSICION_AVERIA', 'EXCLUSION_AVERIA')
    ),
    ADD CONSTRAINT chk_batch_record_consumo_cantidad CHECK (
        cantidad <> 0
        AND (
            (tipo = 'EXCLUSION_AVERIA' AND cantidad < 0)
            OR (tipo <> 'EXCLUSION_AVERIA' AND cantidad > 0)
        )
    );

ALTER TABLE batch_record_desviacion
    ADD COLUMN ocurrida_en TIMESTAMP NULL,
    ADD COLUMN origen VARCHAR(30) NULL,
    ADD COLUMN accion_inmediata TEXT NULL,
    ADD COLUMN causa_raiz TEXT NULL,
    ADD COLUMN acciones_correctivas_preventivas TEXT NULL;

ALTER TABLE batch_record_desviacion
    ADD CONSTRAINT chk_batch_record_desviacion_origen CHECK (
        origen IS NULL OR origen IN (
            'PROCESO', 'MATERIAL', 'EQUIPO', 'AMBIENTE',
            'DOCUMENTACION', 'CONTROL_CALIDAD', 'OTRO'
        )
    ),
    ADD CONSTRAINT chk_batch_record_desviacion_ocurrencia CHECK (
        ocurrida_en IS NULL OR ocurrida_en <= detectada_en
    );

ALTER TABLE calidad_control_proceso_ejecucion
    ADD COLUMN batch_record_etapa_id BIGINT NULL
        REFERENCES batch_record_etapa(id),
    ADD COLUMN resultado VARCHAR(20) NULL;

ALTER TABLE calidad_control_proceso_ejecucion
    ADD CONSTRAINT chk_calidad_control_ejecucion_resultado CHECK (
        resultado IS NULL OR resultado IN ('CONFORME', 'NO_CONFORME')
    );

CREATE INDEX idx_calidad_control_ejecucion_etapa
    ON calidad_control_proceso_ejecucion(batch_record_etapa_id, fecha_registro);

CREATE TABLE batch_record_revision (
    id BIGSERIAL PRIMARY KEY,
    batch_record_id BIGINT NOT NULL REFERENCES batch_record(id) ON DELETE RESTRICT,
    numero INTEGER NOT NULL,
    tipo VARCHAR(30) NOT NULL,
    contenido_canonico TEXT NOT NULL,
    contenido_sha256 VARCHAR(64) NOT NULL,
    esquema_version VARCHAR(30) NOT NULL,
    plantilla_pdf_version VARCHAR(30) NOT NULL,
    creada_en TIMESTAMP NOT NULL,
    creada_por_id BIGINT NOT NULL REFERENCES users(id),
    creada_por_username VARCHAR(120) NOT NULL,
    creada_por_nombre VARCHAR(200) NOT NULL,
    creada_por_cedula VARCHAR(30) NOT NULL,
    motivo VARCHAR(500) NULL,
    CONSTRAINT uq_batch_record_revision_numero UNIQUE (batch_record_id, numero),
    CONSTRAINT chk_batch_record_revision_numero CHECK (numero > 0),
    CONSTRAINT chk_batch_record_revision_tipo CHECK (
        tipo IN ('ENVIO_CALIDAD', 'DECISION_CALIDAD', 'CORRECCION', 'CIERRE')
    ),
    CONSTRAINT chk_batch_record_revision_hash CHECK (
        contenido_sha256 ~ '^[0-9A-Fa-f]{64}$'
    )
);

CREATE INDEX idx_batch_record_revision_record
    ON batch_record_revision(batch_record_id, numero DESC);

ALTER TABLE batch_record_firma
    DROP CONSTRAINT IF EXISTS batch_record_firma_batch_record_etapa_id_key,
    DROP CONSTRAINT IF EXISTS chk_batch_record_firma_alcance,
    DROP CONSTRAINT IF EXISTS chk_batch_record_firma_decision,
    DROP CONSTRAINT IF EXISTS chk_batch_record_firma_contexto_area;

ALTER TABLE batch_record_firma
    ADD COLUMN seguimiento_evento_id BIGINT NULL
        REFERENCES seguimiento_orden_area_evento(id),
    ADD COLUMN batch_record_revision_id BIGINT NULL
        REFERENCES batch_record_revision(id),
    ADD COLUMN firma_visual_version_id BIGINT NULL
        REFERENCES firma_visual_usuario_version(id);

CREATE UNIQUE INDEX uq_batch_record_firma_evento
    ON batch_record_firma(seguimiento_evento_id)
    WHERE seguimiento_evento_id IS NOT NULL;

CREATE INDEX idx_batch_record_firma_revision
    ON batch_record_firma(batch_record_revision_id, firmado_en)
    WHERE batch_record_revision_id IS NOT NULL;

ALTER TABLE batch_record_firma
    ADD CONSTRAINT chk_batch_record_firma_alcance CHECK (
        alcance IN (
            'CIERRE_ETAPA_AREA', 'CORRECCION_EXPEDIENTE',
            'REVISION_PRODUCCION', 'REVISION_CALIDAD', 'LIBERACION_LOTE'
        )
    ),
    ADD CONSTRAINT chk_batch_record_firma_decision CHECK (
        decision IN ('CONFIRMA', 'APRUEBA', 'RECHAZA', 'DEVUELVE')
    ),
    ADD CONSTRAINT chk_batch_record_firma_contexto CHECK (
        (
            alcance = 'CIERRE_ETAPA_AREA'
            AND batch_record_etapa_id IS NOT NULL
            AND seguimiento_evento_id IS NOT NULL
            AND batch_record_revision_id IS NULL
        )
        OR
        (
            alcance <> 'CIERRE_ETAPA_AREA'
            AND batch_record_etapa_id IS NULL
            AND seguimiento_evento_id IS NULL
            AND batch_record_revision_id IS NOT NULL
        )
    ) NOT VALID;

CREATE TABLE batch_record_correccion (
    id BIGSERIAL PRIMARY KEY,
    batch_record_id BIGINT NOT NULL REFERENCES batch_record(id) ON DELETE RESTRICT,
    batch_record_etapa_id BIGINT NULL REFERENCES batch_record_etapa(id),
    evento_correccion_id BIGINT NOT NULL UNIQUE REFERENCES seguimiento_orden_area_evento(id),
    evento_revertido_id BIGINT NULL REFERENCES seguimiento_orden_area_evento(id),
    valor_anterior VARCHAR(120) NOT NULL,
    valor_nuevo VARCHAR(120) NOT NULL,
    motivo VARCHAR(500) NOT NULL,
    corregida_en TIMESTAMP NOT NULL,
    corregida_por_id BIGINT NOT NULL REFERENCES users(id),
    revision_id BIGINT NULL REFERENCES batch_record_revision(id),
    firma_id BIGINT NULL UNIQUE REFERENCES batch_record_firma(id)
);

CREATE INDEX idx_batch_record_correccion_record
    ON batch_record_correccion(batch_record_id, corregida_en);

CREATE TABLE batch_record_decision_calidad (
    id BIGSERIAL PRIMARY KEY,
    batch_record_id BIGINT NOT NULL REFERENCES batch_record(id) ON DELETE RESTRICT,
    decision VARCHAR(30) NOT NULL,
    motivo VARCHAR(500) NOT NULL,
    decidida_en TIMESTAMP NOT NULL,
    decidida_por_id BIGINT NOT NULL REFERENCES users(id),
    revision_id BIGINT NULL REFERENCES batch_record_revision(id),
    firma_id BIGINT NULL UNIQUE REFERENCES batch_record_firma(id),
    CONSTRAINT chk_batch_record_decision_calidad CHECK (
        decision IN ('LIBERAR', 'RECHAZAR', 'DEVOLVER_A_PRODUCCION')
    )
);

CREATE INDEX idx_batch_record_decision_calidad_record
    ON batch_record_decision_calidad(batch_record_id, decidida_en);

COMMENT ON TABLE batch_record_documento IS
    'Tabla legada de V095. El flujo activo no almacena PDF; usa batch_record_revision y regeneración bajo demanda.';
