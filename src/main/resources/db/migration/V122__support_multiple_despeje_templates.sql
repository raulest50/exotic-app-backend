-- Cada formulario de un area tiene identidad estable y versiones independientes.
-- Una nueva publicacion sustituye solo a la version vigente de la misma familia.
-- No se reescriben el contenido congelado, los hashes ni los intentos existentes.

CREATE TABLE despeje_plantilla (
    id BIGSERIAL PRIMARY KEY,
    area_operativa_id INTEGER NOT NULL
        REFERENCES area_operativa(area_id) ON DELETE RESTRICT,
    nombre VARCHAR(200) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_despeje_familia_area UNIQUE (id, area_operativa_id),
    CONSTRAINT chk_despeje_familia_nombre CHECK (length(btrim(nombre)) > 0)
);

CREATE INDEX idx_despeje_familia_area ON despeje_plantilla(area_operativa_id, id);

-- Antes de esta migracion, todas las versiones de un area pertenecian a un
-- mismo formulario. Se conserva esa identidad sin inferir familias por nombre.
-- El ultimo nombre es solo la etiqueta actual del catalogo; cada version y
-- cada contexto congelado conservan sus propios nombres sin modificacion.
INSERT INTO despeje_plantilla (area_operativa_id, nombre)
SELECT DISTINCT ON (area_operativa_id) area_operativa_id, nombre
FROM despeje_plantilla_version
ORDER BY area_operativa_id, numero DESC, id DESC;

ALTER TABLE despeje_plantilla_version ADD COLUMN familia_id BIGINT NULL;

UPDATE despeje_plantilla_version v
SET familia_id = f.id
FROM despeje_plantilla f
WHERE f.area_operativa_id = v.area_operativa_id;

ALTER TABLE despeje_plantilla_version
    ALTER COLUMN familia_id SET NOT NULL,
    DROP CONSTRAINT uq_despeje_plantilla_numero,
    ADD CONSTRAINT uq_despeje_plantilla_numero UNIQUE (familia_id, numero),
    ADD CONSTRAINT uq_despeje_version_familia_area
        UNIQUE (id, familia_id, area_operativa_id),
    ADD CONSTRAINT fk_despeje_version_familia_area
        FOREIGN KEY (familia_id, area_operativa_id)
        REFERENCES despeje_plantilla(id, area_operativa_id) ON DELETE RESTRICT;

DROP INDEX uq_despeje_plantilla_vigente;
DROP INDEX uq_despeje_plantilla_borrador;

CREATE UNIQUE INDEX uq_despeje_plantilla_vigente
    ON despeje_plantilla_version(familia_id) WHERE estado = 'VIGENTE';
CREATE UNIQUE INDEX uq_despeje_plantilla_borrador
    ON despeje_plantilla_version(familia_id) WHERE estado = 'BORRADOR';

ALTER TABLE despeje_linea_ejecucion ADD COLUMN familia_id BIGINT NULL;

UPDATE despeje_linea_ejecucion e
SET familia_id = v.familia_id
FROM despeje_plantilla_version v
WHERE v.id = e.plantilla_id;

ALTER TABLE despeje_linea_ejecucion
    ALTER COLUMN familia_id SET NOT NULL,
    DROP CONSTRAINT fk_despeje_ejecucion_plantilla_area,
    ADD CONSTRAINT fk_despeje_ejecucion_version_familia_area
        FOREIGN KEY (plantilla_id, familia_id, area_operativa_id)
        REFERENCES despeje_plantilla_version(id, familia_id, area_operativa_id)
        ON DELETE RESTRICT;

-- Los intentos se numeran por formulario, sin afectar a los demas formularios
-- de la misma etapa. Los indices parciales respaldan la exclusividad que el
-- servicio aplica bajo el bloqueo de la orden, la etapa y el area.
DROP INDEX uq_despeje_ejecucion_intento_op;
DROP INDEX uq_despeje_ejecucion_intento_of;

