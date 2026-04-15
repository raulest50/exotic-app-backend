ALTER TABLE IF EXISTS seguimiento_orden_area
    ADD COLUMN IF NOT EXISTS fecha_estado_actual TIMESTAMP;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'seguimiento_orden_area'
        AND column_name = 'fecha_estado_actual'
    ) THEN
        UPDATE seguimiento_orden_area
        SET fecha_estado_actual = CASE
            WHEN estado = 2 AND fecha_completado IS NOT NULL THEN fecha_completado
            WHEN estado = 1 AND fecha_visible IS NOT NULL THEN fecha_visible
            ELSE fecha_creacion
        END
        WHERE fecha_estado_actual IS NULL;
    END IF;
END $$;

ALTER TABLE IF EXISTS seguimiento_orden_area
    ALTER COLUMN fecha_estado_actual SET NOT NULL;

CREATE TABLE IF NOT EXISTS seguimiento_orden_area_evento (
    id BIGSERIAL PRIMARY KEY,
    seguimiento_orden_area_id BIGINT NOT NULL REFERENCES seguimiento_orden_area(id) ON DELETE CASCADE,
    estado_origen INTEGER,
    estado_destino INTEGER NOT NULL,
    fecha_evento TIMESTAMP NOT NULL,
    actor_tipo VARCHAR(16) NOT NULL,
    usuario_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    nota VARCHAR(500)
);

CREATE INDEX IF NOT EXISTS idx_soa_evento_seguimiento_fecha
    ON seguimiento_orden_area_evento(seguimiento_orden_area_id, fecha_evento, id);

CREATE INDEX IF NOT EXISTS idx_soa_evento_usuario
    ON seguimiento_orden_area_evento(usuario_id);

INSERT INTO seguimiento_orden_area_evento (
    seguimiento_orden_area_id,
    estado_origen,
    estado_destino,
    fecha_evento,
    actor_tipo,
    usuario_id,
    nota
)
SELECT
    s.id,
    NULL,
    0,
    s.fecha_creacion,
    'SYSTEM',
    NULL,
    'Backfill historico inicial'
FROM seguimiento_orden_area s
WHERE (
        s.estado = 0
        OR (s.estado IN (1, 2) AND s.fecha_visible IS NOT NULL AND s.fecha_visible > s.fecha_creacion)
      )
  AND NOT EXISTS (
      SELECT 1
      FROM seguimiento_orden_area_evento e
      WHERE e.seguimiento_orden_area_id = s.id
        AND e.estado_origen IS NULL
        AND e.estado_destino = 0
  );

INSERT INTO seguimiento_orden_area_evento (
    seguimiento_orden_area_id,
    estado_origen,
    estado_destino,
    fecha_evento,
    actor_tipo,
    usuario_id,
    nota
)
SELECT
    s.id,
    NULL,
    1,
    COALESCE(s.fecha_visible, s.fecha_creacion),
    'SYSTEM',
    NULL,
    'Backfill historico inicial'
FROM seguimiento_orden_area s
WHERE s.estado IN (1, 2)
  AND (s.fecha_visible IS NULL OR s.fecha_visible <= s.fecha_creacion)
  AND NOT EXISTS (
      SELECT 1
      FROM seguimiento_orden_area_evento e
      WHERE e.seguimiento_orden_area_id = s.id
        AND e.estado_origen IS NULL
        AND e.estado_destino = 1
  );

INSERT INTO seguimiento_orden_area_evento (
    seguimiento_orden_area_id,
    estado_origen,
    estado_destino,
    fecha_evento,
    actor_tipo,
    usuario_id,
    nota
)
SELECT
    s.id,
    NULL,
    3,
    s.fecha_creacion,
    'SYSTEM',
    NULL,
    'Backfill historico inicial'
FROM seguimiento_orden_area s
WHERE s.estado = 3
  AND NOT EXISTS (
      SELECT 1
      FROM seguimiento_orden_area_evento e
      WHERE e.seguimiento_orden_area_id = s.id
        AND e.estado_origen IS NULL
        AND e.estado_destino = 3
  );

INSERT INTO seguimiento_orden_area_evento (
    seguimiento_orden_area_id,
    estado_origen,
    estado_destino,
    fecha_evento,
    actor_tipo,
    usuario_id,
    nota
)
SELECT
    s.id,
    0,
    1,
    s.fecha_visible,
    'SYSTEM',
    NULL,
    'Backfill historico: dependencias resueltas'
FROM seguimiento_orden_area s
WHERE s.fecha_visible IS NOT NULL
  AND s.fecha_visible > s.fecha_creacion
  AND NOT EXISTS (
      SELECT 1
      FROM seguimiento_orden_area_evento e
      WHERE e.seguimiento_orden_area_id = s.id
        AND e.estado_origen = 0
        AND e.estado_destino = 1
  );

INSERT INTO seguimiento_orden_area_evento (
    seguimiento_orden_area_id,
    estado_origen,
    estado_destino,
    fecha_evento,
    actor_tipo,
    usuario_id,
    nota
)
SELECT
    s.id,
    1,
    2,
    s.fecha_completado,
    CASE
        WHEN s.usuario_reporta_id IS NULL THEN 'SYSTEM'
        ELSE 'USER'
    END,
    s.usuario_reporta_id,
    NULLIF(BTRIM(s.observaciones), '')
FROM seguimiento_orden_area s
WHERE s.estado = 2
  AND s.fecha_completado IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM seguimiento_orden_area_evento e
      WHERE e.seguimiento_orden_area_id = s.id
        AND e.estado_origen = 1
        AND e.estado_destino = 2
  );
