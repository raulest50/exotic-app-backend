-- =====================================================================
-- V024: Normalizar producto_id a mayusculas con precheck de colisiones
-- =====================================================================
-- Fecha: 2026-04-11
-- Descripcion:
--   1. Verifica que UPPER(producto_id) no genere colisiones.
--   2. Si hay colisiones, aborta la migracion con RAISE EXCEPTION.
--   3. Si no hay colisiones, actualiza productos.producto_id y todas sus
--      referencias relacionales, historicas y derivadas a mayusculas.
-- =====================================================================

DO $$
DECLARE
    collision_details TEXT;
BEGIN
    SELECT string_agg(
               format('%s -> %s', source_ids, normalized_id),
               E'\n'
           )
    INTO collision_details
    FROM (
        SELECT string_agg(quote_literal(producto_id), ', ' ORDER BY producto_id) AS source_ids,
               UPPER(producto_id) AS normalized_id
        FROM productos
        GROUP BY UPPER(producto_id)
        HAVING COUNT(*) > 1
    ) collisions;

    IF collision_details IS NOT NULL THEN
        RAISE EXCEPTION USING
            MESSAGE = 'Se detectaron colisiones al normalizar productos.producto_id a mayusculas',
            DETAIL = collision_details,
            HINT = 'Corrige manualmente los producto_id en conflicto antes de reiniciar la aplicacion.';
    END IF;
END $$;

CREATE TEMP TABLE tmp_producto_id_uppercase_map (
    old_id VARCHAR(255) PRIMARY KEY,
    new_id VARCHAR(255) NOT NULL UNIQUE
) ON COMMIT DROP;

INSERT INTO tmp_producto_id_uppercase_map (old_id, new_id)
SELECT producto_id, UPPER(producto_id)
FROM productos
WHERE producto_id <> UPPER(producto_id);

DO $$
DECLARE
    unexpected_fk_details TEXT;
BEGIN
    SELECT string_agg(
               format('%I.%I(%I) constraint %I', schema_name, table_name, column_name, constraint_name),
               E'\n'
           )
    INTO unexpected_fk_details
    FROM (
        SELECT nsp.nspname AS schema_name,
               rel.relname AS table_name,
               att.attname AS column_name,
               con.conname AS constraint_name
        FROM pg_constraint con
        JOIN pg_class rel
            ON rel.oid = con.conrelid
        JOIN pg_namespace nsp
            ON nsp.oid = rel.relnamespace
        JOIN pg_class refrel
            ON refrel.oid = con.confrelid
        JOIN unnest(con.conkey) WITH ORDINALITY AS child_key(attnum, ord)
            ON TRUE
        JOIN unnest(con.confkey) WITH ORDINALITY AS parent_key(attnum, ord)
            ON parent_key.ord = child_key.ord
        JOIN pg_attribute att
            ON att.attrelid = con.conrelid
           AND att.attnum = child_key.attnum
        JOIN pg_attribute refatt
            ON refatt.attrelid = con.confrelid
           AND refatt.attnum = parent_key.attnum
        WHERE con.contype = 'f'
          AND refrel.relname = 'productos'
          AND refatt.attname = 'producto_id'
    ) refs
    WHERE NOT (
        (table_name = 'movimientos' AND column_name = 'producto_id') OR
        (table_name = 'ordenes_produccion' AND column_name = 'producto_id') OR
        (table_name = 'item_orden_compra' AND column_name = 'producto_id') OR
        (table_name = 'items_factura_compra' AND column_name = 'materia_prima_id') OR
        (table_name = 'items_orden_venta' AND column_name = 'producto_id') OR
        (table_name = 'items_factura_venta' AND column_name = 'producto_id') OR
        (table_name = 'procesos_produccion' AND column_name = 'producto_id') OR
        (table_name = 'manufacturing_versions' AND column_name = 'producto_id') OR
        (table_name = 'insumos' AND column_name = 'input_producto_id') OR
        (table_name = 'insumos' AND column_name = 'output_producto_id') OR
        (table_name = 'insumos_empaque' AND column_name = 'material_id')
    );

    IF unexpected_fk_details IS NOT NULL THEN
        RAISE EXCEPTION USING
            MESSAGE = 'Se encontraron foreign keys hacia productos(producto_id) no contempladas por V024',
            DETAIL = unexpected_fk_details,
            HINT = 'Amplia la migracion antes de volver a ejecutarla.';
    END IF;