CREATE UNIQUE INDEX uq_despeje_ejecucion_intento_op
    ON despeje_linea_ejecucion(seguimiento_id, familia_id, intento)
    WHERE seguimiento_id IS NOT NULL;
CREATE UNIQUE INDEX uq_despeje_ejecucion_intento_of
    ON despeje_linea_ejecucion(operacion_id, familia_id, intento)
    WHERE operacion_id IS NOT NULL;

CREATE UNIQUE INDEX uq_despeje_ejecucion_activa_op
    ON despeje_linea_ejecucion(seguimiento_id, familia_id, revision_contexto)
    WHERE seguimiento_id IS NOT NULL
        AND estado IN ('BORRADOR', 'PENDIENTE_REVISION', 'APROBADA');
CREATE UNIQUE INDEX uq_despeje_ejecucion_activa_of
    ON despeje_linea_ejecucion(operacion_id, familia_id, revision_contexto)
    WHERE operacion_id IS NOT NULL
        AND estado IN ('BORRADOR', 'PENDIENTE_REVISION', 'APROBADA');
CREATE UNIQUE INDEX uq_despeje_ejecucion_utilizada_op
    ON despeje_linea_ejecucion(seguimiento_id, familia_id, revision_contexto)
    WHERE seguimiento_id IS NOT NULL AND estado = 'UTILIZADA';
CREATE UNIQUE INDEX uq_despeje_ejecucion_utilizada_of
    ON despeje_linea_ejecucion(operacion_id, familia_id, revision_contexto)
    WHERE operacion_id IS NOT NULL AND estado = 'UTILIZADA';

CREATE INDEX idx_despeje_ejecucion_familia
    ON despeje_linea_ejecucion(familia_id, creada_en, id);

-- El puntero singular se reemplaza por el conjunto UTILIZADA identificado por
-- ocupante, area y revision_contexto. Una inconsistencia previa no se repara ni
-- se oculta: se interrumpe la migracion conservando el estado anterior.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM area_operativa a
        LEFT JOIN despeje_linea_ejecucion e ON e.id = a.despeje_ejecucion_actual_id
        WHERE (
            num_nonnulls(a.despeje_ocupante_seguimiento_id,
                         a.despeje_ocupante_operacion_id) = 1
            AND a.despeje_ejecucion_actual_id IS NULL
        ) OR (
            a.despeje_ejecucion_actual_id IS NOT NULL
            AND (
                e.id IS NULL
                OR e.estado <> 'UTILIZADA'
                OR e.area_operativa_id IS DISTINCT FROM a.area_id
                OR e.revision_contexto IS DISTINCT FROM a.despeje_revision_contexto
                OR e.seguimiento_id IS DISTINCT FROM a.despeje_ocupante_seguimiento_id
                OR e.operacion_id IS DISTINCT FROM a.despeje_ocupante_operacion_id
            )
        )
    ) THEN
        RAISE EXCEPTION 'No se puede migrar el despeje: existe una ocupacion cuya evidencia singular no coincide con su origen, area o revision de contexto.';
    END IF;
END;
$$;

ALTER TABLE area_operativa
    DROP CONSTRAINT fk_area_despeje_ejecucion_actual,
    DROP CONSTRAINT chk_area_despeje_ejecucion_ocupante,
    DROP COLUMN despeje_ejecucion_actual_id;

COMMENT ON TABLE despeje_plantilla IS
    'Identidad estable de un formulario de despeje perteneciente a un area; varias familias pueden estar vigentes simultaneamente.';
COMMENT ON COLUMN despeje_plantilla_version.familia_id IS
    'Familia propietaria de la version; la numeracion, el borrador y la vigencia se delimitan por familia.';
COMMENT ON COLUMN despeje_linea_ejecucion.familia_id IS
    'Familia de la version congelada; permite intentos y evidencias independientes por formulario.';
