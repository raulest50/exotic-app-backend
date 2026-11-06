-- Despeje de linea: evidencia operativa propia, anterior al arranque de una etapa.
-- No reutiliza los controles PROCESO ni convierte el Batch Record en un bloqueo.
-- Las areas existentes permanecen deshabilitadas y sin antecedentes atribuidos.

CREATE TABLE despeje_plantilla_version (
    id BIGSERIAL PRIMARY KEY,
    area_operativa_id INTEGER NOT NULL REFERENCES area_operativa(area_id) ON DELETE RESTRICT,
    numero INTEGER NOT NULL,
    nombre VARCHAR(200) NOT NULL,
    estado VARCHAR(20) NOT NULL DEFAULT 'BORRADOR',
    motivo_cambio TEXT NOT NULL,
    creada_en TIMESTAMP NOT NULL,
    creada_por_id BIGINT NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    vigente_desde TIMESTAMP NULL,
    vigente_hasta TIMESTAMP NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_despeje_plantilla_numero UNIQUE (area_operativa_id, numero),
    CONSTRAINT uq_despeje_plantilla_area UNIQUE (id, area_operativa_id),
    CONSTRAINT chk_despeje_plantilla_numero CHECK (numero > 0),
    CONSTRAINT chk_despeje_plantilla_estado CHECK (
        estado IN ('BORRADOR', 'VIGENTE', 'RETIRADA')
    ),
    CONSTRAINT chk_despeje_plantilla_vigencia CHECK (
        (estado = 'BORRADOR' AND vigente_desde IS NULL AND vigente_hasta IS NULL)
        OR (estado = 'VIGENTE' AND vigente_desde IS NOT NULL AND vigente_hasta IS NULL)
        OR (estado = 'RETIRADA' AND vigente_desde IS NOT NULL
            AND vigente_hasta IS NOT NULL AND vigente_hasta >= vigente_desde)
    )
);

CREATE UNIQUE INDEX uq_despeje_plantilla_vigente
    ON despeje_plantilla_version(area_operativa_id) WHERE estado = 'VIGENTE';
CREATE UNIQUE INDEX uq_despeje_plantilla_borrador
    ON despeje_plantilla_version(area_operativa_id) WHERE estado = 'BORRADOR';

CREATE TABLE despeje_plantilla_item (
    id BIGSERIAL PRIMARY KEY,
    plantilla_id BIGINT NOT NULL
        REFERENCES despeje_plantilla_version(id) ON DELETE RESTRICT,
    orden INTEGER NOT NULL,
    texto VARCHAR(1000) NOT NULL,
    tipo VARCHAR(20) NOT NULL,
    obligatorio BOOLEAN NOT NULL DEFAULT TRUE,
    permite_na BOOLEAN NOT NULL DEFAULT FALSE,
    respuesta_conforme VARCHAR(2) NULL,
    minimo NUMERIC(24,8) NULL,
    maximo NUMERIC(24,8) NULL,
    CONSTRAINT uq_despeje_item_orden UNIQUE (plantilla_id, orden),
    CONSTRAINT uq_despeje_item_version UNIQUE (id, plantilla_id),
    CONSTRAINT chk_despeje_item_orden CHECK (orden > 0),
    CONSTRAINT chk_despeje_item_tipo CHECK (tipo IN ('SI_NO_NA', 'NUMERO')),
    CONSTRAINT chk_despeje_item_respuesta CHECK (
        respuesta_conforme IS NULL OR respuesta_conforme IN ('SI', 'NO', 'NA')
    ),
    CONSTRAINT chk_despeje_item_configuracion CHECK (
        (tipo = 'SI_NO_NA' AND minimo IS NULL AND maximo IS NULL
            AND respuesta_conforme IS NOT NULL AND respuesta_conforme IN ('SI', 'NO'))
        OR (tipo = 'NUMERO' AND respuesta_conforme IS NULL AND NOT permite_na)
    ),
    CONSTRAINT chk_despeje_item_limites CHECK (
        minimo IS NULL OR maximo IS NULL OR minimo <= maximo
    )
);

