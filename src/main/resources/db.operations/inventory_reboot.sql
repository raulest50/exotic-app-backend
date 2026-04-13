-- ============================================================================
-- inventory_reboot.sql
-- Reinicio standalone de la operacion de almacen, compras de materiales,
-- produccion operativa y contabilidad derivada del inventario.
--
-- Base de datos objetivo: PostgreSQL
--
-- Uso recomendado:
--   1. Detener la aplicacion o garantizar ausencia de escrituras concurrentes.
--   2. Ejecutar en la misma sesion:
--        SET inventory_reboot.confirmation = 'RESET_INVENTARIO_CONFIRMADO';
--   3. Ejecutar este script manualmente.
--
-- Este script:
--   - PRESERVA catalogos y maestros como productos, categorias, proveedores,
--     procesos, recetas, case packs, snapshots de manufacturing y ventas.
--   - ELIMINA el historico operativo de almacen/compras/produccion y los
--     asientos contables asociados a transacciones de inventario, siempre que
--     esos asientos no sigan referenciados por otros modulos.
-- ============================================================================

ROLLBACK;
SET inventory_reboot.confirmation = 'RESET_INVENTARIO_CONFIRMADO';

BEGIN;

DO $$
BEGIN
    IF current_setting('inventory_reboot.confirmation', true) IS DISTINCT FROM 'RESET_INVENTARIO_CONFIRMADO' THEN
        RAISE EXCEPTION USING
            MESSAGE = 'Guard rail: inventory_reboot.sql requiere confirmacion explicita',
            DETAIL = 'Falta SET inventory_reboot.confirmation = ''RESET_INVENTARIO_CONFIRMADO'' en la sesion actual.',
            HINT = 'Ejecuta el SET de confirmacion y vuelve a correr el script en ventana de mantenimiento.';
    END IF;
END $$;

CREATE TEMP TABLE tmp_reboot_delete_counts (
    sort_order INTEGER PRIMARY KEY,
    table_name TEXT UNIQUE NOT NULL,
    table_exists BOOLEAN NOT NULL,
    row_count BIGINT NOT NULL
) ON COMMIT DROP;

CREATE TEMP TABLE tmp_reboot_delete_targets (
    sort_order INTEGER PRIMARY KEY,
    table_name TEXT UNIQUE NOT NULL
) ON COMMIT DROP;

INSERT INTO tmp_reboot_delete_targets (sort_order, table_name)
VALUES
    (1, 'transaccion_almacen_usuarios_realizadores'),
    (2, 'movimientos'),
    (3, 'seguimiento_orden_area'),
    (4, 'recurso_asignado_orden'),
    (5, 'ordenes_seguimiento'),
    (6, 'movimiento_reserva'),
    (7, 'lote'),
    (8, 'item_orden_compra'),
    (9, 'items_factura_compra'),
    (10, 'orden_compra'),
    (11, 'facturas_compras'),
    (12, 'ordenes_produccion'),
    (13, 'planificacion_recurso'),
    (14, 'planificacion_produccion'),
    (15, 'transaccion_almacen');

DO $$
DECLARE
    rec RECORD;
    current_count BIGINT;
BEGIN
    FOR rec IN
        SELECT sort_order, table_name
        FROM tmp_reboot_delete_targets
        ORDER BY sort_order
    LOOP
        IF to_regclass(format('public.%I', rec.table_name)) IS NULL THEN
            INSERT INTO tmp_reboot_delete_counts (sort_order, table_name, table_exists, row_count)
            VALUES (rec.sort_order, rec.table_name, FALSE, 0);
        ELSE
            EXECUTE format('SELECT COUNT(*) FROM public.%I', rec.table_name)
            INTO current_count;

            INSERT INTO tmp_reboot_delete_counts (sort_order, table_name, table_exists, row_count)
            VALUES (rec.sort_order, rec.table_name, TRUE, current_count);
        END IF;
    END LOOP;
END $$;

CREATE TEMP TABLE tmp_reboot_preserve_counts (
    table_name TEXT PRIMARY KEY,
    row_count BIGINT NOT NULL
) ON COMMIT DROP;

