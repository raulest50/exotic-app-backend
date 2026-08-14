ALTER TABLE categoria
    ADD COLUMN vida_util_cantidad INTEGER NULL,
    ADD COLUMN vida_util_unidad VARCHAR(10) NULL;

ALTER TABLE categoria
    ADD CONSTRAINT chk_categoria_vida_util_consistente CHECK (
        (vida_util_cantidad IS NULL AND vida_util_unidad IS NULL)
        OR
        (vida_util_cantidad IS NOT NULL
            AND vida_util_unidad IS NOT NULL
            AND vida_util_cantidad > 0
            AND vida_util_unidad IN ('DIAS', 'MESES', 'ANIOS'))
    );

ALTER TABLE lote
    ADD COLUMN vida_util_cantidad_aplicada INTEGER NULL,
    ADD COLUMN vida_util_unidad_aplicada VARCHAR(10) NULL;

ALTER TABLE lote
    ADD CONSTRAINT chk_lote_vida_util_consistente CHECK (
        (vida_util_cantidad_aplicada IS NULL AND vida_util_unidad_aplicada IS NULL)
        OR
        (vida_util_cantidad_aplicada IS NOT NULL
            AND vida_util_unidad_aplicada IS NOT NULL
            AND vida_util_cantidad_aplicada > 0
            AND vida_util_unidad_aplicada IN ('DIAS', 'MESES', 'ANIOS'))
    );
