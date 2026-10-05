-- Propuestas independientes del MPS OP; esta migracion no emite OF ni mueve stock.
ALTER TABLE area_operativa
    ADD COLUMN visibilidad_mps VARCHAR(20) NOT NULL DEFAULT 'SOLO_OP',
    ADD COLUMN alcance_mps VARCHAR(20) NOT NULL DEFAULT 'TODOS',
    ADD CONSTRAINT chk_area_visibilidad_mps CHECK (visibilidad_mps IN ('SOLO_OP', 'SOLO_OF', 'AMBOS')),
    ADD CONSTRAINT chk_area_alcance_mps CHECK (alcance_mps IN ('TODOS', 'SOLO_RUTA'));

CREATE TABLE mps_fabricacion_semanal (
    id BIGSERIAL PRIMARY KEY,
    week_start_date DATE NOT NULL UNIQUE,
    version BIGINT NOT NULL DEFAULT 0,
    creado_en TIMESTAMP NOT NULL,
    actualizado_en TIMESTAMP NOT NULL,
    creado_por VARCHAR(255) NOT NULL,
    actualizado_por VARCHAR(255) NOT NULL,
    CONSTRAINT chk_mps_of_lunes CHECK (EXTRACT(ISODOW FROM week_start_date) = 1)
);

CREATE TABLE mps_fabricacion_detalle (
    id BIGSERIAL PRIMARY KEY,
    mps_id BIGINT NOT NULL REFERENCES mps_fabricacion_semanal(id),
    semiterminado_id VARCHAR(255) NOT NULL REFERENCES productos(producto_id),
    cantidad NUMERIC(18,4) NOT NULL CHECK (cantidad > 0),
    unidad_medida VARCHAR(20) NOT NULL,
    fecha_inicio TIMESTAMP NOT NULL,
    fecha_final TIMESTAMP NOT NULL,
    observaciones VARCHAR(2000),
    posicion INTEGER NOT NULL,
    CONSTRAINT chk_mps_of_fechas CHECK (fecha_final >= fecha_inicio)
);
CREATE INDEX idx_mps_of_detalle_programa ON mps_fabricacion_detalle(mps_id, posicion);
CREATE INDEX idx_of_semana ON orden_fabricacion
    (COALESCE(fecha_lanzamiento, fecha_creacion), orden_fabricacion_id);
