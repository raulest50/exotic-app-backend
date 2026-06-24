ALTER TABLE mps_semanal_item
    ADD COLUMN IF NOT EXISTS estado VARCHAR(20) NOT NULL DEFAULT 'ACTIVO';

UPDATE mps_semanal_item
SET estado = 'ACTIVO'
WHERE estado IS NULL OR BTRIM(estado) = '';

ALTER TABLE mps_semanal_item
    DROP CONSTRAINT IF EXISTS ck_mps_sem_item_numero_lotes;

ALTER TABLE mps_semanal_item
    DROP CONSTRAINT IF EXISTS ck_mps_sem_item_cantidad_total;

ALTER TABLE mps_semanal_item
    ADD CONSTRAINT ck_mps_sem_item_estado
        CHECK (estado IN ('ACTIVO', 'CANCELADO'));

ALTER TABLE mps_semanal_item
    ADD CONSTRAINT ck_mps_sem_item_numero_lotes_estado
        CHECK (
            (estado = 'ACTIVO' AND numero_lotes > 0)
            OR (estado = 'CANCELADO' AND numero_lotes = 0)
        );

ALTER TABLE mps_semanal_item
    ADD CONSTRAINT ck_mps_sem_item_cantidad_total_estado
        CHECK (
            (estado = 'ACTIVO' AND cantidad_total > 0)
            OR (estado = 'CANCELADO' AND cantidad_total = 0)
        );

CREATE INDEX IF NOT EXISTS idx_mps_sem_item_estado
    ON mps_semanal_item (estado);
