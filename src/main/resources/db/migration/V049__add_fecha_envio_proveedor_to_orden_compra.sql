ALTER TABLE orden_compra
    ADD COLUMN IF NOT EXISTS fecha_envio_proveedor TIMESTAMP NULL;
