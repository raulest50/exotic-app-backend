-- El Batch Record pasa a ser una proyeccion documental reintentable.
-- La cola no crea expedientes historicos: solo recibe solicitudes nuevas.

ALTER TABLE batch_record
    ADD COLUMN estado_sincronizacion VARCHAR(20) NOT NULL DEFAULT 'ACTUALIZADO',
    ADD COLUMN sincronizado_en TIMESTAMP NULL,
    ADD COLUMN advertencias_documentales TEXT NULL,
    ADD COLUMN ultimo_error_documental TEXT NULL;

ALTER TABLE batch_record
    ADD CONSTRAINT chk_batch_record_estado_sincronizacion CHECK (
        estado_sincronizacion IN (
            'PENDIENTE', 'SINCRONIZANDO', 'ACTUALIZADO', 'INCOMPLETO', 'ERROR'
        )
    );

CREATE TABLE batch_record_projection_task (
    id BIGSERIAL PRIMARY KEY,
    orden_produccion_id INTEGER NULL UNIQUE REFERENCES ordenes_produccion(orden_id),
    orden_fabricacion_id BIGINT NULL UNIQUE REFERENCES orden_fabricacion(orden_fabricacion_id),
    lote_id BIGINT NOT NULL UNIQUE REFERENCES lote(id),
    solicitado_por_id BIGINT NOT NULL REFERENCES users(id),
    objetivo VARCHAR(20) NOT NULL DEFAULT 'ACTIVO',
    estado VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE',
    cantidad_obtenida NUMERIC(18, 4) NULL,
    limpiar_cantidad_obtenida BOOLEAN NOT NULL DEFAULT FALSE,
    intentos INTEGER NOT NULL DEFAULT 0,
    solicitud_version BIGINT NOT NULL DEFAULT 0,
    proximo_intento_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ultimo_error TEXT NULL,
    solicitado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completado_en TIMESTAMP NULL,
    CONSTRAINT chk_batch_record_projection_task_orden CHECK (
        num_nonnulls(orden_produccion_id, orden_fabricacion_id) = 1
    ),
    CONSTRAINT chk_batch_record_projection_task_objetivo CHECK (
        objetivo IN ('ACTIVO', 'CERRADO', 'ANULADO')
    ),
    CONSTRAINT chk_batch_record_projection_task_estado CHECK (
        estado IN ('PENDIENTE', 'PROCESANDO', 'COMPLETADA', 'ERROR')
    ),
    CONSTRAINT chk_batch_record_projection_task_intentos CHECK (intentos >= 0),
    CONSTRAINT chk_batch_record_projection_task_cantidad CHECK (
        cantidad_obtenida IS NULL OR cantidad_obtenida >= 0
    )
);

CREATE INDEX idx_batch_record_projection_task_due
    ON batch_record_projection_task(estado, proximo_intento_en, id);

COMMENT ON TABLE batch_record_projection_task IS
    'Solicitud reintentable para proyectar el historial operativo en un Batch Record sin bloquear la operacion fuente.';
COMMENT ON COLUMN batch_record.estado_sincronizacion IS
    'Estado tecnico e informativo de la proyeccion; nunca gobierna Calidad, produccion ni inventario.';
