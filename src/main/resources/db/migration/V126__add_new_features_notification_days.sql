INSERT INTO master_directive (nombre, resumen, valor, tipo_dato, grupo, ayuda)
VALUES (
    'NEW_FEATURES_NOTIFICATION_DAYS',
    'Días de vigencia de la notificación Nuevo',
    '7',
    'NUMERO',
    'FLEXIBILIDAD_CONTROL',
    'Acepta enteros de 0 a 365; 7 por defecto. Cuenta días de 24 horas desde la publicación de cada novedad. 0 desactiva los indicadores. También desaparecen antes si el usuario consulta la novedad y entra al módulo. El carrusel sigue disponible; cambiar el plazo no reinicia fechas ni lecturas.'
)
ON CONFLICT (nombre) DO UPDATE
SET resumen = EXCLUDED.resumen,
    tipo_dato = EXCLUDED.tipo_dato,
    grupo = EXCLUDED.grupo,
    ayuda = EXCLUDED.ayuda,
    valor = COALESCE(master_directive.valor, EXCLUDED.valor);
