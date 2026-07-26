ALTER TABLE productos
    ADD COLUMN consumo_directo BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE movimientos
    ADD COLUMN afecta_inventario BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE productos
    ADD CONSTRAINT chk_productos_consumo_directo
    CHECK (
        consumo_directo = FALSE
        OR (tipo_producto = 'M' AND inventareable = FALSE)
    );

-- El agua (M0101001) participa en recetas y dispensaciones, pero no representa
-- existencias físicas almacenadas.
UPDATE productos
SET inventareable = FALSE,
    consumo_directo = TRUE,
    punto_reorden = -1,
    stock_minimo = 0
WHERE producto_id = 'M0101001'
  AND tipo_producto = 'M';

UPDATE movimientos
SET afecta_inventario = FALSE
WHERE producto_id = 'M0101001';
