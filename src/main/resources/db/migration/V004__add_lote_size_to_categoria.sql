-- Agregar columna lote_size permitiendo NULL inicialmente
ALTER TABLE categoria ADD COLUMN lote_size INTEGER;

-- Actualizar registros existentes con un valor por defecto (1)
UPDATE categoria SET lote_size = 1 WHERE lote_size IS NULL;

-- Cambiar la columna a NOT NULL
ALTER TABLE categoria ALTER COLUMN lote_size SET NOT NULL;
