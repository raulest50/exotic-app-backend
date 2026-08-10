CREATE TABLE IF NOT EXISTS organigrama_estado (
    id SMALLINT PRIMARY KEY,
    revision BIGINT NOT NULL,
    actualizado_en TIMESTAMP NOT NULL,
    actualizado_por VARCHAR(120) NULL,
    CONSTRAINT chk_organigrama_estado_singleton CHECK (id = 1),
    CONSTRAINT chk_organigrama_revision_non_negative CHECK (revision >= 0)
);

INSERT INTO organigrama_estado (
    id,
    revision,
    actualizado_en,
    actualizado_por
)
VALUES (
    1,
    0,
    CURRENT_TIMESTAMP,
    'system'
)
ON CONFLICT (id) DO NOTHING;
