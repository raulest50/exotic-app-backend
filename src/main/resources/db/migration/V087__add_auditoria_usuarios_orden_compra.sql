ALTER TABLE orden_compra
    ADD COLUMN usuario_creador_id BIGINT,
    ADD COLUMN usuario_creador_username VARCHAR(120),
    ADD COLUMN usuario_liberador_id BIGINT,
    ADD COLUMN usuario_liberador_username VARCHAR(120),
    ADD COLUMN fecha_liberacion TIMESTAMP;

ALTER TABLE orden_compra
    ADD CONSTRAINT fk_orden_compra_usuario_creador
        FOREIGN KEY (usuario_creador_id)
        REFERENCES users(id)
        ON DELETE RESTRICT,
    ADD CONSTRAINT fk_orden_compra_usuario_liberador
        FOREIGN KEY (usuario_liberador_id)
        REFERENCES users(id)
        ON DELETE RESTRICT;

CREATE INDEX idx_orden_compra_usuario_creador
    ON orden_compra (usuario_creador_id);

CREATE INDEX idx_orden_compra_usuario_liberador
    ON orden_compra (usuario_liberador_id);
