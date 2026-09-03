-- Ciclos explícitos de revisión, devolución selectiva y reapertura excepcional.
-- V101 incorpora el motor neutral de controles; esta migración solo agrega
-- la orquestación documental del Batch Record.

ALTER TABLE batch_record
    ADD COLUMN ciclo_revision_actual BIGINT NOT NULL DEFAULT 0;

ALTER TABLE batch_record
    DROP CONSTRAINT IF EXISTS chk_batch_record_estado;

ALTER TABLE batch_record
    ADD CONSTRAINT chk_batch_record_estado CHECK (
        estado IN (
            'BORRADOR',
            'EN_EJECUCION',
            'LISTO_PARA_REVISION',
            'PENDIENTE_REVISION',
            'DEVUELTO_PRODUCCION',
            'EN_CORRECCION',
            'APROBADO',
            'RECHAZADO',
            'CERRADO',
            'ANULADO'
        )
    ),
    ADD CONSTRAINT chk_batch_record_ciclo_revision_actual
        CHECK (ciclo_revision_actual >= 0);

ALTER TABLE batch_record_etapa
    DROP CONSTRAINT IF EXISTS chk_batch_record_etapa_estado;

ALTER TABLE batch_record_etapa
    ADD COLUMN ciclo_correccion_habilitado BIGINT NULL,
    ADD CONSTRAINT chk_batch_record_etapa_estado CHECK (
        estado IN ('PENDIENTE', 'EN_EJECUCION', 'EN_CORRECCION', 'COMPLETADA', 'OMITIDA')
    ),
    ADD CONSTRAINT chk_batch_record_etapa_ciclo_correccion
        CHECK (ciclo_correccion_habilitado IS NULL OR ciclo_correccion_habilitado > 0);

CREATE TABLE batch_record_ciclo_revision (
    id BIGSERIAL PRIMARY KEY,
    batch_record_id BIGINT NOT NULL
        REFERENCES batch_record(id) ON DELETE RESTRICT,
    numero BIGINT NOT NULL,
    origen VARCHAR(40) NOT NULL,
    estado VARCHAR(30) NOT NULL,
    enviado_en TIMESTAMP NOT NULL,
    enviado_por_id BIGINT NOT NULL REFERENCES users(id),
    motivo_envio VARCHAR(500) NOT NULL,
    revision_envio_id BIGINT NULL REFERENCES batch_record_revision(id),
    cerrado_en TIMESTAMP NULL,
    cerrado_por_id BIGINT NULL REFERENCES users(id),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_batch_record_ciclo_numero
        UNIQUE (batch_record_id, numero),
    CONSTRAINT chk_batch_record_ciclo_numero CHECK (numero > 0),
    CONSTRAINT chk_batch_record_ciclo_origen CHECK (
        origen IN ('ENVIO_INICIAL', 'REENVIO', 'REENVIO_TRAS_REAPERTURA')
    ),
    CONSTRAINT chk_batch_record_ciclo_estado CHECK (
        estado IN (
            'EN_REVISION', 'DEVUELTO_PRODUCCION', 'LIBERADO', 'RECHAZADO',
            'MIGRADO_INCOMPLETO'
        )
    ),
    CONSTRAINT chk_batch_record_ciclo_cierre CHECK (
        (estado IN ('EN_REVISION', 'MIGRADO_INCOMPLETO')
            AND cerrado_en IS NULL AND cerrado_por_id IS NULL)
        OR
        (estado NOT IN ('EN_REVISION', 'MIGRADO_INCOMPLETO')
            AND cerrado_en IS NOT NULL AND cerrado_por_id IS NOT NULL
            AND cerrado_en >= enviado_en)
    )
);

CREATE INDEX idx_batch_record_ciclo_record_estado
    ON batch_record_ciclo_revision(batch_record_id, estado, numero DESC);

