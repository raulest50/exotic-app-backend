ALTER TABLE IF EXISTS master_directive
DROP CONSTRAINT IF EXISTS master_directive_tipo_dato_check;

ALTER TABLE IF EXISTS master_directive
ADD CONSTRAINT master_directive_tipo_dato_check
CHECK (tipo_dato IN ('TEXTO', 'NUMERO', 'DECIMAL', 'BOOLEANO', 'FECHA', 'JSON'));

ALTER TABLE IF EXISTS master_directive
DROP CONSTRAINT IF EXISTS master_directive_grupo_check;

ALTER TABLE IF EXISTS master_directive
ADD CONSTRAINT master_directive_grupo_check
CHECK (grupo IN ('FLEXIBILIDAD_CONTROL', 'COMPRAS_ALMACEN'));

INSERT INTO master_directive (nombre, resumen, valor, tipo_dato, grupo, ayuda)
VALUES (
    'DISPENSACION_NO_BLOQUEA_INICIO_PRODUCCION',
    'Permite iniciar el proceso productivo sin esperar la dispensacion de materiales',
    'true',
    'BOOLEANO',
    'FLEXIBILIDAD_CONTROL',
    'Cuando esta activa, Almacen General se marca automaticamente como completado solo a nivel de seguimiento de proceso al crear la orden de produccion. No crea transacciones de almacen, no descuenta inventario y no acredita la dispensacion real.'
)
ON CONFLICT (nombre) DO UPDATE
SET resumen = EXCLUDED.resumen,
    tipo_dato = EXCLUDED.tipo_dato,
    grupo = EXCLUDED.grupo,
    ayuda = EXCLUDED.ayuda,
    valor = COALESCE(master_directive.valor, EXCLUDED.valor);

INSERT INTO master_directive (nombre, resumen, valor, tipo_dato, grupo, ayuda)
VALUES (
    'ENABLE_MASTER_SUPERMASTER_DIRECTIVES_ACCESS',
    'Permite que master vea y entre al modulo de Directivas Super Master',
    'true',
    'BOOLEANO',
    'FLEXIBILIDAD_CONTROL',
    'Cuando esta activa, el usuario master puede ver el acceso en Inicio y entrar a la ruta de Directivas Super Master. No agrega proteccion adicional sobre los endpoints API.'
)
ON CONFLICT (nombre) DO UPDATE
SET resumen = EXCLUDED.resumen,
    tipo_dato = EXCLUDED.tipo_dato,
    grupo = EXCLUDED.grupo,
    ayuda = EXCLUDED.ayuda,
    valor = COALESCE(master_directive.valor, EXCLUDED.valor);
