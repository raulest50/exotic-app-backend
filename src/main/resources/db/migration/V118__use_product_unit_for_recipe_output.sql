-- La salida de receta comparte la unidad del producto en inventario.
-- V117 pudo guardar, por ejemplo, 500 G para un producto en KG: conservar 0.5 KG,
-- sin alterar insumos, coeficientes de dispensación ni snapshots históricos.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM procesos_produccion receta
        LEFT JOIN productos producto ON producto.producto_id = receta.producto_id
        LEFT JOIN unidad_medida_producto origen ON origen.codigo = receta.unidad_salida_receta
        LEFT JOIN unidad_medida_producto destino ON destino.codigo = producto.tipo_unidades
        WHERE receta.cantidad_salida_receta IS NOT NULL
          AND (producto.producto_id IS NULL OR origen.codigo IS NULL OR destino.codigo IS NULL
               OR origen.magnitud <> destino.magnitud)
    ) THEN
        RAISE EXCEPTION 'No se puede unificar la salida: hay recetas con unidades ausentes o de distinta magnitud. Revisar sus productos antes de migrar.';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM procesos_produccion receta
        JOIN productos producto ON producto.producto_id = receta.producto_id
        JOIN unidad_medida_producto origen ON origen.codigo = receta.unidad_salida_receta
        JOIN unidad_medida_producto destino ON destino.codigo = producto.tipo_unidades
        WHERE receta.cantidad_salida_receta IS NOT NULL
          AND (
              receta.cantidad_salida_receta * origen.factor_a_referencia
                  >= 1000000000000 * destino.factor_a_referencia
              -- Comprobar divisibilidad exacta a 12 decimales antes de dividir.
              OR mod(receta.cantidad_salida_receta * origen.factor_a_referencia * 1000000000000,
                     destino.factor_a_referencia) <> 0
          )
    ) THEN
        RAISE EXCEPTION 'La salida convertida excede NUMERIC(24,12). Revisar las cantidades antes de migrar; no se redondearán automáticamente.';
    END IF;
END;
$$;

UPDATE procesos_produccion receta
SET cantidad_salida_receta = receta.cantidad_salida_receta * origen.factor_a_referencia / destino.factor_a_referencia
FROM productos producto, unidad_medida_producto origen, unidad_medida_producto destino
WHERE producto.producto_id = receta.producto_id
  AND origen.codigo = receta.unidad_salida_receta
  AND destino.codigo = producto.tipo_unidades
  AND receta.cantidad_salida_receta IS NOT NULL
  AND receta.unidad_salida_receta <> producto.tipo_unidades;

ALTER TABLE procesos_produccion
    DROP CONSTRAINT ck_receta_salida,
    DROP COLUMN unidad_salida_receta,
    ADD CONSTRAINT ck_receta_salida CHECK (cantidad_salida_receta IS NULL OR cantidad_salida_receta > 0);

COMMENT ON COLUMN procesos_produccion.cantidad_salida_receta IS
    'Cantidad obtenida con la receta completa en productos.tipo_unidades. NULL identifica una receta histórica sin salida explícita.';
COMMENT ON COLUMN productos.tipo_unidades IS
    'Unidad de inventario del producto; también expresa la cantidad de salida de receta de terminados y semiterminados.';
