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
