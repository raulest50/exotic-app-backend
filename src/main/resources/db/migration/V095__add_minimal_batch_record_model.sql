-- Modelo mínimo de órdenes de fabricación y expediente digital de lote.
-- Esta migración no activa flujos ni crea expedientes históricos: únicamente
-- incorpora las invariantes y relaciones necesarias para implementarlos.

ALTER TABLE productos
    ADD COLUMN requiere_orden_fabricacion BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE orden_fabricacion (
    orden_fabricacion_id BIGSERIAL PRIMARY KEY,
    semiterminado_id VARCHAR(255) NOT NULL REFERENCES productos(producto_id),
    manufacturing_version_id BIGINT NOT NULL REFERENCES manufacturing_versions(id),
    estado VARCHAR(30) NOT NULL,
    cantidad_planificada NUMERIC(18, 4) NOT NULL,
    unidad_medida VARCHAR(20) NOT NULL,
    fecha_creacion TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_lanzamiento TIMESTAMP NULL,
    fecha_final_planificada TIMESTAMP NULL,
    fecha_inicio TIMESTAMP NULL,
    fecha_final TIMESTAMP NULL,
    creada_por_id BIGINT NOT NULL REFERENCES users(id),
    responsable_id BIGINT NULL REFERENCES users(id),
    observaciones TEXT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_orden_fabricacion_estado CHECK (
        estado IN (
            'BORRADOR',
            'PLANIFICADA',
            'LIBERADA',
            'EN_EJECUCION',
            'FABRICACION_COMPLETADA',
            'CERRADA',
            'CANCELADA'
        )
    ),
    CONSTRAINT chk_orden_fabricacion_cantidad CHECK (cantidad_planificada > 0),
    CONSTRAINT chk_orden_fabricacion_fechas_plan CHECK (
        fecha_final_planificada IS NULL
        OR fecha_lanzamiento IS NULL
        OR fecha_final_planificada >= fecha_lanzamiento
    ),
    CONSTRAINT chk_orden_fabricacion_fechas_reales CHECK (
        fecha_final IS NULL
        OR fecha_inicio IS NULL
        OR fecha_final >= fecha_inicio
    )
);

CREATE INDEX idx_orden_fabricacion_producto_estado
    ON orden_fabricacion(semiterminado_id, estado);

CREATE INDEX idx_orden_fabricacion_fechas
    ON orden_fabricacion(fecha_lanzamiento, fecha_final_planificada);

ALTER TABLE ordenes_produccion
    ADD COLUMN manufacturing_version_id BIGINT NULL
        REFERENCES manufacturing_versions(id);

CREATE INDEX idx_orden_produccion_manufacturing_version
    ON ordenes_produccion(manufacturing_version_id);

ALTER TABLE lote
    ADD COLUMN producto_id VARCHAR(255) NULL REFERENCES productos(producto_id),
    ADD COLUMN estado_calidad VARCHAR(30) NOT NULL DEFAULT 'SIN_CLASIFICAR',
    ADD COLUMN orden_fabricacion_id BIGINT NULL REFERENCES orden_fabricacion(orden_fabricacion_id);

-- La asociación explícita se completa cuando puede inferirse sin ambigüedad.
UPDATE lote lote_actual
SET producto_id = orden.producto_id
FROM ordenes_produccion orden
WHERE lote_actual.producto_id IS NULL
  AND lote_actual.orden_produccion_id = orden.orden_id;

WITH producto_unico_por_lote AS (
    SELECT movimiento.lote_id, MIN(movimiento.producto_id) AS producto_id
    FROM movimientos movimiento
    WHERE movimiento.lote_id IS NOT NULL
      AND movimiento.producto_id IS NOT NULL
    GROUP BY movimiento.lote_id
    HAVING COUNT(DISTINCT movimiento.producto_id) = 1
)
UPDATE lote lote_actual
SET producto_id = inferido.producto_id
FROM producto_unico_por_lote inferido
WHERE lote_actual.producto_id IS NULL
  AND lote_actual.id = inferido.lote_id;

ALTER TABLE lote
    ADD CONSTRAINT chk_lote_estado_calidad CHECK (
        estado_calidad IN ('SIN_CLASIFICAR', 'CUARENTENA', 'APROBADO', 'LIBERADO', 'RECHAZADO', 'BLOQUEADO')
    ),
    ADD CONSTRAINT chk_lote_un_origen
        CHECK (num_nonnulls(orden_compra_id, orden_produccion_id, orden_fabricacion_id) <= 1)
        NOT VALID;

