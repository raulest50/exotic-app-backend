CREATE TABLE control_idempotencia (
    id BIGSERIAL PRIMARY KEY,
    actor_id BIGINT NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    accion VARCHAR(80) NOT NULL,
    recurso VARCHAR(180) NOT NULL,
    clave VARCHAR(200) NOT NULL,
    huella_payload CHAR(64) NOT NULL,
    respuesta_json TEXT NULL,
    creada_en TIMESTAMP NOT NULL,
    completada_en TIMESTAMP NULL,
    CONSTRAINT uq_control_idempotencia_actor_accion_recurso_clave
        UNIQUE (actor_id, accion, recurso, clave),
    CONSTRAINT chk_control_idempotencia_huella
        CHECK (huella_payload ~ '^[0-9a-f]{64}$'),
    CONSTRAINT chk_control_idempotencia_completada
        CHECK ((respuesta_json IS NULL AND completada_en IS NULL)
            OR (respuesta_json IS NOT NULL AND completada_en IS NOT NULL))
);

CREATE INDEX idx_control_idempotencia_creada_en
    ON control_idempotencia(creada_en);

COMMENT ON TABLE control_idempotencia IS
    'Registro transaccional de mutaciones idempotentes por actor, accion, recurso y clave.';
COMMENT ON COLUMN control_idempotencia.huella_payload IS
    'SHA-256 del payload canonico; impide reutilizar una clave con una intencion diferente.';
