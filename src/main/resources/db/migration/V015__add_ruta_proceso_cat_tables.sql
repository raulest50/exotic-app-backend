-- =====================================================================
-- V015: Crear tablas para el diseñador de rutas de proceso por categoría
-- =====================================================================
-- Fecha: 2026-03-26
-- Descripción: Implementación del sistema de diseño visual de rutas de
--              proceso asociadas a categorías de productos. Permite
--              definir flujos de producción mediante nodos (áreas operativas)
--              y conexiones (edges) en una interfaz gráfica de tipo flowchart.
--
-- Contexto:
-- - Módulo: Producción / Diseñador de Rutas de Proceso
-- - Paquete Java: exotic.app.planta.model.produccion.ruprocatdesigner
-- - Entidades JPA: RutaProcesoCat, RutaProcesoNode, RutaProcesoEdge
--
-- Modelo de datos:
-- - RutaProcesoCat: Contenedor principal, relación 1:1 con Categoria
-- - RutaProcesoNode: Nodos del grafo (representan áreas operativas)
-- - RutaProcesoEdge: Aristas del grafo (conexiones entre nodos)
--
-- Relaciones:
-- - categoria (1) ←→ (1) ruta_proceso_cat
-- - ruta_proceso_cat (1) ←→ (N) ruta_proceso_node
-- - ruta_proceso_cat (1) ←→ (N) ruta_proceso_edge
-- - ruta_proceso_node (1) ←→ (N) ruta_proceso_edge (como source/target)
-- - area_operativa (1) ←→ (N) ruta_proceso_node
--
-- IMPORTANTE - Convención de nombres de columnas:
-- Este proyecto usa globally_quoted_identifiers=true, lo que causa que
-- Hibernate NO aplique la estrategia de naming snake_case por defecto.
-- Por lo tanto, se usan anotaciones @Column explícitas en las entidades
-- para mapear camelCase (Java) a snake_case (SQL):
--   - Java: posicionX con @Column(name = "posicion_x")
--   - Java: posicionY con @Column(name = "posicion_y")
-- =====================================================================