END $$;

CREATE OR REPLACE FUNCTION pg_temp.replace_keyed_ids(payload JSONB, target_key TEXT)
RETURNS JSONB
LANGUAGE plpgsql
AS $$
DECLARE
    result JSONB;
BEGIN
    IF payload IS NULL THEN
        RETURN NULL;
    END IF;

    CASE jsonb_typeof(payload)
        WHEN 'object' THEN
            SELECT COALESCE(
                       jsonb_object_agg(
                           entry.key,
                           CASE
                               WHEN entry.key = target_key
                                   AND jsonb_typeof(entry.value) = 'string'
                               THEN
                                   to_jsonb(
                                       COALESCE(
                                           (
                                               SELECT map.new_id
                                               FROM tmp_producto_id_uppercase_map map
                                               WHERE map.old_id = entry.value #>> '{}'
                                           ),
                                           entry.value #>> '{}'
                                       )
                                   )
                               ELSE
                                   pg_temp.replace_keyed_ids(entry.value, target_key)
                           END
                       ),
                       '{}'::JSONB
                   )
            INTO result
            FROM jsonb_each(payload) AS entry;

            RETURN result;

        WHEN 'array' THEN
            SELECT COALESCE(
                       jsonb_agg(pg_temp.replace_keyed_ids(entry.value, target_key)),
                       '[]'::JSONB
                   )
            INTO result
            FROM jsonb_array_elements(payload) AS entry(value);

            RETURN result;

        ELSE
            RETURN payload;
    END CASE;
END;
$$;

DO $$
DECLARE
    fk_record RECORD;
BEGIN
    IF EXISTS (SELECT 1 FROM tmp_producto_id_uppercase_map) THEN
        FOR fk_record IN
            SELECT nsp.nspname AS schema_name,
                   rel.relname AS table_name,
                   con.conname AS constraint_name
            FROM pg_constraint con
            JOIN pg_class rel
                ON rel.oid = con.conrelid
            JOIN pg_namespace nsp
                ON nsp.oid = rel.relnamespace
            JOIN pg_class refrel
                ON refrel.oid = con.confrelid
            JOIN unnest(con.conkey) WITH ORDINALITY AS child_key(attnum, ord)
                ON TRUE
            JOIN unnest(con.confkey) WITH ORDINALITY AS parent_key(attnum, ord)
                ON parent_key.ord = child_key.ord
            JOIN pg_attribute att
                ON att.attrelid = con.conrelid
               AND att.attnum = child_key.attnum
            JOIN pg_attribute refatt
                ON refatt.attrelid = con.confrelid
               AND refatt.attnum = parent_key.attnum
            WHERE con.contype = 'f'
              AND refrel.relname = 'productos'
              AND refatt.attname = 'producto_id'
              AND (
                    (rel.relname = 'movimientos' AND att.attname = 'producto_id') OR
                    (rel.relname = 'ordenes_produccion' AND att.attname = 'producto_id') OR
                    (rel.relname = 'item_orden_compra' AND att.attname = 'producto_id') OR
                    (rel.relname = 'items_factura_compra' AND att.attname = 'materia_prima_id') OR
                    (rel.relname = 'items_orden_venta' AND att.attname = 'producto_id') OR
                    (rel.relname = 'items_factura_venta' AND att.attname = 'producto_id') OR
                    (rel.relname = 'procesos_produccion' AND att.attname = 'producto_id') OR
                    (rel.relname = 'manufacturing_versions' AND att.attname = 'producto_id') OR
                    (rel.relname = 'insumos' AND att.attname = 'input_producto_id') OR
                    (rel.relname = 'insumos' AND att.attname = 'output_producto_id') OR
                    (rel.relname = 'insumos_empaque' AND att.attname = 'material_id')
                  )
        LOOP
            EXECUTE format(
                'ALTER TABLE %I.%I DROP CONSTRAINT %I',
                fk_record.schema_name,
                fk_record.table_name,
                fk_record.constraint_name
            );
        END LOOP;
    END IF;
