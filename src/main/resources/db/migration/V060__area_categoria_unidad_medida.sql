ALTER TABLE unidad_medida_area_operativa
    RENAME COLUMN unidad_referencia TO unidad_estandar;

ALTER TABLE unidad_medida_area_operativa
    RENAME COLUMN factor_a_referencia TO cantidad_unidad_estandar;

ALTER TABLE unidad_medida_area_operativa
    RENAME CONSTRAINT chk_umao_factor_positive TO chk_umao_cantidad_unidad_estandar_positive;

ALTER TABLE unidad_medida_area_operativa
    RENAME CONSTRAINT chk_umao_unidad_referencia TO chk_umao_unidad_estandar;

ALTER TABLE unidad_medida_area_operativa
    ADD CONSTRAINT uk_umao_area_unidad_id UNIQUE (area_operativa_id, unidad_medida_area_operativa_id);

CREATE TABLE IF NOT EXISTS area_operativa_categoria_unidad_medida (
    area_operativa_categoria_unidad_medida_id BIGSERIAL PRIMARY KEY,
    area_operativa_id INTEGER NOT NULL,
    categoria_id INTEGER NOT NULL,
    unidad_medida_area_operativa_id BIGINT NOT NULL,
    CONSTRAINT fk_aocum_area_categoria
        FOREIGN KEY (area_operativa_id, categoria_id)
        REFERENCES area_operativa_categoria_habilitada (area_operativa_id, categoria_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_aocum_area_unidad
        FOREIGN KEY (area_operativa_id, unidad_medida_area_operativa_id)
        REFERENCES unidad_medida_area_operativa (area_operativa_id, unidad_medida_area_operativa_id)
        ON DELETE CASCADE,
    CONSTRAINT uk_aocum_area_categoria_unidad
        UNIQUE (area_operativa_id, categoria_id, unidad_medida_area_operativa_id)
);

CREATE INDEX IF NOT EXISTS idx_aocum_area_operativa_id
    ON area_operativa_categoria_unidad_medida (area_operativa_id);

CREATE INDEX IF NOT EXISTS idx_aocum_categoria_id
    ON area_operativa_categoria_unidad_medida (categoria_id);

CREATE INDEX IF NOT EXISTS idx_aocum_unidad_medida_id
    ON area_operativa_categoria_unidad_medida (unidad_medida_area_operativa_id);
