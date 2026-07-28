ALTER TABLE orden_compra_activo
    ADD COLUMN IF NOT EXISTS empresa_identidad_legal_version_id BIGINT NULL,
    ADD COLUMN IF NOT EXISTS empresa_logo_documental_version_id BIGINT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_ocaf_empresa_identidad_legal_version'
    ) THEN
        ALTER TABLE orden_compra_activo
            ADD CONSTRAINT fk_ocaf_empresa_identidad_legal_version
            FOREIGN KEY (empresa_identidad_legal_version_id)
            REFERENCES empresa_identidad_legal_version (id);
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_ocaf_empresa_logo_documental_version'
    ) THEN
        ALTER TABLE orden_compra_activo
            ADD CONSTRAINT fk_ocaf_empresa_logo_documental_version
            FOREIGN KEY (empresa_logo_documental_version_id)
            REFERENCES empresa_logo_documental_version (id);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_ocaf_empresa_identidad_legal_version
    ON orden_compra_activo (empresa_identidad_legal_version_id);

CREATE INDEX IF NOT EXISTS idx_ocaf_empresa_logo_documental_version
    ON orden_compra_activo (empresa_logo_documental_version_id);
