CREATE TABLE IF NOT EXISTS empresa_identidad_legal_version (
    id BIGSERIAL PRIMARY KEY,
    version INTEGER NOT NULL UNIQUE,
    estado VARCHAR(20) NOT NULL,
    razon_social VARCHAR(255) NOT NULL,
    nombre_comercial VARCHAR(160) NOT NULL,
    tipo_identificacion VARCHAR(30) NOT NULL,
    numero_identificacion VARCHAR(40) NOT NULL,
    digito_verificacion VARCHAR(10) NOT NULL,
    telefono_principal VARCHAR(80) NOT NULL,
    email_principal VARCHAR(255) NOT NULL,
    vigente_desde TIMESTAMP NOT NULL,
    vigente_hasta TIMESTAMP NULL,
    creado_en TIMESTAMP NOT NULL,
    creado_por VARCHAR(120) NULL,
    motivo_cambio TEXT NULL,
    CONSTRAINT chk_empresa_identidad_legal_estado
        CHECK (estado IN ('VIGENTE', 'RETIRADA'))
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_empresa_identidad_legal_vigente
    ON empresa_identidad_legal_version (estado)
    WHERE estado = 'VIGENTE';

INSERT INTO empresa_identidad_legal_version (
    version,
    estado,
    razon_social,
    nombre_comercial,
    tipo_identificacion,
    numero_identificacion,
    digito_verificacion,
    telefono_principal,
    email_principal,
    vigente_desde,
    creado_en,
    creado_por,
    motivo_cambio
)
SELECT
    1,
    'VIGENTE',
    'Napolitana J.P S.A.S.',
    'EXOTIC EXPERT',
    'NIT',
    '901751897',
    '1',
    '301 711 51 81',
    'produccion.exotic@gmail.com',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    'system',
    'Carga inicial desde literales historicos del PDF OCM'
WHERE NOT EXISTS (
    SELECT 1 FROM empresa_identidad_legal_version
);

ALTER TABLE orden_compra
    ADD COLUMN IF NOT EXISTS empresa_identidad_legal_version_id BIGINT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_orden_compra_empresa_identidad_legal_version'
    ) THEN
        ALTER TABLE orden_compra
            ADD CONSTRAINT fk_orden_compra_empresa_identidad_legal_version
            FOREIGN KEY (empresa_identidad_legal_version_id)
            REFERENCES empresa_identidad_legal_version (id);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_orden_compra_empresa_identidad_legal_version
    ON orden_compra (empresa_identidad_legal_version_id);
