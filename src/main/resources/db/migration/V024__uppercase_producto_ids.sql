-- ============================================================================
-- V024: Normalizar producto_id, resolver duplicados conocidos con ".0"
--       y subir referencias a mayusculas
-- ============================================================================
-- Fecha: 2026-04-11
-- Descripcion:
--   1. Reasigna/elimina duplicados historicos conocidos 10009.0 -> 10009
--      y 10013.0 -> 10013, si existen.
--   2. Verifica que no queden foreign keys hacia productos(producto_id)
--      fuera del alcance de la migracion.
--   3. Verifica que UPPER(producto_id) no genere colisiones.
--   4. Actualiza productos.producto_id y todas sus referencias relacionales,
--      historicas y derivadas.
-- ============================================================================

CREATE TEMP TABLE tmp_producto_id_forced_merge_map (
    old_id VARCHAR(255) PRIMARY KEY,
    new_id VARCHAR(255) NOT NULL,
    target_exists BOOLEAN NOT NULL
) ON COMMIT DROP;

INSERT INTO tmp_producto_id_forced_merge_map (old_id, new_id, target_exists)
SELECT merge_rule.old_id,
       merge_rule.new_id,
       EXISTS (
           SELECT 1
           FROM productos p_target
           WHERE p_target.producto_id = merge_rule.new_id
       ) AS target_exists
FROM (
    VALUES
        ('10009.0', '10009'),
        ('10013.0', '10013')
) AS merge_rule(old_id, new_id)
WHERE EXISTS (
    SELECT 1
    FROM productos p_source
    WHERE p_source.producto_id = merge_rule.old_id
);

CREATE TEMP TABLE tmp_producto_id_active_map (
    old_id VARCHAR(255) PRIMARY KEY,
    new_id VARCHAR(255) NOT NULL
) ON COMMIT DROP;

CREATE TEMP TABLE tmp_producto_id_applied_map (
    old_id VARCHAR(255) PRIMARY KEY,
    new_id VARCHAR(255) NOT NULL
) ON COMMIT DROP;

DO $$
DECLARE
    fk_record RECORD;
    total_fk_count INTEGER := 0;
    unexpected_fk_count INTEGER := 0;
    unexpected_table_count INTEGER := 0;
    unexpected_table_names TEXT;
    unexpected_fk_details TEXT := '';
    total_rows BIGINT;
    non_null_rows BIGINT;
    distinct_fk_values BIGINT;
    classification TEXT;