INSERT INTO tmp_reboot_preserve_counts (table_name, row_count)
SELECT 'productos', COUNT(*) FROM public.productos
UNION ALL SELECT 'categoria', COUNT(*) FROM public.categoria
UNION ALL SELECT 'familia', COUNT(*) FROM public.familia
UNION ALL SELECT 'case_pack', COUNT(*) FROM public.case_pack
UNION ALL SELECT 'insumos', COUNT(*) FROM public.insumos
UNION ALL SELECT 'insumos_empaque', COUNT(*) FROM public.insumos_empaque
UNION ALL SELECT 'procesos_produccion', COUNT(*) FROM public.procesos_produccion
UNION ALL SELECT 'proceso_produccion', COUNT(*) FROM public.proceso_produccion
UNION ALL SELECT 'proceso_fabricacion_nodo', COUNT(*) FROM public.proceso_fabricacion_nodo
UNION ALL SELECT 'proceso_fabricacion_edge', COUNT(*) FROM public.proceso_fabricacion_edge
UNION ALL SELECT 'manufacturing_versions', COUNT(*) FROM public.manufacturing_versions
UNION ALL SELECT 'proveedores', COUNT(*) FROM public.proveedores
UNION ALL SELECT 'contacto_proveedor', COUNT(*) FROM public.contacto_proveedor
UNION ALL SELECT 'ordenes_venta', COUNT(*) FROM public.ordenes_venta
UNION ALL SELECT 'items_orden_venta', COUNT(*) FROM public.items_orden_venta
UNION ALL SELECT 'facturas_venta', COUNT(*) FROM public.facturas_venta
UNION ALL SELECT 'items_factura_venta', COUNT(*) FROM public.items_factura_venta
UNION ALL SELECT 'users', COUNT(*) FROM public.users
UNION ALL SELECT 'area_operativa', COUNT(*) FROM public.area_operativa
UNION ALL SELECT 'clientes', COUNT(*) FROM public.clientes;

CREATE TEMP TABLE tmp_asientos_inventario_borrables (
    asiento_id BIGINT PRIMARY KEY
) ON COMMIT DROP;

INSERT INTO tmp_asientos_inventario_borrables (asiento_id)
SELECT DISTINCT ta.asiento_contable_id
FROM public.transaccion_almacen ta
WHERE ta.asiento_contable_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM public.incorporacion_activo_header iah
      WHERE iah.asiento_contable_id = ta.asiento_contable_id
  )
  AND NOT EXISTS (
      SELECT 1
      FROM public.depreciacion_activo da
      WHERE da.asiento_contable_id = ta.asiento_contable_id
  )
  AND NOT EXISTS (
      SELECT 1
      FROM public.documento_baja_activo dba
      WHERE dba.asiento_contable_id = ta.asiento_contable_id
  );

DO $$
DECLARE
    rec RECORD;
BEGIN
    RAISE NOTICE '==== PRECHECK REBOOT INVENTARIO ====';

    FOR rec IN
        SELECT table_name, table_exists, row_count
        FROM tmp_reboot_delete_counts
        ORDER BY sort_order
    LOOP
        IF rec.table_exists THEN
            RAISE NOTICE 'Tabla a limpiar: % -> % fila(s)', rec.table_name, rec.row_count;
        ELSE
            RAISE NOTICE 'Tabla a limpiar: % -> ausente en este ambiente (se omitira)', rec.table_name;
        END IF;
    END LOOP;

    FOR rec IN
        SELECT table_name, row_count
        FROM tmp_reboot_preserve_counts
        ORDER BY table_name
    LOOP
        RAISE NOTICE 'Tabla a preservar: % -> % fila(s)', rec.table_name, rec.row_count;
    END LOOP;

    RAISE NOTICE 'Asientos contables elegibles para borrado: %',
        (SELECT COUNT(*) FROM tmp_asientos_inventario_borrables);
END $$;

DO $$
DECLARE
    rec RECORD;
    deleted_count BIGINT;
BEGIN
    FOR rec IN
        SELECT sort_order, table_name, table_exists
        FROM tmp_reboot_delete_counts
        ORDER BY sort_order
    LOOP
        IF rec.table_exists THEN
            EXECUTE format('DELETE FROM public.%I WHERE TRUE', rec.table_name);
            GET DIAGNOSTICS deleted_count = ROW_COUNT;

            RAISE NOTICE 'Tabla limpiada: % -> % fila(s) eliminada(s)', rec.table_name, deleted_count;
        ELSE
            RAISE NOTICE 'Tabla omitida por ausencia en este ambiente: %', rec.table_name;
        END IF;
    END LOOP;
END $$;

DELETE FROM public.linea_asiento_contable lac
USING tmp_asientos_inventario_borrables t
WHERE lac.asiento_id = t.asiento_id;

DELETE FROM public.asiento_contable ac
USING tmp_asientos_inventario_borrables t
WHERE ac.id = t.asiento_id;