CREATE TABLE batch_record_seccion_correccion (
    id BIGSERIAL PRIMARY KEY,
    batch_record_id BIGINT NOT NULL
        REFERENCES batch_record(id) ON DELETE RESTRICT,
    ciclo_revision_numero BIGINT NOT NULL,
    seccion VARCHAR(120) NOT NULL,
    estado VARCHAR(20) NOT NULL,
    solicitada_en TIMESTAMP NOT NULL,
    solicitada_por_id BIGINT NOT NULL REFERENCES users(id),
    atendida_en TIMESTAMP NULL,
    atendida_por_id BIGINT NULL REFERENCES users(id),
    justificacion VARCHAR(500) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_batch_record_seccion_ciclo
        UNIQUE (batch_record_id, ciclo_revision_numero, seccion),
    CONSTRAINT chk_batch_record_seccion_ciclo CHECK (ciclo_revision_numero > 0),
    CONSTRAINT chk_batch_record_seccion_estado CHECK (
        estado IN ('PENDIENTE', 'ATENDIDA')
    ),
    CONSTRAINT chk_batch_record_seccion_atencion CHECK (
        (estado = 'PENDIENTE'
            AND atendida_en IS NULL AND atendida_por_id IS NULL AND justificacion IS NULL)
        OR
        (estado = 'ATENDIDA'
            AND atendida_en IS NOT NULL AND atendida_por_id IS NOT NULL
            AND justificacion IS NOT NULL)
    )
);

CREATE INDEX idx_batch_record_seccion_pendiente
    ON batch_record_seccion_correccion(batch_record_id, ciclo_revision_numero, estado);

ALTER TABLE batch_record_decision_calidad
    ADD COLUMN ciclo_revision_id BIGINT NULL
        REFERENCES batch_record_ciclo_revision(id),
    ADD COLUMN alcance_devolucion_json TEXT NULL;

CREATE UNIQUE INDEX uq_batch_record_decision_ciclo
    ON batch_record_decision_calidad(ciclo_revision_id)
    WHERE ciclo_revision_id IS NOT NULL;

-- Reconstrucción determinista: el n-ésimo envío se asocia con la n-ésima
-- decisión histórica. Se preservan actores, fechas, revisión y motivo reales.
WITH envios AS (
    SELECT revision.id AS revision_id,
           revision.batch_record_id,
           revision.tipo AS tipo_revision,
           revision.creada_en AS enviado_en,
           revision.creada_por_id AS enviado_por_id,
           COALESCE(NULLIF(BTRIM(revision.motivo), ''),
                    'Envío histórico migrado al modelo de ciclos') AS motivo_envio,
           ROW_NUMBER() OVER (
               PARTITION BY revision.batch_record_id
               ORDER BY revision.numero, revision.id
           ) AS numero,
           LEAD(revision.creada_en) OVER (
               PARTITION BY revision.batch_record_id
               ORDER BY revision.numero, revision.id
           ) AS siguiente_envio_en,
           COUNT(*) OVER (PARTITION BY revision.batch_record_id) AS total_envios
    FROM batch_record_revision revision
    WHERE revision.tipo IN ('ENVIO_CALIDAD', 'REENVIO_CALIDAD')
), ciclos_migrables AS (
    SELECT envio.*,
           record.estado AS estado_expediente,
           decision.decision_id,
           decision.decision,
           decision.decidida_en,
           decision.decidida_por_id
    FROM envios envio
    JOIN batch_record record ON record.id = envio.batch_record_id
    LEFT JOIN LATERAL (
        SELECT decision.id AS decision_id,
               decision.decision,
               decision.decidida_en,
               decision.decidida_por_id
        FROM batch_record_decision_calidad decision
        WHERE decision.batch_record_id = envio.batch_record_id
          AND decision.decidida_en >= envio.enviado_en
          AND (envio.siguiente_envio_en IS NULL
               OR decision.decidida_en < envio.siguiente_envio_en)
        ORDER BY decision.decidida_en, decision.id
        LIMIT 1
    ) decision ON TRUE
)
INSERT INTO batch_record_ciclo_revision (
    batch_record_id, numero, origen, estado, enviado_en, enviado_por_id,
    motivo_envio, revision_envio_id, cerrado_en, cerrado_por_id, version
)
SELECT ciclo.batch_record_id,
       ciclo.numero,
       CASE
           WHEN ciclo.numero = 1 AND ciclo.tipo_revision = 'ENVIO_CALIDAD'
               THEN 'ENVIO_INICIAL'
           ELSE 'REENVIO'
       END,
       CASE ciclo.decision
           WHEN 'LIBERAR' THEN 'LIBERADO'
           WHEN 'RECHAZAR' THEN 'RECHAZADO'
           WHEN 'DEVOLVER_A_PRODUCCION' THEN 'DEVUELTO_PRODUCCION'
           ELSE CASE
               WHEN ciclo.numero = ciclo.total_envios
                    AND ciclo.estado_expediente = 'PENDIENTE_REVISION'
                   THEN 'EN_REVISION'
               ELSE 'MIGRADO_INCOMPLETO'
           END
       END,
       ciclo.enviado_en,
       ciclo.enviado_por_id,
       ciclo.motivo_envio,
       ciclo.revision_id,
       ciclo.decidida_en,
       ciclo.decidida_por_id,
       0
