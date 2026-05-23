UPDATE proveedores
SET limite_recepciones_parciales_ocm = 2
WHERE limite_recepciones_parciales_ocm IS NULL;

INSERT INTO master_directive (nombre, resumen, valor, tipo_dato, grupo, ayuda)
VALUES (
    'LIMITE_RECEPCIONES_PARCIALES_OCM',
    'Tope global de recepciones parciales permitidas por OCM',
    '3',
    'NUMERO',
    'COMPRAS_ALMACEN',
    'Define el maximo global que puede asignarse como limite de recepciones parciales OCM a cualquier proveedor.'
)
ON CONFLICT (nombre) DO UPDATE
SET valor = '3',
    tipo_dato = 'NUMERO',
    grupo = 'COMPRAS_ALMACEN',
    resumen = EXCLUDED.resumen,
    ayuda = EXCLUDED.ayuda;
