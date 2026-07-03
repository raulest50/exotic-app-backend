CREATE TABLE calidad_control_proceso_plantilla (
    id BIGSERIAL PRIMARY KEY,
    area_operativa_id INTEGER NOT NULL,
    version INTEGER NOT NULL,
    estado VARCHAR(20) NOT NULL,
    CONSTRAINT fk_calidad_plantilla_area
        FOREIGN KEY (area_operativa_id) REFERENCES area_operativa(area_id),
    CONSTRAINT chk_calidad_plantilla_estado
        CHECK (estado IN ('BORRADOR', 'VIGENTE', 'RETIRADA')),
    CONSTRAINT uq_calidad_plantilla_area_version
        UNIQUE (area_operativa_id, version)
);

CREATE UNIQUE INDEX uq_calidad_plantilla_area_vigente
    ON calidad_control_proceso_plantilla(area_operativa_id)
    WHERE estado = 'VIGENTE';

CREATE UNIQUE INDEX uq_calidad_plantilla_area_borrador
    ON calidad_control_proceso_plantilla(area_operativa_id)
    WHERE estado = 'BORRADOR';

CREATE TABLE calidad_control_proceso_caracteristica (
    id BIGSERIAL PRIMARY KEY,
    plantilla_id BIGINT NOT NULL,
    nombre VARCHAR(120) NOT NULL,
    tipo VARCHAR(20) NOT NULL,
    unidad VARCHAR(30),
    orden INTEGER NOT NULL,
    cantidad_muestras INTEGER NOT NULL,
    unidades_por_muestra INTEGER NOT NULL,
    limite_inferior DOUBLE PRECISION,
    limite_superior DOUBLE PRECISION,
    CONSTRAINT fk_calidad_caracteristica_plantilla
        FOREIGN KEY (plantilla_id) REFERENCES calidad_control_proceso_plantilla(id) ON DELETE CASCADE,
    CONSTRAINT chk_calidad_caracteristica_tipo
        CHECK (tipo IN ('NUMERICA', 'BOOLEANA')),
    CONSTRAINT chk_calidad_caracteristica_muestras
        CHECK (cantidad_muestras > 0),
    CONSTRAINT chk_calidad_caracteristica_unidades
        CHECK (unidades_por_muestra > 0),
    CONSTRAINT chk_calidad_caracteristica_limites
        CHECK (limite_inferior IS NULL OR limite_superior IS NULL OR limite_inferior <= limite_superior),
    CONSTRAINT uq_calidad_caracteristica_orden
        UNIQUE (plantilla_id, orden)
);

CREATE TABLE calidad_control_proceso_ejecucion (
    id BIGSERIAL PRIMARY KEY,
    plantilla_id BIGINT NOT NULL,
    lote_id BIGINT NOT NULL,
    usuario_id BIGINT NOT NULL,
    fecha_registro TIMESTAMP NOT NULL,
    observaciones TEXT,
    CONSTRAINT fk_calidad_ejecucion_plantilla
        FOREIGN KEY (plantilla_id) REFERENCES calidad_control_proceso_plantilla(id),
    CONSTRAINT fk_calidad_ejecucion_lote
        FOREIGN KEY (lote_id) REFERENCES lote(id),
    CONSTRAINT fk_calidad_ejecucion_usuario
        FOREIGN KEY (usuario_id) REFERENCES users(id)
);

CREATE TABLE calidad_control_proceso_muestra (
    id BIGSERIAL PRIMARY KEY,
    ejecucion_id BIGINT NOT NULL,
    caracteristica_id BIGINT NOT NULL,
    numero_muestra INTEGER NOT NULL,
    CONSTRAINT fk_calidad_muestra_ejecucion
        FOREIGN KEY (ejecucion_id) REFERENCES calidad_control_proceso_ejecucion(id) ON DELETE CASCADE,
    CONSTRAINT fk_calidad_muestra_caracteristica
        FOREIGN KEY (caracteristica_id) REFERENCES calidad_control_proceso_caracteristica(id),
    CONSTRAINT chk_calidad_muestra_numero
        CHECK (numero_muestra > 0),
    CONSTRAINT uq_calidad_muestra
        UNIQUE (ejecucion_id, caracteristica_id, numero_muestra)
);

CREATE TABLE calidad_control_proceso_lectura (
    id BIGSERIAL PRIMARY KEY,
    muestra_id BIGINT NOT NULL,
    indice_unidad INTEGER NOT NULL,
    valor_numerico DOUBLE PRECISION,
    valor_booleano BOOLEAN,
    CONSTRAINT fk_calidad_lectura_muestra
        FOREIGN KEY (muestra_id) REFERENCES calidad_control_proceso_muestra(id) ON DELETE CASCADE,
    CONSTRAINT chk_calidad_lectura_indice
        CHECK (indice_unidad > 0),
    CONSTRAINT chk_calidad_lectura_un_valor
        CHECK (
            (valor_numerico IS NOT NULL AND valor_booleano IS NULL)
            OR (valor_numerico IS NULL AND valor_booleano IS NOT NULL)
        ),
    CONSTRAINT uq_calidad_lectura
        UNIQUE (muestra_id, indice_unidad)
);
