DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM orden_compra oc
        WHERE oc.proveedor_pk IS NULL
    ) THEN
        RAISE EXCEPTION USING
            MESSAGE = 'No se puede aplicar V030: existen filas en orden_compra con proveedor_pk NULL.',
            HINT = 'Corrija manualmente las ordenes de compra sin proveedor antes de reintentar la migracion.';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM orden_compra oc
        LEFT JOIN proveedores p ON p.pk = oc.proveedor_pk
        WHERE oc.proveedor_pk IS NOT NULL
          AND p.pk IS NULL
    ) THEN
        RAISE EXCEPTION USING
            MESSAGE = 'No se puede aplicar V030: existen filas en orden_compra con proveedor_pk huerfano.',
            HINT = 'Corrija manualmente las referencias a proveedores inexistentes antes de reintentar la migracion.';
    END IF;
END $$;

ALTER TABLE orden_compra
    DROP COLUMN IF EXISTS proveedor_id;

ALTER TABLE orden_compra
    ALTER COLUMN proveedor_pk SET NOT NULL;

DO $$
DECLARE
    existing_constraint_name text;
BEGIN
    SELECT con.conname
    INTO existing_constraint_name
    FROM pg_constraint con
    JOIN pg_class rel ON rel.oid = con.conrelid
    JOIN pg_namespace nsp ON nsp.oid = rel.relnamespace
    WHERE nsp.nspname = current_schema()
      AND rel.relname = 'orden_compra'
      AND con.contype = 'f'
      AND con.conkey = ARRAY[
          (
              SELECT att.attnum
              FROM pg_attribute att
              WHERE att.attrelid = rel.oid
                AND att.attname = 'proveedor_pk'
                AND NOT att.attisdropped
          )
      ];

    IF existing_constraint_name IS NULL THEN
        ALTER TABLE orden_compra
            ADD CONSTRAINT fk_orden_compra_proveedor_pk
            FOREIGN KEY (proveedor_pk) REFERENCES proveedores(pk);
    END IF;
END $$;