BEGIN
    CREATE TEMP TABLE tmp_producto_fk_refs (
        schema_name TEXT NOT NULL,
        table_name TEXT NOT NULL,
        column_name TEXT NOT NULL,
        constraint_name TEXT NOT NULL,
        constraint_oid OID NOT NULL,
        table_oid OID NOT NULL,
        referenced_table TEXT NOT NULL,
        referenced_column TEXT NOT NULL,
        constraint_def TEXT NOT NULL,
        convalidated BOOLEAN NOT NULL,
        is_handled BOOLEAN NOT NULL,
        is_known_model_table BOOLEAN NOT NULL
    ) ON COMMIT DROP;

    INSERT INTO tmp_producto_fk_refs (
        schema_name,
        table_name,
        column_name,
        constraint_name,
        constraint_oid,
        table_oid,
        referenced_table,
        referenced_column,
        constraint_def,
        convalidated,
        is_handled,
        is_known_model_table
    )
    SELECT nsp.nspname AS schema_name,
           rel.relname AS table_name,
           att.attname AS column_name,
           con.conname AS constraint_name,
           con.oid AS constraint_oid,
           rel.oid AS table_oid,
           refrel.relname AS referenced_table,
           refatt.attname AS referenced_column,
           pg_get_constraintdef(con.oid, true) AS constraint_def,
           con.convalidated,
           (
               (rel.relname = 'movimientos' AND att.attname = 'producto_id') OR
               (rel.relname = 'movimiento_reserva' AND att.attname = 'producto_id') OR
               (rel.relname = 'planificacion_produccion' AND att.attname = 'producto_id') OR
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
           ) AS is_handled,
           rel.relname IN (
               'productos',
               'movimientos',
               'movimiento_reserva',
               'planificacion_produccion',
               'ordenes_produccion',
               'item_orden_compra',
               'items_factura_compra',
               'items_orden_venta',
               'items_factura_venta',
               'procesos_produccion',
               'manufacturing_versions',
               'insumos',
               'insumos_empaque',
               'transaccion_almacen',
               'lote',
               'orden_compra',
               'facturas_compras',
               'seguimiento_orden_area',
               'ruta_proceso_cat',
               'ruta_proceso_node',
               'ruta_proceso_edge',
               'proceso_produccion',
               'proceso_recurso',
               'proceso_fabricacion_nodo',
               'proceso_fabricacion_edge',
               'area_operativa',
               'categoria',
               'familia',
               'proveedores',
               'contacto_proveedor',
               'vendedores',
               'ordenes_venta',
               'facturas_venta',
               'clientes',
               'users',
               'maestra_notificacion',
               'maestra_notificacion_users',
               'modulo_accesos',
               'tab_accesos',
               'password_reset_tokens',
               'asiento_contable',
               'linea_asiento_contable',
               'cuenta',
               'periodo_contable',
               'activo',
               'orden_compra_activo',
               'item_orden_compra_activo',
               'factura_compra_activo',
               'incorporacion_activo_header',
               'incorporacion_activo_line',
               'depreciacion_activo',
               'documento_baja_activo',
               'mantenimiento_activo',
               'traslado_activo',
               'integrante_personal',
               'doc_tran_de_personal',
               'super_master_config',
               'super_master_verification_codes'
           ) AS is_known_model_table
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
      AND refatt.attname = 'producto_id';

    SELECT COUNT(*) INTO total_fk_count
    FROM tmp_producto_fk_refs;

    SELECT COUNT(*) INTO unexpected_fk_count
    FROM tmp_producto_fk_refs
    WHERE NOT is_handled;

    IF unexpected_fk_count > 0 THEN
        SELECT COUNT(DISTINCT table_name) INTO unexpected_table_count
        FROM tmp_producto_fk_refs
        WHERE NOT is_handled;

        SELECT string_agg(
                   DISTINCT format('%I.%I', schema_name, table_name),
                   ', '
                   ORDER BY format('%I.%I', schema_name, table_name)
               )
        INTO unexpected_table_names
        FROM tmp_producto_fk_refs
        WHERE NOT is_handled;

        FOR fk_record IN
            SELECT *
            FROM tmp_producto_fk_refs
            WHERE NOT is_handled
            ORDER BY schema_name, table_name, column_name, constraint_name
        LOOP
            EXECUTE format('SELECT COUNT(*) FROM %I.%I', fk_record.schema_name, fk_record.table_name)
            INTO total_rows;

            EXECUTE format(
                'SELECT COUNT(*) FROM %I.%I WHERE %I IS NOT NULL',
                fk_record.schema_name,
                fk_record.table_name,
                fk_record.column_name
            )
            INTO non_null_rows;

            EXECUTE format(
                'SELECT COUNT(DISTINCT %I) FROM %I.%I WHERE %I IS NOT NULL',
                fk_record.column_name,
                fk_record.schema_name,
                fk_record.table_name,
                fk_record.column_name
            )
            INTO distinct_fk_values;

            classification := CASE
                WHEN fk_record.is_handled THEN 'KNOWN_HANDLED'
                WHEN fk_record.is_known_model_table THEN 'UNEXPECTED'
                ELSE 'LIKELY_SCHEMA_DRIFT'
            END;

            unexpected_fk_details := unexpected_fk_details
                || CASE WHEN unexpected_fk_details = '' THEN '' ELSE E'\n\n' END
                || format(
                    '[%s] %I.%I(%I)%sconstraint=%I%sconstraint_def=%s%sreferenced=%I(%I)%sconvalidated=%s%sconstraint_oid=%s table_oid=%s%srow_count=%s non_null_fk_rows=%s distinct_fk_values=%s',
                    classification,
                    fk_record.schema_name,
                    fk_record.table_name,
                    fk_record.column_name,
                    E'\n',
                    fk_record.constraint_name,
                    E'\n',
                    fk_record.constraint_def,
                    E'\n',
                    fk_record.referenced_table,
                    fk_record.referenced_column,
                    E'\n',
                    fk_record.convalidated,
                    E'\n',
                    fk_record.constraint_oid,
                    fk_record.table_oid,
                    E'\n',
                    total_rows,
                    non_null_rows,
                    distinct_fk_values
                );
        END LOOP;

        RAISE EXCEPTION USING
            MESSAGE = format(
                'Se encontraron %s foreign keys hacia productos(producto_id) no contempladas por V024, repartidas en %s tabla(s): %s',
                unexpected_fk_count,
                unexpected_table_count,
                unexpected_table_names
            ),
            DETAIL = format(
                'FKs totales hacia productos(producto_id): %s%sFKs inesperadas: %s%sTablas inesperadas: %s%sDetalle por FK:%s%s',
                total_fk_count,
                E'\n',
                unexpected_fk_count,
                E'\n',
                unexpected_table_names,
                E'\n\n',
                E'\n',
                unexpected_fk_details
            ),
            HINT = 'La BD contiene referencias a productos fuera del modelo esperado por V024. Revise si la tabla es drift historico o una dependencia funcional activa. Si debe migrarse, agreguela al allowlist y al bloque de updates; si es drift sin uso, elimine la FK o trate esa tabla antes de rerun.';
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
                                               FROM tmp_producto_id_active_map map
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

CREATE OR REPLACE FUNCTION pg_temp.remap_json_text(payload_text TEXT, target_key TEXT)
RETURNS TEXT
LANGUAGE plpgsql
AS $$
BEGIN
    IF payload_text IS NULL THEN
        RETURN NULL;
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM tmp_producto_id_active_map map
        WHERE payload_text LIKE '%' || map.old_id || '%'
    ) THEN
        RETURN payload_text;
    END IF;

    RETURN pg_temp.replace_keyed_ids(payload_text::JSONB, target_key)::TEXT;
