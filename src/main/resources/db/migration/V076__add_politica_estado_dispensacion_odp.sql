ALTER TABLE ordenes_produccion
    ADD COLUMN IF NOT EXISTS politica_dispensacion_inicio VARCHAR(30);

ALTER TABLE ordenes_produccion
    ADD COLUMN IF NOT EXISTS fecha_aplicacion_politica_dispensacion TIMESTAMP;

ALTER TABLE ordenes_produccion
    ADD COLUMN IF NOT EXISTS estado_dispensacion_materiales VARCHAR(40);

UPDATE ordenes_produccion op
SET politica_dispensacion_inicio = CASE
    WHEN EXISTS (
        SELECT 1
        FROM seguimiento_orden_area s
        WHERE s.orden_produccion_id = op.orden_id
          AND s.area_operativa_id = -1
          AND s.estado = 2
          AND s.observaciones ILIKE '%directiva maestra%'
    )
        THEN 'NO_BLOQUEANTE'
    ELSE 'BLOQUEANTE'
END
WHERE op.politica_dispensacion_inicio IS NULL;

UPDATE ordenes_produccion
SET fecha_aplicacion_politica_dispensacion = COALESCE(fecha_creacion, now())
WHERE fecha_aplicacion_politica_dispensacion IS NULL;

UPDATE ordenes_produccion op
SET estado_dispensacion_materiales = CASE
    WHEN EXISTS (
        SELECT 1
        FROM transaccion_almacen t
        WHERE t.tipo_entidad_causante = 4
          AND t.id_entidad_causante = op.orden_id
    )
        THEN 'PARCIAL'
    WHEN op.politica_dispensacion_inicio = 'NO_BLOQUEANTE'
        THEN 'LIBERADA_SIN_DISPENSACION'
    ELSE 'PENDIENTE'
END
WHERE op.estado_dispensacion_materiales IS NULL;

ALTER TABLE ordenes_produccion
    ALTER COLUMN politica_dispensacion_inicio SET NOT NULL;

ALTER TABLE ordenes_produccion
    ALTER COLUMN estado_dispensacion_materiales SET NOT NULL;

ALTER TABLE ordenes_produccion
    DROP CONSTRAINT IF EXISTS chk_ordenes_produccion_politica_dispensacion_inicio;

ALTER TABLE ordenes_produccion
    ADD CONSTRAINT chk_ordenes_produccion_politica_dispensacion_inicio
    CHECK (politica_dispensacion_inicio IN ('BLOQUEANTE', 'NO_BLOQUEANTE'));

ALTER TABLE ordenes_produccion
    DROP CONSTRAINT IF EXISTS chk_ordenes_produccion_estado_dispensacion_materiales;

ALTER TABLE ordenes_produccion
    ADD CONSTRAINT chk_ordenes_produccion_estado_dispensacion_materiales
    CHECK (estado_dispensacion_materiales IN ('PENDIENTE', 'PARCIAL', 'COMPLETA', 'LIBERADA_SIN_DISPENSACION'));

CREATE INDEX IF NOT EXISTS idx_ordenes_produccion_estado_dispensacion_materiales
    ON ordenes_produccion (estado_dispensacion_materiales);

UPDATE master_directive
SET ayuda = 'Cuando esta activa, Almacen General se marca automaticamente como completado solo a nivel de seguimiento de proceso al crear ordenes de produccion nuevas. No crea transacciones de almacen, no descuenta inventario y no acredita la dispensacion real. Para ordenes existentes use la accion retroactiva de esta pantalla.'
WHERE nombre = 'DISPENSACION_NO_BLOQUEA_INICIO_PRODUCCION';