CREATE TEMP TABLE tmp_reboot_sequences (
    table_name TEXT PRIMARY KEY,
    column_name TEXT NOT NULL
) ON COMMIT DROP;

INSERT INTO tmp_reboot_sequences (table_name, column_name)
VALUES
    ('movimientos', 'movimiento_id'),
    ('seguimiento_orden_area', 'id'),
    ('recurso_asignado_orden', 'id'),
    ('ordenes_seguimiento', 'seguimiento_id'),
    ('movimiento_reserva', 'id'),
    ('lote', 'id'),
    ('item_orden_compra', 'item_id'),
    ('items_factura_compra', 'item_compra_id'),
    ('orden_compra', 'orden_compra_id'),
    ('facturas_compras', 'factura_compra_id'),
    ('ordenes_produccion', 'orden_id'),
    ('planificacion_produccion', 'id'),
    ('transaccion_almacen', 'transaccion_id');

DO $$
DECLARE
    rec RECORD;
    seq_name TEXT;
BEGIN
    FOR rec IN
        SELECT table_name, column_name
        FROM tmp_reboot_sequences
        ORDER BY table_name
    LOOP
        IF to_regclass(format('public.%I', rec.table_name)) IS NULL THEN
            RAISE NOTICE 'Tabla ausente; se omite reinicio de secuencia para %.%', rec.table_name, rec.column_name;
            CONTINUE;
        END IF;

        SELECT pg_get_serial_sequence(format('public.%I', rec.table_name), rec.column_name)
        INTO seq_name;

        IF seq_name IS NOT NULL THEN
            EXECUTE format('SELECT setval(%L, 1, false)', seq_name);
            RAISE NOTICE 'Secuencia reiniciada: %', seq_name;
        ELSE
            RAISE NOTICE 'Sin secuencia asociada para %.%', rec.table_name, rec.column_name;
        END IF;
    END LOOP;
END $$;

DO $$
DECLARE
    rec RECORD;
    current_count BIGINT;
BEGIN
    FOR rec IN
        SELECT table_name, table_exists
        FROM tmp_reboot_delete_counts
        ORDER BY sort_order
    LOOP
        IF NOT rec.table_exists THEN
            CONTINUE;
        END IF;

        EXECUTE format('SELECT COUNT(*) FROM public.%I', rec.table_name)
        INTO current_count;

        IF current_count <> 0 THEN
            RAISE EXCEPTION USING
                MESSAGE = format('Validacion final fallida: la tabla %s no quedo vacia', rec.table_name),
                DETAIL = format('Filas remanentes: %s', current_count);
        END IF;
    END LOOP;
END $$;

DO $$
DECLARE
    rec RECORD;
    current_count BIGINT;
BEGIN
    FOR rec IN
        SELECT table_name, row_count
        FROM tmp_reboot_preserve_counts
        ORDER BY table_name
    LOOP
        EXECUTE format('SELECT COUNT(*) FROM public.%I', rec.table_name)
        INTO current_count;

        IF current_count <> rec.row_count THEN
            RAISE EXCEPTION USING
                MESSAGE = format('Validacion final fallida: la tabla preservada %s cambio de cardinalidad', rec.table_name),
                DETAIL = format('Conteo antes: %s. Conteo despues: %s', rec.row_count, current_count);
        END IF;
    END LOOP;
END $$;

DO $$
DECLARE
    dangling_line_count BIGINT;
    dangling_asiento_count BIGINT;
BEGIN
    SELECT COUNT(*)
    INTO dangling_line_count
    FROM public.linea_asiento_contable lac
    JOIN tmp_asientos_inventario_borrables t
      ON t.asiento_id = lac.asiento_id;

    IF dangling_line_count <> 0 THEN
        RAISE EXCEPTION USING
            MESSAGE = 'Validacion final fallida: persisten lineas contables para asientos elegibles de inventario',
            DETAIL = format('Lineas remanentes: %s', dangling_line_count);
    END IF;

    SELECT COUNT(*)
    INTO dangling_asiento_count
    FROM public.asiento_contable ac
    JOIN tmp_asientos_inventario_borrables t
      ON t.asiento_id = ac.id;

    IF dangling_asiento_count <> 0 THEN
        RAISE EXCEPTION USING
            MESSAGE = 'Validacion final fallida: persisten asientos contables elegibles de inventario',
            DETAIL = format('Asientos remanentes: %s', dangling_asiento_count);
    END IF;
END $$;

DO $$
BEGIN
    RAISE NOTICE '==== INVENTORY REBOOT COMPLETADO EXITOSAMENTE ====';
END $$;

COMMIT;
