INSERT INTO master_directive (nombre, resumen, valor, tipo_dato, grupo, ayuda)
VALUES (
    'AREA_OPERATIVA_PANEL_HISTORICO_TOGGLE_ENABLED',
    'Habilita alternar entre semana actual e historico en Area Operativa',
    'false',
    'BOOLEANO',
    'FLEXIBILIDAD_CONTROL',
    'Cuando esta activa, el panel de Area Operativa muestra un control para que el operario alterne entre ordenes con fecha planificada de entrega en la semana actual e historico completo. Cuando esta apagada, el panel conserva la vista historica actual.'
)
ON CONFLICT (nombre) DO UPDATE
SET resumen = EXCLUDED.resumen,
    tipo_dato = EXCLUDED.tipo_dato,
    grupo = EXCLUDED.grupo,
    ayuda = EXCLUDED.ayuda,
    valor = COALESCE(master_directive.valor, EXCLUDED.valor);

CREATE INDEX IF NOT EXISTS idx_ordenes_produccion_fecha_final_planificada
    ON ordenes_produccion (fecha_final_planificada);
