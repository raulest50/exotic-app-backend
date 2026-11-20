-- Identificador de equipo para el codigo de lote OF; sin asignar a activos historicos.
ALTER TABLE activo
    ADD COLUMN codigo_lote_fabricacion VARCHAR(255),
    ADD CONSTRAINT uk_activo_codigo_lote_fabricacion UNIQUE (codigo_lote_fabricacion);

COMMENT ON COLUMN activo.codigo_lote_fabricacion IS
    'Codigo unico del equipo para componer lotes OF, distinto del ID del activo. Opcional para adopcion gradual; conserva ceros iniciales. No cambia lotes existentes.';
