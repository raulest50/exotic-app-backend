-- ============================================================================
-- V035: Extender MPS semanal para persistencia de borradores
-- ============================================================================

ALTER TABLE master_production_schedule_semanal
    ADD COLUMN IF NOT EXISTS estado VARCHAR(20) NOT NULL DEFAULT 'BORRADOR';

ALTER TABLE master_production_schedule_semanal
    ADD COLUMN IF NOT EXISTS fecha_actualizacion TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE master_production_schedule_semanal
    ADD COLUMN IF NOT EXISTS snapshot_json TEXT;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'uk_mps_week_start_date'
    ) THEN
        ALTER TABLE master_production_schedule_semanal
            ADD CONSTRAINT uk_mps_week_start_date UNIQUE (week_start_date);
    END IF;
END $$;