CREATE INDEX idx_lote_producto_estado_calidad
    ON lote(producto_id, estado_calidad);

CREATE INDEX idx_lote_orden_fabricacion
    ON lote(orden_fabricacion_id);

CREATE TABLE batch_record (
    id BIGSERIAL PRIMARY KEY,
    codigo VARCHAR(60) NOT NULL UNIQUE,
    orden_produccion_id INTEGER NULL UNIQUE REFERENCES ordenes_produccion(orden_id),
    orden_fabricacion_id BIGINT NULL UNIQUE REFERENCES orden_fabricacion(orden_fabricacion_id),
    lote_resultado_id BIGINT NOT NULL UNIQUE REFERENCES lote(id),
    producto_resultado_id VARCHAR(255) NOT NULL REFERENCES productos(producto_id),
    manufacturing_version_id BIGINT NOT NULL REFERENCES manufacturing_versions(id),
    estado VARCHAR(30) NOT NULL,
    revision_documental INTEGER NOT NULL DEFAULT 1,
    cantidad_planificada NUMERIC(18, 4) NOT NULL,
    cantidad_obtenida NUMERIC(18, 4) NULL,
    unidad_medida VARCHAR(20) NOT NULL,
    contenido_sha256 VARCHAR(64) NULL,
    creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    creado_por_id BIGINT NOT NULL REFERENCES users(id),
    iniciado_en TIMESTAMP NULL,
    enviado_revision_en TIMESTAMP NULL,
    cerrado_en TIMESTAMP NULL,
    observaciones TEXT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_batch_record_una_orden CHECK (
        num_nonnulls(orden_produccion_id, orden_fabricacion_id) = 1
    ),
    CONSTRAINT chk_batch_record_estado CHECK (
        estado IN (
            'BORRADOR',
            'EN_EJECUCION',
            'PENDIENTE_REVISION',
            'APROBADO',
            'RECHAZADO',
            'CERRADO',
            'ANULADO'
        )
    ),
    CONSTRAINT chk_batch_record_revision CHECK (revision_documental > 0),
    CONSTRAINT chk_batch_record_cantidad_planificada CHECK (cantidad_planificada > 0),
    CONSTRAINT chk_batch_record_cantidad_obtenida CHECK (
        cantidad_obtenida IS NULL OR cantidad_obtenida >= 0
    ),
    CONSTRAINT chk_batch_record_hash CHECK (
        contenido_sha256 IS NULL OR contenido_sha256 ~ '^[0-9A-Fa-f]{64}$'
    ),
    CONSTRAINT chk_batch_record_revision_contenido CHECK (
        estado NOT IN ('PENDIENTE_REVISION', 'APROBADO', 'RECHAZADO', 'CERRADO')
        OR (cantidad_obtenida IS NOT NULL AND contenido_sha256 IS NOT NULL)
    ),
    CONSTRAINT chk_batch_record_fechas_revision CHECK (
        enviado_revision_en IS NULL OR iniciado_en IS NULL OR enviado_revision_en >= iniciado_en
    ),
    CONSTRAINT chk_batch_record_fechas_cierre CHECK (
        cerrado_en IS NULL OR enviado_revision_en IS NULL OR cerrado_en >= enviado_revision_en
    ),
    CONSTRAINT chk_batch_record_cerrado CHECK (
        estado <> 'CERRADO' OR cerrado_en IS NOT NULL
    )
);

CREATE INDEX idx_batch_record_estado
    ON batch_record(estado, creado_en);

CREATE INDEX idx_batch_record_producto
    ON batch_record(producto_resultado_id, creado_en);

CREATE TABLE batch_record_consumo (
    id BIGSERIAL PRIMARY KEY,
    batch_record_id BIGINT NOT NULL REFERENCES batch_record(id) ON DELETE RESTRICT,
    producto_id VARCHAR(255) NOT NULL REFERENCES productos(producto_id),
    lote_origen_id BIGINT NULL REFERENCES lote(id),
    movimiento_id INTEGER NULL UNIQUE REFERENCES movimientos(movimiento_id),
    cantidad NUMERIC(18, 4) NOT NULL,
    unidad_medida VARCHAR(20) NOT NULL,
    registrado_en TIMESTAMP NOT NULL,
    registrado_por_id BIGINT NOT NULL REFERENCES users(id),
    observaciones VARCHAR(500) NULL,
    CONSTRAINT chk_batch_record_consumo_cantidad CHECK (cantidad > 0)
);

