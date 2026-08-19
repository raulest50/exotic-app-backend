-- Ciclo de vida operativo de Orden de Fabricacion (OF).
-- Las evidencias historicas siguen siendo append-only y los PDF se reconstruyen
-- desde las entidades del expediente; esta migracion no almacena binarios.

ALTER TABLE orden_fabricacion
    ADD COLUMN orden_produccion_origen_id INTEGER NULL
        REFERENCES ordenes_produccion(orden_id),
    ADD COLUMN liberada_en TIMESTAMP NULL,
    ADD COLUMN politica_dispensacion_inicio VARCHAR(30) NOT NULL DEFAULT 'BLOQUEANTE',
    ADD COLUMN fecha_aplicacion_politica_dispensacion TIMESTAMP NULL,
    ADD COLUMN estado_dispensacion_materiales VARCHAR(40) NOT NULL DEFAULT 'PENDIENTE';

ALTER TABLE orden_fabricacion
    ADD CONSTRAINT chk_of_politica_dispensacion CHECK (
        politica_dispensacion_inicio IN ('BLOQUEANTE', 'NO_BLOQUEANTE')
    ),
    ADD CONSTRAINT chk_of_estado_dispensacion CHECK (
        estado_dispensacion_materiales IN (
            'PENDIENTE', 'PARCIAL', 'COMPLETA', 'LIBERADA_SIN_DISPENSACION'
        )
    );

CREATE UNIQUE INDEX uq_of_orden_origen_semi
    ON orden_fabricacion(orden_produccion_origen_id, semiterminado_id)
    WHERE orden_produccion_origen_id IS NOT NULL;

CREATE INDEX idx_of_estado_lanzamiento
    ON orden_fabricacion(estado, fecha_lanzamiento);

ALTER TABLE batch_record
    ADD COLUMN requerimientos_materiales_json TEXT NULL;

CREATE TABLE orden_fabricacion_operacion (
    id BIGSERIAL PRIMARY KEY,
    orden_fabricacion_id BIGINT NOT NULL
        REFERENCES orden_fabricacion(orden_fabricacion_id) ON DELETE RESTRICT,
    area_operativa_id INTEGER NOT NULL REFERENCES area_operativa(area_id),
    poe_documento_version_id BIGINT NULL
        REFERENCES proceso_produccion_documento_version(id) ON DELETE RESTRICT,
    frontend_node_id VARCHAR(255) NOT NULL,
    proceso_produccion_id INTEGER NULL,
    proceso_nombre VARCHAR(200) NOT NULL,
    posicion_secuencia INTEGER NOT NULL,
    estado INTEGER NOT NULL,
    fecha_estado_actual TIMESTAMP NOT NULL,
    fecha_visible TIMESTAMP NULL,
    fecha_completado TIMESTAMP NULL,
    usuario_reporta_id BIGINT NULL REFERENCES users(id),
    observaciones VARCHAR(500) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_of_operacion_frontend UNIQUE (orden_fabricacion_id, frontend_node_id),
    CONSTRAINT uq_of_operacion_secuencia UNIQUE (orden_fabricacion_id, posicion_secuencia),
    CONSTRAINT chk_of_operacion_estado CHECK (estado IN (0, 1, 2, 3, 4)),
    CONSTRAINT chk_of_operacion_secuencia CHECK (posicion_secuencia >= 0)
);

CREATE INDEX idx_of_operacion_area_estado
    ON orden_fabricacion_operacion(area_operativa_id, estado, fecha_estado_actual);

CREATE TABLE orden_fabricacion_operacion_dependencia (
    id BIGSERIAL PRIMARY KEY,
    operacion_predecesora_id BIGINT NOT NULL
        REFERENCES orden_fabricacion_operacion(id) ON DELETE RESTRICT,
    operacion_sucesora_id BIGINT NOT NULL
        REFERENCES orden_fabricacion_operacion(id) ON DELETE RESTRICT,
    CONSTRAINT uq_of_operacion_dependencia UNIQUE (
        operacion_predecesora_id, operacion_sucesora_id
    ),
    CONSTRAINT chk_of_operacion_dependencia_distinta CHECK (
        operacion_predecesora_id <> operacion_sucesora_id
    )
);

CREATE INDEX idx_of_operacion_dependencia_sucesora
    ON orden_fabricacion_operacion_dependencia(operacion_sucesora_id);

