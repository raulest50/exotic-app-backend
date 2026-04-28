CREATE TABLE IF NOT EXISTS area_operativa_categoria_habilitada (
    area_operativa_id INTEGER NOT NULL,
    categoria_id INTEGER NOT NULL,
    CONSTRAINT pk_area_operativa_categoria_habilitada PRIMARY KEY (area_operativa_id, categoria_id),
    CONSTRAINT fk_aoch_area_operativa
        FOREIGN KEY (area_operativa_id)
        REFERENCES area_operativa (area_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_aoch_categoria
        FOREIGN KEY (categoria_id)
        REFERENCES categoria (categoria_id)
        ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_aoch_area_operativa_id
    ON area_operativa_categoria_habilitada (area_operativa_id);

CREATE INDEX IF NOT EXISTS idx_aoch_categoria_id
    ON area_operativa_categoria_habilitada (categoria_id);