CREATE INDEX idx_batch_record_consumo_record
    ON batch_record_consumo(batch_record_id, registrado_en);

CREATE INDEX idx_batch_record_consumo_genealogia
    ON batch_record_consumo(lote_origen_id, batch_record_id)
    WHERE lote_origen_id IS NOT NULL;

CREATE TABLE batch_record_etapa (
    id BIGSERIAL PRIMARY KEY,
    batch_record_id BIGINT NOT NULL REFERENCES batch_record(id) ON DELETE RESTRICT,
    area_operativa_id INTEGER NOT NULL REFERENCES area_operativa(area_id),
    seguimiento_evento_origen_id BIGINT NULL UNIQUE REFERENCES seguimiento_orden_area_evento(id),
    nombre VARCHAR(200) NOT NULL,
    secuencia INTEGER NOT NULL,
    estado VARCHAR(20) NOT NULL,
    iniciada_en TIMESTAMP NULL,
    completada_en TIMESTAMP NULL,
    reportada_por_id BIGINT NULL REFERENCES users(id),
    contenido_sha256 VARCHAR(64) NULL,
    observaciones TEXT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_batch_record_etapa_secuencia UNIQUE (batch_record_id, secuencia),
    CONSTRAINT chk_batch_record_etapa_secuencia CHECK (secuencia >= 0),
    CONSTRAINT chk_batch_record_etapa_estado CHECK (
        estado IN ('PENDIENTE', 'EN_EJECUCION', 'COMPLETADA', 'OMITIDA')
    ),
    CONSTRAINT chk_batch_record_etapa_fechas CHECK (
        completada_en IS NULL OR iniciada_en IS NULL OR completada_en >= iniciada_en
    ),
    CONSTRAINT chk_batch_record_etapa_completada CHECK (
        estado <> 'COMPLETADA'
        OR (iniciada_en IS NOT NULL AND completada_en IS NOT NULL AND reportada_por_id IS NOT NULL)
    ),
    CONSTRAINT chk_batch_record_etapa_hash CHECK (
        contenido_sha256 IS NULL OR contenido_sha256 ~ '^[0-9A-Fa-f]{64}$'
    )
);

CREATE INDEX idx_batch_record_etapa_record
    ON batch_record_etapa(batch_record_id, secuencia);

CREATE INDEX idx_batch_record_etapa_area_estado
    ON batch_record_etapa(area_operativa_id, estado);

ALTER TABLE calidad_control_proceso_ejecucion
    ADD COLUMN batch_record_id BIGINT NULL REFERENCES batch_record(id);

CREATE INDEX idx_calidad_control_ejecucion_batch_record
    ON calidad_control_proceso_ejecucion(batch_record_id);

CREATE TABLE batch_record_desviacion (
    id BIGSERIAL PRIMARY KEY,
    batch_record_id BIGINT NOT NULL REFERENCES batch_record(id) ON DELETE RESTRICT,
    batch_record_etapa_id BIGINT NULL REFERENCES batch_record_etapa(id),
    codigo VARCHAR(60) NOT NULL,
    descripcion TEXT NOT NULL,
    estado VARCHAR(30) NOT NULL,
    detectada_en TIMESTAMP NOT NULL,
    detectada_por_id BIGINT NOT NULL REFERENCES users(id),
    evaluacion_impacto TEXT NULL,
    resolucion TEXT NULL,
    resuelta_en TIMESTAMP NULL,
    resuelta_por_id BIGINT NULL REFERENCES users(id),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_batch_record_desviacion_codigo UNIQUE (batch_record_id, codigo),
    CONSTRAINT chk_batch_record_desviacion_estado CHECK (
        estado IN ('ABIERTA', 'EN_INVESTIGACION', 'RESUELTA', 'CERRADA')
    ),
    CONSTRAINT chk_batch_record_desviacion_resolucion CHECK (
        (estado IN ('ABIERTA', 'EN_INVESTIGACION') AND resuelta_en IS NULL AND resuelta_por_id IS NULL)
        OR
        (estado IN ('RESUELTA', 'CERRADA')
            AND resuelta_en IS NOT NULL
            AND resuelta_por_id IS NOT NULL
            AND resolucion IS NOT NULL
            AND evaluacion_impacto IS NOT NULL
            AND resuelta_en >= detectada_en)
    )
);

CREATE INDEX idx_batch_record_desviacion_record_estado
    ON batch_record_desviacion(batch_record_id, estado);