FROM ciclos_migrables ciclo;

-- Compatibilidad con expedientes que ya tenían fecha de envío pero carecían
-- de snapshot ENVIO_CALIDAD. Solo se infiere un cierre cuando existe decisión.
WITH ultima_decision AS (
    SELECT DISTINCT ON (decision.batch_record_id)
           decision.batch_record_id,
           decision.id AS decision_id,
           decision.decision,
           decision.decidida_en,
           decision.decidida_por_id
    FROM batch_record_decision_calidad decision
    ORDER BY decision.batch_record_id, decision.decidida_en DESC, decision.id DESC
)
INSERT INTO batch_record_ciclo_revision (
    batch_record_id, numero, origen, estado, enviado_en, enviado_por_id,
    motivo_envio, revision_envio_id, cerrado_en, cerrado_por_id, version
)
SELECT record.id,
       1,
       'ENVIO_INICIAL',
       CASE decision.decision
           WHEN 'LIBERAR' THEN 'LIBERADO'
           WHEN 'RECHAZAR' THEN 'RECHAZADO'
           WHEN 'DEVOLVER_A_PRODUCCION' THEN 'DEVUELTO_PRODUCCION'
           ELSE 'EN_REVISION'
       END,
       record.enviado_revision_en,
       record.creado_por_id,
       'Envío histórico sin revisión estructurada; contexto conservado por migración',
       NULL,
       decision.decidida_en,
       decision.decidida_por_id,
       0
FROM batch_record record
LEFT JOIN ultima_decision decision ON decision.batch_record_id = record.id
WHERE record.enviado_revision_en IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM batch_record_ciclo_revision ciclo
      WHERE ciclo.batch_record_id = record.id
  )
  AND (record.estado = 'PENDIENTE_REVISION' OR decision.decision_id IS NOT NULL);

WITH ventanas AS (
    SELECT ciclo.id AS ciclo_id,
           ciclo.batch_record_id,
           ciclo.enviado_en,
           LEAD(ciclo.enviado_en) OVER (
               PARTITION BY ciclo.batch_record_id
               ORDER BY ciclo.numero
           ) AS siguiente_envio_en
    FROM batch_record_ciclo_revision ciclo
), decisiones_mapeadas AS (
    SELECT decision.id AS decision_id,
           ventana.ciclo_id,
           ROW_NUMBER() OVER (
               PARTITION BY ventana.ciclo_id
               ORDER BY decision.decidida_en, decision.id
           ) AS prioridad
    FROM batch_record_decision_calidad decision
    JOIN ventanas ventana
      ON ventana.batch_record_id = decision.batch_record_id
     AND decision.decidida_en >= ventana.enviado_en
     AND (ventana.siguiente_envio_en IS NULL
          OR decision.decidida_en < ventana.siguiente_envio_en)
)
UPDATE batch_record_decision_calidad decision
SET ciclo_revision_id = mapeada.ciclo_id
FROM decisiones_mapeadas mapeada
WHERE decision.id = mapeada.decision_id
  AND mapeada.prioridad = 1;

UPDATE batch_record record
SET ciclo_revision_actual = ciclos.ultimo
FROM (
    SELECT batch_record_id, MAX(numero) AS ultimo
    FROM batch_record_ciclo_revision
    GROUP BY batch_record_id
) ciclos
WHERE record.id = ciclos.batch_record_id;

DO $$
DECLARE
    expedientes_no_inferibles BIGINT;
    ciclos_incompletos BIGINT;
    decisiones_sin_ciclo BIGINT;
BEGIN
    SELECT COUNT(*) INTO expedientes_no_inferibles
    FROM batch_record record
    WHERE record.enviado_revision_en IS NOT NULL
      AND record.ciclo_revision_actual = 0;
    RAISE NOTICE
        'Batch Records históricos enviados sin ciclo inferible (conservados para lectura): %',
        expedientes_no_inferibles;
    SELECT COUNT(*) INTO ciclos_incompletos
    FROM batch_record_ciclo_revision ciclo
    WHERE ciclo.estado = 'MIGRADO_INCOMPLETO';
    RAISE NOTICE
        'Ciclos históricos preservados con cierre no inferible: %',
        ciclos_incompletos;
    SELECT COUNT(*) INTO decisiones_sin_ciclo
    FROM batch_record_decision_calidad decision
    WHERE decision.ciclo_revision_id IS NULL;
    RAISE NOTICE
        'Decisiones históricas sin ciclo temporalmente inferible (conservadas): %',
        decisiones_sin_ciclo;
