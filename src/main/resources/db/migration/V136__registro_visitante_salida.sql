-- Los registros anteriores no permiten inferir presencia ni una salida real.
ALTER TABLE registro_visitante
    ADD COLUMN estado VARCHAR(30),
    ADD COLUMN fecha_salida TIMESTAMP WITH TIME ZONE,
    ADD COLUMN salida_registrada_en TIMESTAMP WITH TIME ZONE,
    ADD COLUMN salida_registrada_por_id BIGINT,
    ADD COLUMN salida_registrada_por_username VARCHAR(255);

UPDATE registro_visitante
SET estado = 'SIN_SEGUIMIENTO';

-- El default se establece despues de clasificar el historico.
ALTER TABLE registro_visitante
    ALTER COLUMN estado SET NOT NULL,
    ALTER COLUMN estado SET DEFAULT 'PENDIENTE_SALIDA',
    ADD CONSTRAINT ck_registro_visitante_estado CHECK (
        estado IN ('PENDIENTE_SALIDA', 'SALIO', 'SIN_SEGUIMIENTO')
    ),
    ADD CONSTRAINT ck_registro_visitante_salida CHECK (
        (CASE WHEN estado = 'SALIO' THEN
            fecha_salida IS NOT NULL
            AND salida_registrada_en IS NOT NULL
            AND salida_registrada_por_id IS NOT NULL
            AND salida_registrada_por_username IS NOT NULL
            AND salida_registrada_por_username ~ '[^[:space:]]'
            AND fecha_salida >= fecha_registro
            AND salida_registrada_en >= fecha_salida
        ELSE
            fecha_salida IS NULL
            AND salida_registrada_en IS NULL
            AND salida_registrada_por_id IS NULL
            AND salida_registrada_por_username IS NULL
        END) IS TRUE
    );

-- El actor es una instantanea de auditoria sin FK a una cuenta mutable.
