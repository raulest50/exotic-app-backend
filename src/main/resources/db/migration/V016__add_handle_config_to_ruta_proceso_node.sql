-- V016: Agregar configuración de handles a ruta_proceso_node
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'ruta_proceso_node' AND column_name = 'has_left_handle'
    ) THEN
        ALTER TABLE ruta_proceso_node
            ADD COLUMN has_left_handle BOOLEAN NOT NULL DEFAULT TRUE;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'ruta_proceso_node' AND column_name = 'has_right_handle'
    ) THEN
        ALTER TABLE ruta_proceso_node
            ADD COLUMN has_right_handle BOOLEAN NOT NULL DEFAULT TRUE;
    END IF;
END $$;
