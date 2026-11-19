ALTER TABLE registro_visitante
    ADD COLUMN motivo VARCHAR(40),
    ADD COLUMN motivo_otro VARCHAR(300);

-- NULL / NULL preserves visits created before the reason was collected.
ALTER TABLE registro_visitante
    ADD CONSTRAINT ck_registro_visitante_motivo CHECK (
        CASE
            WHEN motivo IS NULL THEN motivo_otro IS NULL
            WHEN motivo = 'OTROS' THEN
                motivo_otro IS NOT NULL AND length(btrim(motivo_otro)) > 0
            ELSE motivo IN (
                'ENTREGA_MATERIALES',
                'RECOGIDA_MERCANCIA',
                'MANTENIMIENTO_EQUIPOS',
                'MENSAJERIA_DOCUMENTOS',
                'REUNION_COMERCIAL',
                'CALIBRACION_VALIDACION',
                'SERVICIOS_INSTALACIONES',
                'AUDITORIA_INSPECCION',
                'CAPACITACION_ASESORIA',
                'GESTION_HUMANA'
            ) AND motivo_otro IS NULL
        END
    );