END;
$$;

CREATE OR REPLACE FUNCTION pg_temp.remap_json_oid(payload_oid OID, target_key TEXT)
RETURNS OID
LANGUAGE plpgsql
AS $$
DECLARE
    original_text TEXT;
    remapped_text TEXT;
BEGIN
    IF payload_oid IS NULL THEN
        RETURN NULL;
    END IF;

    original_text := convert_from(lo_get(payload_oid), 'UTF8');
    remapped_text := pg_temp.remap_json_text(original_text, target_key);

    IF remapped_text = original_text THEN
        RETURN payload_oid;
    END IF;

    RETURN lo_from_bytea(0, convert_to(remapped_text, 'UTF8'));
END;
$$;

CREATE OR REPLACE FUNCTION pg_temp.apply_active_producto_id_map_to_refs()
RETURNS VOID
LANGUAGE plpgsql
AS $$
BEGIN
    UPDATE movimientos m
    SET producto_id = map.new_id
    FROM tmp_producto_id_active_map map
    WHERE m.producto_id = map.old_id;

    UPDATE movimiento_reserva mr
    SET producto_id = map.new_id
    FROM tmp_producto_id_active_map map
    WHERE mr.producto_id = map.old_id;

    UPDATE planificacion_produccion pp
    SET producto_id = map.new_id
    FROM tmp_producto_id_active_map map
    WHERE pp.producto_id = map.old_id;

    UPDATE ordenes_produccion op
    SET producto_id = map.new_id
    FROM tmp_producto_id_active_map map
    WHERE op.producto_id = map.old_id;

    UPDATE item_orden_compra ioc
    SET producto_id = map.new_id
    FROM tmp_producto_id_active_map map
    WHERE ioc.producto_id = map.old_id;

    UPDATE items_factura_compra ifc
    SET materia_prima_id = map.new_id
    FROM tmp_producto_id_active_map map
    WHERE ifc.materia_prima_id = map.old_id;

    UPDATE items_orden_venta iov
    SET producto_id = map.new_id
    FROM tmp_producto_id_active_map map
    WHERE iov.producto_id = map.old_id;

    UPDATE items_factura_venta ifv
    SET producto_id = map.new_id
    FROM tmp_producto_id_active_map map
    WHERE ifv.producto_id = map.old_id;

    UPDATE procesos_produccion pp
    SET producto_id = map.new_id
    FROM tmp_producto_id_active_map map
    WHERE pp.producto_id = map.old_id;

    UPDATE manufacturing_versions mv
    SET producto_id = map.new_id
    FROM tmp_producto_id_active_map map
    WHERE mv.producto_id = map.old_id;

    UPDATE insumos i
    SET input_producto_id = map.new_id
    FROM tmp_producto_id_active_map map
    WHERE i.input_producto_id = map.old_id;

    UPDATE insumos i
    SET output_producto_id = map.new_id
    FROM tmp_producto_id_active_map map
    WHERE i.output_producto_id = map.old_id;

    UPDATE insumos_empaque ie
    SET material_id = map.new_id
    FROM tmp_producto_id_active_map map
    WHERE ie.material_id = map.old_id;

    UPDATE lote l
    SET batch_number = 'INIT-' || map.new_id
    FROM tmp_producto_id_active_map map
    WHERE l.batch_number = 'INIT-' || map.old_id;
END;
$$;

CREATE OR REPLACE FUNCTION pg_temp.apply_active_producto_id_map_to_snapshots()
RETURNS VOID
LANGUAGE plpgsql
AS $$
DECLARE
    insumos_json_type TEXT;
    proceso_json_type TEXT;
    case_pack_json_type TEXT;
