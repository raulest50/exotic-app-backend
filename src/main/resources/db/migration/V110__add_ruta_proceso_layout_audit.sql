ALTER TABLE ruta_proceso_cat_version
    ADD COLUMN IF NOT EXISTS layout_revision INTEGER NOT NULL DEFAULT 0;

ALTER TABLE ruta_proceso_cat_version
    ADD COLUMN IF NOT EXISTS layout_actualizado_en TIMESTAMP NULL;

ALTER TABLE ruta_proceso_cat_version
    ADD COLUMN IF NOT EXISTS layout_actualizado_por VARCHAR(120) NULL;

