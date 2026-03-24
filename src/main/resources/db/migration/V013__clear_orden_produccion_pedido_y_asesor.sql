-- Vacía número de pedido comercial y deja sin asesor (FK) las órdenes de producción existentes.
-- responsable_id es entero FK; no admite cadena vacía, se usa NULL.

ALTER TABLE ordenes_produccion
    ALTER COLUMN responsable_id DROP NOT NULL;

UPDATE ordenes_produccion
SET numero_pedido_comercial = '';

UPDATE ordenes_produccion
SET responsable_id = NULL;