BEGIN
    SELECT format_type(a.atttypid, a.atttypmod)
    INTO insumos_json_type
    FROM pg_attribute a
    JOIN pg_class c
        ON c.oid = a.attrelid
    WHERE c.relname = 'manufacturing_versions'
      AND a.attname = 'insumos_json'
      AND a.attnum > 0
      AND NOT a.attisdropped;

    SELECT format_type(a.atttypid, a.atttypmod)
    INTO proceso_json_type
    FROM pg_attribute a
    JOIN pg_class c
        ON c.oid = a.attrelid
    WHERE c.relname = 'manufacturing_versions'
      AND a.attname = 'proceso_produccion_json'
      AND a.attnum > 0
      AND NOT a.attisdropped;

    SELECT format_type(a.atttypid, a.atttypmod)
    INTO case_pack_json_type
    FROM pg_attribute a
    JOIN pg_class c
        ON c.oid = a.attrelid
    WHERE c.relname = 'manufacturing_versions'
      AND a.attname = 'case_pack_json'
      AND a.attnum > 0
      AND NOT a.attisdropped;

    IF insumos_json_type = 'oid' THEN
        UPDATE manufacturing_versions
        SET insumos_json = pg_temp.remap_json_oid(insumos_json, 'productoId')
        WHERE insumos_json IS NOT NULL;
    ELSIF insumos_json_type = 'text' OR insumos_json_type = 'json' OR insumos_json_type = 'jsonb'
       OR insumos_json_type LIKE 'character varying%' THEN
        EXECUTE format(
            'UPDATE manufacturing_versions
             SET insumos_json = %s
             WHERE insumos_json IS NOT NULL',
            CASE
                WHEN insumos_json_type = 'json' THEN 'pg_temp.remap_json_text(insumos_json::TEXT, ''productoId'')::JSON'
                WHEN insumos_json_type = 'jsonb' THEN 'pg_temp.remap_json_text(insumos_json::TEXT, ''productoId'')::JSONB'
                ELSE 'pg_temp.remap_json_text(insumos_json::TEXT, ''productoId'')'
            END
        );
    ELSE
        RAISE EXCEPTION 'Tipo no soportado para manufacturing_versions.insumos_json: %', insumos_json_type;
    END IF;

    IF proceso_json_type = 'oid' THEN
        UPDATE manufacturing_versions
        SET proceso_produccion_json = pg_temp.remap_json_oid(proceso_produccion_json, 'inputProductoId')
        WHERE proceso_produccion_json IS NOT NULL;
    ELSIF proceso_json_type = 'text' OR proceso_json_type = 'json' OR proceso_json_type = 'jsonb'
       OR proceso_json_type LIKE 'character varying%' THEN
        EXECUTE format(
            'UPDATE manufacturing_versions
             SET proceso_produccion_json = %s
             WHERE proceso_produccion_json IS NOT NULL',
            CASE
                WHEN proceso_json_type = 'json' THEN 'pg_temp.remap_json_text(proceso_produccion_json::TEXT, ''inputProductoId'')::JSON'
                WHEN proceso_json_type = 'jsonb' THEN 'pg_temp.remap_json_text(proceso_produccion_json::TEXT, ''inputProductoId'')::JSONB'
                ELSE 'pg_temp.remap_json_text(proceso_produccion_json::TEXT, ''inputProductoId'')'
            END
        );
    ELSE
        RAISE EXCEPTION 'Tipo no soportado para manufacturing_versions.proceso_produccion_json: %', proceso_json_type;
    END IF;

    IF case_pack_json_type = 'oid' THEN
        UPDATE manufacturing_versions
        SET case_pack_json = pg_temp.remap_json_oid(case_pack_json, 'materialId')
        WHERE case_pack_json IS NOT NULL;
    ELSIF case_pack_json_type = 'text' OR case_pack_json_type = 'json' OR case_pack_json_type = 'jsonb'
       OR case_pack_json_type LIKE 'character varying%' THEN
        EXECUTE format(
            'UPDATE manufacturing_versions
             SET case_pack_json = %s
             WHERE case_pack_json IS NOT NULL',
            CASE
                WHEN case_pack_json_type = 'json' THEN 'pg_temp.remap_json_text(case_pack_json::TEXT, ''materialId'')::JSON'
                WHEN case_pack_json_type = 'jsonb' THEN 'pg_temp.remap_json_text(case_pack_json::TEXT, ''materialId'')::JSONB'
                ELSE 'pg_temp.remap_json_text(case_pack_json::TEXT, ''materialId'')'
            END
        );
    ELSE
        RAISE EXCEPTION 'Tipo no soportado para manufacturing_versions.case_pack_json: %', case_pack_json_type;
    END IF;
END;
$$;

DO $$
DECLARE
    fk_record RECORD;
BEGIN
    IF EXISTS (SELECT 1 FROM tmp_producto_id_forced_merge_map)
       OR EXISTS (SELECT 1 FROM productos WHERE producto_id <> UPPER(producto_id)) THEN
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
                    (rel.relname = 'movimiento_reserva' AND att.attname = 'producto_id') OR
                    (rel.relname = 'planificacion_produccion' AND att.attname = 'producto_id') OR
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

TRUNCATE TABLE tmp_producto_id_active_map;

INSERT INTO tmp_producto_id_active_map (old_id, new_id)
SELECT old_id, new_id
FROM tmp_producto_id_forced_merge_map;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM tmp_producto_id_active_map) THEN
        PERFORM pg_temp.apply_active_producto_id_map_to_refs();
        PERFORM pg_temp.apply_active_producto_id_map_to_snapshots();

        UPDATE productos p
        SET producto_id = map.new_id
        FROM tmp_producto_id_forced_merge_map map
        WHERE p.producto_id = map.old_id
          AND NOT map.target_exists;

        DELETE FROM productos p
        USING tmp_producto_id_forced_merge_map map
        WHERE p.producto_id = map.old_id
          AND map.target_exists;

        INSERT INTO tmp_producto_id_applied_map (old_id, new_id)
        SELECT old_id, new_id
        FROM tmp_producto_id_active_map
        ON CONFLICT (old_id) DO UPDATE
        SET new_id = EXCLUDED.new_id;
    END IF;
