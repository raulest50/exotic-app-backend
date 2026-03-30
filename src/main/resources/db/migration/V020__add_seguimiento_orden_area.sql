-- Tabla para seguimiento de órdenes de producción por áreas operativas
-- Rastrea el progreso de cada orden a través de los nodos de la ruta de proceso

CREATE TABLE seguimiento_orden_area (
    id BIGSERIAL PRIMARY KEY,
    orden_produccion_id INTEGER NOT NULL REFERENCES ordenes_produccion(orden_id) ON DELETE CASCADE,
    ruta_proceso_node_id BIGINT NOT NULL REFERENCES ruta_proceso_node(id) ON DELETE CASCADE,
    area_operativa_id INTEGER NOT NULL REFERENCES area_operativa(area_id) ON DELETE CASCADE,
    estado INTEGER NOT NULL DEFAULT 0,  -- 0=PENDIENTE, 1=VISIBLE, 2=COMPLETADO, 3=OMITIDO
    posicion_secuencia INTEGER,
    fecha_creacion TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    fecha_visible TIMESTAMP,
    fecha_completado TIMESTAMP,
    usuario_reporta_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    observaciones VARCHAR(500),
    CONSTRAINT uq_soa_orden_nodo UNIQUE (orden_produccion_id, ruta_proceso_node_id)
);

-- Indices para consultas frecuentes
CREATE INDEX idx_soa_area_estado ON seguimiento_orden_area(area_operativa_id, estado);
CREATE INDEX idx_soa_orden ON seguimiento_orden_area(orden_produccion_id);
CREATE INDEX idx_soa_usuario ON seguimiento_orden_area(usuario_reporta_id);
