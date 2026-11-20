-- Reporte informativo por etapa: conserva sin cambios las ordenes historicas.
ALTER TABLE area_operativa
    ADD COLUMN requiere_reporte_produccion_etapa BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE seguimiento_orden_area
    ADD COLUMN configuracion_reporte_produccion JSONB NULL,
    ADD CONSTRAINT chk_soa_config_reporte_produccion CHECK (
        configuracion_reporte_produccion IS NULL
        OR (
            jsonb_typeof(configuracion_reporte_produccion) = 'object'
            AND jsonb_typeof(configuracion_reporte_produccion -> 'requerido') = 'boolean'
            AND jsonb_typeof(configuracion_reporte_produccion -> 'productos') = 'array'
        ) IS TRUE
    );

ALTER TABLE orden_fabricacion_operacion
    ADD COLUMN configuracion_reporte_produccion JSONB NULL,
    ADD CONSTRAINT chk_of_operacion_config_reporte_produccion CHECK (
        configuracion_reporte_produccion IS NULL
        OR (
            jsonb_typeof(configuracion_reporte_produccion) = 'object'
            AND jsonb_typeof(configuracion_reporte_produccion -> 'requerido') = 'boolean'
            AND jsonb_typeof(configuracion_reporte_produccion -> 'productos') = 'array'
        ) IS TRUE
    );

-- CASE evita evaluar jsonb_array_length sobre objetos o valores escalares.
ALTER TABLE seguimiento_orden_area_evento
    ADD COLUMN reportes_produccion JSONB NULL,
    ADD CONSTRAINT chk_soa_evento_reportes_produccion CHECK (
        reportes_produccion IS NULL
        OR (
            CASE WHEN jsonb_typeof(reportes_produccion) = 'array'
                THEN jsonb_array_length(reportes_produccion) > 0
                ELSE FALSE
            END
            AND estado_destino = 2
            AND tipo_evento = 'OPERATIVO'
            AND actor_tipo = 'USER'
        ) IS TRUE
    );

ALTER TABLE orden_fabricacion_operacion_evento
    ADD COLUMN reportes_produccion JSONB NULL,
    ADD CONSTRAINT chk_of_operacion_evento_reportes_produccion CHECK (
        reportes_produccion IS NULL
        OR (
            CASE WHEN jsonb_typeof(reportes_produccion) = 'array'
                THEN jsonb_array_length(reportes_produccion) > 0
                ELSE FALSE
            END
            AND estado_destino = 2
            AND tipo_evento = 'OPERATIVO'
            AND actor_tipo = 'USER'
        ) IS TRUE
    );

COMMENT ON COLUMN area_operativa.requiere_reporte_produccion_etapa IS
    'Exige reporte informativo al completar etapas emitidas con esta configuracion.';
COMMENT ON COLUMN seguimiento_orden_area.configuracion_reporte_produccion IS
    'Configuracion del reporte por etapa congelada al emitir la OP; NULL conserva etapas historicas.';
COMMENT ON COLUMN orden_fabricacion_operacion.configuracion_reporte_produccion IS
    'Configuracion del reporte por etapa congelada al emitir la OF; NULL conserva etapas historicas.';
COMMENT ON COLUMN seguimiento_orden_area_evento.reportes_produccion IS
    'Cantidades informativas de la finalizacion operativa de una etapa OP; no modifican inventario.';
COMMENT ON COLUMN orden_fabricacion_operacion_evento.reportes_produccion IS
    'Cantidades informativas de la finalizacion operativa de una etapa OF; no modifican inventario.';