DO $$
BEGIN
    -- ===================================================================
    -- 1. Crear tabla principal: ruta_proceso_cat
    -- ===================================================================
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = 'public'
          AND table_name = 'ruta_proceso_cat'
    ) THEN
        CREATE TABLE ruta_proceso_cat (
            id BIGSERIAL PRIMARY KEY,
            categoria_id INTEGER NOT NULL,
            fecha_creacion TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
            fecha_modificacion TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

            -- Constraint de unicidad: una categoría tiene máximo una ruta de proceso
            CONSTRAINT uq_ruta_proceso_cat_categoria UNIQUE (categoria_id),

            -- FK a tabla categoria
            CONSTRAINT fk_ruta_proceso_cat_categoria
                FOREIGN KEY (categoria_id)
                REFERENCES categoria(categoria_id)
                ON DELETE CASCADE
        );

        RAISE NOTICE 'Tabla ruta_proceso_cat creada exitosamente';

        -- Índice para búsquedas por categoría
        CREATE INDEX IF NOT EXISTS idx_ruta_proceso_cat_categoria
            ON ruta_proceso_cat(categoria_id);

        RAISE NOTICE 'Índice idx_ruta_proceso_cat_categoria creado';
    ELSE
        RAISE NOTICE 'Tabla ruta_proceso_cat ya existe, omitiendo creación';
    END IF;


    -- ===================================================================
    -- 2. Crear tabla: ruta_proceso_node
    -- ===================================================================
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = 'public'
          AND table_name = 'ruta_proceso_node'
    ) THEN
        CREATE TABLE ruta_proceso_node (
            id BIGSERIAL PRIMARY KEY,
            ruta_proceso_cat_id BIGINT NOT NULL,
            frontend_id VARCHAR(255),
            posicion_x DOUBLE PRECISION NOT NULL DEFAULT 0.0,
            posicion_y DOUBLE PRECISION NOT NULL DEFAULT 0.0,
            area_operativa_id INTEGER,
            label VARCHAR(255),

            -- FK a tabla ruta_proceso_cat (orphanRemoval via ON DELETE CASCADE)
            CONSTRAINT fk_ruta_proceso_node_ruta_cat
                FOREIGN KEY (ruta_proceso_cat_id)
                REFERENCES ruta_proceso_cat(id)
                ON DELETE CASCADE,

            -- FK a tabla area_operativa
            CONSTRAINT fk_ruta_proceso_node_area_operativa
                FOREIGN KEY (area_operativa_id)
                REFERENCES area_operativa(area_id)
                ON DELETE SET NULL
        );

        RAISE NOTICE 'Tabla ruta_proceso_node creada exitosamente';

        -- Índices para optimizar consultas
        CREATE INDEX IF NOT EXISTS idx_ruta_proceso_node_ruta_cat
            ON ruta_proceso_node(ruta_proceso_cat_id);

        CREATE INDEX IF NOT EXISTS idx_ruta_proceso_node_area_operativa
            ON ruta_proceso_node(area_operativa_id);

        CREATE INDEX IF NOT EXISTS idx_ruta_proceso_node_frontend_id
            ON ruta_proceso_node(frontend_id);

        RAISE NOTICE 'Índices de ruta_proceso_node creados';
    ELSE
        RAISE NOTICE 'Tabla ruta_proceso_node ya existe, omitiendo creación';
    END IF;


    -- ===================================================================
    -- 3. Crear tabla: ruta_proceso_edge
    -- ===================================================================
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = 'public'
          AND table_name = 'ruta_proceso_edge'
    ) THEN
        CREATE TABLE ruta_proceso_edge (
            id BIGSERIAL PRIMARY KEY,
            ruta_proceso_cat_id BIGINT NOT NULL,
            frontend_id VARCHAR(255),
            source_node_id BIGINT,
            target_node_id BIGINT,

            -- FK a tabla ruta_proceso_cat (orphanRemoval via ON DELETE CASCADE)
            CONSTRAINT fk_ruta_proceso_edge_ruta_cat
                FOREIGN KEY (ruta_proceso_cat_id)
                REFERENCES ruta_proceso_cat(id)
                ON DELETE CASCADE,

            -- FK a nodo origen
            CONSTRAINT fk_ruta_proceso_edge_source_node
                FOREIGN KEY (source_node_id)
                REFERENCES ruta_proceso_node(id)
                ON DELETE CASCADE,

            -- FK a nodo destino
            CONSTRAINT fk_ruta_proceso_edge_target_node
                FOREIGN KEY (target_node_id)
                REFERENCES ruta_proceso_node(id)
                ON DELETE CASCADE
        );

        RAISE NOTICE 'Tabla ruta_proceso_edge creada exitosamente';

        -- Índices para optimizar consultas
        CREATE INDEX IF NOT EXISTS idx_ruta_proceso_edge_ruta_cat
            ON ruta_proceso_edge(ruta_proceso_cat_id);

        CREATE INDEX IF NOT EXISTS idx_ruta_proceso_edge_source_node
            ON ruta_proceso_edge(source_node_id);

        CREATE INDEX IF NOT EXISTS idx_ruta_proceso_edge_target_node
            ON ruta_proceso_edge(target_node_id);

        CREATE INDEX IF NOT EXISTS idx_ruta_proceso_edge_frontend_id
            ON ruta_proceso_edge(frontend_id);

        RAISE NOTICE 'Índices de ruta_proceso_edge creados';
    ELSE
        RAISE NOTICE 'Tabla ruta_proceso_edge ya existe, omitiendo creación';
    END IF;


    -- ===================================================================
    -- 4. Resumen de migración
    -- ===================================================================
    RAISE NOTICE '═══════════════════════════════════════════════════════════';
    RAISE NOTICE 'Migración V015 completada exitosamente';
    RAISE NOTICE 'Tablas creadas: ruta_proceso_cat, ruta_proceso_node, ruta_proceso_edge';
    RAISE NOTICE 'Sistema de diseñador de rutas de proceso está listo';
    RAISE NOTICE '═══════════════════════════════════════════════════════════';

END $$;
