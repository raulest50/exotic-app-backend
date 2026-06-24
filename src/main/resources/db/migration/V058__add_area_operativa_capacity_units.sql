CREATE TABLE IF NOT EXISTS unidad_medida_area_operativa (
    unidad_medida_area_operativa_id BIGSERIAL PRIMARY KEY,
    area_operativa_id INTEGER NOT NULL,
    codigo VARCHAR(32) NOT NULL,
    nombre VARCHAR(120) NOT NULL,
    descripcion VARCHAR(255),
    dimension VARCHAR(32) NOT NULL,
    unidad_referencia VARCHAR(16) NOT NULL,
    factor_a_referencia NUMERIC(19, 6) NOT NULL,
    principal BOOLEAN NOT NULL DEFAULT FALSE,
    discreta BOOLEAN NOT NULL DEFAULT FALSE,
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_umao_area_operativa
        FOREIGN KEY (area_operativa_id)
        REFERENCES area_operativa (area_id)
        ON DELETE CASCADE,
    CONSTRAINT uk_umao_area_codigo UNIQUE (area_operativa_id, codigo),
    CONSTRAINT chk_umao_factor_positive CHECK (factor_a_referencia > 0),
    CONSTRAINT chk_umao_dimension CHECK (dimension IN ('VOLUMEN', 'MASA', 'CONTEO', 'TIEMPO')),
    CONSTRAINT chk_umao_unidad_referencia CHECK (unidad_referencia IN ('L', 'KG', 'U', 'MIN'))
);

CREATE INDEX IF NOT EXISTS idx_umao_area_operativa_id
    ON unidad_medida_area_operativa (area_operativa_id);

CREATE INDEX IF NOT EXISTS idx_umao_area_activo
    ON unidad_medida_area_operativa (area_operativa_id, activo);

CREATE TABLE IF NOT EXISTS capacidad_area_operativa (
    capacidad_area_operativa_id BIGSERIAL PRIMARY KEY,
    area_operativa_id INTEGER NOT NULL,
    unidad_medida_area_operativa_id BIGINT NOT NULL,
    tipo_capacidad VARCHAR(32) NOT NULL,
    cantidad NUMERIC(19, 6) NOT NULL,
    periodo VARCHAR(32) NOT NULL,
    eficiencia NUMERIC(5, 4) NOT NULL DEFAULT 1,
    vigente_desde DATE,
    vigente_hasta DATE,
    descripcion VARCHAR(255),
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_cao_area_operativa
        FOREIGN KEY (area_operativa_id)
        REFERENCES area_operativa (area_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_cao_unidad_medida_area_operativa
        FOREIGN KEY (unidad_medida_area_operativa_id)
        REFERENCES unidad_medida_area_operativa (unidad_medida_area_operativa_id)
        ON DELETE RESTRICT,
    CONSTRAINT chk_cao_tipo_capacidad CHECK (tipo_capacidad IN ('PRODUCTIVA', 'ALMACENAMIENTO')),
    CONSTRAINT chk_cao_periodo CHECK (periodo IN ('HORA', 'TURNO', 'DIA', 'SEMANA')),
    CONSTRAINT chk_cao_cantidad_positive CHECK (cantidad > 0),
    CONSTRAINT chk_cao_eficiencia_range CHECK (eficiencia >= 0 AND eficiencia <= 1),
    CONSTRAINT chk_cao_vigencia CHECK (vigente_hasta IS NULL OR vigente_desde IS NULL OR vigente_hasta >= vigente_desde)
);

CREATE INDEX IF NOT EXISTS idx_cao_area_operativa_id
    ON capacidad_area_operativa (area_operativa_id);

CREATE INDEX IF NOT EXISTS idx_cao_unidad_medida_id
    ON capacidad_area_operativa (unidad_medida_area_operativa_id);

CREATE INDEX IF NOT EXISTS idx_cao_area_activo
    ON capacidad_area_operativa (area_operativa_id, activo);
