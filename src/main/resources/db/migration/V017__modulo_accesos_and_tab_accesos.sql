-- Reemplazo del modelo plano accesos por modulo_accesos + tab_accesos (reset de datos de accesos).

DELETE FROM accesos;

DROP TABLE IF EXISTS accesos;

CREATE TABLE modulo_accesos (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    modulo      VARCHAR(100) NOT NULL,
    CONSTRAINT uq_modulo_accesos_user_modulo UNIQUE (user_id, modulo)
);

CREATE TABLE tab_accesos (
    id                 BIGSERIAL PRIMARY KEY,
    modulo_acceso_id   BIGINT       NOT NULL REFERENCES modulo_accesos (id) ON DELETE CASCADE,
    tab_id             VARCHAR(128) NOT NULL,
    nivel              INTEGER      NOT NULL,
    CONSTRAINT uq_tab_accesos_modulo_tab UNIQUE (modulo_acceso_id, tab_id)
);

CREATE INDEX idx_modulo_accesos_user_id ON modulo_accesos (user_id);
