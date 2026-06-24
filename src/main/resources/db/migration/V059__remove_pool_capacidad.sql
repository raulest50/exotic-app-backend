ALTER TABLE IF EXISTS categoria
    DROP CONSTRAINT IF EXISTS fk_categoria_pool_capacidad;

DROP INDEX IF EXISTS idx_categoria_pool_capacidad_id;

ALTER TABLE IF EXISTS categoria
    DROP COLUMN IF EXISTS pool_capacidad_id;

DROP TABLE IF EXISTS pool_capacidad;
