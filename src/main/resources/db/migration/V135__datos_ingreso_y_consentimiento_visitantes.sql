-- Los registros historicos conservan NULL; no se presume consentimiento retroactivo.
ALTER TABLE registro_visitante
    ADD COLUMN cargo_empresa VARCHAR(160),
    ADD COLUMN tipo_ingreso VARCHAR(30),
    ADD COLUMN area_persona_visita VARCHAR(160),
    ADD COLUMN consentimiento_version VARCHAR(40),
    ADD COLUMN consentimiento_texto TEXT,
    ADD COLUMN consentimiento_aceptado_en TIMESTAMP WITH TIME ZONE,
    ADD COLUMN firma_contenido BYTEA,
    ADD COLUMN firma_sha256 VARCHAR(64),
    ADD COLUMN consentimiento_firma_texto TEXT;

ALTER TABLE registro_visitante
    ADD CONSTRAINT ck_registro_visitante_cargo_empresa CHECK (
        cargo_empresa IS NULL OR length(btrim(cargo_empresa)) > 0
    ),
    ADD CONSTRAINT ck_registro_visitante_tipo_ingreso CHECK (
        tipo_ingreso IS NULL OR tipo_ingreso IN ('TRABAJADOR', 'VISITANTE', 'CONTRATISTA', 'PROVEEDOR', 'OTRO')
    ),
    ADD CONSTRAINT ck_registro_visitante_area_persona CHECK (
        area_persona_visita IS NULL OR length(btrim(area_persona_visita)) > 0
    ),
    ADD CONSTRAINT ck_registro_visitante_consentimiento CHECK (
        CASE WHEN consentimiento_aceptado_en IS NULL THEN
            consentimiento_version IS NULL AND consentimiento_texto IS NULL
        ELSE (
            consentimiento_version IS NOT NULL AND length(btrim(consentimiento_version)) > 0
            AND consentimiento_texto IS NOT NULL AND length(btrim(consentimiento_texto)) > 0
            AND consentimiento_aceptado_en = fecha_registro
        ) IS TRUE END
    ),
    ADD CONSTRAINT ck_registro_visitante_firma CHECK (
        CASE WHEN firma_contenido IS NULL THEN
            firma_sha256 IS NULL AND consentimiento_firma_texto IS NULL
        ELSE (
            octet_length(firma_contenido) BETWEEN 1 AND 131072
            AND firma_sha256 IS NOT NULL AND firma_sha256 ~ '^[0-9a-f]{64}$'
            AND consentimiento_firma_texto IS NOT NULL AND length(btrim(consentimiento_firma_texto)) > 0
            AND consentimiento_aceptado_en IS NOT NULL
        ) IS TRUE END
    );
