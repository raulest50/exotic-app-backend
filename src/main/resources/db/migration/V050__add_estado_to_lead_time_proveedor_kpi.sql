ALTER TABLE lead_time_proveedor_kpi
    ADD COLUMN IF NOT EXISTS estado VARCHAR(32) NOT NULL DEFAULT 'VIGENTE',
    ADD COLUMN IF NOT EXISTS motivo_estado VARCHAR(255) NULL,
    ADD COLUMN IF NOT EXISTS ultima_evaluacion_en TIMESTAMP NULL,
    ADD COLUMN IF NOT EXISTS ultima_fecha_corte_evaluada DATE NULL;

ALTER TABLE lead_time_proveedor_kpi
    ALTER COLUMN lead_time_mediano_dias DROP NOT NULL,
    ALTER COLUMN calculado_en DROP NOT NULL;

UPDATE lead_time_proveedor_kpi
SET estado = COALESCE(estado, 'VIGENTE'),
    ultima_evaluacion_en = COALESCE(ultima_evaluacion_en, calculado_en),
    ultima_fecha_corte_evaluada = COALESCE(ultima_fecha_corte_evaluada, fecha_corte)
WHERE ultima_evaluacion_en IS NULL
   OR ultima_fecha_corte_evaluada IS NULL
   OR estado IS NULL;

ALTER TABLE lead_time_proveedor_kpi
    DROP CONSTRAINT IF EXISTS chk_lead_time_proveedor_kpi_observaciones,
    DROP CONSTRAINT IF EXISTS chk_lead_time_proveedor_kpi_ordenes,
    DROP CONSTRAINT IF EXISTS chk_lead_time_proveedor_kpi_estado;

ALTER TABLE lead_time_proveedor_kpi
    ADD CONSTRAINT chk_lead_time_proveedor_kpi_observaciones
        CHECK (observaciones >= 0),
    ADD CONSTRAINT chk_lead_time_proveedor_kpi_ordenes
        CHECK (ordenes_consideradas >= 0),
    ADD CONSTRAINT chk_lead_time_proveedor_kpi_estado
        CHECK (estado IN ('VIGENTE', 'DESACTUALIZADO', 'SIN_INFORMACION'));
