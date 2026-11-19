-- Catalogos globales del area: sin cantidades, stock, reservas ni exclusividad.
CREATE TABLE area_operativa_semiterminado_manufacturado (
    area_operativa_id INTEGER NOT NULL,
    producto_id VARCHAR(255) NOT NULL,
    PRIMARY KEY (area_operativa_id, producto_id),
    CONSTRAINT fk_area_manufactura_area FOREIGN KEY (area_operativa_id)
        REFERENCES area_operativa(area_id) ON DELETE CASCADE,
    CONSTRAINT fk_area_manufactura_producto FOREIGN KEY (producto_id)
        REFERENCES productos(producto_id) ON DELETE RESTRICT
);

CREATE INDEX idx_area_manufactura_producto
    ON area_operativa_semiterminado_manufacturado(producto_id);

CREATE TABLE area_operativa_recurso_produccion (
    area_operativa_id INTEGER NOT NULL,
    recurso_produccion_id BIGINT NOT NULL,
    PRIMARY KEY (area_operativa_id, recurso_produccion_id),
    CONSTRAINT fk_area_recurso_area FOREIGN KEY (area_operativa_id)
        REFERENCES area_operativa(area_id) ON DELETE CASCADE,
    CONSTRAINT fk_area_recurso_tipo FOREIGN KEY (recurso_produccion_id)
        REFERENCES recurso_produccion(id) ON DELETE RESTRICT
);

CREATE INDEX idx_area_recurso_tipo
    ON area_operativa_recurso_produccion(recurso_produccion_id);