END $$;

CREATE TABLE batch_record_solicitud_reapertura_rechazo (
    id BIGSERIAL PRIMARY KEY,
    batch_record_id BIGINT NOT NULL
        REFERENCES batch_record(id) ON DELETE RESTRICT,
    ciclo_revision_numero BIGINT NOT NULL,
    estado VARCHAR(20) NOT NULL,
    solicitada_en TIMESTAMP NOT NULL,
    solicitada_por_id BIGINT NOT NULL REFERENCES users(id),
    motivo VARCHAR(500) NOT NULL,
    evidencia TEXT NOT NULL,
    alcance TEXT NOT NULL,
    revision_solicitud_id BIGINT NULL REFERENCES batch_record_revision(id),
    firma_solicitud_id BIGINT NULL UNIQUE REFERENCES batch_record_firma(id),
    aprobada_en TIMESTAMP NULL,
    aprobada_por_id BIGINT NULL REFERENCES users(id),
    motivo_aprobacion VARCHAR(500) NULL,
    revision_aprobacion_id BIGINT NULL REFERENCES batch_record_revision(id),
    firma_aprobacion_id BIGINT NULL UNIQUE REFERENCES batch_record_firma(id),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_batch_record_reapertura_estado CHECK (
        estado IN ('PENDIENTE', 'APROBADA')
    ),
    CONSTRAINT chk_batch_record_reapertura_ciclo CHECK (ciclo_revision_numero > 0),
    CONSTRAINT chk_batch_record_reapertura_aprobacion CHECK (
        (estado = 'PENDIENTE'
            AND aprobada_en IS NULL AND aprobada_por_id IS NULL
            AND motivo_aprobacion IS NULL
            AND revision_aprobacion_id IS NULL AND firma_aprobacion_id IS NULL)
        OR
        (estado = 'APROBADA'
            AND aprobada_en IS NOT NULL AND aprobada_por_id IS NOT NULL
            AND motivo_aprobacion IS NOT NULL
            AND aprobada_en >= solicitada_en
            AND aprobada_por_id <> solicitada_por_id)
    )
);

CREATE UNIQUE INDEX uq_batch_record_reapertura_pendiente
    ON batch_record_solicitud_reapertura_rechazo(batch_record_id)
    WHERE estado = 'PENDIENTE';

CREATE INDEX idx_batch_record_reapertura_record
    ON batch_record_solicitud_reapertura_rechazo(batch_record_id, solicitada_en DESC);

ALTER TABLE batch_record_revision
    DROP CONSTRAINT IF EXISTS chk_batch_record_revision_tipo;

ALTER TABLE batch_record_revision
    ADD CONSTRAINT chk_batch_record_revision_tipo CHECK (
        tipo IN (
            'ENVIO_CALIDAD', 'REENVIO_CALIDAD', 'DECISION_CALIDAD',
            'SOLICITUD_REAPERTURA_RECHAZO', 'REAPERTURA_RECHAZO',
            'ADICION_CONTROL_REQUERIDO',
            'CORRECCION', 'CIERRE'
        )
    );

ALTER TABLE batch_record_firma
    DROP CONSTRAINT IF EXISTS chk_batch_record_firma_alcance,
    DROP CONSTRAINT IF EXISTS chk_batch_record_firma_decision;

ALTER TABLE batch_record_firma
    ADD CONSTRAINT chk_batch_record_firma_alcance CHECK (
        alcance IN (
            'CIERRE_ETAPA_AREA', 'CORRECCION_EXPEDIENTE',
            'REVISION_PRODUCCION', 'REVISION_CALIDAD', 'LIBERACION_LOTE',
            'SOLICITUD_REAPERTURA_RECHAZO', 'APROBACION_REAPERTURA_RECHAZO',
            'ADICION_CONTROL_REQUERIDO'
        )
    ),
    ADD CONSTRAINT chk_batch_record_firma_decision CHECK (
        decision IN (
            'CONFIRMA', 'APRUEBA', 'RECHAZA', 'DEVUELVE', 'SOLICITA', 'REABRE'
        )
    );

COMMENT ON TABLE batch_record_ciclo_revision IS
    'Cada fila conserva un envío independiente a Calidad y su decisión terminal.';

COMMENT ON TABLE batch_record_solicitud_reapertura_rechazo IS
    'Flujo excepcional con solicitante nivel 2 y aprobador nivel 3 distinto.';