END $$;

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
               normalized_id
        FROM (
            SELECT producto_id,
                   CASE
                       WHEN producto_id ~ '^[0-9]+\.0$'
                       THEN regexp_replace(producto_id, '\.0$', '')
                       ELSE producto_id
                   END AS normalized_id
            FROM productos
        ) normalized
        GROUP BY normalized_id
        HAVING COUNT(*) > 1
    ) collisions;

    IF collision_details IS NOT NULL THEN
        RAISE EXCEPTION USING
            MESSAGE = 'Se detectaron colisiones al remover el sufijo .0 de productos.producto_id',
            DETAIL = collision_details,
            HINT = 'Corrige manualmente los producto_id numericos con sufijo .0 en conflicto antes de reiniciar la aplicacion.';
    END IF;
END $$;

CREATE TEMP TABLE tmp_producto_id_strip_dot_zero_map (
    old_id VARCHAR(255) PRIMARY KEY,
    new_id VARCHAR(255) NOT NULL UNIQUE
) ON COMMIT DROP;

INSERT INTO tmp_producto_id_strip_dot_zero_map (old_id, new_id)
SELECT producto_id,
       regexp_replace(producto_id, '\.0$', '')
FROM productos
WHERE producto_id ~ '^[0-9]+\.0$';

TRUNCATE TABLE tmp_producto_id_active_map;

INSERT INTO tmp_producto_id_active_map (old_id, new_id)
SELECT old_id, new_id
FROM tmp_producto_id_strip_dot_zero_map;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM tmp_producto_id_active_map) THEN
        PERFORM pg_temp.apply_active_producto_id_map_to_refs();
        PERFORM pg_temp.apply_active_producto_id_map_to_snapshots();

        UPDATE productos p
        SET producto_id = map.new_id
        FROM tmp_producto_id_active_map map
        WHERE p.producto_id = map.old_id;

        INSERT INTO tmp_producto_id_applied_map (old_id, new_id)
        SELECT old_id, new_id
        FROM tmp_producto_id_active_map
        ON CONFLICT (old_id) DO UPDATE
        SET new_id = EXCLUDED.new_id;
    END IF;
END $$;

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

TRUNCATE TABLE tmp_producto_id_active_map;

INSERT INTO tmp_producto_id_active_map (old_id, new_id)
SELECT old_id, new_id
FROM tmp_producto_id_uppercase_map;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM tmp_producto_id_active_map) THEN
        PERFORM pg_temp.apply_active_producto_id_map_to_refs();
        PERFORM pg_temp.apply_active_producto_id_map_to_snapshots();

        UPDATE productos p
        SET producto_id = map.new_id
        FROM tmp_producto_id_active_map map
        WHERE p.producto_id = map.old_id;

        INSERT INTO tmp_producto_id_applied_map (old_id, new_id)
        SELECT old_id, new_id
        FROM tmp_producto_id_active_map
        ON CONFLICT (old_id) DO UPDATE
        SET new_id = EXCLUDED.new_id;
    END IF;
