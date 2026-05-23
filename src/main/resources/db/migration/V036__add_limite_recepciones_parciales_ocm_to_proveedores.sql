ALTER TABLE IF EXISTS proveedores
ADD COLUMN IF NOT EXISTS limite_recepciones_parciales_ocm integer;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'proveedores_limite_recepciones_parciales_ocm_check'
    ) THEN
        ALTER TABLE proveedores
        ADD CONSTRAINT proveedores_limite_recepciones_parciales_ocm_check
        CHECK (
            limite_recepciones_parciales_ocm IS NULL
            OR limite_recepciones_parciales_ocm >= 1
        );
    END IF;
END $$;
