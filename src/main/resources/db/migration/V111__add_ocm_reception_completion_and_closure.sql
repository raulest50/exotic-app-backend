ALTER TABLE orden_compra
    ADD COLUMN fecha_recepcion_completa TIMESTAMP,
    ADD COLUMN fecha_cierre TIMESTAMP,
    ADD COLUMN origen_cierre VARCHAR(40),
    ADD COLUMN usuario_cierre_username VARCHAR(120);

CREATE INDEX idx_ocm_cierre_automatico
    ON orden_compra (fecha_recepcion_completa, orden_compra_id)
    WHERE estado = 2 AND fecha_recepcion_completa IS NOT NULL;

INSERT INTO master_directive (nombre, resumen, valor, tipo_dato, grupo, ayuda)
VALUES ('CIERRE_AUTOMATICO_OCM', 'Cierre de órdenes de compra de materiales',
        '{"modo":"DESACTIVADO","dias":null,"activadoDesde":null}',
        'JSON', 'COMPRAS_ALMACEN',
        'Cierra OCM completas inmediatamente o tras un plazo. Cada activación aplica solo a recepciones que se completen desde ese momento. Las anteriores se cierran manualmente.')
ON CONFLICT (nombre) DO NOTHING;
