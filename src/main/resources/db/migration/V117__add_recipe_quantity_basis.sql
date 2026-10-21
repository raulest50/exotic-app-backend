-- New recipes explicitly describe a batch. Historical recipes remain unchanged.
-- cantidad_requerida keeps its legacy meaning: input units per output unit.
ALTER TABLE procesos_produccion
    ADD COLUMN cantidad_salida_receta NUMERIC(24,12),
    ADD COLUMN unidad_salida_receta VARCHAR(16) REFERENCES unidad_medida_producto(codigo),
    ADD CONSTRAINT ck_receta_salida CHECK (
        (cantidad_salida_receta IS NULL AND unidad_salida_receta IS NULL) OR
        (cantidad_salida_receta IS NOT NULL AND cantidad_salida_receta > 0 AND unidad_salida_receta IS NOT NULL)
    );

ALTER TABLE insumos
    ADD COLUMN cantidad_receta NUMERIC(24,12),
    ADD COLUMN unidad_receta VARCHAR(16) REFERENCES unidad_medida_producto(codigo),
    ADD CONSTRAINT ck_insumo_receta CHECK (
        (cantidad_receta IS NULL AND unidad_receta IS NULL) OR
        (cantidad_receta IS NOT NULL AND cantidad_receta > 0 AND unidad_receta IS NOT NULL)
    );

-- Content of a presentation is independent of its inventory unit.
ALTER TABLE productos ADD COLUMN unidad_contenido_por_envase VARCHAR(16)
    REFERENCES unidad_medida_producto(codigo);
