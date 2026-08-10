CREATE TABLE IF NOT EXISTS mision_vision_version (
    id BIGSERIAL PRIMARY KEY,
    version INTEGER NOT NULL UNIQUE,
    estado VARCHAR(20) NOT NULL,
    mision_html TEXT NOT NULL,
    vision_html TEXT NOT NULL,
    vigente_desde TIMESTAMP NOT NULL,
    vigente_hasta TIMESTAMP NULL,
    creado_en TIMESTAMP NOT NULL,
    creado_por VARCHAR(120) NULL,
    motivo_cambio TEXT NOT NULL,
    origen_version_id BIGINT NULL,
    CONSTRAINT chk_mision_vision_estado
        CHECK (estado IN ('VIGENTE', 'RETIRADA')),
    CONSTRAINT fk_mision_vision_origen_version
        FOREIGN KEY (origen_version_id)
        REFERENCES mision_vision_version (id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_mision_vision_vigente
    ON mision_vision_version (estado)
    WHERE estado = 'VIGENTE';

CREATE INDEX IF NOT EXISTS idx_mision_vision_origen_version
    ON mision_vision_version (origen_version_id);

CREATE TABLE IF NOT EXISTS mision_vision_valor (
    id BIGSERIAL PRIMARY KEY,
    mision_vision_version_id BIGINT NOT NULL,
    orden INTEGER NOT NULL,
    titulo VARCHAR(120) NOT NULL,
    descripcion_html TEXT NOT NULL,
    CONSTRAINT fk_mision_vision_valor_version
        FOREIGN KEY (mision_vision_version_id)
        REFERENCES mision_vision_version (id)
        ON DELETE CASCADE,
    CONSTRAINT uk_mision_vision_valor_version_orden
        UNIQUE (mision_vision_version_id, orden),
    CONSTRAINT chk_mision_vision_valor_orden
        CHECK (orden >= 0)
);

CREATE INDEX IF NOT EXISTS idx_mision_vision_valor_version
    ON mision_vision_valor (mision_vision_version_id);

WITH inserted_version AS (
    INSERT INTO mision_vision_version (
        version,
        estado,
        mision_html,
        vision_html,
        vigente_desde,
        creado_en,
        creado_por,
        motivo_cambio
    )
    SELECT
        1,
        'VIGENTE',
        '<p>Proporcionar productos capilares innovadores y de alta calidad, comprometidos con la excelencia en cada proceso, la satisfacción de nuestros clientes y el desarrollo sostenible de nuestra comunidad. Buscamos transformar ingredientes naturales en soluciones capilares excepcionales que mejoren la salud y belleza del cabello.</p>',
        '<p>Ser reconocidos como líderes en la industria de productos capilares a nivel nacional e internacional, destacándonos por la innovación, calidad y sostenibilidad de nuestras formulaciones y procesos. Aspiramos a expandir nuestra presencia en nuevos mercados, manteniendo siempre nuestro compromiso con la excelencia, la belleza natural y la responsabilidad social y ambiental.</p>',
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP,
        'system',
        'Carga inicial desde el contenido historico de la pestaña Mision y Vision'
    WHERE NOT EXISTS (
        SELECT 1 FROM mision_vision_version
    )
    RETURNING id
)
INSERT INTO mision_vision_valor (
    mision_vision_version_id,
    orden,
    titulo,
    descripcion_html
)
SELECT
    inserted_version.id,
    defaults.orden,
    defaults.titulo,
    defaults.descripcion_html
FROM inserted_version
CROSS JOIN (
    VALUES
        (0, 'Integridad', '<p>Actuamos con honestidad, transparencia y ética en todas nuestras relaciones y decisiones empresariales.</p>'),
        (1, 'Excelencia', '<p>Nos esforzamos por alcanzar los más altos estándares de calidad y belleza en todos nuestros productos capilares, garantizando resultados excepcionales para el cuidado del cabello.</p>'),
        (2, 'Sostenibilidad', '<p>Desarrollamos nuestras actividades con respeto al medio ambiente, utilizando ingredientes naturales, prácticas libres de crueldad animal y un compromiso con las futuras generaciones.</p>'),
        (3, 'Trabajo en Equipo', '<p>Fomentamos la colaboración, el respeto mutuo y la comunicación efectiva entre todos los miembros de nuestra organización.</p>')
) AS defaults(orden, titulo, descripcion_html);
