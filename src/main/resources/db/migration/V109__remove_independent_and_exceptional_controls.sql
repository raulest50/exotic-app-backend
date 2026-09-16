-- Retira las dos vías laterales del motor de controles. Los controles nuevos
-- solo pueden materializarse como parte de un expediente (Batch Record).
--
-- La migración se detiene si encuentra historia creada por estas funciones:
-- ese caso requiere una decisión explícita de archivo y no una eliminación
-- automática de evidencia regulatoria.
DO $$
DECLARE
    independent_count BIGINT;
    exceptional_count BIGINT;
    exceptional_revision_count BIGINT;
    exceptional_signature_count BIGINT;
BEGIN
    SELECT COUNT(*) INTO independent_count
    FROM control_requerido
    WHERE origen = 'INDEPENDIENTE';

    SELECT COUNT(*) INTO exceptional_count
    FROM control_requerido
    WHERE agregado_excepcionalmente
       OR motivo_adicion IS NOT NULL
       OR agregado_por_id IS NOT NULL
       OR revision_adicion_id IS NOT NULL
       OR firma_adicion_id IS NOT NULL;

    SELECT COUNT(*) INTO exceptional_revision_count
    FROM batch_record_revision
    WHERE tipo = 'ADICION_CONTROL_REQUERIDO';

    SELECT COUNT(*) INTO exceptional_signature_count
    FROM batch_record_firma
    WHERE alcance = 'ADICION_CONTROL_REQUERIDO';

    IF independent_count > 0
       OR exceptional_count > 0
       OR exceptional_revision_count > 0
       OR exceptional_signature_count > 0 THEN
        RAISE EXCEPTION USING
            MESSAGE = format(
                'No se pueden retirar los controles independientes/excepcionales: independientes=%s, requisitos_excepcionales=%s, revisiones=%s, firmas=%s.',
                independent_count,
                exceptional_count,
                exceptional_revision_count,
                exceptional_signature_count
            ),
            HINT = 'Archive o defina explícitamente el tratamiento de estos registros auditados antes de aplicar V109.';
    END IF;
END $$;

DROP INDEX IF EXISTS uq_control_requerido_independiente_aplicacion;
DROP INDEX IF EXISTS uq_control_requerido_batch_aplicacion;

ALTER TABLE control_requerido
    DROP CONSTRAINT IF EXISTS chk_control_requerido_origen,
    DROP CONSTRAINT IF EXISTS chk_control_requerido_adicion,
    DROP COLUMN agregado_excepcionalmente,
    DROP COLUMN motivo_adicion,
    DROP COLUMN agregado_por_id,
    DROP COLUMN revision_adicion_id,
    DROP COLUMN firma_adicion_id;

ALTER TABLE control_requerido
    ADD CONSTRAINT chk_control_requerido_origen
        CHECK (origen IN ('BATCH_RECORD', 'LEGACY'));

CREATE UNIQUE INDEX uq_control_requerido_batch_aplicacion
    ON control_requerido(batch_record_id, version_id, COALESCE(batch_record_etapa_id, 0))
    WHERE origen = 'BATCH_RECORD';

ALTER TABLE batch_record_revision
    DROP CONSTRAINT IF EXISTS chk_batch_record_revision_tipo;

ALTER TABLE batch_record_revision
    ADD CONSTRAINT chk_batch_record_revision_tipo CHECK (
        tipo IN (
            'ENVIO_CALIDAD', 'REENVIO_CALIDAD', 'DECISION_CALIDAD',
            'SOLICITUD_REAPERTURA_RECHAZO', 'REAPERTURA_RECHAZO',
            'CORRECCION', 'CIERRE'
        )
    );

ALTER TABLE batch_record_firma
    DROP CONSTRAINT IF EXISTS chk_batch_record_firma_alcance;

ALTER TABLE batch_record_firma
    ADD CONSTRAINT chk_batch_record_firma_alcance CHECK (
        alcance IN (
            'CIERRE_ETAPA_AREA', 'CORRECCION_EXPEDIENTE',
            'REVISION_PRODUCCION', 'REVISION_CALIDAD', 'LIBERACION_LOTE',
            'SOLICITUD_REAPERTURA_RECHAZO', 'APROBACION_REAPERTURA_RECHAZO'
        )
    );
