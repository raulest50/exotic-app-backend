-- Adversarial V100 fixture for manually validating V101/V102 against populated data.
-- Run only in a disposable database cloned at V100.

INSERT INTO users (id, cedula, estado, nombre_completo, username)
VALUES (900001, 900001, 1, 'Usuario auditor de migracion', 'audit_control_engine');

INSERT INTO area_operativa (area_id, nombre, descripcion)
VALUES (900001, 'Area auditoria controles', 'Fixture de migracion V101/V102');

INSERT INTO categoria (categoria_id, categoria_nombre, tiempo_dias_fabricacion)
VALUES (900001, 'Categoria auditoria', 1);

INSERT INTO productos (
    tipo_producto, producto_id, cantidad_unidad, costo, iva_percentual,
    nombre, stock_minimo, categoria_id, costo_version,
    consumo_directo, requiere_orden_fabricacion
)
VALUES
    ('T', 'PT-AUDIT-V101', 1, 0, 0, 'Terminado auditoria', 0, 900001, 0, FALSE, FALSE),
    ('S', 'SEMI-AUDIT-V101', 1, 0, 0, 'Semiterminado auditoria', 0, NULL, 0, FALSE, TRUE);

INSERT INTO manufacturing_versions (id, version_number, producto_id)
VALUES
    (900001, 1, 'PT-AUDIT-V101'),
    (900002, 1, 'SEMI-AUDIT-V101');

INSERT INTO ordenes_produccion (
    orden_id, cantidad_producir, estado_orden, fecha_creacion, producto_id,
    politica_dispensacion_inicio, estado_dispensacion_materiales,
    manufacturing_version_id
)
VALUES (
    900001, 100, 2, TIMESTAMP '2026-08-01 08:00:00', 'PT-AUDIT-V101',
    'BLOQUEANTE', 'COMPLETA', 900001
);

INSERT INTO orden_fabricacion (
    orden_fabricacion_id, semiterminado_id, manufacturing_version_id,
    estado, cantidad_planificada, unidad_medida, fecha_creacion,
    creada_por_id, orden_produccion_origen_id
)
VALUES (
    900001, 'SEMI-AUDIT-V101', 900002,
    'EN_EJECUCION', 50, 'kg', TIMESTAMP '2026-08-01 09:00:00',
    900001, 900001
);

INSERT INTO orden_fabricacion_operacion (
    id, orden_fabricacion_id, area_operativa_id, frontend_node_id,
    proceso_nombre, posicion_secuencia, estado, fecha_estado_actual, version
)
VALUES (
    900001, 900001, 900001, 'of-node-audit',
    'Mezclado auditoria', 0, 2, TIMESTAMP '2026-08-01 10:00:00', 0
);

INSERT INTO lote (
    id, batch_number, production_date, orden_produccion_id, producto_id,
    estado_calidad
)
VALUES (
    900001, 'LOT-OP-AUDIT-V101', DATE '2026-08-01', 900001,
    'PT-AUDIT-V101', 'CUARENTENA'
);

INSERT INTO lote (
    id, batch_number, production_date, orden_fabricacion_id, producto_id,
    estado_calidad
)
VALUES (
    900002, 'LOT-OF-AUDIT-V101', DATE '2026-08-01', 900001,
    'SEMI-AUDIT-V101', 'CUARENTENA'
);

INSERT INTO batch_record (
    id, codigo, orden_produccion_id, lote_resultado_id,
    producto_resultado_id, manufacturing_version_id, estado,
    cantidad_planificada, unidad_medida, creado_en, creado_por_id
)
VALUES (
    900001, 'BR-OP-AUDIT-V101', 900001, 900001,
    'PT-AUDIT-V101', 900001, 'PENDIENTE_REVISION',
    100, 'kg', TIMESTAMP '2026-08-01 08:00:00', 900001
);

INSERT INTO batch_record (
    id, codigo, orden_fabricacion_id, lote_resultado_id,
    producto_resultado_id, manufacturing_version_id, estado,
    cantidad_planificada, cantidad_obtenida, unidad_medida,
    contenido_sha256, creado_en, creado_por_id, enviado_revision_en, cerrado_en
)
VALUES (
    900002, 'BR-OF-AUDIT-V101', 900001, 900002,
    'SEMI-AUDIT-V101', 900002, 'RECHAZADO',
    50, 49, 'kg', repeat('a', 64),
    TIMESTAMP '2026-08-01 09:00:00', 900001,
    TIMESTAMP '2026-08-02 10:00:00', TIMESTAMP '2026-08-02 11:00:00'
);

INSERT INTO calidad_control_proceso_plantilla (
    id, area_operativa_id, version, estado
)
VALUES
    (900001, 900001, 1, 'VIGENTE'),
    (900002, 900001, 2, 'RETIRADA');

INSERT INTO calidad_control_proceso_caracteristica (
    id, plantilla_id, nombre, tipo, unidad, orden,
    cantidad_muestras, unidades_por_muestra, limite_inferior, limite_superior
)
VALUES
    (900001, 900001, 'Peso', 'NUMERICA', 'g', 1, 1, 2, 1, 10),
    (900002, 900001, 'Envase integro', 'BOOLEANA', NULL, 2, 1, 1, NULL, NULL),
    (900003, 900002, 'Viscosidad', 'NUMERICA', 'mPa*s', 1, 1, 1, 1, 10),
    (900004, 900002, '', 'NUMERICA', NULL, 2, 1, 1, 1, 10),
    (900005, 900002, 'Peso', 'NUMERICA', 'cP', 3, 1, 1, 1, 10);

