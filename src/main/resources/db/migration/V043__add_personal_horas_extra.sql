ALTER TABLE IF EXISTS doc_tran_de_personal
    ADD COLUMN IF NOT EXISTS integrante_id BIGINT;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.table_constraints
        WHERE table_schema = 'public'
          AND table_name = 'doc_tran_de_personal'
          AND constraint_name = 'fk_doc_tran_personal_integrante'
    ) THEN
        ALTER TABLE doc_tran_de_personal
            ADD CONSTRAINT fk_doc_tran_personal_integrante
                FOREIGN KEY (integrante_id)
                REFERENCES integrante_personal(id)
                ON DELETE SET NULL;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_doc_tran_personal_integrante
    ON doc_tran_de_personal(integrante_id);

CREATE TABLE IF NOT EXISTS registro_hora_extra (
    registro_hora_extra_id BIGSERIAL PRIMARY KEY,
    integrante_id BIGINT NOT NULL,
    fecha DATE NOT NULL,
    hora_inicio TIME NOT NULL,
    hora_fin TIME NOT NULL,
    minutos INTEGER NOT NULL,
    motivo VARCHAR(500) NOT NULL,
    observaciones VARCHAR(1000),
    estado VARCHAR(20) NOT NULL,
    registrado_por_id BIGINT NOT NULL,
    aprobado_por_id BIGINT,
    fecha_registro TIMESTAMP NOT NULL,
    fecha_decision TIMESTAMP,
    motivo_rechazo_o_anulacion VARCHAR(1000),
    CONSTRAINT fk_registro_hora_extra_integrante
        FOREIGN KEY (integrante_id)
        REFERENCES integrante_personal(id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_registro_hora_extra_registrado_por
        FOREIGN KEY (registrado_por_id)
        REFERENCES users(id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_registro_hora_extra_aprobado_por
        FOREIGN KEY (aprobado_por_id)
        REFERENCES users(id)
        ON DELETE SET NULL,
    CONSTRAINT chk_registro_hora_extra_estado
        CHECK (estado IN ('REGISTRADA', 'APROBADA', 'RECHAZADA', 'ANULADA')),
    CONSTRAINT chk_registro_hora_extra_rango
        CHECK (hora_fin > hora_inicio),
    CONSTRAINT chk_registro_hora_extra_minutos
        CHECK (minutos > 0)
);

CREATE INDEX IF NOT EXISTS idx_registro_hora_extra_integrante_fecha
    ON registro_hora_extra(integrante_id, fecha DESC);

CREATE INDEX IF NOT EXISTS idx_registro_hora_extra_fecha
    ON registro_hora_extra(fecha DESC);

CREATE INDEX IF NOT EXISTS idx_registro_hora_extra_estado
    ON registro_hora_extra(estado);
