-- Assertions to run after V101 and V102 over unified_control_engine_populated_fixture.sql.
DO $$
DECLARE
    revision_fingerprint TEXT;
BEGIN
    IF (SELECT COUNT(*) FROM control_plan_version WHERE legacy_plantilla_id IS NOT NULL) <> 2 THEN
        RAISE EXCEPTION 'No se preservaron las dos plantillas como versiones.';
    END IF;
    IF (SELECT COUNT(*) FROM control_plan_caracteristica WHERE legacy_caracteristica_id IS NOT NULL) <> 5 THEN
        RAISE EXCEPTION 'No se preservaron las cinco caracteristicas.';
    END IF;
    IF (SELECT COUNT(*) FROM control_ejecucion WHERE legacy_ejecucion_id IS NOT NULL) <> 4 THEN
        RAISE EXCEPTION 'No se preservaron las cuatro ejecuciones.';
    END IF;
    IF (SELECT COUNT(*) FROM control_ejecucion_muestra muestra
        JOIN control_ejecucion ejecucion ON ejecucion.id = muestra.ejecucion_id
        WHERE ejecucion.legacy_ejecucion_id IS NOT NULL) <> 5 THEN
        RAISE EXCEPTION 'No se preservaron las cinco muestras.';
    END IF;
    IF (SELECT COUNT(*) FROM control_ejecucion_lectura lectura
        JOIN control_ejecucion_muestra muestra ON muestra.id = lectura.muestra_id
        JOIN control_ejecucion ejecucion ON ejecucion.id = muestra.ejecucion_id
        WHERE ejecucion.legacy_ejecucion_id IS NOT NULL) <> 7 THEN
        RAISE EXCEPTION 'No se preservaron las siete lecturas.';
    END IF;
    IF EXISTS (
        SELECT 1 FROM calidad_control_proceso_ejecucion legacy
        LEFT JOIN control_ejecucion neutral ON neutral.legacy_ejecucion_id = legacy.id
        WHERE neutral.id IS NULL
    ) THEN
        RAISE EXCEPTION 'Existe una ejecucion legacy sin enlace neutral.';
    END IF;
    IF (SELECT COUNT(*) FROM control_desviacion desviacion
        JOIN control_ejecucion ejecucion ON ejecucion.id = desviacion.ejecucion_origen_id
        WHERE ejecucion.legacy_ejecucion_id IN (900001, 900004)
          AND desviacion.estado = 'ABIERTA'
          AND desviacion.disposicion IS NULL) <> 2 THEN
        RAISE EXCEPTION 'Los dos resultados no conformes no abrieron desviacion conservadora.';
    END IF;
    IF EXISTS (
        SELECT 1 FROM control_ejecucion ejecucion
        JOIN control_requerido requisito ON requisito.id = ejecucion.control_requerido_id
        WHERE ejecucion.legacy_ejecucion_id IN (900001, 900004)
          AND requisito.estado <> 'NO_CONFORME'
    ) THEN
        RAISE EXCEPTION 'Una ocurrencia con desviacion abierta no quedo NO_CONFORME.';
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM control_plan_caracteristica caracteristica
        JOIN control_unidad unidad ON unidad.id = caracteristica.unidad_id
        WHERE caracteristica.legacy_caracteristica_id = 900004
          AND unidad.codigo = 'LEGACY_SIN_UNIDAD_900004'
          AND unidad.activo = FALSE
          AND caracteristica.requiere_depuracion = TRUE
          AND caracteristica.nombre = '[LEGACY SIN NOMBRE #900004]'
          AND caracteristica.magnitud_codigo_snapshot = 'LEGACY_ID_900004'
          AND caracteristica.magnitud_simbolo_snapshot = '?'
          AND caracteristica.unidad_codigo_snapshot <> 'ADIMENSIONAL'
    ) THEN
        RAISE EXCEPTION 'La magnitud/nombre/unidad vacios no quedaron identificados para depuracion.';
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM control_plan_caracteristica caracteristica
        WHERE caracteristica.legacy_caracteristica_id = 900001
          AND caracteristica.magnitud_codigo_snapshot = 'PESO'
          AND caracteristica.magnitud_simbolo_snapshot = 'm'
    ) THEN
        RAISE EXCEPTION 'No se congelo el simbolo de una magnitud normalizada.';
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM control_plan_caracteristica caracteristica
        JOIN control_unidad unidad ON unidad.id = caracteristica.unidad_id
        WHERE caracteristica.legacy_caracteristica_id = 900003
          AND caracteristica.magnitud_codigo_snapshot = 'VISCOSIDAD_DINAMICA'
          AND caracteristica.unidad_codigo_snapshot = 'MPA_S'
          AND unidad.activo = TRUE
          AND caracteristica.requiere_depuracion = FALSE
    ) THEN
        RAISE EXCEPTION 'La variante historica mPa*s no se normalizo igual que el bridge runtime.';
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM control_plan_caracteristica caracteristica
        WHERE caracteristica.legacy_caracteristica_id = 900005
          AND caracteristica.magnitud_codigo_snapshot = 'PESO'
          AND caracteristica.unidad_codigo_snapshot = 'CP'
          AND caracteristica.requiere_depuracion = TRUE
    ) THEN
        RAISE EXCEPTION 'Una unidad de dimension incompatible no quedo marcada para depuracion.';
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM control_ejecucion ejecucion
        JOIN control_requerido requisito ON requisito.id = ejecucion.control_requerido_id
        WHERE ejecucion.legacy_ejecucion_id = 900003
          AND requisito.origen = 'LEGACY'
          AND requisito.batch_record_etapa_id = 900001
          AND requisito.version_numero_snapshot = 2
          AND requisito.tipo_orden_snapshot = 'OF'
          AND requisito.manufacturing_version_id_snapshot = 900002
          AND requisito.orden_fabricacion_operacion_id_snapshot = 900001
    ) THEN
        RAISE EXCEPTION 'La ejecucion con version distinta a la etapa no se preservo como ocurrencia OF.';
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM control_requerido requisito
        WHERE requisito.batch_record_etapa_id = 900001
          AND requisito.origen = 'BATCH_RECORD'
          AND requisito.version_numero_snapshot = 1
          AND requisito.categoria_id_snapshot = 900001
          AND requisito.tipo_orden_snapshot = 'OF'
    ) THEN
        RAISE EXCEPTION 'La ocurrencia congelada OF no heredo la categoria del terminado origen.';
    END IF;
    IF (SELECT COUNT(*) FROM batch_record_ciclo_revision WHERE batch_record_id = 900001) <> 2
       OR NOT EXISTS (
           SELECT 1 FROM batch_record_ciclo_revision
           WHERE batch_record_id = 900001 AND numero = 1
             AND estado = 'DEVUELTO_PRODUCCION' AND origen = 'ENVIO_INICIAL'
       )
       OR NOT EXISTS (
           SELECT 1 FROM batch_record_ciclo_revision
           WHERE batch_record_id = 900001 AND numero = 2
             AND estado = 'EN_REVISION' AND origen = 'REENVIO'
       ) THEN
        RAISE EXCEPTION 'Los ciclos OP no se reconstruyeron por ventanas temporales.';
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM batch_record_ciclo_revision
        WHERE batch_record_id = 900002 AND numero = 1
          AND estado = 'RECHAZADO' AND origen = 'ENVIO_INICIAL'
    ) THEN
        RAISE EXCEPTION 'El ciclo OF rechazado no se reconstruyo.';
    END IF;
    IF (SELECT ciclo_revision_actual FROM batch_record WHERE id = 900001) <> 2
       OR (SELECT ciclo_revision_actual FROM batch_record WHERE id = 900002) <> 1 THEN
        RAISE EXCEPTION 'El numero de ciclo actual no coincide con el historial reconstruido.';
    END IF;
    IF EXISTS (
        SELECT 1 FROM batch_record_decision_calidad
        WHERE id IN (900001, 900002) AND ciclo_revision_id IS NULL
    ) THEN
        RAISE EXCEPTION 'Una decision temporalmente inferible quedo sin ciclo.';
    END IF;

    SELECT MD5(STRING_AGG(
        id || '|' || batch_record_id || '|' || numero || '|' || tipo || '|'
        || contenido_canonico || '|' || contenido_sha256 || '|' || esquema_version
        || '|' || plantilla_pdf_version,
        E'\n' ORDER BY id
    )) INTO revision_fingerprint
    FROM batch_record_revision
    WHERE id BETWEEN 900001 AND 900005;
    IF revision_fingerprint <> '28335cc764f9dc1cd8b6733d060665eb' THEN
        RAISE EXCEPTION 'V101/V102 alteraron contenido, hashes o metadatos v2: %', revision_fingerprint;
    END IF;
END $$;

SELECT
    (SELECT COUNT(*) FROM control_plan_version WHERE legacy_plantilla_id IS NOT NULL) AS versiones,
    (SELECT COUNT(*) FROM control_plan_caracteristica WHERE legacy_caracteristica_id IS NOT NULL) AS caracteristicas,
    (SELECT COUNT(*) FROM control_ejecucion WHERE legacy_ejecucion_id IS NOT NULL) AS ejecuciones,
    (SELECT COUNT(*) FROM control_desviacion) AS desviaciones,
    (SELECT COUNT(*) FROM batch_record_ciclo_revision) AS ciclos,
    (SELECT COUNT(*) FROM batch_record_revision WHERE id BETWEEN 900001 AND 900005) AS revisiones_v2;
