-- Catálogo independiente de las unidades particulares de áreas operativas y calidad.
-- No modifica productos ni recetas existentes. Las referencias son kg, L y U.
CREATE TABLE unidad_medida_producto (
    codigo VARCHAR(16) PRIMARY KEY,
    simbolo VARCHAR(16) NOT NULL,
    magnitud VARCHAR(16) NOT NULL,
    factor_a_referencia NUMERIC(24, 12) NOT NULL,
    CONSTRAINT ck_ump_codigo CHECK (codigo ~ '^[A-Z][A-Z0-9_]*$'),
    CONSTRAINT ck_ump_simbolo CHECK (btrim(simbolo) <> ''),
    CONSTRAINT ck_ump_magnitud CHECK (magnitud IN ('MASA', 'VOLUMEN', 'CONTEO')),
    CONSTRAINT ck_ump_factor CHECK (factor_a_referencia > 0)
);

COMMENT ON COLUMN unidad_medida_producto.factor_a_referencia IS
    'Cantidad en kg (MASA), L (VOLUMEN) o U (CONTEO) equivalente a una unidad del código.';

INSERT INTO unidad_medida_producto (codigo, simbolo, magnitud, factor_a_referencia)
VALUES
    ('KG', 'kg', 'MASA', 1),
    ('G', 'g', 'MASA', 0.001),
    ('L', 'L', 'VOLUMEN', 1),
    ('ML', 'mL', 'VOLUMEN', 0.001),
    ('U', 'U', 'CONTEO', 1);
