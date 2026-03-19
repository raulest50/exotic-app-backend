-- Migración: Crear tabla contacto_proveedor y migrar datos desde columna JSONB
-- Fecha: 2026-03-17
-- Descripción: Reemplaza el almacenamiento de contactos como JSONB sin esquema en
--              la tabla proveedores por una relación OneToMany hacia la nueva tabla
--              contacto_proveedor. Los datos existentes son migrados de forma
--              tolerante a fallos, manejando dos formatos de JSON históricos:
--
--              Formato A (frontend normal):
--                [{"nombre": "...", "email": "...", "telefono": "3.204559908E9"}]
--
--              Formato B (CargaMasiva):
--                [{"tipo": "telefono", "valor": "...", "nombre": "..."},
--                 {"tipo": "email", "valor": "..."}]
--
--              Los campos de texto almacenados en notación científica (ej: 3.204559908E9)
--              son convertidos a cadena entera antes de ser insertados.
--              Los elementos malformados o incompletos se omiten con RAISE NOTICE.

-- ============================================================
-- PASO 1: Crear tabla contacto_proveedor
-- ============================================================
CREATE TABLE IF NOT EXISTS contacto_proveedor (
    contacto_id  SERIAL       PRIMARY KEY,
    proveedor_pk BIGINT       NOT NULL REFERENCES proveedores(pk) ON DELETE CASCADE,
    full_name    VARCHAR(255),
    cargo        VARCHAR(255),
    cel          VARCHAR(100),
    email        VARCHAR(255)
);

-- ============================================================
-- PASO 2: Migrar datos desde la columna JSONB
--         Solo se ejecuta si la columna contactos todavía existe
-- ============================================================
DO $$
DECLARE
    prov      RECORD;
    elem      JSONB;
    v_full_name TEXT;
    v_email     TEXT;
    v_cel       TEXT;
    v_cargo     TEXT;
    v_tel_raw   TEXT;
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'proveedores' AND column_name = 'contactos'
    ) THEN
        RAISE NOTICE 'Columna contactos no existe en proveedores. Migración de datos omitida.';
        RETURN;
    END IF;

    FOR prov IN
        SELECT pk, contactos
        FROM proveedores
        WHERE contactos IS NOT NULL
          AND jsonb_typeof(contactos) = 'array'
          AND jsonb_array_length(contactos) > 0
    LOOP
        BEGIN
            FOR elem IN SELECT * FROM jsonb_array_elements(prov.contactos)
            LOOP
                BEGIN
                    -- Inicializar valores vacíos para cada elemento
                    v_full_name := '';
                    v_email     := '';
                    v_cel       := '';
                    v_cargo     := '';

                    -- Extraer fullName: clave "nombre" (Formato A y B) o "fullName"
                    v_full_name := COALESCE(
                        NULLIF(TRIM(elem->>'nombre'), ''),
                        NULLIF(TRIM(elem->>'fullName'), ''),
                        ''
                    );

                    -- Extraer cargo si existe
                    v_cargo := COALESCE(NULLIF(TRIM(elem->>'cargo'), ''), '');

                    -- Extraer email:
                    --   Formato A: clave "email"
                    --   Formato B: cuando tipo='email', el valor está en "valor"
                    IF elem->>'tipo' = 'email' THEN
                        v_email := COALESCE(NULLIF(TRIM(elem->>'valor'), ''), '');
                        -- En Formato B, el nombre también puede estar aquí
                        IF v_full_name = '' THEN
                            v_full_name := COALESCE(NULLIF(TRIM(elem->>'nombre'), ''), '');
                        END IF;
                    ELSE
                        v_email := COALESCE(NULLIF(TRIM(elem->>'email'), ''), '');
                    END IF;

                    -- Extraer teléfono/celular:
                    --   Formato A: clave "telefono" (puede venir en notación científica)
                    --   Formato B: cuando tipo='telefono', el valor está en "valor"
                    IF elem->>'tipo' = 'telefono' THEN
                        v_tel_raw := NULLIF(TRIM(elem->>'valor'), '');
                        IF v_full_name = '' THEN
                            v_full_name := COALESCE(NULLIF(TRIM(elem->>'nombre'), ''), '');
                        END IF;
                    ELSE
                        v_tel_raw := NULLIF(TRIM(elem->>'telefono'), NULL);
                        IF v_tel_raw IS NULL THEN
                            v_tel_raw := NULLIF(TRIM(elem->>'cel'), '');
                        END IF;
                    END IF;

                    -- Convertir notación científica a entero si aplica (ej: "3.204559908E9" -> "3204559908")
                    IF v_tel_raw IS NOT NULL THEN
                        BEGIN
                            IF v_tel_raw ~ '^[0-9]*\.?[0-9]+[Ee][+-]?[0-9]+$' THEN
                                v_cel := CAST(CAST(v_tel_raw AS FLOAT8)::BIGINT AS TEXT);
                            ELSE
                                v_cel := v_tel_raw;
                            END IF;
                        EXCEPTION WHEN OTHERS THEN
                            v_cel := v_tel_raw;
                        END;
                    END IF;

                    -- Insertar solo si hay al menos un dato útil
                    IF v_full_name <> '' OR v_email <> '' OR v_cel <> '' THEN
                        INSERT INTO contacto_proveedor (proveedor_pk, full_name, cargo, cel, email)
                        VALUES (prov.pk, v_full_name, v_cargo, v_cel, v_email);
                    END IF;

                EXCEPTION WHEN OTHERS THEN
                    RAISE NOTICE 'Elemento omitido para proveedor pk=% (elem=%): %',
                        prov.pk, elem::TEXT, SQLERRM;
                END;
            END LOOP;

        EXCEPTION WHEN OTHERS THEN
            RAISE NOTICE 'Proveedor pk=% omitido completamente: %', prov.pk, SQLERRM;
        END;
    END LOOP;

    RAISE NOTICE 'Migración de contactos completada.';
END $$;

-- ============================================================
-- PASO 3: Eliminar columna JSONB de proveedores
--         Solo si la columna todavía existe
-- ============================================================
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'proveedores' AND column_name = 'contactos'
    ) THEN
        ALTER TABLE proveedores DROP COLUMN contactos;
        RAISE NOTICE 'Columna contactos eliminada de la tabla proveedores.';
    ELSE
        RAISE NOTICE 'Columna contactos ya no existe. DROP omitido.';
    END IF;
END $$;
