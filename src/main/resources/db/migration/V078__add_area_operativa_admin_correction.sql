ALTER TABLE IF EXISTS seguimiento_orden_area_evento
    ADD COLUMN IF NOT EXISTS tipo_evento VARCHAR(32);

UPDATE seguimiento_orden_area_evento
SET tipo_evento = CASE
    WHEN actor_tipo = 'SYSTEM' THEN 'SISTEMA'
    ELSE 'OPERATIVO'
END
WHERE tipo_evento IS NULL;

ALTER TABLE IF EXISTS seguimiento_orden_area_evento
    ALTER COLUMN tipo_evento SET NOT NULL;

ALTER TABLE IF EXISTS seguimiento_orden_area_evento
    ADD COLUMN IF NOT EXISTS evento_revertido_id BIGINT;

ALTER TABLE IF EXISTS seguimiento_orden_area_evento
    ADD CONSTRAINT fk_soa_evento_revertido
    FOREIGN KEY (evento_revertido_id)
    REFERENCES seguimiento_orden_area_evento(id)
    ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_soa_evento_tipo
    ON seguimiento_orden_area_evento(tipo_evento);

CREATE INDEX IF NOT EXISTS idx_soa_evento_revertido
    ON seguimiento_orden_area_evento(evento_revertido_id);

INSERT INTO master_directive (nombre, resumen, valor, tipo_dato, grupo, ayuda)
VALUES (
    'AREA_OPERATIVA_ADMIN_CORRECTION_ENABLED',
    'Habilita correcciones administrativas de estados en monitoreo de Area Operativa',
    'false',
    'BOOLEANO',
    'FLEXIBILIDAD_CONTROL',
    'Cuando esta activa, usuarios con nivel suficiente en Monitorear Areas Operativas pueden corregir estados de ordenes del area desde el monitoreo. Cada cambio queda auditado como correccion administrativa y no debe usarse como flujo operativo normal.'
)
ON CONFLICT (nombre) DO UPDATE
SET resumen = EXCLUDED.resumen,
    tipo_dato = EXCLUDED.tipo_dato,
    grupo = EXCLUDED.grupo,
    ayuda = EXCLUDED.ayuda,
    valor = COALESCE(master_directive.valor, EXCLUDED.valor);
