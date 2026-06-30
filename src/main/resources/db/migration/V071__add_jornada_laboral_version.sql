CREATE TABLE IF NOT EXISTS jornada_laboral_version (
    id BIGSERIAL PRIMARY KEY,
    version INTEGER NOT NULL UNIQUE,
    estado VARCHAR(20) NOT NULL,
    vigente_desde TIMESTAMP NOT NULL,
    vigente_hasta TIMESTAMP NULL,
    creado_en TIMESTAMP NOT NULL,
    creado_por VARCHAR(120) NULL,
    motivo_cambio TEXT NULL,
    CONSTRAINT chk_jornada_laboral_estado
        CHECK (estado IN ('VIGENTE', 'RETIRADA'))
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_jornada_laboral_vigente
    ON jornada_laboral_version (estado)
    WHERE estado = 'VIGENTE';

CREATE TABLE IF NOT EXISTS jornada_laboral_bloque (
    id BIGSERIAL PRIMARY KEY,
    jornada_laboral_version_id BIGINT NOT NULL,
    dia_semana INTEGER NOT NULL,
    orden INTEGER NOT NULL,
    hora_inicio TIME NOT NULL,
    hora_fin TIME NOT NULL,
    CONSTRAINT fk_jornada_laboral_bloque_version
        FOREIGN KEY (jornada_laboral_version_id)
        REFERENCES jornada_laboral_version (id)
        ON DELETE CASCADE,
    CONSTRAINT uk_jornada_laboral_bloque_dia_orden
        UNIQUE (jornada_laboral_version_id, dia_semana, orden),
    CONSTRAINT chk_jornada_laboral_bloque_dia
        CHECK (dia_semana BETWEEN 1 AND 7),
    CONSTRAINT chk_jornada_laboral_bloque_orden
        CHECK (orden >= 0),
    CONSTRAINT chk_jornada_laboral_bloque_horas
        CHECK (hora_inicio < hora_fin)
);

CREATE INDEX IF NOT EXISTS idx_jornada_laboral_bloque_version
    ON jornada_laboral_bloque (jornada_laboral_version_id);

WITH inserted_version AS (
    INSERT INTO jornada_laboral_version (
        version,
        estado,
        vigente_desde,
        creado_en,
        creado_por,
        motivo_cambio
    )
    SELECT
        1,
        'VIGENTE',
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP,
        'system',
        'Carga inicial de jornada laboral operativa'
    WHERE NOT EXISTS (
        SELECT 1 FROM jornada_laboral_version
    )
    RETURNING id
),
target_version AS (
    SELECT id FROM inserted_version
    UNION ALL
    SELECT id
    FROM jornada_laboral_version
    WHERE version = 1
      AND NOT EXISTS (
          SELECT 1 FROM jornada_laboral_bloque
      )
)
INSERT INTO jornada_laboral_bloque (
    jornada_laboral_version_id,
    dia_semana,
    orden,
    hora_inicio,
    hora_fin
)
SELECT
    target_version.id,
    defaults.dia_semana,
    defaults.orden,
    defaults.hora_inicio,
    defaults.hora_fin
FROM target_version
CROSS JOIN (
    VALUES
        (1, 0, TIME '07:30', TIME '12:00'),
        (1, 1, TIME '13:00', TIME '17:00'),
        (2, 0, TIME '07:30', TIME '12:00'),
        (2, 1, TIME '13:00', TIME '17:00'),
        (3, 0, TIME '07:30', TIME '12:00'),
        (3, 1, TIME '13:00', TIME '17:00'),
        (4, 0, TIME '07:30', TIME '12:00'),
        (4, 1, TIME '13:00', TIME '17:00'),
        (5, 0, TIME '07:30', TIME '12:00'),
        (5, 1, TIME '13:00', TIME '17:00'),
        (6, 0, TIME '07:30', TIME '12:00'),
        (6, 1, TIME '13:00', TIME '17:00')
) AS defaults(dia_semana, orden, hora_inicio, hora_fin)
ON CONFLICT DO NOTHING;

INSERT INTO tab_accesos (modulo_acceso_id, tab_id, nivel)
SELECT
    modulo_accesos.id,
    'JORNADA_LABORAL',
    COALESCE(MAX(tab_accesos.nivel), 1)
FROM modulo_accesos
LEFT JOIN tab_accesos
    ON tab_accesos.modulo_acceso_id = modulo_accesos.id
WHERE modulo_accesos.modulo = 'ADMINISTRACION_GLOBAL'
GROUP BY modulo_accesos.id
ON CONFLICT (modulo_acceso_id, tab_id) DO NOTHING;
