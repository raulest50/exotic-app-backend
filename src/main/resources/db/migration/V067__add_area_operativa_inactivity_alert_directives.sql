INSERT INTO master_directive (nombre, resumen, valor, tipo_dato, grupo, ayuda)
VALUES (
    'AREA_OPERATIVA_INACTIVITY_ALERT_ENABLED',
    'Habilita alertas de inactividad por terminaciones',
    'true',
    'BOOLEANO',
    'FLEXIBILIDAD_CONTROL',
    'Cuando esta activa, el monitoreo de Area Operativa puede marcar areas con carga activa que llevan demasiado tiempo sin terminaciones reportadas por el lider.'
)
ON CONFLICT (nombre) DO UPDATE
SET resumen = EXCLUDED.resumen,
    tipo_dato = EXCLUDED.tipo_dato,
    grupo = EXCLUDED.grupo,
    ayuda = EXCLUDED.ayuda,
    valor = COALESCE(master_directive.valor, EXCLUDED.valor);

INSERT INTO master_directive (nombre, resumen, valor, tipo_dato, grupo, ayuda)
VALUES (
    'AREA_OPERATIVA_INACTIVITY_THRESHOLD_MINUTES',
    'Umbral sin terminaciones para alertar inactividad',
    '20',
    'NUMERO',
    'FLEXIBILIDAD_CONTROL',
    'Define cuantos minutos puede pasar un area con carga activa sin terminaciones reportadas antes de marcar alerta. Acepta valores entre 5 y 480.'
)
ON CONFLICT (nombre) DO UPDATE
SET resumen = EXCLUDED.resumen,
    tipo_dato = EXCLUDED.tipo_dato,
    grupo = EXCLUDED.grupo,
    ayuda = EXCLUDED.ayuda,
    valor = COALESCE(master_directive.valor, EXCLUDED.valor);

INSERT INTO master_directive (nombre, resumen, valor, tipo_dato, grupo, ayuda)
VALUES (
    'AREA_OPERATIVA_INACTIVITY_CHECK_INTERVAL_MINUTES',
    'Intervalo de chequeo de alertas en monitoreo',
    '10',
    'NUMERO',
    'FLEXIBILIDAD_CONTROL',
    'Define cada cuantos minutos el tab de monitoreo consulta las alertas mientras esta abierto y visible. Acepta valores entre 5 y 20.'
)
ON CONFLICT (nombre) DO UPDATE
SET resumen = EXCLUDED.resumen,
    tipo_dato = EXCLUDED.tipo_dato,
    grupo = EXCLUDED.grupo,
    ayuda = EXCLUDED.ayuda,
    valor = COALESCE(master_directive.valor, EXCLUDED.valor);