CREATE TABLE orden_fabricacion_operacion_evento (
    id BIGSERIAL PRIMARY KEY,
    operacion_id BIGINT NOT NULL
        REFERENCES orden_fabricacion_operacion(id) ON DELETE RESTRICT,
    estado_origen INTEGER NULL,
    estado_destino INTEGER NOT NULL,
    fecha_evento TIMESTAMP NOT NULL,
    actor_tipo VARCHAR(16) NOT NULL,
    tipo_evento VARCHAR(32) NOT NULL,
    evento_revertido_id BIGINT NULL
        REFERENCES orden_fabricacion_operacion_evento(id) ON DELETE RESTRICT,
    usuario_id BIGINT NULL REFERENCES users(id),
    nota VARCHAR(500) NULL,
    CONSTRAINT chk_of_operacion_evento_estados CHECK (
        (estado_origen IS NULL OR estado_origen IN (0, 1, 2, 3, 4))
        AND estado_destino IN (0, 1, 2, 3, 4)
    ),
    CONSTRAINT chk_of_operacion_evento_actor CHECK (
        actor_tipo IN ('SYSTEM', 'USER')
    ),
    CONSTRAINT chk_of_operacion_evento_tipo CHECK (
        tipo_evento IN ('OPERATIVO', 'SISTEMA', 'CORRECCION_ADMINISTRATIVA')
    )
);

CREATE INDEX idx_of_operacion_evento_operacion
    ON orden_fabricacion_operacion_evento(operacion_id, fecha_evento, id);

ALTER TABLE batch_record_etapa
    ADD COLUMN orden_fabricacion_operacion_id BIGINT NULL
        REFERENCES orden_fabricacion_operacion(id),
    ADD COLUMN orden_fabricacion_evento_origen_id BIGINT NULL
        REFERENCES orden_fabricacion_operacion_evento(id);

CREATE UNIQUE INDEX uq_batch_record_etapa_of_operacion
    ON batch_record_etapa(orden_fabricacion_operacion_id)
    WHERE orden_fabricacion_operacion_id IS NOT NULL;

CREATE UNIQUE INDEX uq_batch_record_etapa_of_evento
    ON batch_record_etapa(orden_fabricacion_evento_origen_id)
    WHERE orden_fabricacion_evento_origen_id IS NOT NULL;

ALTER TABLE batch_record_firma
    ADD COLUMN orden_fabricacion_evento_id BIGINT NULL
        REFERENCES orden_fabricacion_operacion_evento(id);

CREATE UNIQUE INDEX uq_batch_record_firma_of_evento
    ON batch_record_firma(orden_fabricacion_evento_id)
    WHERE orden_fabricacion_evento_id IS NOT NULL;

ALTER TABLE batch_record_firma
    DROP CONSTRAINT IF EXISTS chk_batch_record_firma_contexto;

ALTER TABLE batch_record_firma
    ADD CONSTRAINT chk_batch_record_firma_contexto CHECK (
        (
            alcance = 'CIERRE_ETAPA_AREA'
            AND batch_record_etapa_id IS NOT NULL
            AND num_nonnulls(seguimiento_evento_id, orden_fabricacion_evento_id) = 1
            AND batch_record_revision_id IS NULL
        )
        OR
        (
            alcance <> 'CIERRE_ETAPA_AREA'
            AND batch_record_etapa_id IS NULL
            AND seguimiento_evento_id IS NULL
            AND orden_fabricacion_evento_id IS NULL
            AND batch_record_revision_id IS NOT NULL
        )
    ) NOT VALID;

ALTER TABLE batch_record_correccion
    ALTER COLUMN evento_correccion_id DROP NOT NULL,
    ADD COLUMN orden_fabricacion_evento_correccion_id BIGINT NULL
        REFERENCES orden_fabricacion_operacion_evento(id),
    ADD COLUMN orden_fabricacion_evento_revertido_id BIGINT NULL
        REFERENCES orden_fabricacion_operacion_evento(id);

CREATE UNIQUE INDEX uq_batch_record_correccion_of_evento
    ON batch_record_correccion(orden_fabricacion_evento_correccion_id)
    WHERE orden_fabricacion_evento_correccion_id IS NOT NULL;

ALTER TABLE batch_record_correccion
    ADD CONSTRAINT chk_batch_record_correccion_un_origen CHECK (
        num_nonnulls(evento_correccion_id, orden_fabricacion_evento_correccion_id) = 1
    );

-- Se agregan al final para conservar los ordinales historicos:
-- OD_OF=11 y OF=12. Esto tambien corrige el limite latente para DVC=9/DVP=10.
ALTER TABLE transaccion_almacen
    DROP CONSTRAINT IF EXISTS transaccion_almacen_tipo_entidad_causante_check;

ALTER TABLE transaccion_almacen
    ADD CONSTRAINT transaccion_almacen_tipo_entidad_causante_check
        CHECK (tipo_entidad_causante >= 0 AND tipo_entidad_causante <= 12);

ALTER TABLE lote
    DROP CONSTRAINT IF EXISTS chk_lote_estado_calidad;

