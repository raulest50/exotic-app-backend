ALTER TABLE proveedores
    ADD COLUMN lead_time_negociado integer;

ALTER TABLE proveedores
    ADD CONSTRAINT proveedores_lead_time_negociado_check
    CHECK (lead_time_negociado IS NULL OR lead_time_negociado >= 0);

COMMENT ON COLUMN proveedores.lead_time_negociado IS
    'Referencia manual vigente del plazo negociado en dias calendario enteros. NULL indica sin definir; cero es valido.';