END $$;

UPDATE movimientos m
SET producto_id = map.new_id
FROM tmp_producto_id_uppercase_map map
WHERE m.producto_id = map.old_id;

UPDATE ordenes_produccion op
SET producto_id = map.new_id
FROM tmp_producto_id_uppercase_map map
WHERE op.producto_id = map.old_id;

UPDATE item_orden_compra ioc
SET producto_id = map.new_id
FROM tmp_producto_id_uppercase_map map
WHERE ioc.producto_id = map.old_id;

UPDATE items_factura_compra ifc
SET materia_prima_id = map.new_id
FROM tmp_producto_id_uppercase_map map
WHERE ifc.materia_prima_id = map.old_id;

UPDATE items_orden_venta iov
SET producto_id = map.new_id
FROM tmp_producto_id_uppercase_map map
WHERE iov.producto_id = map.old_id;

UPDATE items_factura_venta ifv
SET producto_id = map.new_id
FROM tmp_producto_id_uppercase_map map
WHERE ifv.producto_id = map.old_id;

UPDATE procesos_produccion pp
SET producto_id = map.new_id
FROM tmp_producto_id_uppercase_map map
WHERE pp.producto_id = map.old_id;

UPDATE manufacturing_versions mv
SET producto_id = map.new_id
FROM tmp_producto_id_uppercase_map map
WHERE mv.producto_id = map.old_id;

UPDATE insumos i
SET input_producto_id = map.new_id
FROM tmp_producto_id_uppercase_map map
WHERE i.input_producto_id = map.old_id;

UPDATE insumos i
SET output_producto_id = map.new_id
FROM tmp_producto_id_uppercase_map map
WHERE i.output_producto_id = map.old_id;

UPDATE insumos_empaque ie
SET material_id = map.new_id
FROM tmp_producto_id_uppercase_map map
WHERE ie.material_id = map.old_id;

UPDATE productos p
SET producto_id = map.new_id
FROM tmp_producto_id_uppercase_map map
WHERE p.producto_id = map.old_id;

UPDATE lote l
SET batch_number = 'INIT-' || map.new_id
FROM tmp_producto_id_uppercase_map map
WHERE l.batch_number = 'INIT-' || map.old_id;

UPDATE manufacturing_versions mv
SET insumos_json = pg_temp.replace_keyed_ids(mv.insumos_json::JSONB, 'productoId')::TEXT
WHERE mv.insumos_json IS NOT NULL
  AND EXISTS (
      SELECT 1
      FROM tmp_producto_id_uppercase_map map
      WHERE mv.insumos_json LIKE '%' || map.old_id || '%'
  );

UPDATE manufacturing_versions mv
SET proceso_produccion_json = pg_temp.replace_keyed_ids(mv.proceso_produccion_json::JSONB, 'inputProductoId')::TEXT
WHERE mv.proceso_produccion_json IS NOT NULL
  AND EXISTS (
      SELECT 1
      FROM tmp_producto_id_uppercase_map map
      WHERE mv.proceso_produccion_json LIKE '%' || map.old_id || '%'
  );

