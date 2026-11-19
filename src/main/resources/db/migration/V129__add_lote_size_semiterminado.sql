-- Nominal OF del semiterminado; NULL conserva los productos existentes sin parametrizar.
ALTER TABLE productos
    ADD COLUMN lote_size_of NUMERIC(18,4),
    ADD CONSTRAINT chk_producto_lote_size_of
        CHECK (lote_size_of IS NULL OR (tipo_producto = 'S' AND lote_size_of > 0));

COMMENT ON COLUMN productos.lote_size_of IS
    'Tamano nominal de una tanda OF en tipo_unidades, exclusivo de semiterminados. Independiente de la salida de receta; no modifica cantidades de MPS ni ordenes existentes.';
