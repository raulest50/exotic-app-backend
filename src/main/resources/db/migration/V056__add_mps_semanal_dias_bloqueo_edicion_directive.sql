INSERT INTO master_directive (nombre, resumen, valor, tipo_dato, grupo, ayuda)
VALUES (
    'MPS_SEMANAL_DIAS_BLOQUEO_EDICION',
    'Cantidad de dias bloqueados para editar MPS semanal',
    '2',
    'NUMERO',
    'FLEXIBILIDAD_CONTROL',
    'Define cuantos dias desde la fecha actual quedan bloqueados para editar el MPS semanal. Acepta valores de 0 a 7: 0 permite editar desde hoy, 1 bloquea hoy, 2 bloquea hoy y manana, y asi sucesivamente.'
)
ON CONFLICT (nombre) DO UPDATE
SET resumen = EXCLUDED.resumen,
    tipo_dato = EXCLUDED.tipo_dato,
    grupo = EXCLUDED.grupo,
    ayuda = EXCLUDED.ayuda,
    valor = COALESCE(master_directive.valor, EXCLUDED.valor);
