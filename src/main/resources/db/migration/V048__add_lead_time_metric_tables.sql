CREATE TABLE IF NOT EXISTS proveedor_material_lead_time_metric (
    id BIGSERIAL PRIMARY KEY,
    proveedor_pk BIGINT NOT NULL,
    producto_id VARCHAR(255) NOT NULL,
    fecha_corte DATE NOT NULL,
    ventana_dias INTEGER NOT NULL,
    lead_time_mediano_dias DOUBLE PRECISION NOT NULL,
    observaciones INTEGER NOT NULL,
    ordenes_consideradas INTEGER NOT NULL,
    calculado_en TIMESTAMP NOT NULL,
    CONSTRAINT uk_proveedor_material_lead_time_metric_pair UNIQUE (proveedor_pk, producto_id),
    CONSTRAINT fk_proveedor_material_lead_time_metric_proveedor
        FOREIGN KEY (proveedor_pk)
        REFERENCES proveedores(pk)
        ON DELETE RESTRICT,
    CONSTRAINT fk_proveedor_material_lead_time_metric_material
        FOREIGN KEY (producto_id)
        REFERENCES productos(producto_id)
        ON DELETE RESTRICT,
    CONSTRAINT chk_proveedor_material_lead_time_metric_ventana
        CHECK (ventana_dias >= 1),
    CONSTRAINT chk_proveedor_material_lead_time_metric_lead_time
        CHECK (lead_time_mediano_dias >= 0),
    CONSTRAINT chk_proveedor_material_lead_time_metric_observaciones
        CHECK (observaciones >= 1),
    CONSTRAINT chk_proveedor_material_lead_time_metric_ordenes
        CHECK (ordenes_consideradas >= observaciones)
);

CREATE INDEX IF NOT EXISTS idx_proveedor_material_lead_time_metric_producto
    ON proveedor_material_lead_time_metric(producto_id);

CREATE INDEX IF NOT EXISTS idx_proveedor_material_lead_time_metric_proveedor
    ON proveedor_material_lead_time_metric(proveedor_pk);

CREATE TABLE IF NOT EXISTS lead_time_proveedor_kpi (
    id BIGSERIAL PRIMARY KEY,
    proveedor_pk BIGINT NOT NULL,
    fecha_corte DATE NOT NULL,
    ventana_dias INTEGER NOT NULL,
    lead_time_mediano_dias DOUBLE PRECISION NOT NULL,
    observaciones INTEGER NOT NULL,
    ordenes_consideradas INTEGER NOT NULL,
    calculado_en TIMESTAMP NOT NULL,
    CONSTRAINT uk_lead_time_proveedor_kpi_proveedor UNIQUE (proveedor_pk),
    CONSTRAINT fk_lead_time_proveedor_kpi_proveedor
        FOREIGN KEY (proveedor_pk)
        REFERENCES proveedores(pk)
        ON DELETE RESTRICT,
    CONSTRAINT chk_lead_time_proveedor_kpi_ventana
        CHECK (ventana_dias >= 1),
    CONSTRAINT chk_lead_time_proveedor_kpi_lead_time
        CHECK (lead_time_mediano_dias >= 0),
    CONSTRAINT chk_lead_time_proveedor_kpi_observaciones
        CHECK (observaciones >= 1),
    CONSTRAINT chk_lead_time_proveedor_kpi_ordenes
        CHECK (ordenes_consideradas >= observaciones)
);

CREATE INDEX IF NOT EXISTS idx_lead_time_proveedor_kpi_proveedor
    ON lead_time_proveedor_kpi(proveedor_pk);
