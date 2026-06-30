ALTER TABLE unidad_medida_area_operativa
    DROP CONSTRAINT IF EXISTS uk_umao_area_codigo,
    DROP CONSTRAINT IF EXISTS chk_umao_cantidad_unidad_estandar_positive,
    DROP CONSTRAINT IF EXISTS chk_umao_dimension,
    DROP CONSTRAINT IF EXISTS chk_umao_unidad_estandar;

DROP INDEX IF EXISTS idx_umao_area_activo;

ALTER TABLE unidad_medida_area_operativa
    RENAME COLUMN cantidad_unidad_estandar TO relacion_estandar;

ALTER TABLE unidad_medida_area_operativa
    RENAME COLUMN unidad_estandar TO unidad_relacion;

UPDATE unidad_medida_area_operativa
SET unidad_relacion = 'U'
WHERE unidad_relacion = 'MIN';

WITH ranked_names AS (
    SELECT
        unidad_medida_area_operativa_id,
        ROW_NUMBER() OVER (
            PARTITION BY LOWER(TRIM(nombre))
            ORDER BY unidad_medida_area_operativa_id
        ) AS row_num
    FROM unidad_medida_area_operativa
)
UPDATE unidad_medida_area_operativa unidad
SET nombre = LEFT(unidad.nombre, 70) || ' - Area ' || unidad.area_operativa_id || ' #' || unidad.unidad_medida_area_operativa_id
FROM ranked_names ranked
WHERE unidad.unidad_medida_area_operativa_id = ranked.unidad_medida_area_operativa_id
  AND ranked.row_num > 1;

ALTER TABLE unidad_medida_area_operativa
    DROP COLUMN IF EXISTS codigo,
    DROP COLUMN IF EXISTS descripcion,
    DROP COLUMN IF EXISTS dimension,
    DROP COLUMN IF EXISTS principal,
    DROP COLUMN IF EXISTS discreta,
    DROP COLUMN IF EXISTS activo,
    ADD CONSTRAINT uk_umao_nombre UNIQUE (nombre),
    ADD CONSTRAINT chk_umao_relacion_estandar_positive CHECK (relacion_estandar > 0),
    ADD CONSTRAINT chk_umao_unidad_relacion CHECK (unidad_relacion IN ('ML', 'L', 'G', 'KG', 'U'));

ALTER TABLE area_operativa_categoria_unidad_medida
    ADD COLUMN IF NOT EXISTS factor_lote NUMERIC(19, 6) NOT NULL DEFAULT 1;

ALTER TABLE area_operativa_categoria_unidad_medida
    DROP CONSTRAINT IF EXISTS uk_aocum_area_categoria_unidad;

WITH ranked_associations AS (
    SELECT
        area_operativa_categoria_unidad_medida_id,
        ROW_NUMBER() OVER (
            PARTITION BY area_operativa_id, categoria_id
            ORDER BY area_operativa_categoria_unidad_medida_id
        ) AS row_num
    FROM area_operativa_categoria_unidad_medida
)
DELETE FROM area_operativa_categoria_unidad_medida association
USING ranked_associations ranked
WHERE association.area_operativa_categoria_unidad_medida_id = ranked.area_operativa_categoria_unidad_medida_id
  AND ranked.row_num > 1;

ALTER TABLE area_operativa_categoria_unidad_medida
    ADD CONSTRAINT uk_aocum_area_categoria UNIQUE (area_operativa_id, categoria_id),
    ADD CONSTRAINT chk_aocum_factor_lote_positive CHECK (factor_lote > 0);
