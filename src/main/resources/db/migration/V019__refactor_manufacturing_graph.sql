DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'procesos_produccion'
          AND column_name = 'producto_id'
    ) THEN
        ALTER TABLE procesos_produccion
            ADD COLUMN producto_id VARCHAR(255);
    END IF;
END $$;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'productos'
          AND column_name = 'proceso_prod_id'
    ) THEN
        ALTER TABLE productos
            DROP COLUMN proceso_prod_id;
    END IF;
END $$;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'procesos_produccion'
          AND column_name = 'area_operativa_id'
    ) THEN
        ALTER TABLE procesos_produccion
            DROP COLUMN area_operativa_id;
    END IF;
END $$;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'procesos_produccion'
          AND column_name = 'diagrama_json'
    ) THEN
        ALTER TABLE procesos_produccion
            DROP COLUMN diagrama_json;
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.table_constraints
        WHERE table_name = 'procesos_produccion'
          AND constraint_type = 'UNIQUE'
          AND constraint_name = 'uk_procesos_produccion_producto'
    ) THEN
        ALTER TABLE procesos_produccion
            ADD CONSTRAINT uk_procesos_produccion_producto UNIQUE (producto_id);
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.table_constraints
        WHERE table_name = 'procesos_produccion'
          AND constraint_name = 'fk_procesos_produccion_producto'
    ) THEN
        ALTER TABLE procesos_produccion
            ADD CONSTRAINT fk_procesos_produccion_producto
                FOREIGN KEY (producto_id)
                REFERENCES productos(producto_id)
                ON DELETE CASCADE;
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS proceso_fabricacion_nodo (
    id BIGSERIAL PRIMARY KEY,
    tipo_nodo VARCHAR(31) NOT NULL,
    frontend_id VARCHAR(255) NOT NULL,
    posicion_x DOUBLE PRECISION NOT NULL DEFAULT 0,
    posicion_y DOUBLE PRECISION NOT NULL DEFAULT 0,
    label VARCHAR(255),
    proceso_completo_id INTEGER NOT NULL,
    insumo_id INTEGER,
    proceso_id INTEGER,
    area_operativa_id INTEGER
);

CREATE TABLE IF NOT EXISTS proceso_fabricacion_edge (
    id BIGSERIAL PRIMARY KEY,
    frontend_id VARCHAR(255) NOT NULL,
    proceso_completo_id INTEGER NOT NULL,
    source_node_id BIGINT NOT NULL,
    target_node_id BIGINT NOT NULL
);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.table_constraints
        WHERE table_name = 'proceso_fabricacion_nodo'
          AND constraint_name = 'fk_pf_nodo_proceso_completo'
    ) THEN
        ALTER TABLE proceso_fabricacion_nodo
            ADD CONSTRAINT fk_pf_nodo_proceso_completo
                FOREIGN KEY (proceso_completo_id)
                REFERENCES procesos_produccion(proceso_completo_id)
                ON DELETE CASCADE;
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.table_constraints
        WHERE table_name = 'proceso_fabricacion_nodo'
          AND constraint_name = 'fk_pf_nodo_insumo'
    ) THEN
        ALTER TABLE proceso_fabricacion_nodo
            ADD CONSTRAINT fk_pf_nodo_insumo
                FOREIGN KEY (insumo_id)
                REFERENCES insumos(insumo_id)
                ON DELETE SET NULL;
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.table_constraints
        WHERE table_name = 'proceso_fabricacion_nodo'
          AND constraint_name = 'fk_pf_nodo_proceso'
    ) THEN
        ALTER TABLE proceso_fabricacion_nodo
            ADD CONSTRAINT fk_pf_nodo_proceso
                FOREIGN KEY (proceso_id)
                REFERENCES proceso_produccion(proceso_id)
                ON DELETE SET NULL;
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.table_constraints
        WHERE table_name = 'proceso_fabricacion_nodo'
          AND constraint_name = 'fk_pf_nodo_area_operativa'
    ) THEN
        ALTER TABLE proceso_fabricacion_nodo
            ADD CONSTRAINT fk_pf_nodo_area_operativa
                FOREIGN KEY (area_operativa_id)
                REFERENCES area_operativa(area_id)
                ON DELETE SET NULL;
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.table_constraints
        WHERE table_name = 'proceso_fabricacion_edge'
          AND constraint_name = 'fk_pf_edge_proceso_completo'
    ) THEN
        ALTER TABLE proceso_fabricacion_edge
            ADD CONSTRAINT fk_pf_edge_proceso_completo
                FOREIGN KEY (proceso_completo_id)
                REFERENCES procesos_produccion(proceso_completo_id)
                ON DELETE CASCADE;
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.table_constraints
        WHERE table_name = 'proceso_fabricacion_edge'
          AND constraint_name = 'fk_pf_edge_source'
    ) THEN
        ALTER TABLE proceso_fabricacion_edge
            ADD CONSTRAINT fk_pf_edge_source
                FOREIGN KEY (source_node_id)
                REFERENCES proceso_fabricacion_nodo(id)
                ON DELETE CASCADE;
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.table_constraints
        WHERE table_name = 'proceso_fabricacion_edge'
          AND constraint_name = 'fk_pf_edge_target'
    ) THEN
        ALTER TABLE proceso_fabricacion_edge
            ADD CONSTRAINT fk_pf_edge_target
                FOREIGN KEY (target_node_id)
                REFERENCES proceso_fabricacion_nodo(id)
                ON DELETE CASCADE;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_pf_nodo_proceso_completo
    ON proceso_fabricacion_nodo(proceso_completo_id);

CREATE INDEX IF NOT EXISTS idx_pf_nodo_frontend_id
    ON proceso_fabricacion_nodo(frontend_id);

CREATE INDEX IF NOT EXISTS idx_pf_nodo_insumo
    ON proceso_fabricacion_nodo(insumo_id);

CREATE INDEX IF NOT EXISTS idx_pf_nodo_proceso
    ON proceso_fabricacion_nodo(proceso_id);

CREATE INDEX IF NOT EXISTS idx_pf_nodo_area_operativa
    ON proceso_fabricacion_nodo(area_operativa_id);

CREATE INDEX IF NOT EXISTS idx_pf_edge_proceso_completo
    ON proceso_fabricacion_edge(proceso_completo_id);

CREATE INDEX IF NOT EXISTS idx_pf_edge_frontend_id
    ON proceso_fabricacion_edge(frontend_id);

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'node_connection') THEN
        DROP TABLE node_connection;
    END IF;
END $$;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'node_handle') THEN
        DROP TABLE node_handle;
    END IF;
END $$;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'proceso_produccion_node') THEN
        DROP TABLE proceso_produccion_node;
    END IF;
END $$;