CREATE TABLE despeje_linea_ejecucion (
    id BIGSERIAL PRIMARY KEY,
    plantilla_id BIGINT NOT NULL,
    area_operativa_id INTEGER NOT NULL REFERENCES area_operativa(area_id) ON DELETE RESTRICT,
    area_nombre VARCHAR(200) NOT NULL,
    seguimiento_id BIGINT NULL REFERENCES seguimiento_orden_area(id) ON DELETE RESTRICT,
    operacion_id BIGINT NULL REFERENCES orden_fabricacion_operacion(id) ON DELETE RESTRICT,
    intento INTEGER NOT NULL,
    revision_contexto BIGINT NOT NULL,
    estado VARCHAR(30) NOT NULL DEFAULT 'BORRADOR',
    requiere_revision BOOLEAN NOT NULL DEFAULT FALSE,
    lote_actual_id BIGINT NOT NULL REFERENCES lote(id) ON DELETE RESTRICT,
    lote_actual_numero VARCHAR(255) NOT NULL,
    lote_anterior_id BIGINT NULL REFERENCES lote(id) ON DELETE RESTRICT,
    lote_anterior_numero VARCHAR(255) NULL,
    antecedente_conocido BOOLEAN NOT NULL DEFAULT FALSE,
    confirma_antecedente_desconocido BOOLEAN NOT NULL DEFAULT FALSE,
    observacion_antecedente TEXT NULL,
    observaciones TEXT NULL,
    motivo_revision TEXT NULL,
    motivo_invalidacion TEXT NULL,
    contexto_json TEXT NOT NULL,
    creada_en TIMESTAMP NOT NULL,
    enviada_en TIMESTAMP NULL,
    revisada_en TIMESTAMP NULL,
    utilizada_en TIMESTAMP NULL,
    creada_por_id BIGINT NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    creada_por_username VARCHAR(120) NOT NULL,
    creada_por_nombre VARCHAR(200) NOT NULL,
    enviada_por_id BIGINT NULL REFERENCES users(id) ON DELETE RESTRICT,
    enviada_por_username VARCHAR(120) NULL,
    enviada_por_nombre VARCHAR(200) NULL,
    revisada_por_id BIGINT NULL REFERENCES users(id) ON DELETE RESTRICT,
    revisada_por_username VARCHAR(120) NULL,
    revisada_por_nombre VARCHAR(200) NULL,
    contenido_sha256 VARCHAR(64) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_despeje_ejecucion_version UNIQUE (id, plantilla_id),
    CONSTRAINT uq_despeje_ejecucion_area UNIQUE (id, area_operativa_id),
    CONSTRAINT fk_despeje_ejecucion_plantilla_area
        FOREIGN KEY (plantilla_id, area_operativa_id)
        REFERENCES despeje_plantilla_version(id, area_operativa_id) ON DELETE RESTRICT,
    CONSTRAINT chk_despeje_ejecucion_origen CHECK (
        num_nonnulls(seguimiento_id, operacion_id) = 1
    ),
    CONSTRAINT chk_despeje_ejecucion_intento CHECK (intento > 0),
    CONSTRAINT chk_despeje_ejecucion_contexto CHECK (revision_contexto >= 0),
    CONSTRAINT chk_despeje_ejecucion_estado CHECK (
        estado IN ('BORRADOR', 'PENDIENTE_REVISION', 'APROBADA',
                   'RECHAZADA', 'INVALIDADA', 'UTILIZADA')
    ),
    CONSTRAINT chk_despeje_ejecucion_envio CHECK (
        num_nonnulls(enviada_en, enviada_por_id) IN (0, 2)
    ),
    CONSTRAINT chk_despeje_ejecucion_revision CHECK (
        num_nonnulls(revisada_en, revisada_por_id) IN (0, 2)
    ),
    CONSTRAINT chk_despeje_ejecucion_uso CHECK (
        estado <> 'UTILIZADA' OR utilizada_en IS NOT NULL
    )
);

CREATE UNIQUE INDEX uq_despeje_ejecucion_intento_op
    ON despeje_linea_ejecucion(seguimiento_id, intento) WHERE seguimiento_id IS NOT NULL;
CREATE UNIQUE INDEX uq_despeje_ejecucion_intento_of
    ON despeje_linea_ejecucion(operacion_id, intento) WHERE operacion_id IS NOT NULL;
CREATE INDEX idx_despeje_ejecucion_area_estado
    ON despeje_linea_ejecucion(area_operativa_id, estado, creada_en, id);
CREATE INDEX idx_despeje_ejecucion_lote
    ON despeje_linea_ejecucion(lote_actual_id, creada_en, id);