INSERT INTO batch_record_etapa (
    id, batch_record_id, area_operativa_id, nombre, secuencia, estado,
    iniciada_en, orden_fabricacion_operacion_id, control_proceso_plantilla_id
)
VALUES (
    900001, 900002, 900001, 'Mezclado auditoria', 0, 'EN_EJECUCION',
    TIMESTAMP '2026-08-01 10:00:00', 900001, 900001
);

INSERT INTO calidad_control_proceso_ejecucion (
    id, plantilla_id, lote_id, usuario_id, fecha_registro,
    observaciones, batch_record_id, batch_record_etapa_id, resultado
)
VALUES
    (900001, 900001, 900001, 900001, TIMESTAMP '2026-08-01 11:00:00',
     'OP independiente no conforme', NULL, NULL, 'NO_CONFORME'),
    (900002, 900001, 900002, 900001, TIMESTAMP '2026-08-01 12:00:00',
     'OF en etapa conforme', 900002, 900001, 'CONFORME'),
    (900003, 900002, 900002, 900001, TIMESTAMP '2026-08-01 13:00:00',
     'OF con version distinta a la congelada en etapa', 900002, 900001, 'CONFORME'),
    (900004, 900001, 900001, 900001, TIMESTAMP '2026-08-01 14:00:00',
     'Matriz incompleta sin resultado explicito', NULL, NULL, NULL);

INSERT INTO calidad_control_proceso_muestra (
    id, ejecucion_id, caracteristica_id, numero_muestra
)
VALUES
    (900001, 900001, 900001, 1),
    (900002, 900001, 900002, 1),
    (900003, 900002, 900001, 1),
    (900004, 900002, 900002, 1),
    (900005, 900003, 900003, 1);

INSERT INTO calidad_control_proceso_lectura (
    id, muestra_id, indice_unidad, valor_numerico, valor_booleano
)
VALUES
    (900001, 900001, 1, 11, NULL),
    (900002, 900001, 2, 5, NULL),
    (900003, 900002, 1, NULL, TRUE),
    (900004, 900003, 1, 5, NULL),
    (900005, 900003, 2, 6, NULL),
    (900006, 900004, 1, NULL, TRUE),
    (900007, 900005, 1, 5, NULL);

-- Dos envios para OP, con devolucion en la primera ventana y segundo ciclo abierto.
INSERT INTO batch_record_revision (
    id, batch_record_id, numero, tipo, contenido_canonico,
    contenido_sha256, esquema_version, plantilla_pdf_version,
    creada_en, creada_por_id, creada_por_username,
    creada_por_nombre, creada_por_cedula, motivo
)
VALUES
    (900001, 900001, 1, 'ENVIO_CALIDAD', '{"audit":1}', repeat('1', 64),
     'batch-record-v2', 'batch-record-pdf-v2', TIMESTAMP '2026-08-02 10:00:00',
     900001, 'audit_control_engine', 'Usuario auditor de migracion', '900001', 'Envio inicial'),
    (900002, 900001, 2, 'DECISION_CALIDAD', '{"audit":2}', repeat('2', 64),
     'batch-record-v2', 'batch-record-pdf-v2', TIMESTAMP '2026-08-02 11:00:00',
     900001, 'audit_control_engine', 'Usuario auditor de migracion', '900001', 'Devolucion'),
    (900003, 900001, 3, 'ENVIO_CALIDAD', '{"audit":3}', repeat('3', 64),
     'batch-record-v2', 'batch-record-pdf-v2', TIMESTAMP '2026-08-02 12:00:00',
     900001, 'audit_control_engine', 'Usuario auditor de migracion', '900001', 'Reenvio historico'),
    (900004, 900002, 1, 'ENVIO_CALIDAD', '{"audit":4}', repeat('4', 64),
     'batch-record-v2', 'batch-record-pdf-v2', TIMESTAMP '2026-08-02 10:00:00',
     900001, 'audit_control_engine', 'Usuario auditor de migracion', '900001', 'Envio OF'),
    (900005, 900002, 2, 'DECISION_CALIDAD', '{"audit":5}', repeat('5', 64),
     'batch-record-v2', 'batch-record-pdf-v2', TIMESTAMP '2026-08-02 11:00:00',
     900001, 'audit_control_engine', 'Usuario auditor de migracion', '900001', 'Rechazo OF');

INSERT INTO batch_record_decision_calidad (
    id, batch_record_id, decision, motivo, decidida_en, decidida_por_id, revision_id
)
VALUES
    (900001, 900001, 'DEVOLVER_A_PRODUCCION', 'Ajustar proceso',
     TIMESTAMP '2026-08-02 11:00:00', 900001, 900002),
    (900002, 900002, 'RECHAZAR', 'Resultado no aceptable',
     TIMESTAMP '2026-08-02 11:00:00', 900001, 900005);

UPDATE batch_record
SET enviado_revision_en = TIMESTAMP '2026-08-02 12:00:00'
WHERE id = 900001;
