-- Cada version de un plan de control representa una sola aplicabilidad y ubicacion.
-- Las ejecuciones ya materializadas conservan todos sus snapshots aunque la
-- aplicabilidad adicional que las origino sea purgada.
CREATE TEMP TABLE control_aplicabilidades_adicionales ON COMMIT DROP AS
SELECT id
FROM (
    SELECT id,
           ROW_NUMBER() OVER (PARTITION BY version_id ORDER BY id ASC) AS posicion
    FROM control_plan_aplicabilidad
) ranked
WHERE ranked.posicion > 1;

UPDATE control_requerido requerido
SET aplicabilidad_id = NULL
FROM control_aplicabilidades_adicionales adicional
WHERE requerido.aplicabilidad_id = adicional.id;

DELETE FROM control_plan_aplicabilidad aplicabilidad
USING control_aplicabilidades_adicionales adicional
WHERE aplicabilidad.id = adicional.id;

ALTER TABLE control_plan_aplicabilidad
    ADD CONSTRAINT uq_control_plan_aplicabilidad_version
    UNIQUE (version_id)
    DEFERRABLE INITIALLY DEFERRED;

COMMENT ON CONSTRAINT uq_control_plan_aplicabilidad_version ON control_plan_aplicabilidad IS
    'Garantiza una unica aplicabilidad y ubicacion por version de plan de control.';