UPDATE manufacturing_versions mv
SET case_pack_json = pg_temp.replace_keyed_ids(mv.case_pack_json::JSONB, 'materialId')::TEXT
WHERE mv.case_pack_json IS NOT NULL
  AND EXISTS (
      SELECT 1
      FROM tmp_producto_id_uppercase_map map
      WHERE mv.case_pack_json LIKE '%' || map.old_id || '%'
  );

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM tmp_producto_id_uppercase_map) THEN
        IF NOT EXISTS (
            SELECT 1
            FROM information_schema.table_constraints
            WHERE table_schema = current_schema()
              AND table_name = 'movimientos'
              AND constraint_name = 'fk_movimientos_producto_id_uppercase'
        ) THEN
            ALTER TABLE movimientos
                ADD CONSTRAINT fk_movimientos_producto_id_uppercase
                FOREIGN KEY (producto_id)
                REFERENCES productos(producto_id);
        END IF;

        IF NOT EXISTS (
            SELECT 1
            FROM information_schema.table_constraints
            WHERE table_schema = current_schema()
              AND table_name = 'ordenes_produccion'
              AND constraint_name = 'fk_ordenes_produccion_producto_id_uppercase'
        ) THEN
            ALTER TABLE ordenes_produccion
                ADD CONSTRAINT fk_ordenes_produccion_producto_id_uppercase
                FOREIGN KEY (producto_id)
                REFERENCES productos(producto_id);
        END IF;

        IF NOT EXISTS (
            SELECT 1
            FROM information_schema.table_constraints
            WHERE table_schema = current_schema()
              AND table_name = 'item_orden_compra'
              AND constraint_name = 'fk_item_orden_compra_producto_id_uppercase'
        ) THEN
            ALTER TABLE item_orden_compra
                ADD CONSTRAINT fk_item_orden_compra_producto_id_uppercase
                FOREIGN KEY (producto_id)
                REFERENCES productos(producto_id);
        END IF;

        IF NOT EXISTS (
            SELECT 1
            FROM information_schema.table_constraints
            WHERE table_schema = current_schema()
              AND table_name = 'items_factura_compra'
              AND constraint_name = 'fk_items_factura_compra_materia_prima_uppercase'
        ) THEN
            ALTER TABLE items_factura_compra
                ADD CONSTRAINT fk_items_factura_compra_materia_prima_uppercase
                FOREIGN KEY (materia_prima_id)
                REFERENCES productos(producto_id);
        END IF;

        IF NOT EXISTS (
            SELECT 1
            FROM information_schema.table_constraints
            WHERE table_schema = current_schema()
              AND table_name = 'items_orden_venta'
              AND constraint_name = 'fk_items_orden_venta_producto_id_uppercase'
        ) THEN
            ALTER TABLE items_orden_venta
                ADD CONSTRAINT fk_items_orden_venta_producto_id_uppercase
                FOREIGN KEY (producto_id)
                REFERENCES productos(producto_id);
        END IF;

        IF NOT EXISTS (
            SELECT 1
            FROM information_schema.table_constraints
            WHERE table_schema = current_schema()
              AND table_name = 'items_factura_venta'
              AND constraint_name = 'fk_items_factura_venta_producto_id_uppercase'
        ) THEN
            ALTER TABLE items_factura_venta
                ADD CONSTRAINT fk_items_factura_venta_producto_id_uppercase
                FOREIGN KEY (producto_id)
                REFERENCES productos(producto_id);
        END IF;

        IF NOT EXISTS (
            SELECT 1
            FROM information_schema.table_constraints
            WHERE table_schema = current_schema()
              AND table_name = 'procesos_produccion'
              AND constraint_name = 'fk_procesos_produccion_producto_id_uppercase'
        ) THEN
            ALTER TABLE procesos_produccion
                ADD CONSTRAINT fk_procesos_produccion_producto_id_uppercase
                FOREIGN KEY (producto_id)
                REFERENCES productos(producto_id)
                ON DELETE CASCADE;
        END IF;

        IF NOT EXISTS (
            SELECT 1
            FROM information_schema.table_constraints
            WHERE table_schema = current_schema()
              AND table_name = 'manufacturing_versions'
              AND constraint_name = 'fk_manufacturing_versions_producto_id_uppercase'
        ) THEN
            ALTER TABLE manufacturing_versions
                ADD CONSTRAINT fk_manufacturing_versions_producto_id_uppercase
                FOREIGN KEY (producto_id)
                REFERENCES productos(producto_id);
        END IF;

        IF NOT EXISTS (
            SELECT 1
            FROM information_schema.table_constraints
            WHERE table_schema = current_schema()
              AND table_name = 'insumos'
              AND constraint_name = 'fk_insumos_input_producto_id_uppercase'
        ) THEN
            ALTER TABLE insumos
                ADD CONSTRAINT fk_insumos_input_producto_id_uppercase
                FOREIGN KEY (input_producto_id)
                REFERENCES productos(producto_id);
        END IF;

        IF NOT EXISTS (
            SELECT 1
            FROM information_schema.table_constraints
            WHERE table_schema = current_schema()
              AND table_name = 'insumos'
              AND constraint_name = 'fk_insumos_output_producto_id_uppercase'
        ) THEN
            ALTER TABLE insumos
                ADD CONSTRAINT fk_insumos_output_producto_id_uppercase
                FOREIGN KEY (output_producto_id)
                REFERENCES productos(producto_id);
        END IF;

        IF NOT EXISTS (
            SELECT 1
            FROM information_schema.table_constraints
            WHERE table_schema = current_schema()
              AND table_name = 'insumos_empaque'
              AND constraint_name = 'fk_insumos_empaque_material_id_uppercase'
        ) THEN
            ALTER TABLE insumos_empaque
                ADD CONSTRAINT fk_insumos_empaque_material_id_uppercase
                FOREIGN KEY (material_id)
                REFERENCES productos(producto_id);
        END IF;
    END IF;
