ALTER TABLE categoria
    ADD COLUMN IF NOT EXISTS tiempo_dias_fabricacion INTEGER;

UPDATE categoria
SET tiempo_dias_fabricacion = 0
WHERE tiempo_dias_fabricacion IS NULL;

ALTER TABLE categoria
    ALTER COLUMN tiempo_dias_fabricacion SET DEFAULT 0;

ALTER TABLE categoria
    ALTER COLUMN tiempo_dias_fabricacion SET NOT NULL;
