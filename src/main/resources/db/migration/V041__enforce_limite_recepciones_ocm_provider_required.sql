UPDATE proveedores
SET limite_recepciones_parciales_ocm = 2
WHERE limite_recepciones_parciales_ocm IS NULL
   OR limite_recepciones_parciales_ocm <= 0;

ALTER TABLE proveedores
ALTER COLUMN limite_recepciones_parciales_ocm SET DEFAULT 2;

ALTER TABLE proveedores
ALTER COLUMN limite_recepciones_parciales_ocm SET NOT NULL;

ALTER TABLE proveedores
DROP CONSTRAINT IF EXISTS proveedores_limite_recepciones_parciales_ocm_check;

ALTER TABLE proveedores
ADD CONSTRAINT proveedores_limite_recepciones_parciales_ocm_check
CHECK (limite_recepciones_parciales_ocm >= 1);
