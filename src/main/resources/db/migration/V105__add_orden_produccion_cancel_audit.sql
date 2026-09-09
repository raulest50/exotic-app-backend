ALTER TABLE ordenes_produccion
    ADD COLUMN cancelada_en TIMESTAMP,
    ADD COLUMN cancelada_por_id BIGINT,
    ADD COLUMN cancelada_por_username VARCHAR(255),
    ADD COLUMN cancelada_por_nombre_completo VARCHAR(255);

ALTER TABLE ordenes_produccion
    ADD CONSTRAINT fk_orden_produccion_cancelada_por
        FOREIGN KEY (cancelada_por_id)
        REFERENCES users(id)
        ON DELETE RESTRICT,
    ADD CONSTRAINT chk_orden_produccion_cancelacion_auditoria
        CHECK (
            (
                cancelada_en IS NULL
                AND cancelada_por_id IS NULL
                AND cancelada_por_username IS NULL
                AND cancelada_por_nombre_completo IS NULL
            )
            OR
            (
                estado_orden = -1
                AND cancelada_en IS NOT NULL
                AND cancelada_por_id IS NOT NULL
                AND cancelada_por_username IS NOT NULL
                AND cancelada_por_nombre_completo IS NOT NULL
            )
        );

CREATE INDEX idx_orden_produccion_cancelada_por
    ON ordenes_produccion (cancelada_por_id);
