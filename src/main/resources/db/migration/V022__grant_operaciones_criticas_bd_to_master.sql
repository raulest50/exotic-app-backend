-- Otorga al usuario master acceso al modulo OPERACIONES_CRITICAS_BD
-- con todas sus tabs validas en nivel 1.
-- Migracion idempotente: crea solo los registros faltantes.

DO $$
DECLARE
    master_user_id BIGINT;
    operaciones_criticas_modulo_id BIGINT;
BEGIN
    SELECT id
    INTO master_user_id
    FROM users
    WHERE username = 'master'
    LIMIT 1;

    IF master_user_id IS NULL THEN
        RAISE NOTICE 'Usuario master no encontrado. Se omite asignacion de OPERACIONES_CRITICAS_BD.';
        RETURN;
    END IF;

    INSERT INTO modulo_accesos (user_id, modulo)
    VALUES (master_user_id, 'OPERACIONES_CRITICAS_BD')
    ON CONFLICT (user_id, modulo) DO NOTHING;

    SELECT id
    INTO operaciones_criticas_modulo_id
    FROM modulo_accesos
    WHERE user_id = master_user_id
      AND modulo = 'OPERACIONES_CRITICAS_BD'
    LIMIT 1;

    INSERT INTO tab_accesos (modulo_acceso_id, tab_id, nivel)
    VALUES
        (operaciones_criticas_modulo_id, 'CARGA_MASIVA_ALMACEN', 1),
        (operaciones_criticas_modulo_id, 'CARGA_MASIVA_MATERIALES', 1),
        (operaciones_criticas_modulo_id, 'CARGA_MASIVA_TERMINADOS', 1),
        (operaciones_criticas_modulo_id, 'ELIMINACIONES_FORZADAS', 1),
        (operaciones_criticas_modulo_id, 'EXPORTACION_DATOS', 1)
    ON CONFLICT (modulo_acceso_id, tab_id) DO NOTHING;
END $$;
