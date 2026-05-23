-- ============================================================================
-- V038: Agregar metadata de aprobacion al MPS semanal
-- ============================================================================

ALTER TABLE master_production_schedule_semanal
    ADD COLUMN IF NOT EXISTS fecha_aprobacion TIMESTAMP;

ALTER TABLE master_production_schedule_semanal
    ADD COLUMN IF NOT EXISTS aprobado_por_username VARCHAR(100);
