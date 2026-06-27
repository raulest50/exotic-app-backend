INSERT INTO master_directive (nombre, resumen, valor, tipo_dato, grupo, ayuda)
VALUES (
    'AREA_OPERATIVA_NOISE_ENABLED',
    'Habilita la medicion de ruido en tablets del Area Operativa',
    'false',
    'BOOLEANO',
    'FLEXIBILIDAD_CONTROL',
    'Cuando esta activa, el panel de Area Operativa puede tomar muestras cortas de audio desde el navegador de la tablet, convertirlas a dB relativo y enviarlas al backend. No almacena audio crudo.'
)
ON CONFLICT (nombre) DO UPDATE
SET resumen = EXCLUDED.resumen,
    tipo_dato = EXCLUDED.tipo_dato,
    grupo = EXCLUDED.grupo,
    ayuda = EXCLUDED.ayuda,
    valor = COALESCE(master_directive.valor, EXCLUDED.valor);

INSERT INTO master_directive (nombre, resumen, valor, tipo_dato, grupo, ayuda)
VALUES (
    'AREA_OPERATIVA_NOISE_INTERVAL_MINUTES',
    'Intervalo de muestreo de ruido en minutos',
    '15',
    'NUMERO',
    'FLEXIBILIDAD_CONTROL',
    'Define cada cuantos minutos la tablet intentara tomar una muestra de ruido mientras el panel de Area Operativa este activo. Acepta valores entre 10 y 60.'
)
ON CONFLICT (nombre) DO UPDATE
SET resumen = EXCLUDED.resumen,
    tipo_dato = EXCLUDED.tipo_dato,
    grupo = EXCLUDED.grupo,
    ayuda = EXCLUDED.ayuda,
    valor = COALESCE(master_directive.valor, EXCLUDED.valor);

INSERT INTO master_directive (nombre, resumen, valor, tipo_dato, grupo, ayuda)
VALUES (
    'AREA_OPERATIVA_NOISE_SAMPLE_SECONDS',
    'Tamano de muestra de ruido en segundos',
    '2',
    'NUMERO',
    'FLEXIBILIDAD_CONTROL',
    'Define cuantos segundos de audio se procesan localmente para calcular un unico valor de ruido relativo en dB. Acepta valores entre 1 y 5.'
)
ON CONFLICT (nombre) DO UPDATE
SET resumen = EXCLUDED.resumen,
    tipo_dato = EXCLUDED.tipo_dato,
    grupo = EXCLUDED.grupo,
    ayuda = EXCLUDED.ayuda,
    valor = COALESCE(master_directive.valor, EXCLUDED.valor);
