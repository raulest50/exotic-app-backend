CREATE TABLE IF NOT EXISTS area_operativa_ruido_muestra (
    id BIGSERIAL PRIMARY KEY,
    area_operativa_id INTEGER NOT NULL,
    usuario_id BIGINT NOT NULL,
    fecha_muestra TIMESTAMP NOT NULL,
    ruido_db DOUBLE PRECISION NOT NULL,
    rms DOUBLE PRECISION NOT NULL,
    duracion_ms INTEGER NOT NULL,
    sample_rate INTEGER NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_area_operativa_ruido_muestra_area
        FOREIGN KEY (area_operativa_id)
        REFERENCES area_operativa(area_id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_area_operativa_ruido_muestra_usuario
        FOREIGN KEY (usuario_id)
        REFERENCES users(id)
        ON DELETE RESTRICT,
    CONSTRAINT chk_area_operativa_ruido_muestra_ruido_db
        CHECK (ruido_db >= -160 AND ruido_db <= 20),
    CONSTRAINT chk_area_operativa_ruido_muestra_rms
        CHECK (rms >= 0 AND rms <= 1),
    CONSTRAINT chk_area_operativa_ruido_muestra_duracion
        CHECK (duracion_ms BETWEEN 500 AND 5000),
    CONSTRAINT chk_area_operativa_ruido_muestra_sample_rate
        CHECK (sample_rate BETWEEN 8000 AND 192000)
);

CREATE INDEX IF NOT EXISTS idx_area_operativa_ruido_muestra_area_fecha
    ON area_operativa_ruido_muestra(area_operativa_id, fecha_muestra);

CREATE INDEX IF NOT EXISTS idx_area_operativa_ruido_muestra_usuario_created_at
    ON area_operativa_ruido_muestra(usuario_id, created_at);
