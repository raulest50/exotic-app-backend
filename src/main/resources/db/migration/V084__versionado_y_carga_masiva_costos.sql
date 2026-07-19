DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM productos
        WHERE costo::text IN ('NaN', 'Infinity', '-Infinity')
           OR costo < 0
           OR ABS(costo) > 9999999999999.999999
    ) THEN
        RAISE EXCEPTION 'productos.costo contiene valores no validos para NUMERIC(19,6)';
    END IF;
END $$;

ALTER TABLE productos
    ALTER COLUMN costo TYPE NUMERIC(19, 6)
    USING ROUND(COALESCE(costo, 0)::numeric, 6);

ALTER TABLE productos
    ALTER COLUMN costo SET DEFAULT 0,
    ALTER COLUMN costo SET NOT NULL,
    ADD COLUMN costo_version BIGINT NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_productos_costo_no_negativo CHECK (costo >= 0);

UPDATE productos SET costo_version = 1;

CREATE TABLE carga_costos_lote (
    id UUID PRIMARY KEY,
    usuario_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    usuario_username VARCHAR(120) NOT NULL,
    nombre_archivo VARCHAR(255) NOT NULL,
    archivo_sha256 VARCHAR(64) NOT NULL,
    motivo VARCHAR(500) NOT NULL,
    estado VARCHAR(30) NOT NULL,
    token_hash VARCHAR(255),
    token_expira_en TIMESTAMP,
    intentos_token INTEGER NOT NULL DEFAULT 0,
    generaciones_token INTEGER NOT NULL DEFAULT 0,
    ultima_generacion_token_en TIMESTAMP,
    creado_en TIMESTAMP NOT NULL,
    expira_en TIMESTAMP NOT NULL,
    ejecutado_en TIMESTAMP,
    total_filas INTEGER NOT NULL DEFAULT 0,
    total_candidatas INTEGER NOT NULL DEFAULT 0,
    total_actualizadas INTEGER NOT NULL DEFAULT 0,
    total_sin_cambio INTEGER NOT NULL DEFAULT 0,
    total_omitidas INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT ck_carga_costos_lote_contadores CHECK (
        intentos_token >= 0
        AND generaciones_token >= 0
        AND total_filas >= 0
        AND total_candidatas >= 0
        AND total_actualizadas >= 0
        AND total_sin_cambio >= 0
        AND total_omitidas >= 0
    ),
    CONSTRAINT ck_carga_costos_lote_estado CHECK (
        estado IN ('PREPARADO', 'EJECUTADO', 'EXPIRADO', 'BLOQUEADO', 'CANCELADO')
    )
);

CREATE TABLE carga_costos_item (
    id BIGSERIAL PRIMARY KEY,
    lote_id UUID NOT NULL REFERENCES carga_costos_lote(id) ON DELETE CASCADE,
    fila_excel INTEGER NOT NULL,
    producto_id VARCHAR(255) NOT NULL,
    producto_nombre VARCHAR(200),
    tipo_producto VARCHAR(1) NOT NULL,
    descripcion_excel VARCHAR(500),
    descripcion_coincide BOOLEAN NOT NULL,
    costo_anterior NUMERIC(19, 6) NOT NULL,
    costo_nuevo NUMERIC(19, 6) NOT NULL,
    costo_version_anterior BIGINT NOT NULL,
    CONSTRAINT ck_carga_costos_item_valores CHECK (
        fila_excel > 1
        AND costo_anterior >= 0
        AND costo_nuevo > 0
        AND costo_version_anterior >= 1
    ),
    CONSTRAINT uq_carga_costos_item_lote_producto UNIQUE (lote_id, producto_id)
);

CREATE TABLE producto_costo_historial (
    id BIGSERIAL PRIMARY KEY,
    producto_id VARCHAR(255) NOT NULL,
    producto_nombre VARCHAR(200),
    tipo_producto VARCHAR(1) NOT NULL,
    version BIGINT NOT NULL,
    costo_anterior NUMERIC(19, 6),
    costo_nuevo NUMERIC(19, 6) NOT NULL,
    cambiado_en TIMESTAMP NOT NULL,
    usuario_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    usuario_username VARCHAR(120) NOT NULL,
    origen VARCHAR(50) NOT NULL,
    motivo VARCHAR(500),
    referencia VARCHAR(255),
    carga_costos_lote_id UUID REFERENCES carga_costos_lote(id) ON DELETE SET NULL,
    CONSTRAINT ck_producto_costo_historial_valores CHECK (
        version >= 1
        AND (costo_anterior IS NULL OR costo_anterior >= 0)
        AND costo_nuevo >= 0
    ),
    CONSTRAINT uq_producto_costo_historial_version UNIQUE (producto_id, version)
);

INSERT INTO producto_costo_historial (
    producto_id, producto_nombre, tipo_producto, version,
    costo_anterior, costo_nuevo, cambiado_en,
    usuario_username, origen, motivo
)
SELECT producto_id, nombre, tipo_producto, 1,
       NULL, costo, CURRENT_TIMESTAMP,
       'system', 'MIGRACION_INICIAL', 'Valor vigente al habilitar el historial de costos'
FROM productos;

CREATE INDEX idx_carga_costos_lote_usuario_estado
    ON carga_costos_lote (usuario_id, estado, creado_en DESC);
CREATE INDEX idx_carga_costos_lote_expira
    ON carga_costos_lote (estado, expira_en);
CREATE INDEX idx_carga_costos_item_lote_fila
    ON carga_costos_item (lote_id, fila_excel);
CREATE INDEX idx_producto_costo_historial_producto_fecha
    ON producto_costo_historial (producto_id, cambiado_en DESC);
CREATE INDEX idx_producto_costo_historial_origen
    ON producto_costo_historial (origen, cambiado_en DESC);
CREATE INDEX idx_producto_costo_historial_lote
    ON producto_costo_historial (carga_costos_lote_id);
