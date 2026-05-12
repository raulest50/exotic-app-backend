CREATE TABLE IF NOT EXISTS pool_capacidad (
    pool_capacidad_id SERIAL PRIMARY KEY,
    nombre VARCHAR(255) NOT NULL,
    capacidad_diaria INTEGER NOT NULL DEFAULT 0,
    descripcion VARCHAR(255),
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uk_pool_capacidad_nombre UNIQUE (nombre)
);

ALTER TABLE categoria
    ADD COLUMN IF NOT EXISTS pool_capacidad_id INTEGER;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.table_constraints
        WHERE table_schema = current_schema()
          AND table_name = 'categoria'
          AND constraint_name = 'fk_categoria_pool_capacidad'
    ) THEN
        ALTER TABLE categoria
            ADD CONSTRAINT fk_categoria_pool_capacidad
            FOREIGN KEY (pool_capacidad_id)
            REFERENCES pool_capacidad (pool_capacidad_id)
            ON DELETE RESTRICT;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_categoria_pool_capacidad_id
    ON categoria (pool_capacidad_id);
