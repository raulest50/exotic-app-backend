ALTER TABLE transaccion_almacen
    ADD COLUMN IF NOT EXISTS causa_ajuste VARCHAR(64);

ALTER TABLE transaccion_almacen
    DROP CONSTRAINT IF EXISTS transaccion_almacen_causa_ajuste_check;

ALTER TABLE transaccion_almacen
    ADD CONSTRAINT transaccion_almacen_causa_ajuste_check
        CHECK (
            causa_ajuste IS NULL
            OR causa_ajuste IN (
                'PRODUCCION_CONTINGENCIA',
                'DIFERENCIA_CONTEO',
                'MERMA_DANO_PERDIDA',
                'CORRECCION_REGISTRO',
                'OTRA_REGULARIZACION'
            )
        );