ALTER TABLE lote
    ADD CONSTRAINT chk_lote_estado_calidad CHECK (
        estado_calidad IN (
            'SIN_CLASIFICAR', 'CUARENTENA', 'APROBADO', 'LIBERADO',
            'RECHAZADO', 'BLOQUEADO', 'NO_APLICA_CALIDAD'
        )
    );

-- Backfill conservador. Materializa la proyeccion de ordenes legadas, pero no
-- inventa ejecuciones, firmas ni POE historicos.
WITH nodos AS (
    SELECT ofa.orden_fabricacion_id,
           br.id AS batch_record_id,
           nodo.value AS nodo,
           nodo.ordinality - 1 AS secuencia
    FROM orden_fabricacion ofa
    JOIN manufacturing_versions mv ON mv.id = ofa.manufacturing_version_id
    JOIN batch_record br ON br.orden_fabricacion_id = ofa.orden_fabricacion_id
    CROSS JOIN LATERAL jsonb_array_elements(
        COALESCE(
            (
                NULLIF(
                    BTRIM(convert_from(lo_get(mv.proceso_produccion_json), 'UTF8')),
                    ''
                )::jsonb
            )->'nodes',
            '[]'::jsonb
        )
    ) WITH ORDINALITY AS nodo(value, ordinality)
    WHERE ofa.estado <> 'CANCELADA'
      AND UPPER(COALESCE(nodo.value->>'nodeType', '')) = 'PROCESO'
      AND nodo.value ? 'areaOperativaId'
      AND COALESCE(nodo.value->>'areaOperativaId', '') ~ '^-?[0-9]+$'
      AND (nodo.value->>'areaOperativaId')::INTEGER <> -1
      AND NOT EXISTS (
          SELECT 1 FROM orden_fabricacion_operacion existente
          WHERE existente.orden_fabricacion_id = ofa.orden_fabricacion_id
      )
), nodos_numerados AS (
    SELECT nodos.*,
           ROW_NUMBER() OVER (
               PARTITION BY orden_fabricacion_id ORDER BY secuencia
           ) - 1 AS secuencia_proceso
    FROM nodos
)
INSERT INTO orden_fabricacion_operacion (
    orden_fabricacion_id, area_operativa_id, frontend_node_id,
    proceso_produccion_id, proceso_nombre, posicion_secuencia, estado,
    fecha_estado_actual, version
)
SELECT n.orden_fabricacion_id,
       (n.nodo->>'areaOperativaId')::INTEGER,
       COALESCE(NULLIF(n.nodo->>'frontendId', ''),
                'legacy-of-' || n.secuencia_proceso::TEXT),
       CASE
           WHEN COALESCE(n.nodo->>'procesoId', '') ~ '^[0-9]+$'
               THEN (n.nodo->>'procesoId')::INTEGER
           ELSE NULL
       END,
       LEFT(COALESCE(NULLIF(n.nodo->>'procesoNombre', ''),
                     NULLIF(n.nodo->>'label', ''), 'Etapa'), 200),
       n.secuencia_proceso,
       0,
       CURRENT_TIMESTAMP,
       0
FROM nodos_numerados n;

-- Vincula por el orden determinista que uso la implementacion inicial.
WITH etapas AS (
    SELECT bre.id,
           br.orden_fabricacion_id,
           ROW_NUMBER() OVER (
               PARTITION BY br.orden_fabricacion_id ORDER BY bre.secuencia, bre.id
           ) - 1 AS secuencia
    FROM batch_record_etapa bre
    JOIN batch_record br ON br.id = bre.batch_record_id
    WHERE br.orden_fabricacion_id IS NOT NULL
      AND bre.orden_fabricacion_operacion_id IS NULL
)
UPDATE batch_record_etapa bre
SET orden_fabricacion_operacion_id = operacion.id
FROM etapas etapa
JOIN orden_fabricacion_operacion operacion
  ON operacion.orden_fabricacion_id = etapa.orden_fabricacion_id
 AND operacion.posicion_secuencia = etapa.secuencia
WHERE bre.id = etapa.id;

-- Exige un unico origen operativo para filas nuevas. Se deja NOT VALID para
-- no atribuir artificialmente un seguimiento a expedientes historicos que no
-- puedan vincularse sin ambiguedad.
ALTER TABLE batch_record_etapa
    ADD CONSTRAINT chk_batch_record_etapa_un_origen_operativo CHECK (
        num_nonnulls(
            seguimiento_orden_area_id,
            orden_fabricacion_operacion_id
        ) = 1
    ) NOT VALID;

COMMENT ON COLUMN batch_record.requerimientos_materiales_json IS
    'Requerimientos normalizados y congelados al emitir la orden; Disp V2 no recalcula recetas vigentes.';