END $$;

DO $$
DECLARE
    lowercase_product_ids TEXT;
    stale_relational_refs TEXT;
    stale_init_batches TEXT;
    stale_snapshot_refs TEXT;
BEGIN
    SELECT string_agg(quote_literal(producto_id), ', ' ORDER BY producto_id)
    INTO lowercase_product_ids
    FROM productos
    WHERE producto_id <> UPPER(producto_id);

    IF lowercase_product_ids IS NOT NULL THEN
        RAISE EXCEPTION USING
            MESSAGE = 'Validacion final fallida: todavia existen producto_id con minusculas en productos',
            DETAIL = lowercase_product_ids;
    END IF;

    SELECT string_agg(ref_line, E'\n' ORDER BY ref_line)
    INTO stale_relational_refs
    FROM (
        SELECT format('movimientos.movimiento_id=%s producto_id=%L', m.movimiento_id, m.producto_id) AS ref_line
        FROM movimientos m
        WHERE m.producto_id IN (SELECT old_id FROM tmp_producto_id_uppercase_map)

        UNION ALL

        SELECT format('ordenes_produccion.orden_id=%s producto_id=%L', op.orden_id, op.producto_id)
        FROM ordenes_produccion op
        WHERE op.producto_id IN (SELECT old_id FROM tmp_producto_id_uppercase_map)

        UNION ALL

        SELECT format('item_orden_compra.item_id=%s producto_id=%L', ioc.item_id, ioc.producto_id)
        FROM item_orden_compra ioc
        WHERE ioc.producto_id IN (SELECT old_id FROM tmp_producto_id_uppercase_map)

        UNION ALL

        SELECT format('items_factura_compra.item_compra_id=%s materia_prima_id=%L', ifc.item_compra_id, ifc.materia_prima_id)
        FROM items_factura_compra ifc
        WHERE ifc.materia_prima_id IN (SELECT old_id FROM tmp_producto_id_uppercase_map)

        UNION ALL

        SELECT format('items_orden_venta.item_id=%s producto_id=%L', iov.item_id, iov.producto_id)
        FROM items_orden_venta iov
        WHERE iov.producto_id IN (SELECT old_id FROM tmp_producto_id_uppercase_map)

        UNION ALL

        SELECT format('items_factura_venta.item_factura_id=%s producto_id=%L', ifv.item_factura_id, ifv.producto_id)
        FROM items_factura_venta ifv
        WHERE ifv.producto_id IN (SELECT old_id FROM tmp_producto_id_uppercase_map)

        UNION ALL

        SELECT format('procesos_produccion.proceso_completo_id=%s producto_id=%L', pp.proceso_completo_id, pp.producto_id)
        FROM procesos_produccion pp
        WHERE pp.producto_id IN (SELECT old_id FROM tmp_producto_id_uppercase_map)

        UNION ALL

        SELECT format('manufacturing_versions.id=%s producto_id=%L', mv.id, mv.producto_id)
        FROM manufacturing_versions mv
        WHERE mv.producto_id IN (SELECT old_id FROM tmp_producto_id_uppercase_map)

        UNION ALL

        SELECT format('insumos.insumo_id=%s input_producto_id=%L', i.insumo_id, i.input_producto_id)
        FROM insumos i
        WHERE i.input_producto_id IN (SELECT old_id FROM tmp_producto_id_uppercase_map)

        UNION ALL

        SELECT format('insumos.insumo_id=%s output_producto_id=%L', i.insumo_id, i.output_producto_id)
        FROM insumos i
        WHERE i.output_producto_id IN (SELECT old_id FROM tmp_producto_id_uppercase_map)

        UNION ALL

        SELECT format('insumos_empaque.id=%s material_id=%L', ie.id, ie.material_id)
        FROM insumos_empaque ie
        WHERE ie.material_id IN (SELECT old_id FROM tmp_producto_id_uppercase_map)
    ) stale_refs;

    IF stale_relational_refs IS NOT NULL THEN
        RAISE EXCEPTION USING
            MESSAGE = 'Validacion final fallida: persisten referencias relacionales con producto_id anteriores',
            DETAIL = stale_relational_refs;
    END IF;

    SELECT string_agg(
               format('lote.id=%s batch_number=%L', l.id, l.batch_number),
               E'\n'
               ORDER BY l.id
           )
    INTO stale_init_batches
    FROM lote l
    WHERE l.batch_number IN (
        SELECT 'INIT-' || old_id
        FROM tmp_producto_id_uppercase_map
    );

    IF stale_init_batches IS NOT NULL THEN
        RAISE EXCEPTION USING
            MESSAGE = 'Validacion final fallida: persisten lotes INIT con producto_id anteriores',
            DETAIL = stale_init_batches;
    END IF;

    SELECT string_agg(snapshot_line, E'\n' ORDER BY snapshot_line)
    INTO stale_snapshot_refs
    FROM (
        SELECT format('manufacturing_versions.id=%s columna=insumos_json', mv.id) AS snapshot_line
        FROM manufacturing_versions mv
        WHERE mv.insumos_json IS NOT NULL
          AND mv.insumos_json::JSONB <> pg_temp.replace_keyed_ids(mv.insumos_json::JSONB, 'productoId')

        UNION ALL

        SELECT format('manufacturing_versions.id=%s columna=proceso_produccion_json', mv.id)
        FROM manufacturing_versions mv
        WHERE mv.proceso_produccion_json IS NOT NULL
          AND mv.proceso_produccion_json::JSONB <> pg_temp.replace_keyed_ids(mv.proceso_produccion_json::JSONB, 'inputProductoId')

        UNION ALL

        SELECT format('manufacturing_versions.id=%s columna=case_pack_json', mv.id)
        FROM manufacturing_versions mv
        WHERE mv.case_pack_json IS NOT NULL
          AND mv.case_pack_json::JSONB <> pg_temp.replace_keyed_ids(mv.case_pack_json::JSONB, 'materialId')
    ) stale_snapshots;

    IF stale_snapshot_refs IS NOT NULL THEN
        RAISE EXCEPTION USING
            MESSAGE = 'Validacion final fallida: persisten referencias historicas con producto_id anteriores en manufacturing_versions',
            DETAIL = stale_snapshot_refs;
    END IF;
END $$;
