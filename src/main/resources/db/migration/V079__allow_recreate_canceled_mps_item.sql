ALTER TABLE mps_semanal_item
    DROP CONSTRAINT IF EXISTS uk_mps_sem_item_dia_terminado;

CREATE UNIQUE INDEX IF NOT EXISTS uk_mps_sem_item_dia_terminado_activo
    ON mps_semanal_item (mps_dia_id, terminado_id)
    WHERE estado <> 'CANCELADO';
