-- Adopcion gradual: no exige equipos ni renumera lotes de OF historicas.
ALTER TABLE area_operativa
    ADD COLUMN requiere_equipo_lote_of BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE orden_fabricacion_operacion
    ADD COLUMN requiere_equipo_lote_of BOOLEAN NOT NULL DEFAULT FALSE;

-- Seleccion propia de la receta; el catalogo general del proceso no se modifica.
ALTER TABLE proceso_fabricacion_nodo
    ADD COLUMN recursos_produccion_ids JSONB NULL,
    ADD CONSTRAINT chk_pf_nodo_recursos_produccion CHECK (
        recursos_produccion_ids IS NULL
        OR (tipo_nodo = 'PROCESO' AND jsonb_typeof(recursos_produccion_ids) = 'array')
    );

-- La reserva sobrevive al cambio del codigo provisional a su formato definitivo.
-- Las referencias al registro Lote permanecen intactas.
ALTER TABLE orden_fabricacion
    ADD COLUMN prefijo_lote_reservado VARCHAR(20) NULL,
    ADD COLUMN consecutivo_lote_reservado BIGINT NULL,
    ADD COLUMN fecha_lote_origen VARCHAR(20) NULL,
    ADD COLUMN activo_lote_id VARCHAR(255) NULL,
    ADD COLUMN codigo_equipo_lote VARCHAR(255) NULL,
    ADD CONSTRAINT uk_of_consecutivo_lote
        UNIQUE (semiterminado_id, consecutivo_lote_reservado),
    ADD CONSTRAINT chk_of_reserva_lote CHECK (
        (prefijo_lote_reservado IS NULL AND consecutivo_lote_reservado IS NULL)
        OR (prefijo_lote_reservado IS NOT NULL
            AND btrim(prefijo_lote_reservado) <> ''
            AND consecutivo_lote_reservado IS NOT NULL
            AND consecutivo_lote_reservado BETWEEN 1 AND 99999)
    ),
    ADD CONSTRAINT chk_of_fecha_lote_origen CHECK (
        fecha_lote_origen IS NULL OR fecha_lote_origen IN ('INICIO', 'TERMINACION')
    ),
    ADD CONSTRAINT fk_of_activo_lote
        FOREIGN KEY (activo_lote_id) REFERENCES activo(id) ON DELETE RESTRICT,
    ADD CONSTRAINT chk_of_equipo_lote CHECK (
        (activo_lote_id IS NULL AND codigo_equipo_lote IS NULL)
        OR (activo_lote_id IS NOT NULL
            AND codigo_equipo_lote IS NOT NULL
            AND btrim(codigo_equipo_lote) <> '')
    );

CREATE INDEX idx_of_activo_lote ON orden_fabricacion(activo_lote_id)
    WHERE activo_lote_id IS NOT NULL;

COMMENT ON COLUMN area_operativa.requiere_equipo_lote_of IS
    'Exige identificar equipo al completar la salida OF. No altera OP ni exige datos historicos.';
COMMENT ON COLUMN orden_fabricacion_operacion.requiere_equipo_lote_of IS
    'Requisito de equipo congelado al emitir la OF, independiente de cambios posteriores del area.';
COMMENT ON COLUMN proceso_fabricacion_nodo.recursos_produccion_ids IS
    'Tipos de recurso previstos para esta operacion del semiterminado OF. NULL conserva recetas historicas sin seleccion.';
COMMENT ON COLUMN orden_fabricacion.prefijo_lote_reservado IS
    'Prefijo congelado al reservar el consecutivo OF; NULL conserva el formato historico.';
COMMENT ON COLUMN orden_fabricacion.consecutivo_lote_reservado IS
    'Consecutivo OF por semiterminado, sin reinicio anual e independiente del texto del lote.';
COMMENT ON COLUMN orden_fabricacion.fecha_lote_origen IS
    'Politica congelada para la fecha del codigo definitivo: inicio o terminacion real.';
COMMENT ON COLUMN orden_fabricacion.codigo_equipo_lote IS
    'Codigo del equipo congelado al cerrar la OF; cambios del catalogo no renumeran lotes.';
