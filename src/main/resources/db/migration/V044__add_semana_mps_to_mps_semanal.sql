-- ============================================================================
-- V044: Agregar entidad canonica SemanaMPS para programacion semanal
-- ============================================================================

CREATE TABLE IF NOT EXISTS semana_mps (
    id BIGSERIAL PRIMARY KEY,
    codigo VARCHAR(20) NOT NULL,
    anio_semana INTEGER NOT NULL,
    numero_semana INTEGER NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    standard VARCHAR(40) NOT NULL,
    CONSTRAINT uk_semana_mps_codigo UNIQUE (codigo),
    CONSTRAINT uk_semana_mps_standard_anio_numero UNIQUE (standard, anio_semana, numero_semana),
    CONSTRAINT uk_semana_mps_standard_start_date UNIQUE (standard, start_date)
);

INSERT INTO semana_mps (codigo, anio_semana, numero_semana, start_date, end_date, standard)
SELECT DISTINCT
    'S' || LPAD(EXTRACT(WEEK FROM week_start_date)::int::text, 2, '0') || '-' || EXTRACT(ISOYEAR FROM week_start_date)::int AS codigo,
    EXTRACT(ISOYEAR FROM week_start_date)::int AS anio_semana,
    EXTRACT(WEEK FROM week_start_date)::int AS numero_semana,
    week_start_date AS start_date,
    week_start_date + 5 AS end_date,
    'ISO_8601_MONDAY_SATURDAY' AS standard
FROM master_production_schedule_semanal
WHERE week_start_date IS NOT NULL
ON CONFLICT (codigo) DO NOTHING;

ALTER TABLE master_production_schedule_semanal
    ADD COLUMN IF NOT EXISTS semana_mps_id BIGINT;

UPDATE master_production_schedule_semanal mps
SET semana_mps_id = semana.id
FROM semana_mps semana
WHERE mps.week_start_date = semana.start_date
  AND semana.standard = 'ISO_8601_MONDAY_SATURDAY'
  AND mps.semana_mps_id IS NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_mps_semana_mps'
    ) THEN
        ALTER TABLE master_production_schedule_semanal
            ADD CONSTRAINT fk_mps_semana_mps
            FOREIGN KEY (semana_mps_id)
            REFERENCES semana_mps(id)
            ON DELETE RESTRICT;
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'uk_mps_semana_mps_id'
    ) THEN
        ALTER TABLE master_production_schedule_semanal
            ADD CONSTRAINT uk_mps_semana_mps_id UNIQUE (semana_mps_id);
    END IF;
END $$;

ALTER TABLE master_production_schedule_semanal
    ALTER COLUMN semana_mps_id SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_semana_mps_anio_numero
    ON semana_mps (anio_semana, numero_semana);
