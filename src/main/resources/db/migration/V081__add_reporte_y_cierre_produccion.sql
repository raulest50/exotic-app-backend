CREATE TABLE cierre_produccion (
    id BIGSERIAL PRIMARY KEY,
    fecha_produccion DATE NOT NULL,
    cerrado_en TIMESTAMP NOT NULL,
    cerrado_por_id BIGINT NOT NULL REFERENCES users(id),
    idempotency_key UUID NOT NULL,
    solicitud_hash VARCHAR(64) NOT NULL,
    CONSTRAINT uq_cierre_produccion_idempotency UNIQUE (idempotency_key)
);

CREATE INDEX idx_cierre_produccion_fecha
    ON cierre_produccion (fecha_produccion, cerrado_en);

CREATE TABLE reporte_produccion_lote (
    id BIGSERIAL PRIMARY KEY,
    orden_produccion_id INTEGER NOT NULL REFERENCES ordenes_produccion(orden_id),
    lote_id BIGINT NOT NULL REFERENCES lote(id),
    seguimiento_orden_area_id BIGINT NOT NULL REFERENCES seguimiento_orden_area(id),
    cierre_produccion_id BIGINT NULL REFERENCES cierre_produccion(id),
    transaccion_almacen_id INTEGER NULL REFERENCES transaccion_almacen(transaccion_id),
    cantidad_reportada NUMERIC(18, 4) NOT NULL,
    cantidad_confirmada NUMERIC(18, 4) NULL,
    fecha_produccion DATE NOT NULL,
    reportado_en TIMESTAMP NOT NULL,
    reportado_por_id BIGINT NOT NULL REFERENCES users(id),
    estado VARCHAR(20) NOT NULL,
    motivo_correccion VARCHAR(500) NULL,
    estado_orden_anterior INTEGER NOT NULL,
    anulado_en TIMESTAMP NULL,
    anulado_por_id BIGINT NULL REFERENCES users(id),
    motivo_anulacion VARCHAR(500) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_reporte_produccion_transaccion UNIQUE (transaccion_almacen_id),
    CONSTRAINT chk_reporte_produccion_estado
        CHECK (estado IN ('PENDIENTE', 'CONFIRMADO', 'ANULADO')),
    CONSTRAINT chk_reporte_produccion_cantidad_reportada
        CHECK (cantidad_reportada > 0),
    CONSTRAINT chk_reporte_produccion_cantidad_confirmada
        CHECK (cantidad_confirmada IS NULL OR cantidad_confirmada > 0),
    CONSTRAINT chk_reporte_produccion_consistencia CHECK (
        (estado = 'PENDIENTE'
            AND cierre_produccion_id IS NULL
            AND transaccion_almacen_id IS NULL
            AND cantidad_confirmada IS NULL
            AND anulado_en IS NULL
            AND anulado_por_id IS NULL)
        OR
        (estado = 'CONFIRMADO'
            AND cierre_produccion_id IS NOT NULL
            AND transaccion_almacen_id IS NOT NULL
            AND cantidad_confirmada IS NOT NULL
            AND anulado_en IS NULL
            AND anulado_por_id IS NULL)
        OR
        (estado = 'ANULADO'
            AND cierre_produccion_id IS NULL
            AND transaccion_almacen_id IS NULL
            AND cantidad_confirmada IS NULL
            AND anulado_en IS NOT NULL
            AND anulado_por_id IS NOT NULL)
    )
);

CREATE UNIQUE INDEX uq_reporte_produccion_op_no_anulado
    ON reporte_produccion_lote (orden_produccion_id)
    WHERE estado <> 'ANULADO';

CREATE UNIQUE INDEX uq_reporte_produccion_lote_no_anulado
    ON reporte_produccion_lote (lote_id)
    WHERE estado <> 'ANULADO';

CREATE UNIQUE INDEX uq_reporte_produccion_seguimiento_no_anulado
    ON reporte_produccion_lote (seguimiento_orden_area_id)
    WHERE estado <> 'ANULADO';

CREATE INDEX idx_reporte_produccion_pendiente_fecha
    ON reporte_produccion_lote (fecha_produccion, reportado_en)
    WHERE estado = 'PENDIENTE';

CREATE INDEX idx_reporte_produccion_cierre
    ON reporte_produccion_lote (cierre_produccion_id);