CREATE TABLE batch_record_firma (
    id BIGSERIAL PRIMARY KEY,
    batch_record_id BIGINT NOT NULL REFERENCES batch_record(id) ON DELETE RESTRICT,
    firmante_id BIGINT NOT NULL REFERENCES users(id),
    batch_record_etapa_id BIGINT NULL UNIQUE REFERENCES batch_record_etapa(id),
    alcance VARCHAR(30) NOT NULL,
    decision VARCHAR(20) NOT NULL,
    metodo VARCHAR(40) NOT NULL,
    firmado_en TIMESTAMP NOT NULL,
    autenticado_en TIMESTAMP NOT NULL,
    hash_contenido_firmado VARCHAR(64) NOT NULL,
    algoritmo_hash VARCHAR(20) NOT NULL DEFAULT 'SHA-256',
    username_firmante VARCHAR(120) NOT NULL,
    nombre_firmante VARCHAR(200) NOT NULL,
    cedula_firmante VARCHAR(30) NOT NULL,
    rol_firmante VARCHAR(120) NOT NULL,
    manifestacion TEXT NOT NULL,
    comentario VARCHAR(500) NULL,
    ip_origen VARCHAR(64) NULL,
    user_agent VARCHAR(500) NULL,
    CONSTRAINT chk_batch_record_firma_alcance CHECK (
        alcance IN ('CIERRE_ETAPA_AREA', 'REVISION_PRODUCCION', 'REVISION_CALIDAD', 'LIBERACION_LOTE')
    ),
    CONSTRAINT chk_batch_record_firma_decision CHECK (
        decision IN ('CONFIRMA', 'APRUEBA', 'RECHAZA')
    ),
    CONSTRAINT chk_batch_record_firma_metodo CHECK (
        metodo IN ('SESION_AUTENTICADA', 'REAUTENTICACION_CREDENCIALES', 'SEGUNDO_FACTOR')
    ),
    CONSTRAINT chk_batch_record_firma_hash CHECK (
        hash_contenido_firmado ~ '^[0-9A-Fa-f]{64}$'
    ),
    CONSTRAINT chk_batch_record_firma_contexto_area CHECK (
        (
            alcance = 'CIERRE_ETAPA_AREA'
            AND batch_record_etapa_id IS NOT NULL
        )
        OR
        (
            alcance <> 'CIERRE_ETAPA_AREA'
            AND batch_record_etapa_id IS NULL
        )
    ),
    CONSTRAINT chk_batch_record_firma_decision_area CHECK (
        alcance <> 'CIERRE_ETAPA_AREA' OR decision = 'CONFIRMA'
    ),
    CONSTRAINT chk_batch_record_firma_tiempos CHECK (autenticado_en <= firmado_en)
);

CREATE INDEX idx_batch_record_firma_record
    ON batch_record_firma(batch_record_id, firmado_en);

CREATE INDEX idx_batch_record_firma_firmante
    ON batch_record_firma(firmante_id, firmado_en);

CREATE TABLE batch_record_documento (
    id BIGSERIAL PRIMARY KEY,
    batch_record_id BIGINT NOT NULL REFERENCES batch_record(id) ON DELETE RESTRICT,
    tipo VARCHAR(30) NOT NULL,
    version_documento INTEGER NOT NULL,
    nombre_archivo VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    tamano_bytes BIGINT NOT NULL,
    sha256 VARCHAR(64) NOT NULL,
    storage_key VARCHAR(500) NOT NULL UNIQUE,
    generado_en TIMESTAMP NOT NULL,
    generado_por_id BIGINT NOT NULL REFERENCES users(id),
    CONSTRAINT uq_batch_record_documento_version
        UNIQUE (batch_record_id, tipo, version_documento),
    CONSTRAINT chk_batch_record_documento_tipo CHECK (
        tipo IN ('EXPEDIENTE_PDF', 'ANEXO')
    ),
    CONSTRAINT chk_batch_record_documento_version CHECK (version_documento > 0),
    CONSTRAINT chk_batch_record_documento_tamano CHECK (tamano_bytes > 0),
    CONSTRAINT chk_batch_record_documento_hash CHECK (sha256 ~ '^[0-9A-Fa-f]{64}$'),
    CONSTRAINT chk_batch_record_documento_pdf CHECK (
        tipo <> 'EXPEDIENTE_PDF' OR LOWER(content_type) = 'application/pdf'
    )
);

CREATE INDEX idx_batch_record_documento_record
    ON batch_record_documento(batch_record_id, tipo, version_documento DESC);