CREATE TABLE despeje_respuesta (
    id BIGSERIAL PRIMARY KEY,
    ejecucion_id BIGINT NOT NULL,
    item_id BIGINT NOT NULL,
    plantilla_id BIGINT NOT NULL
        REFERENCES despeje_plantilla_version(id) ON DELETE RESTRICT,
    valor VARCHAR(2) NULL,
    numero NUMERIC(24,8) NULL,
    observacion TEXT NULL,
    CONSTRAINT uq_despeje_respuesta_item UNIQUE (ejecucion_id, item_id),
    CONSTRAINT fk_despeje_respuesta_ejecucion_version
        FOREIGN KEY (ejecucion_id, plantilla_id)
        REFERENCES despeje_linea_ejecucion(id, plantilla_id) ON DELETE RESTRICT,
    CONSTRAINT fk_despeje_respuesta_item_version
        FOREIGN KEY (item_id, plantilla_id)
        REFERENCES despeje_plantilla_item(id, plantilla_id) ON DELETE RESTRICT,
    CONSTRAINT chk_despeje_respuesta_valor CHECK (
        valor IS NULL OR valor IN ('SI', 'NO', 'NA')
    ),
    CONSTRAINT chk_despeje_respuesta_un_tipo CHECK (num_nonnulls(valor, numero) <= 1)
);

CREATE INDEX idx_despeje_respuesta_item ON despeje_respuesta(item_id, plantilla_id);

ALTER TABLE area_operativa
    ADD COLUMN requiere_despeje BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN requiere_revision_despeje BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN despeje_linea_exclusiva_confirmada BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN despeje_revision_contexto BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN despeje_antecedente_conocido BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN despeje_ultimo_lote_id BIGINT NULL REFERENCES lote(id) ON DELETE RESTRICT,
    ADD COLUMN despeje_ultimo_inicio_en TIMESTAMP NULL,
    ADD COLUMN despeje_ocupante_seguimiento_id BIGINT NULL
        REFERENCES seguimiento_orden_area(id) ON DELETE RESTRICT,
    ADD COLUMN despeje_ocupante_operacion_id BIGINT NULL
        REFERENCES orden_fabricacion_operacion(id) ON DELETE RESTRICT,
    ADD COLUMN despeje_ejecucion_actual_id BIGINT NULL,
    ADD COLUMN despeje_version BIGINT NOT NULL DEFAULT 0,
    ADD CONSTRAINT fk_area_despeje_ejecucion_actual
        FOREIGN KEY (despeje_ejecucion_actual_id, area_id)
        REFERENCES despeje_linea_ejecucion(id, area_operativa_id) ON DELETE RESTRICT,
    ADD CONSTRAINT chk_area_despeje_contexto CHECK (despeje_revision_contexto >= 0),
    ADD CONSTRAINT chk_area_despeje_exclusividad CHECK (
        NOT requiere_despeje OR despeje_linea_exclusiva_confirmada
    ),
    ADD CONSTRAINT chk_area_despeje_ocupante CHECK (
        num_nonnulls(despeje_ocupante_seguimiento_id, despeje_ocupante_operacion_id) <= 1
    ),
    ADD CONSTRAINT chk_area_despeje_ejecucion_ocupante CHECK (
        despeje_ejecucion_actual_id IS NULL
        OR num_nonnulls(despeje_ocupante_seguimiento_id, despeje_ocupante_operacion_id) = 1
    );

COMMENT ON TABLE despeje_linea_ejecucion IS
    'Intento operativo de despeje de una etapa OP u OF; conserva la version, el contexto y las identidades documentales.';
COMMENT ON TABLE despeje_respuesta IS
    'Las FK compuestas impiden responder con un item de una version distinta a la de la ejecucion.';
COMMENT ON COLUMN area_operativa.requiere_despeje IS
    'Activacion explicita por linea fisica exclusiva; FALSE preserva la operacion existente.';
COMMENT ON COLUMN area_operativa.despeje_revision_contexto IS
    'Revision monotona del contexto fisico para detectar intentos obsoletos; no equivale a la version tecnica JPA.';
COMMENT ON COLUMN area_operativa.despeje_antecedente_conocido IS
    'FALSE indica antecedente desconocido; la migracion no atribuye lotes, despejes ni aprobaciones historicas.';
