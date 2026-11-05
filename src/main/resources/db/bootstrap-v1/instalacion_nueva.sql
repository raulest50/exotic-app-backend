-- SOLO PARA UNA INSTALACION NUEVA Y VACIA, DESPUES DE APLICAR V1.
-- Ejecutar antes del primer arranque de la aplicacion, fuera de Flyway.
-- NO ejecutar antes de una restauracion data-only: el respaldo incluye estas filas.
-- No usar para reinicializar revisiones ni reparar una base con datos de negocio.
-- Desde la raiz del backend, con la conexion configurada por el operador:
-- psql -v ON_ERROR_STOP=1 --single-transaction -f src/main/resources/db/bootstrap-v1/instalacion_nueva.sql
-- SQL puro para PostgreSQL 18; no contiene credenciales, usuarios ni metacomandos.

DO $bootstrap$
DECLARE
    tabla RECORD;
    tiene_filas BOOLEAN;
BEGIN
    -- Exigir la raiz consolidada aplicada por Flyway; una linea legada no sirve.
    IF pg_catalog.to_regclass('public.flyway_schema_history') IS NULL THEN
        RAISE EXCEPTION
            'Bootstrap cancelado: no existe public.flyway_schema_history; aplicar primero la V1 consolidada con Flyway.';
    END IF;

    IF EXISTS (
        SELECT 1
          FROM public.flyway_schema_history
         WHERE type = 'BASELINE'
    ) THEN
        RAISE EXCEPTION
            'Bootstrap cancelado: el historial contiene un marcador BASELINE; no es una instalacion nueva de la linea consolidada.';
    END IF;

    IF NOT EXISTS (
        SELECT 1
          FROM public.flyway_schema_history
         WHERE version IN ('1', '001')
           AND type = 'SQL'
           AND script = 'V001__esquema_actual_consolidado.sql'
           AND success IS TRUE
    ) THEN
        RAISE EXCEPTION
            'Bootstrap cancelado: falta la migracion SQL exitosa V001__esquema_actual_consolidado.sql (version 1/001).'
            USING HINT = 'Aplicar la V1 consolidada con Flyway; este script no admite la linea de migraciones legada.';
    END IF;

    -- La repeticion solo es admisible mientras no existan datos de negocio.
    FOR tabla IN
        SELECT table_schema, table_name
          FROM information_schema.tables
         WHERE table_schema = 'public'
           AND table_type = 'BASE TABLE'
           AND table_name NOT IN (
               'flyway_schema_history',
               'unidad_medida_producto',
               'control_unidad',
               'control_magnitud',
               'receta_costos_revision',
               'organigrama_estado',
               'area_operativa'
           )
         ORDER BY table_name
    LOOP
        EXECUTE format(
            'SELECT EXISTS (SELECT 1 FROM %I.%I)',
            tabla.table_schema,
            tabla.table_name
        ) INTO tiene_filas;

        IF tiene_filas THEN
            RAISE EXCEPTION
                'Bootstrap cancelado: %.% contiene datos; se requiere una instalacion nueva y vacia.',
                tabla.table_schema, tabla.table_name
                USING HINT = 'No ejecutar este script en una restauracion data-only ni despues del primer arranque.';
        END IF;
    END LOOP;

    -- El identificador y el nombre del almacen son un contrato del inicializador.
    IF EXISTS (
        SELECT 1
          FROM public.area_operativa
         WHERE area_id = -1
           AND nombre IS DISTINCT FROM 'Almacen General'
    ) OR EXISTS (
        SELECT 1
          FROM public.area_operativa
         WHERE nombre = 'Almacen General'
           AND area_id <> -1
    ) THEN
        RAISE EXCEPTION
            'Bootstrap cancelado: Almacen General debe tener area_id=-1 y nombre=''Almacen General''; existe una identidad incompatible.'
            USING HINT = 'Revisar area_operativa; el bootstrap no renombra, reemplaza ni reasigna areas existentes.';
    END IF;

    IF EXISTS (SELECT 1 FROM public.area_operativa WHERE area_id <> -1) THEN
        RAISE EXCEPTION
            'Bootstrap cancelado: area_operativa contiene areas de negocio; se requiere una instalacion nueva y vacia.';
    END IF;

    -- Catalogo canonico de V116. No modificar unidades existentes.
    INSERT INTO public.unidad_medida_producto (
        codigo, simbolo, magnitud, factor_a_referencia
    )
    VALUES
        ('KG', 'kg', 'MASA', 1),
        ('G', 'g', 'MASA', 0.001),
        ('L', 'L', 'VOLUMEN', 1),
        ('ML', 'mL', 'VOLUMEN', 0.001),
        ('U', 'U', 'CONTEO', 1)
    ON CONFLICT (codigo) DO NOTHING;

    -- Catalogos canonicos de V101; no incluir registros derivados LEGACY_*.
    -- Filtrar codigos existentes evita consumir secuencias al repetir el script.
    INSERT INTO public.control_unidad (codigo, nombre, simbolo, dimension, activo)
    SELECT semilla.codigo, semilla.nombre, semilla.simbolo, semilla.dimension, TRUE
      FROM (VALUES
          ('ADIMENSIONAL', 'Adimensional', '1', 'ADIMENSIONAL'),
          ('G', 'Gramo', 'g', 'MASA'),
          ('KG', 'Kilogramo', 'kg', 'MASA'),
          ('PH', 'Unidad de pH', 'pH', 'PH'),
          ('MPA_S', 'Milipascal segundo', 'mPa·s', 'VISCOSIDAD_DINAMICA'),
          ('CP', 'Centipoise', 'cP', 'VISCOSIDAD_DINAMICA')
      ) AS semilla(codigo, nombre, simbolo, dimension)
     WHERE NOT EXISTS (
         SELECT 1 FROM public.control_unidad existente
          WHERE existente.codigo = semilla.codigo
     )
    ON CONFLICT (codigo) DO NOTHING;

    INSERT INTO public.control_magnitud (codigo, nombre, simbolo, dimension, activo)
    SELECT semilla.codigo, semilla.nombre, semilla.simbolo, semilla.dimension, TRUE
      FROM (VALUES
          ('PESO', 'Peso', 'm', 'MASA'),
          ('PH', 'pH', 'pH', 'PH'),
          ('VISCOSIDAD_DINAMICA', 'Viscosidad dinamica', 'η', 'VISCOSIDAD_DINAMICA')
      ) AS semilla(codigo, nombre, simbolo, dimension)
     WHERE NOT EXISTS (
         SELECT 1 FROM public.control_magnitud existente
          WHERE existente.codigo = semilla.codigo
     )
    ON CONFLICT (codigo) DO NOTHING;

    -- Singleton de V088: preservar la revision si ya existe.
    INSERT INTO public.receta_costos_revision (id, version, actualizado_en)
    VALUES (1, 1, CURRENT_TIMESTAMP)
    ON CONFLICT (id) DO NOTHING;

    -- Singleton de V094: preservar revision y auditoria si ya existe.
    INSERT INTO public.organigrama_estado (
        id, revision, actualizado_en, actualizado_por
    )
    VALUES (1, 0, CURRENT_TIMESTAMP, 'system')
    ON CONFLICT (id) DO NOTHING;

    -- Identidad reservada de V018 y AreaOperativaInitializer.
    INSERT INTO public.area_operativa (
        area_id, nombre, descripcion, responsable_id
    )
    VALUES (-1, 'Almacen General', 'Area operativa por defecto del sistema', NULL)
    ON CONFLICT (area_id) DO NOTHING;
END;
$bootstrap$;
