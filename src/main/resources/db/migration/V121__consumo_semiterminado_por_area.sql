-- Asignacion de semiterminados inventariables a etapas de una version de ruta.
-- El servicio valida que el producto sea un SemiTerminado con OF propia y que
-- aparezca en una sola etapa por version. No se asignan consumos historicos.

CREATE TABLE ruta_proceso_node_semiterminado (
    node_id BIGINT NOT NULL,
    producto_id VARCHAR(255) NOT NULL,
    CONSTRAINT pk_ruta_node_semiterminado PRIMARY KEY (node_id, producto_id),
    CONSTRAINT fk_ruta_node_semiterminado_node
        FOREIGN KEY (node_id) REFERENCES ruta_proceso_node(id) ON DELETE CASCADE,
    CONSTRAINT fk_ruta_node_semiterminado_producto
        FOREIGN KEY (producto_id) REFERENCES productos(producto_id) ON DELETE RESTRICT
);

CREATE INDEX idx_ruta_node_semiterminado_producto
    ON ruta_proceso_node_semiterminado(producto_id);

-- NULL conserva el flujo previo de las OP historicas. Las OP nuevas congelan
-- requerimientos, unidades y etapa de consumo al emitirse, sin usar la receta
-- ni la ruta vigente para reinterpretar posteriormente su ejecucion.
ALTER TABLE ordenes_produccion
    ADD COLUMN requerimientos_materiales_json TEXT NULL;

-- La clave se conserva en la misma transaccion que sus movimientos. No se
-- generan claves ni hashes para transacciones historicas. El servicio autoriza
-- la OP/area antes de devolver un resultado previo y verifica el hash canonico.
ALTER TABLE transaccion_almacen
    ADD COLUMN idempotency_key UUID NULL,
    ADD COLUMN request_hash VARCHAR(64) NULL,
    ADD CONSTRAINT uk_transaccion_almacen_idempotency_key UNIQUE (idempotency_key),
    ADD CONSTRAINT chk_transaccion_almacen_idempotency_pair CHECK (
        (idempotency_key IS NULL AND request_hash IS NULL)
        OR (idempotency_key IS NOT NULL AND request_hash IS NOT NULL)
    ),
    ADD CONSTRAINT chk_transaccion_almacen_request_hash CHECK (
        request_hash IS NULL OR request_hash ~ '^[0-9a-fA-F]{64}$'
    );

COMMENT ON TABLE ruta_proceso_node_semiterminado IS
    'Semiterminados consumidos por una etapa de la ruta versionada; cantidades y lotes se determinan por OP, no en la ruta.';
COMMENT ON COLUMN ordenes_produccion.requerimientos_materiales_json IS
    'Snapshot operativo de requerimientos y asignaciones de consumo al emitir la OP. NULL mantiene el flujo historico sin atribuirle una configuracion actual.';
COMMENT ON COLUMN transaccion_almacen.idempotency_key IS
    'Clave de la solicitud de consumo por area. Su unicidad evita registrar dos transacciones para un mismo reintento.';
COMMENT ON COLUMN transaccion_almacen.request_hash IS
    'SHA-256 canonico de la solicitud; impide reutilizar una clave de idempotencia con contenido diferente.';
