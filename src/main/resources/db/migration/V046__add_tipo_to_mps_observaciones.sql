-- ============================================================================
-- V046: Agregar criticidad/tipo a observaciones de MPS semanal
-- ============================================================================

ALTER TABLE mps_semanal_observacion
    ADD COLUMN IF NOT EXISTS tipo VARCHAR(20) DEFAULT 'BLOQUEANTE';

UPDATE mps_semanal_observacion
SET tipo = 'BLOQUEANTE'
WHERE tipo IS NULL;

ALTER TABLE mps_semanal_observacion
    ALTER COLUMN tipo SET DEFAULT 'BLOQUEANTE';

ALTER TABLE mps_semanal_observacion
    ALTER COLUMN tipo SET NOT NULL;

ALTER TABLE mps_semanal_observacion
    DROP CONSTRAINT IF EXISTS ck_mps_sem_obs_tipo;

ALTER TABLE mps_semanal_observacion
    ADD CONSTRAINT ck_mps_sem_obs_tipo
        CHECK (tipo IN ('BLOQUEANTE', 'ADVERTENCIA', 'INFORMATIVA', 'OTRO'));

CREATE INDEX IF NOT EXISTS idx_mps_sem_obs_tipo
    ON mps_semanal_observacion (tipo);