END $$;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM tmp_producto_id_applied_map) THEN
        IF NOT EXISTS (
            SELECT 1
            FROM information_schema.table_constraints
            WHERE table_schema = current_schema()
              AND table_name = 'movimientos'
              AND constraint_name = 'fk_movimientos_producto_id_v024'
        ) THEN
            ALTER TABLE movimientos
                ADD CONSTRAINT fk_movimientos_producto_id_v024
                FOREIGN KEY (producto_id)
                REFERENCES productos(producto_id);
        END IF;

        IF NOT EXISTS (
            SELECT 1
            FROM information_schema.table_constraints
            WHERE table_schema = current_schema()
              AND table_name = 'movimiento_reserva'
              AND constraint_name = 'fk_movimiento_reserva_producto_id_v024'
        ) THEN
            ALTER TABLE movimiento_reserva
                ADD CONSTRAINT fk_movimiento_reserva_producto_id_v024
                FOREIGN KEY (producto_id)
                REFERENCES productos(producto_id);
        END IF;

        IF NOT EXISTS (
            SELECT 1
            FROM information_schema.table_constraints
            WHERE table_schema = current_schema()
              AND table_name = 'planificacion_produccion'
              AND constraint_name = 'fk_planificacion_produccion_producto_id_v024'
        ) THEN
            ALTER TABLE planificacion_produccion
                ADD CONSTRAINT fk_planificacion_produccion_producto_id_v024
                FOREIGN KEY (producto_id)
                REFERENCES productos(producto_id);
        END IF;

        IF NOT EXISTS (
            SELECT 1
            FROM information_schema.table_constraints
            WHERE table_schema = current_schema()
              AND table_name = 'ordenes_produccion'
              AND constraint_name = 'fk_ordenes_produccion_producto_id_v024'
        ) THEN
            ALTER TABLE ordenes_produccion
                ADD CONSTRAINT fk_ordenes_produccion_producto_id_v024
                FOREIGN KEY (producto_id)
                REFERENCES productos(producto_id);
        END IF;

        IF NOT EXISTS (
            SELECT 1
            FROM information_schema.table_constraints
            WHERE table_schema = current_schema()
              AND table_name = 'item_orden_compra'
              AND constraint_name = 'fk_item_orden_compra_producto_id_v024'
        ) THEN
            ALTER TABLE item_orden_compra
                ADD CONSTRAINT fk_item_orden_compra_producto_id_v024
                FOREIGN KEY (producto_id)
                REFERENCES productos(producto_id);
        END IF;

        IF NOT EXISTS (
            SELECT 1
            FROM information_schema.table_constraints
            WHERE table_schema = current_schema()
              AND table_name = 'items_factura_compra'
              AND constraint_name = 'fk_items_factura_compra_materia_prima_v024'
        ) THEN
            ALTER TABLE items_factura_compra
                ADD CONSTRAINT fk_items_factura_compra_materia_prima_v024
                FOREIGN KEY (materia_prima_id)
                REFERENCES productos(producto_id);
        END IF;

        IF NOT EXISTS (
            SELECT 1
            FROM information_schema.table_constraints
            WHERE table_schema = current_schema()
              AND table_name = 'items_orden_venta'
              AND constraint_name = 'fk_items_orden_venta_producto_id_v024'
        ) THEN
            ALTER TABLE items_orden_venta
                ADD CONSTRAINT fk_items_orden_venta_producto_id_v024
                FOREIGN KEY (producto_id)
                REFERENCES productos(producto_id);
        END IF;

        IF NOT EXISTS (
            SELECT 1
            FROM information_schema.table_constraints
            WHERE table_schema = current_schema()
              AND table_name = 'items_factura_venta'
              AND constraint_name = 'fk_items_factura_venta_producto_id_v024'
        ) THEN
            ALTER TABLE items_factura_venta
                ADD CONSTRAINT fk_items_factura_venta_producto_id_v024
                FOREIGN KEY (producto_id)
                REFERENCES productos(producto_id);
        END IF;

        IF NOT EXISTS (
            SELECT 1
            FROM information_schema.table_constraints
            WHERE table_schema = current_schema()
              AND table_name = 'procesos_produccion'
              AND constraint_name = 'fk_procesos_produccion_producto_id_v024'
        ) THEN
            ALTER TABLE procesos_produccion
                ADD CONSTRAINT fk_procesos_produccion_producto_id_v024
                FOREIGN KEY (producto_id)
                REFERENCES productos(producto_id)
                ON DELETE CASCADE;
        END IF;

        IF NOT EXISTS (
            SELECT 1
            FROM information_schema.table_constraints
            WHERE table_schema = current_schema()
              AND table_name = 'manufacturing_versions'
              AND constraint_name = 'fk_manufacturing_versions_producto_id_v024'
        ) THEN
            ALTER TABLE manufacturing_versions
                ADD CONSTRAINT fk_manufacturing_versions_producto_id_v024
                FOREIGN KEY (producto_id)
                REFERENCES productos(producto_id);
        END IF;

        IF NOT EXISTS (
            SELECT 1
            FROM information_schema.table_constraints
            WHERE table_schema = current_schema()
              AND table_name = 'insumos'
              AND constraint_name = 'fk_insumos_input_producto_id_v024'
        ) THEN
            ALTER TABLE insumos
                ADD CONSTRAINT fk_insumos_input_producto_id_v024
                FOREIGN KEY (input_producto_id)
                REFERENCES productos(producto_id);
        END IF;

        IF NOT EXISTS (
            SELECT 1
            FROM information_schema.table_constraints
            WHERE table_schema = current_schema()
              AND table_name = 'insumos'
              AND constraint_name = 'fk_insumos_output_producto_id_v024'
        ) THEN
            ALTER TABLE insumos
                ADD CONSTRAINT fk_insumos_output_producto_id_v024
                FOREIGN KEY (output_producto_id)
                REFERENCES productos(producto_id);
        END IF;

        IF NOT EXISTS (
            SELECT 1
            FROM information_schema.table_constraints
            WHERE table_schema = current_schema()
              AND table_name = 'insumos_empaque'
              AND constraint_name = 'fk_insumos_empaque_material_id_v024'
        ) THEN
            ALTER TABLE insumos_empaque
                ADD CONSTRAINT fk_insumos_empaque_material_id_v024
                FOREIGN KEY (material_id)
                REFERENCES productos(producto_id);
        END IF;
    END IF;
END $$;

TRUNCATE TABLE tmp_producto_id_active_map;

INSERT INTO tmp_producto_id_active_map (old_id, new_id)
SELECT old_id, new_id
FROM tmp_producto_id_applied_map;

