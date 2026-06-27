INSERT INTO master_directive (nombre, resumen, valor, tipo_dato, grupo, ayuda)
VALUES (
    'MPS_SEMANAL_PERMITIR_AGREGAR_TERMINADOS_APROBADO',
    'Permite agregar terminados nuevos en MPS semanal aprobada',
    'false',
    'BOOLEANO',
    'FLEXIBILIDAD_CONTROL',
    'Controla si una MPS aprobada o cerrada permite agregar terminados nuevos.'
)
ON CONFLICT (nombre) DO UPDATE
SET resumen = EXCLUDED.resumen,
    tipo_dato = EXCLUDED.tipo_dato,
    grupo = EXCLUDED.grupo,
    ayuda = EXCLUDED.ayuda,
    valor = COALESCE(master_directive.valor, EXCLUDED.valor);
