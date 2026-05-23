-- ============================================================================
-- V040: Metadata de trazabilidad para generacion de ODPs desde MPS semanal
-- ============================================================================

ALTER TABLE master_production_schedule_semanal
    ADD COLUMN IF NOT EXISTS fecha_generacion_odps TIMESTAMP;

ALTER TABLE master_production_schedule_semanal
    ADD COLUMN IF NOT EXISTS generado_por_username VARCHAR(100);