DO $$
DECLARE
    lowercase_product_ids TEXT;
    decimal_suffix_product_ids TEXT;
    stale_relational_refs TEXT;
    stale_init_batches TEXT;
    stale_snapshot_refs TEXT := '';
    stale_piece TEXT;
    insumos_json_type TEXT;
    proceso_json_type TEXT;
    case_pack_json_type TEXT;
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

    SELECT string_agg(quote_literal(producto_id), ', ' ORDER BY producto_id)
    INTO decimal_suffix_product_ids
    FROM productos
    WHERE producto_id ~ '^[0-9]+\.0$';

    IF decimal_suffix_product_ids IS NOT NULL THEN
        RAISE EXCEPTION USING
            MESSAGE = 'Validacion final fallida: todavia existen producto_id numericos con sufijo .0 en productos',
            DETAIL = decimal_suffix_product_ids;
    END IF;

    SELECT string_agg(ref_line, E'\n' ORDER BY ref_line)
    INTO stale_relational_refs
    FROM (
        SELECT format('movimientos.movimiento_id=%s producto_id=%L', m.movimiento_id, m.producto_id) AS ref_line
        FROM movimientos m
        WHERE m.producto_id IN (SELECT old_id FROM tmp_producto_id_applied_map)

        UNION ALL

        SELECT format('movimiento_reserva.id=%s producto_id=%L', mr.id, mr.producto_id)
        FROM movimiento_reserva mr
        WHERE mr.producto_id IN (SELECT old_id FROM tmp_producto_id_applied_map)

        UNION ALL

        SELECT format('planificacion_produccion.id=%s producto_id=%L', pp.id, pp.producto_id)
        FROM planificacion_produccion pp
        WHERE pp.producto_id IN (SELECT old_id FROM tmp_producto_id_applied_map)

        UNION ALL

        SELECT format('ordenes_produccion.orden_id=%s producto_id=%L', op.orden_id, op.producto_id)
        FROM ordenes_produccion op
        WHERE op.producto_id IN (SELECT old_id FROM tmp_producto_id_applied_map)

        UNION ALL

        SELECT format('item_orden_compra.item_id=%s producto_id=%L', ioc.item_id, ioc.producto_id)
        FROM item_orden_compra ioc
        WHERE ioc.producto_id IN (SELECT old_id FROM tmp_producto_id_applied_map)

        UNION ALL

        SELECT format('items_factura_compra.item_compra_id=%s materia_prima_id=%L', ifc.item_compra_id, ifc.materia_prima_id)
        FROM items_factura_compra ifc
        WHERE ifc.materia_prima_id IN (SELECT old_id FROM tmp_producto_id_applied_map)

        UNION ALL

        SELECT format('items_orden_venta.item_id=%s producto_id=%L', iov.item_id, iov.producto_id)
        FROM items_orden_venta iov
        WHERE iov.producto_id IN (SELECT old_id FROM tmp_producto_id_applied_map)

        UNION ALL

        SELECT format('items_factura_venta.item_factura_id=%s producto_id=%L', ifv.item_factura_id, ifv.producto_id)
        FROM items_factura_venta ifv
        WHERE ifv.producto_id IN (SELECT old_id FROM tmp_producto_id_applied_map)

        UNION ALL

        SELECT format('procesos_produccion.proceso_completo_id=%s producto_id=%L', pp.proceso_completo_id, pp.producto_id)
        FROM procesos_produccion pp
        WHERE pp.producto_id IN (SELECT old_id FROM tmp_producto_id_applied_map)

        UNION ALL

        SELECT format('manufacturing_versions.id=%s producto_id=%L', mv.id, mv.producto_id)
        FROM manufacturing_versions mv
        WHERE mv.producto_id IN (SELECT old_id FROM tmp_producto_id_applied_map)

        UNION ALL

        SELECT format('insumos.insumo_id=%s input_producto_id=%L', i.insumo_id, i.input_producto_id)
        FROM insumos i
        WHERE i.input_producto_id IN (SELECT old_id FROM tmp_producto_id_applied_map)

        UNION ALL

        SELECT format('insumos.insumo_id=%s output_producto_id=%L', i.insumo_id, i.output_producto_id)
        FROM insumos i
        WHERE i.output_producto_id IN (SELECT old_id FROM tmp_producto_id_applied_map)

        UNION ALL

        SELECT format('insumos_empaque.id=%s material_id=%L', ie.id, ie.material_id)
        FROM insumos_empaque ie
        WHERE ie.material_id IN (SELECT old_id FROM tmp_producto_id_applied_map)
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
        FROM tmp_producto_id_applied_map
    );

    IF stale_init_batches IS NOT NULL THEN
        RAISE EXCEPTION USING
            MESSAGE = 'Validacion final fallida: persisten lotes INIT con producto_id anteriores',
            DETAIL = stale_init_batches;
    END IF;

    SELECT format_type(a.atttypid, a.atttypmod)
    INTO insumos_json_type
    FROM pg_attribute a
    JOIN pg_class c
        ON c.oid = a.attrelid
    WHERE c.relname = 'manufacturing_versions'
      AND a.attname = 'insumos_json'
      AND a.attnum > 0
      AND NOT a.attisdropped;

    SELECT format_type(a.atttypid, a.atttypmod)
    INTO proceso_json_type
    FROM pg_attribute a
    JOIN pg_class c
        ON c.oid = a.attrelid
    WHERE c.relname = 'manufacturing_versions'
      AND a.attname = 'proceso_produccion_json'
      AND a.attnum > 0
      AND NOT a.attisdropped;

    SELECT format_type(a.atttypid, a.atttypmod)
    INTO case_pack_json_type
    FROM pg_attribute a
    JOIN pg_class c
        ON c.oid = a.attrelid
    WHERE c.relname = 'manufacturing_versions'
      AND a.attname = 'case_pack_json'
      AND a.attnum > 0
      AND NOT a.attisdropped;

    IF insumos_json_type = 'oid' THEN
        EXECUTE
            'SELECT string_agg(format(''manufacturing_versions.id=%s columna=insumos_json'', id), E''\n'' ORDER BY id)
             FROM manufacturing_versions
             WHERE insumos_json IS NOT NULL
               AND convert_from(lo_get(insumos_json), ''UTF8'') <> pg_temp.remap_json_text(convert_from(lo_get(insumos_json), ''UTF8''), ''productoId'')'
        INTO stale_piece;
    ELSIF insumos_json_type = 'text' OR insumos_json_type = 'json' OR insumos_json_type = 'jsonb'
       OR insumos_json_type LIKE 'character varying%' THEN
        EXECUTE
            'SELECT string_agg(format(''manufacturing_versions.id=%s columna=insumos_json'', id), E''\n'' ORDER BY id)
             FROM manufacturing_versions
             WHERE insumos_json IS NOT NULL
               AND insumos_json::TEXT <> pg_temp.remap_json_text(insumos_json::TEXT, ''productoId'')'
        INTO stale_piece;
    ELSE
        RAISE EXCEPTION 'Tipo no soportado para validacion de manufacturing_versions.insumos_json: %', insumos_json_type;
    END IF;

    IF stale_piece IS NOT NULL THEN
        stale_snapshot_refs := stale_piece;
    END IF;

    IF proceso_json_type = 'oid' THEN
        EXECUTE
            'SELECT string_agg(format(''manufacturing_versions.id=%s columna=proceso_produccion_json'', id), E''\n'' ORDER BY id)
             FROM manufacturing_versions
             WHERE proceso_produccion_json IS NOT NULL
               AND convert_from(lo_get(proceso_produccion_json), ''UTF8'') <> pg_temp.remap_json_text(convert_from(lo_get(proceso_produccion_json), ''UTF8''), ''inputProductoId'')'
        INTO stale_piece;
    ELSIF proceso_json_type = 'text' OR proceso_json_type = 'json' OR proceso_json_type = 'jsonb'
       OR proceso_json_type LIKE 'character varying%' THEN
        EXECUTE
            'SELECT string_agg(format(''manufacturing_versions.id=%s columna=proceso_produccion_json'', id), E''\n'' ORDER BY id)
             FROM manufacturing_versions
             WHERE proceso_produccion_json IS NOT NULL
               AND proceso_produccion_json::TEXT <> pg_temp.remap_json_text(proceso_produccion_json::TEXT, ''inputProductoId'')'
        INTO stale_piece;
    ELSE
        RAISE EXCEPTION 'Tipo no soportado para validacion de manufacturing_versions.proceso_produccion_json: %', proceso_json_type;
    END IF;

    IF stale_piece IS NOT NULL THEN
        stale_snapshot_refs := stale_snapshot_refs
            || CASE WHEN stale_snapshot_refs = '' THEN '' ELSE E'\n' END
            || stale_piece;
    END IF;

    IF case_pack_json_type = 'oid' THEN
        EXECUTE
            'SELECT string_agg(format(''manufacturing_versions.id=%s columna=case_pack_json'', id), E''\n'' ORDER BY id)
             FROM manufacturing_versions
             WHERE case_pack_json IS NOT NULL
               AND convert_from(lo_get(case_pack_json), ''UTF8'') <> pg_temp.remap_json_text(convert_from(lo_get(case_pack_json), ''UTF8''), ''materialId'')'
        INTO stale_piece;
    ELSIF case_pack_json_type = 'text' OR case_pack_json_type = 'json' OR case_pack_json_type = 'jsonb'
       OR case_pack_json_type LIKE 'character varying%' THEN
        EXECUTE
            'SELECT string_agg(format(''manufacturing_versions.id=%s columna=case_pack_json'', id), E''\n'' ORDER BY id)
             FROM manufacturing_versions
             WHERE case_pack_json IS NOT NULL
               AND case_pack_json::TEXT <> pg_temp.remap_json_text(case_pack_json::TEXT, ''materialId'')'
        INTO stale_piece;
    ELSE
        RAISE EXCEPTION 'Tipo no soportado para validacion de manufacturing_versions.case_pack_json: %', case_pack_json_type;
    END IF;

    IF stale_piece IS NOT NULL THEN
        stale_snapshot_refs := stale_snapshot_refs
            || CASE WHEN stale_snapshot_refs = '' THEN '' ELSE E'\n' END
            || stale_piece;
    END IF;

    IF stale_snapshot_refs <> '' THEN
        RAISE EXCEPTION USING
            MESSAGE = 'Validacion final fallida: persisten referencias historicas con producto_id anteriores en manufacturing_versions',
            DETAIL = stale_snapshot_refs;
    END IF;
END $$;
