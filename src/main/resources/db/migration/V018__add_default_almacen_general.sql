-- V018: Insert default "Almacen General" AreaOperativa
-- ID negativo (-1) para distinguir de áreas creadas por usuarios

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM area_operativa WHERE nombre = 'Almacen General'
    ) THEN
        INSERT INTO area_operativa (area_id, nombre, descripcion, responsable_id)
        OVERRIDING SYSTEM VALUE
        VALUES (-1, 'Almacen General', 'Area operativa por defecto del sistema', NULL);
    END IF;
END $$;
