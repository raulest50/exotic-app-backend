INSERT INTO master_directive (nombre, resumen, valor, tipo_dato, grupo, ayuda)
VALUES (
    'MPS_SEMANAL_PERMITIR_AGREGAR_TERMINADOS_APROBADO',
    'Permite agregar terminados nuevos en MPS semanal aprobada',
    'false',
    'BOOLEANO',
    'FLEXIBILIDAD_CONTROL',
    'false: mantiene el comportamiento actual; despues de aprobar una MPS solo se pueden mover, aumentar, reducir o cancelar tarjetas existentes. true: permite agregar nuevos cards de terminados en MPS APROBADO o CERRADO. Si la MPS ya esta CERRADO o ya tiene ODPs generadas, las ODPs de los nuevos lotes se generan inmediatamente.'
)
ON CONFLICT (nombre) DO UPDATE
SET resumen = EXCLUDED.resumen,
    tipo_dato = EXCLUDED.tipo_dato,
    grupo = EXCLUDED.grupo,
    ayuda = EXCLUDED.ayuda,
    valor = COALESCE(master_directive.valor, EXCLUDED.valor);
