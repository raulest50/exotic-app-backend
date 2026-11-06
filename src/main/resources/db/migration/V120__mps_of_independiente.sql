-- MPS OF: revision, aprobacion y emision independientes del MPS OP.
-- Las propuestas actuales siguen siendo borradores sin aprobacion atribuida.
-- Las OF historicas/manuales no se enlazan por producto, fecha o semana.

ALTER TABLE mps_fabricacion_semanal
    ADD COLUMN estado VARCHAR(20) NOT NULL DEFAULT 'BORRADOR',
    ADD COLUMN revision_numero INTEGER NOT NULL DEFAULT 1,
    ADD COLUMN aprobado_por_username VARCHAR(100) NULL,
    ADD COLUMN fecha_aprobacion TIMESTAMP NULL,
    ADD COLUMN emitido_por_username VARCHAR(100) NULL,
    ADD COLUMN fecha_emision TIMESTAMP NULL,
    ADD CONSTRAINT chk_mps_of_estado
        CHECK (estado IN ('BORRADOR', 'APROBADO', 'CERRADO')),
    ADD CONSTRAINT chk_mps_of_revision CHECK (revision_numero > 0),
    ADD CONSTRAINT chk_mps_of_aprobacion_audit CHECK (
        (aprobado_por_username IS NULL AND fecha_aprobacion IS NULL)
        OR (aprobado_por_username IS NOT NULL AND fecha_aprobacion IS NOT NULL
            AND btrim(aprobado_por_username) <> '')
    ),
    ADD CONSTRAINT chk_mps_of_emision_audit CHECK (
        (emitido_por_username IS NULL AND fecha_emision IS NULL)
        OR (emitido_por_username IS NOT NULL AND fecha_emision IS NOT NULL
            AND btrim(emitido_por_username) <> '')
    );

ALTER TABLE mps_fabricacion_detalle
    ADD COLUMN estado VARCHAR(20) NOT NULL DEFAULT 'ACTIVO',
    ADD CONSTRAINT chk_mps_of_detalle_estado
        CHECK (estado IN ('ACTIVO', 'CANCELADO'));

CREATE INDEX idx_mps_of_detalle_estado_posicion
    ON mps_fabricacion_detalle(mps_id, estado, posicion);

ALTER TABLE orden_fabricacion
    ADD COLUMN mps_fabricacion_detalle_id BIGINT NULL,
    ADD CONSTRAINT fk_of_mps_fabricacion_detalle
        FOREIGN KEY (mps_fabricacion_detalle_id)
        REFERENCES mps_fabricacion_detalle(id) ON DELETE RESTRICT,
    ADD CONSTRAINT uk_of_mps_fabricacion_detalle
        UNIQUE (mps_fabricacion_detalle_id),
    ADD CONSTRAINT chk_of_origen_independiente CHECK (
        mps_fabricacion_detalle_id IS NULL OR orden_produccion_origen_id IS NULL
    );

CREATE TABLE mps_fabricacion_observacion (
    observacion_id BIGSERIAL PRIMARY KEY,
    mps_id BIGINT NOT NULL,
    revision_mps INTEGER NOT NULL DEFAULT 1,
    autor_username VARCHAR(100) NOT NULL,
    mensaje TEXT NOT NULL,
    tipo VARCHAR(20) NOT NULL DEFAULT 'BLOQUEANTE',
    estado VARCHAR(20) NOT NULL DEFAULT 'ABIERTA',
    respuesta_correccion TEXT NULL,
    atendida_por_username VARCHAR(100) NULL,
    fecha_atencion TIMESTAMP NULL,
    cerrada_por_username VARCHAR(100) NULL,
    fecha_cierre TIMESTAMP NULL,
    fecha_creacion TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_mps_fabricacion_observacion_mps
        FOREIGN KEY (mps_id)
        REFERENCES mps_fabricacion_semanal(id) ON DELETE RESTRICT,
    CONSTRAINT chk_mps_of_obs_revision CHECK (revision_mps > 0),
    CONSTRAINT chk_mps_of_obs_tipo CHECK (
        tipo IN ('BLOQUEANTE', 'ADVERTENCIA', 'INFORMATIVA', 'OTRO')
    ),
    CONSTRAINT chk_mps_of_obs_estado CHECK (
        estado IN ('ABIERTA', 'ATENDIDA', 'CERRADA')
    ),
    CONSTRAINT chk_mps_of_obs_autor CHECK (btrim(autor_username) <> ''),
    CONSTRAINT chk_mps_of_obs_mensaje CHECK (btrim(mensaje) <> ''),
    CONSTRAINT chk_mps_of_obs_atencion_audit CHECK (
        (atendida_por_username IS NULL AND fecha_atencion IS NULL)
        OR (atendida_por_username IS NOT NULL AND fecha_atencion IS NOT NULL
            AND btrim(atendida_por_username) <> '')
    ),
    CONSTRAINT chk_mps_of_obs_cierre_audit CHECK (
        (cerrada_por_username IS NULL AND fecha_cierre IS NULL)
        OR (cerrada_por_username IS NOT NULL AND fecha_cierre IS NOT NULL
            AND btrim(cerrada_por_username) <> '')
    )
);

CREATE INDEX idx_mps_of_obs_mps_estado
    ON mps_fabricacion_observacion(mps_id, estado);
CREATE INDEX idx_mps_of_obs_mps_revision
    ON mps_fabricacion_observacion(mps_id, revision_mps);

COMMENT ON COLUMN mps_fabricacion_semanal.revision_numero IS
    'Revision del programa revisado por el negocio; independiente de version tecnica JPA.';
COMMENT ON COLUMN orden_fabricacion.mps_fabricacion_detalle_id IS
    'Una tanda del MPS OF emite una sola OF. NULL conserva ordenes manuales e historicas sin atribuirles una propuesta.';
COMMENT ON TABLE mps_fabricacion_observacion IS
    'Observaciones de aprobacion del MPS OF, independientes del MPS OP y asociadas a la revision del programa.';
