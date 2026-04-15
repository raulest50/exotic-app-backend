-- ============================================================================
-- V027: Agregar entidad minima de Master Production Schedule semanal
-- ============================================================================
-- Fecha: 2026-04-14
-- Descripcion:
--   Crea la tabla padre master_production_schedule_semanal y agrega la
--   referencia opcional desde ordenes_produccion para futuras ODP agrupadas
--   por semana.
-- ============================================================================

CREATE TABLE IF NOT EXISTS master_production_schedule_semanal (
    mps_id SERIAL PRIMARY KEY,
    week_start_date DATE NOT NULL,
    week_end_date DATE NOT NULL,
    fecha_creacion TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE ordenes_produccion
    ADD COLUMN IF NOT EXISTS mps_id INTEGER;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_ordenes_produccion_mps'
    ) THEN
        ALTER TABLE ordenes_produccion
            ADD CONSTRAINT fk_ordenes_produccion_mps
            FOREIGN KEY (mps_id)
            REFERENCES master_production_schedule_semanal(mps_id)
            ON DELETE SET NULL;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_ordenes_produccion_mps_id
    ON ordenes_produccion (mps_id);

CREATE INDEX IF NOT EXISTS idx_mps_week_start_date
    ON master_production_schedule_semanal (week_start_date);
