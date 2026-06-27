CREATE TABLE IF NOT EXISTS categoria_manufacturing_template (
    id BIGSERIAL PRIMARY KEY,
    categoria_id INTEGER NOT NULL,
    rendimiento_teorico DOUBLE PRECISION NOT NULL DEFAULT 0,
    fecha_creacion TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_modificacion TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_categoria_manufacturing_template_categoria UNIQUE (categoria_id),
    CONSTRAINT fk_categoria_manufacturing_template_categoria
        FOREIGN KEY (categoria_id)
        REFERENCES categoria(categoria_id)
        ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS categoria_template_insumo (
    id BIGSERIAL PRIMARY KEY,
    template_id BIGINT NOT NULL,
    input_producto_id VARCHAR(255) NOT NULL,
    cantidad_requerida DOUBLE PRECISION NOT NULL,
    orden INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT fk_categoria_template_insumo_template
        FOREIGN KEY (template_id)
        REFERENCES categoria_manufacturing_template(id)
        ON DELETE CASCADE,
    CONSTRAINT fk_categoria_template_insumo_producto
        FOREIGN KEY (input_producto_id)
        REFERENCES productos(producto_id)
        ON DELETE RESTRICT
);

CREATE TABLE IF NOT EXISTS categoria_template_case_pack (
    id BIGSERIAL PRIMARY KEY,
    template_id BIGINT NOT NULL,
    units_per_case INTEGER NOT NULL,
    ean14 VARCHAR(255),
    largo_cm DOUBLE PRECISION,
    ancho_cm DOUBLE PRECISION,
    alto_cm DOUBLE PRECISION,
    gross_weight_kg DOUBLE PRECISION,
    default_for_shipping BOOLEAN,
    CONSTRAINT uq_categoria_template_case_pack_template UNIQUE (template_id),
    CONSTRAINT fk_categoria_template_case_pack_template
        FOREIGN KEY (template_id)
        REFERENCES categoria_manufacturing_template(id)
        ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS categoria_template_insumo_empaque (
    id BIGSERIAL PRIMARY KEY,
    case_pack_id BIGINT NOT NULL,
    material_id VARCHAR(255) NOT NULL,
    cantidad DOUBLE PRECISION NOT NULL,
    uom VARCHAR(12),
    CONSTRAINT fk_categoria_template_insumo_empaque_case_pack
        FOREIGN KEY (case_pack_id)
        REFERENCES categoria_template_case_pack(id)
        ON DELETE CASCADE,
    CONSTRAINT fk_categoria_template_insumo_empaque_material
        FOREIGN KEY (material_id)
        REFERENCES productos(producto_id)
        ON DELETE RESTRICT
);

CREATE TABLE IF NOT EXISTS categoria_template_proceso_node (
    id BIGSERIAL PRIMARY KEY,
    template_id BIGINT NOT NULL,
    node_type VARCHAR(20) NOT NULL,
    frontend_id VARCHAR(255) NOT NULL,
    posicion_x DOUBLE PRECISION NOT NULL DEFAULT 0,
    posicion_y DOUBLE PRECISION NOT NULL DEFAULT 0,
    label VARCHAR(255),
    input_producto_id VARCHAR(255),
    proceso_id INTEGER,
    area_operativa_id INTEGER,
    CONSTRAINT fk_categoria_template_proceso_node_template
        FOREIGN KEY (template_id)
        REFERENCES categoria_manufacturing_template(id)
        ON DELETE CASCADE,
    CONSTRAINT fk_categoria_template_proceso_node_producto
        FOREIGN KEY (input_producto_id)
        REFERENCES productos(producto_id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_categoria_template_proceso_node_proceso
        FOREIGN KEY (proceso_id)
        REFERENCES proceso_produccion(proceso_id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_categoria_template_proceso_node_area
        FOREIGN KEY (area_operativa_id)
        REFERENCES area_operativa(area_id)
        ON DELETE RESTRICT
);

CREATE TABLE IF NOT EXISTS categoria_template_proceso_edge (
    id BIGSERIAL PRIMARY KEY,
    template_id BIGINT NOT NULL,
    frontend_id VARCHAR(255) NOT NULL,
    source_frontend_id VARCHAR(255) NOT NULL,
    target_frontend_id VARCHAR(255) NOT NULL,
    CONSTRAINT fk_categoria_template_proceso_edge_template
        FOREIGN KEY (template_id)
        REFERENCES categoria_manufacturing_template(id)
        ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_categoria_template_insumo_template
    ON categoria_template_insumo(template_id);

CREATE INDEX IF NOT EXISTS idx_categoria_template_insumo_producto
    ON categoria_template_insumo(input_producto_id);

CREATE INDEX IF NOT EXISTS idx_categoria_template_case_pack_template
    ON categoria_template_case_pack(template_id);

CREATE INDEX IF NOT EXISTS idx_categoria_template_insumo_empaque_case_pack
    ON categoria_template_insumo_empaque(case_pack_id);

CREATE INDEX IF NOT EXISTS idx_categoria_template_proceso_node_template
    ON categoria_template_proceso_node(template_id);

CREATE INDEX IF NOT EXISTS idx_categoria_template_proceso_node_frontend
    ON categoria_template_proceso_node(frontend_id);

CREATE INDEX IF NOT EXISTS idx_categoria_template_proceso_edge_template
    ON categoria_template_proceso_edge(template_id);
