-- ============================================================================
-- V045: Agregar observaciones de revision para MPS semanal
-- ============================================================================

ALTER TABLE master_production_schedule_semanal
    ADD COLUMN IF NOT EXISTS revision_numero INTEGER DEFAULT 1;

UPDATE master_production_schedule_semanal
SET revision_numero = 1
WHERE revision_numero IS NULL;

ALTER TABLE master_production_schedule_semanal
    ALTER COLUMN revision_numero SET DEFAULT 1;

ALTER TABLE master_production_schedule_semanal
    ALTER COLUMN revision_numero SET NOT NULL;

CREATE TABLE IF NOT EXISTS mps_semanal_observacion (
    observacion_id BIGSERIAL PRIMARY KEY,
    mps_id INTEGER NOT NULL,
    revision_mps INTEGER NOT NULL DEFAULT 1,
    autor_username VARCHAR(100) NOT NULL,
    mensaje TEXT NOT NULL,
    estado VARCHAR(20) NOT NULL DEFAULT 'ABIERTA',
    respuesta_correccion TEXT,
    atendida_por_username VARCHAR(100),
    fecha_atencion TIMESTAMP,
    cerrada_por_username VARCHAR(100),
    fecha_cierre TIMESTAMP,
    fecha_creacion TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_mps_semanal_observacion_mps
        FOREIGN KEY (mps_id)
        REFERENCES master_production_schedule_semanal(mps_id)
        ON DELETE CASCADE,
    CONSTRAINT ck_mps_sem_obs_estado
        CHECK (estado IN ('ABIERTA', 'ATENDIDA', 'CERRADA')),
    CONSTRAINT ck_mps_sem_obs_revision_positive
        CHECK (revision_mps >= 1)
);

CREATE INDEX IF NOT EXISTS idx_mps_sem_obs_mps_id
    ON mps_semanal_observacion (mps_id);

CREATE INDEX IF NOT EXISTS idx_mps_sem_obs_estado
    ON mps_semanal_observacion (estado);

CREATE INDEX IF NOT EXISTS idx_mps_sem_obs_mps_estado
    ON mps_semanal_observacion (mps_id, estado);

CREATE INDEX IF NOT EXISTS idx_mps_sem_obs_mps_revision
    ON mps_semanal_observacion (mps_id, revision_mps);
