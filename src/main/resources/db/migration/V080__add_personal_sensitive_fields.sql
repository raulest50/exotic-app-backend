ALTER TABLE IF EXISTS integrante_personal
    ADD COLUMN IF NOT EXISTS nombre_contacto_emergencia VARCHAR(255),
    ADD COLUMN IF NOT EXISTS celular_contacto_emergencia VARCHAR(255),
    ADD COLUMN IF NOT EXISTS estado_civil VARCHAR(30),
    ADD COLUMN IF NOT EXISTS numero_hijos INTEGER,
    ADD COLUMN IF NOT EXISTS fecha_ingreso DATE,
    ADD COLUMN IF NOT EXISTS numero_cuenta_bancaria VARCHAR(255),
    ADD COLUMN IF NOT EXISTS banco VARCHAR(255);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.table_constraints
        WHERE table_schema = 'public'
          AND table_name = 'integrante_personal'
          AND constraint_name = 'chk_integrante_personal_numero_hijos'
    ) THEN
        ALTER TABLE integrante_personal
            ADD CONSTRAINT chk_integrante_personal_numero_hijos
                CHECK (numero_hijos IS NULL OR numero_hijos >= 0);
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.table_constraints
        WHERE table_schema = 'public'
          AND table_name = 'integrante_personal'
          AND constraint_name = 'chk_integrante_personal_estado_civil'
    ) THEN
        ALTER TABLE integrante_personal
            ADD CONSTRAINT chk_integrante_personal_estado_civil
                CHECK (
                    estado_civil IS NULL OR estado_civil IN (
                        'SOLTERO',
                        'CASADO',
                        'UNION_LIBRE',
                        'SEPARADO',
                        'DIVORCIADO',
                        'VIUDO'
                    )
                );
    END IF;
END $$;
