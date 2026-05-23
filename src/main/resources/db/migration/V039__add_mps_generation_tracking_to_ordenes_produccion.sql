-- ============================================================================
-- V039: Trazabilidad de generacion de ODPs desde MPS
-- ============================================================================

ALTER TABLE ordenes_produccion
    ADD COLUMN IF NOT EXISTS mps_block_id VARCHAR(120);

ALTER TABLE ordenes_produccion
    ADD COLUMN IF NOT EXISTS mps_lote_ordinal INTEGER;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'uk_ordenes_produccion_mps_block_lote'
    ) THEN
        ALTER TABLE ordenes_produccion
            ADD CONSTRAINT uk_ordenes_produccion_mps_block_lote
            UNIQUE (mps_id, mps_block_id, mps_lote_ordinal);
    END IF;
END $$;
