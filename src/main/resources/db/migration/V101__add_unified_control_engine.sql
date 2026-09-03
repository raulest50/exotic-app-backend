-- Motor neutral de controles de proceso y ensayos de calidad.
-- La migracion es deliberadamente aditiva: las tablas calidad_* de V077
-- permanecen como fuente historica durante el periodo de compatibilidad.

CREATE TABLE control_magnitud (
    id BIGSERIAL PRIMARY KEY,
    codigo VARCHAR(40) NOT NULL UNIQUE,
    nombre VARCHAR(120) NOT NULL,
    simbolo VARCHAR(30) NOT NULL,
    dimension VARCHAR(80) NOT NULL,
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE control_unidad (
    id BIGSERIAL PRIMARY KEY,
    codigo VARCHAR(40) NOT NULL UNIQUE,
    nombre VARCHAR(120) NOT NULL,
    simbolo VARCHAR(30) NOT NULL,
    dimension VARCHAR(80) NOT NULL,
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO control_unidad (codigo, nombre, simbolo, dimension, activo)
VALUES
    ('ADIMENSIONAL', 'Adimensional', '1', 'ADIMENSIONAL', TRUE),
    ('G', 'Gramo', 'g', 'MASA', TRUE),
    ('KG', 'Kilogramo', 'kg', 'MASA', TRUE),
    ('PH', 'Unidad de pH', 'pH', 'PH', TRUE),
    ('MPA_S', 'Milipascal segundo', 'mPa·s', 'VISCOSIDAD_DINAMICA', TRUE),
    ('CP', 'Centipoise', 'cP', 'VISCOSIDAD_DINAMICA', TRUE)
ON CONFLICT (codigo) DO NOTHING;

INSERT INTO control_magnitud (codigo, nombre, simbolo, dimension, activo)
VALUES
    ('PESO', 'Peso', 'm', 'MASA', TRUE),
    ('PH', 'pH', 'pH', 'PH', TRUE),
    ('VISCOSIDAD_DINAMICA', 'Viscosidad dinamica', 'η', 'VISCOSIDAD_DINAMICA', TRUE)
ON CONFLICT (codigo) DO NOTHING;

CREATE TABLE control_plan (
    id BIGSERIAL PRIMARY KEY,
    codigo VARCHAR(60) NOT NULL UNIQUE,
    nombre VARCHAR(160) NOT NULL,
    ambito VARCHAR(20) NOT NULL,
    creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    creado_por_id BIGINT NULL REFERENCES users(id),
    CONSTRAINT chk_control_plan_ambito CHECK (ambito IN ('PROCESO', 'CALIDAD'))
);

CREATE TABLE control_plan_version (
    id BIGSERIAL PRIMARY KEY,
    plan_id BIGINT NOT NULL REFERENCES control_plan(id) ON DELETE RESTRICT,
    numero INTEGER NOT NULL,
    estado VARCHAR(20) NOT NULL,
    proposito VARCHAR(120) NOT NULL,
    motivo_cambio VARCHAR(500) NULL,
    responsable_ejecucion VARCHAR(120) NOT NULL,
    responsable_revision VARCHAR(120) NULL,
    responsable_disposicion VARCHAR(120) NULL,
    creada_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    creada_por_id BIGINT NULL REFERENCES users(id),
    publicada_en TIMESTAMP NULL,
    publicada_por_id BIGINT NULL REFERENCES users(id),
    retirada_en TIMESTAMP NULL,
    retirada_por_id BIGINT NULL REFERENCES users(id),
    legacy_plantilla_id BIGINT NULL UNIQUE REFERENCES calidad_control_proceso_plantilla(id),
    CONSTRAINT uq_control_plan_version_numero UNIQUE (plan_id, numero),
    CONSTRAINT chk_control_plan_version_numero CHECK (numero > 0),
    CONSTRAINT chk_control_plan_version_estado CHECK (estado IN ('BORRADOR', 'VIGENTE', 'RETIRADA')),
    CONSTRAINT chk_control_plan_version_fechas CHECK (
        (estado <> 'VIGENTE' OR publicada_en IS NOT NULL)
        AND (estado <> 'RETIRADA' OR retirada_en IS NOT NULL)
    )
);

CREATE UNIQUE INDEX uq_control_plan_version_borrador
    ON control_plan_version(plan_id) WHERE estado = 'BORRADOR';
CREATE UNIQUE INDEX uq_control_plan_version_vigente
    ON control_plan_version(plan_id) WHERE estado = 'VIGENTE';

CREATE TABLE control_plan_aplicabilidad (
    id BIGSERIAL PRIMARY KEY,
    version_id BIGINT NOT NULL REFERENCES control_plan_version(id) ON DELETE CASCADE,
    producto_id VARCHAR(255) NULL REFERENCES productos(producto_id),
    categoria_id INTEGER NULL REFERENCES categoria(categoria_id),
    tipo_orden VARCHAR(10) NOT NULL,
    punto_aplicacion VARCHAR(30) NOT NULL,
    area_operativa_id INTEGER NULL REFERENCES area_operativa(area_id),
    proceso_id INTEGER NULL REFERENCES proceso_produccion(proceso_id),
    momento VARCHAR(30) NOT NULL,
    punto_exigencia VARCHAR(30) NOT NULL,
    legado_global BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT chk_control_aplicabilidad_objetivo CHECK (
        (legado_global AND producto_id IS NULL AND categoria_id IS NULL)
        OR (NOT legado_global AND ((producto_id IS NULL) <> (categoria_id IS NULL)))
    ),
    CONSTRAINT chk_control_aplicabilidad_tipo_orden CHECK (tipo_orden IN ('OP', 'OF', 'AMBAS')),
    CONSTRAINT chk_control_aplicabilidad_punto CHECK (punto_aplicacion IN ('LOTE_FINAL', 'SALIDA_OPERACION')),
    CONSTRAINT chk_control_aplicabilidad_momento CHECK (momento IN ('DURANTE_FABRICACION', 'REVISION_FINAL')),
    CONSTRAINT chk_control_aplicabilidad_exigencia CHECK (
        punto_exigencia IN ('INFORMATIVO', 'CIERRE_ETAPA', 'ENVIO_CALIDAD', 'LIBERACION')
    ),
    CONSTRAINT chk_control_aplicabilidad_etapa CHECK (
        punto_exigencia <> 'CIERRE_ETAPA'
        OR (punto_aplicacion = 'SALIDA_OPERACION' AND momento = 'DURANTE_FABRICACION')
    ),
    CONSTRAINT chk_control_aplicabilidad_contexto CHECK (
        legado_global
        OR (punto_aplicacion = 'LOTE_FINAL' AND area_operativa_id IS NULL AND proceso_id IS NULL)
        OR (punto_aplicacion = 'SALIDA_OPERACION' AND area_operativa_id IS NOT NULL AND proceso_id IS NOT NULL)
    )
);

CREATE INDEX idx_control_aplicabilidad_producto ON control_plan_aplicabilidad(producto_id);
CREATE INDEX idx_control_aplicabilidad_categoria ON control_plan_aplicabilidad(categoria_id);
CREATE INDEX idx_control_aplicabilidad_area ON control_plan_aplicabilidad(area_operativa_id);

CREATE TABLE control_plan_aplicabilidad_exclusion (
    aplicabilidad_id BIGINT NOT NULL REFERENCES control_plan_aplicabilidad(id) ON DELETE CASCADE,
    producto_id VARCHAR(255) NOT NULL REFERENCES productos(producto_id),
    PRIMARY KEY (aplicabilidad_id, producto_id)
);

CREATE TABLE control_plan_caracteristica (
    id BIGSERIAL PRIMARY KEY,
    version_id BIGINT NOT NULL REFERENCES control_plan_version(id) ON DELETE CASCADE,
    magnitud_id BIGINT NOT NULL REFERENCES control_magnitud(id),
    unidad_id BIGINT NULL REFERENCES control_unidad(id),
    nombre VARCHAR(120) NOT NULL,
    tipo VARCHAR(20) NOT NULL,
    orden INTEGER NOT NULL,
    cantidad_muestras INTEGER NOT NULL,
    unidades_por_muestra INTEGER NOT NULL,
    escala_visible INTEGER NOT NULL DEFAULT 2,
    objetivo NUMERIC(20,8) NULL,
    limite_inferior NUMERIC(20,8) NULL,
    limite_superior NUMERIC(20,8) NULL,
    valor_booleano_esperado BOOLEAN NULL,
    magnitud_codigo_snapshot VARCHAR(40) NOT NULL,
    magnitud_nombre_snapshot VARCHAR(120) NOT NULL,
    magnitud_simbolo_snapshot VARCHAR(30) NOT NULL,
    unidad_codigo_snapshot VARCHAR(40) NULL,
    unidad_nombre_snapshot VARCHAR(120) NULL,
    unidad_simbolo_snapshot VARCHAR(30) NULL,
    legado_sin_limites BOOLEAN NOT NULL DEFAULT FALSE,
    requiere_depuracion BOOLEAN NOT NULL DEFAULT FALSE,
    legacy_caracteristica_id BIGINT NULL UNIQUE REFERENCES calidad_control_proceso_caracteristica(id),
    CONSTRAINT uq_control_caracteristica_orden UNIQUE (version_id, orden),
    CONSTRAINT chk_control_caracteristica_tipo CHECK (tipo IN ('NUMERICA', 'BOOLEANA')),
    CONSTRAINT chk_control_caracteristica_muestreo CHECK (cantidad_muestras > 0 AND unidades_por_muestra > 0),
    CONSTRAINT chk_control_caracteristica_escala CHECK (escala_visible BETWEEN 0 AND 8),
    CONSTRAINT chk_control_caracteristica_config CHECK (
        (tipo = 'NUMERICA' AND unidad_id IS NOT NULL AND valor_booleano_esperado IS NULL
            AND (legado_sin_limites OR limite_inferior IS NOT NULL OR limite_superior IS NOT NULL))
        OR
        (tipo = 'BOOLEANA' AND unidad_id IS NULL AND objetivo IS NULL
            AND limite_inferior IS NULL AND limite_superior IS NULL
            AND valor_booleano_esperado IS NOT NULL)
    ),
    CONSTRAINT chk_control_caracteristica_limites CHECK (
        limite_inferior IS NULL OR limite_superior IS NULL OR limite_inferior <= limite_superior
    ),
    CONSTRAINT chk_control_caracteristica_objetivo CHECK (
        objetivo IS NULL OR (
            (limite_inferior IS NULL OR objetivo >= limite_inferior)
            AND (limite_superior IS NULL OR objetivo <= limite_superior)
        )
    )
);

CREATE TABLE control_requerido (
    id BIGSERIAL PRIMARY KEY,
    version_id BIGINT NOT NULL REFERENCES control_plan_version(id) ON DELETE RESTRICT,
    aplicabilidad_id BIGINT NULL REFERENCES control_plan_aplicabilidad(id) ON DELETE RESTRICT,
    lote_id BIGINT NOT NULL REFERENCES lote(id) ON DELETE RESTRICT,
    batch_record_id BIGINT NULL REFERENCES batch_record(id) ON DELETE RESTRICT,
    batch_record_etapa_id BIGINT NULL REFERENCES batch_record_etapa(id) ON DELETE RESTRICT,
    origen VARCHAR(20) NOT NULL,
    estado VARCHAR(30) NOT NULL DEFAULT 'PENDIENTE',
    ciclo_revision_numero INTEGER NULL,
    requiere_repeticion BOOLEAN NOT NULL DEFAULT FALSE,
    requiere_revalidacion BOOLEAN NOT NULL DEFAULT FALSE,
    creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    agregado_excepcionalmente BOOLEAN NOT NULL DEFAULT FALSE,
    motivo_adicion VARCHAR(500) NULL,
    agregado_por_id BIGINT NULL REFERENCES users(id),
    revision_adicion_id BIGINT NULL UNIQUE REFERENCES batch_record_revision(id),
    firma_adicion_id BIGINT NULL UNIQUE REFERENCES batch_record_firma(id),
    plan_codigo_snapshot VARCHAR(60) NOT NULL,
    plan_nombre_snapshot VARCHAR(160) NOT NULL,
    ambito_snapshot VARCHAR(20) NOT NULL,
    version_numero_snapshot INTEGER NOT NULL,
    producto_id_snapshot VARCHAR(255) NULL,
    producto_nombre_snapshot VARCHAR(255) NULL,
    categoria_id_snapshot INTEGER NULL,
    categoria_nombre_snapshot VARCHAR(255) NULL,
    tipo_orden_snapshot VARCHAR(10) NOT NULL,
    punto_aplicacion_snapshot VARCHAR(30) NOT NULL,
    area_operativa_id_snapshot INTEGER NULL,
    area_operativa_nombre_snapshot VARCHAR(255) NULL,
    proceso_id_snapshot INTEGER NULL,
    proceso_nombre_snapshot VARCHAR(255) NULL,
    manufacturing_version_id_snapshot BIGINT NULL,
    ruta_version_id_snapshot BIGINT NULL,
    ruta_nodo_id_snapshot BIGINT NULL,
    orden_fabricacion_operacion_id_snapshot BIGINT NULL,
    frontend_node_id_snapshot VARCHAR(255) NULL,
    nodo_nombre_snapshot VARCHAR(255) NULL,
    momento_snapshot VARCHAR(30) NOT NULL,
    punto_exigencia_snapshot VARCHAR(30) NOT NULL,
    legacy_ejecucion_id BIGINT NULL UNIQUE REFERENCES calidad_control_proceso_ejecucion(id),
    CONSTRAINT chk_control_requerido_origen CHECK (origen IN ('BATCH_RECORD', 'INDEPENDIENTE', 'LEGACY')),
    CONSTRAINT chk_control_requerido_estado CHECK (
        estado IN ('PENDIENTE', 'CONFORME', 'NO_CONFORME', 'ACEPTADO_POR_DESVIACION', 'POR_REVALIDAR')
    ),
    CONSTRAINT chk_control_requerido_ambito CHECK (ambito_snapshot IN ('PROCESO', 'CALIDAD')),
    CONSTRAINT chk_control_requerido_batch CHECK (
        (origen = 'BATCH_RECORD' AND batch_record_id IS NOT NULL)
        OR (origen <> 'BATCH_RECORD')
    ),
    CONSTRAINT chk_control_requerido_etapa_record CHECK (
        batch_record_etapa_id IS NULL OR batch_record_id IS NOT NULL
    ),
    CONSTRAINT chk_control_requerido_adicion CHECK (
        NOT agregado_excepcionalmente OR (motivo_adicion IS NOT NULL AND agregado_por_id IS NOT NULL)
    )
);

CREATE INDEX idx_control_requerido_pendiente ON control_requerido(ambito_snapshot, estado, creado_en);
CREATE INDEX idx_control_requerido_batch ON control_requerido(batch_record_id, punto_exigencia_snapshot);
CREATE UNIQUE INDEX uq_control_requerido_batch_aplicacion
    ON control_requerido(batch_record_id, version_id, COALESCE(batch_record_etapa_id, 0))
    WHERE origen = 'BATCH_RECORD' AND NOT agregado_excepcionalmente;
CREATE UNIQUE INDEX uq_control_requerido_independiente_aplicacion
    ON control_requerido(
        lote_id, version_id, punto_aplicacion_snapshot,
        COALESCE(area_operativa_id_snapshot, 0), COALESCE(proceso_id_snapshot, 0),
        COALESCE(ruta_nodo_id_snapshot, 0), COALESCE(orden_fabricacion_operacion_id_snapshot, 0)
    )
    WHERE origen = 'INDEPENDIENTE';

CREATE TABLE control_ejecucion (
    id BIGSERIAL PRIMARY KEY,
    control_requerido_id BIGINT NOT NULL REFERENCES control_requerido(id) ON DELETE RESTRICT,
    repeticion_de_id BIGINT NULL REFERENCES control_ejecucion(id) ON DELETE RESTRICT,
    usuario_id BIGINT NOT NULL REFERENCES users(id),
    fecha_registro TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resultado VARCHAR(20) NOT NULL,
    observaciones TEXT NULL,
    motivo_repeticion VARCHAR(500) NULL,
    legacy_ejecucion_id BIGINT NULL UNIQUE REFERENCES calidad_control_proceso_ejecucion(id),
    CONSTRAINT chk_control_ejecucion_resultado CHECK (resultado IN ('CONFORME', 'NO_CONFORME')),
    CONSTRAINT chk_control_ejecucion_repeticion CHECK (
        (repeticion_de_id IS NULL AND motivo_repeticion IS NULL)
        OR (repeticion_de_id IS NOT NULL AND motivo_repeticion IS NOT NULL)
    )
);

CREATE INDEX idx_control_ejecucion_requerido ON control_ejecucion(control_requerido_id, fecha_registro DESC);

CREATE TABLE control_revalidacion (
    id BIGSERIAL PRIMARY KEY,
    control_requerido_id BIGINT NOT NULL REFERENCES control_requerido(id) ON DELETE RESTRICT,
    ejecucion_revalidada_id BIGINT NOT NULL REFERENCES control_ejecucion(id) ON DELETE RESTRICT,
    ciclo_revision_numero INTEGER NOT NULL,
    justificacion VARCHAR(1000) NOT NULL,
    confirmada_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    confirmada_por_id BIGINT NOT NULL REFERENCES users(id),
    CONSTRAINT uq_control_revalidacion_ciclo UNIQUE (control_requerido_id, ciclo_revision_numero),
    CONSTRAINT chk_control_revalidacion_ciclo CHECK (ciclo_revision_numero > 0),
    CONSTRAINT chk_control_revalidacion_justificacion CHECK (TRIM(justificacion) <> '')
);

CREATE TABLE control_ejecucion_muestra (
    id BIGSERIAL PRIMARY KEY,
    ejecucion_id BIGINT NOT NULL REFERENCES control_ejecucion(id) ON DELETE RESTRICT,
    caracteristica_id BIGINT NOT NULL REFERENCES control_plan_caracteristica(id) ON DELETE RESTRICT,
    numero_muestra INTEGER NOT NULL,
    CONSTRAINT chk_control_muestra_numero CHECK (numero_muestra > 0),
    CONSTRAINT uq_control_muestra UNIQUE (ejecucion_id, caracteristica_id, numero_muestra)
);

CREATE TABLE control_ejecucion_lectura (
    id BIGSERIAL PRIMARY KEY,
    muestra_id BIGINT NOT NULL REFERENCES control_ejecucion_muestra(id) ON DELETE RESTRICT,
    indice_unidad INTEGER NOT NULL,
    valor_numerico NUMERIC(20,8) NULL,
    valor_booleano BOOLEAN NULL,
    CONSTRAINT chk_control_lectura_indice CHECK (indice_unidad > 0),
    CONSTRAINT chk_control_lectura_valor CHECK (
        (valor_numerico IS NOT NULL AND valor_booleano IS NULL)
        OR (valor_numerico IS NULL AND valor_booleano IS NOT NULL)
    ),
    CONSTRAINT uq_control_lectura UNIQUE (muestra_id, indice_unidad)
);

CREATE TABLE control_desviacion (
    id BIGSERIAL PRIMARY KEY,
    control_requerido_id BIGINT NOT NULL REFERENCES control_requerido(id) ON DELETE RESTRICT,
    ejecucion_origen_id BIGINT NOT NULL UNIQUE REFERENCES control_ejecucion(id) ON DELETE RESTRICT,
    ambito VARCHAR(20) NOT NULL,
    estado VARCHAR(20) NOT NULL,
    disposicion VARCHAR(30) NULL,
    investigacion TEXT NULL,
    resolucion TEXT NULL,
    justificacion_disposicion TEXT NULL,
    abierta_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    abierta_por_id BIGINT NOT NULL REFERENCES users(id),
    resuelta_en TIMESTAMP NULL,
    resuelta_por_id BIGINT NULL REFERENCES users(id),
    cerrada_en TIMESTAMP NULL,
    cerrada_por_id BIGINT NULL REFERENCES users(id),
    CONSTRAINT chk_control_desviacion_ambito CHECK (ambito IN ('PROCESO', 'CALIDAD')),
    CONSTRAINT chk_control_desviacion_estado CHECK (estado IN ('ABIERTA', 'EN_INVESTIGACION', 'RESUELTA', 'CERRADA')),
    CONSTRAINT chk_control_desviacion_disposicion CHECK (
        disposicion IS NULL OR disposicion IN ('REPETIR', 'CORREGIR_REPROCESAR', 'ACEPTAR_JUSTIFICADAMENTE', 'RECHAZAR')
    ),
    CONSTRAINT chk_control_desviacion_cierre CHECK (
        estado <> 'CERRADA' OR (
            cerrada_en IS NOT NULL AND cerrada_por_id IS NOT NULL
            AND disposicion IS NOT NULL
            AND NULLIF(BTRIM(justificacion_disposicion), '') IS NOT NULL
        )
    ),
    CONSTRAINT chk_control_desviacion_segregacion CHECK (
        ambito <> 'CALIDAD' OR cerrada_por_id IS NULL OR resuelta_por_id IS NULL OR cerrada_por_id <> resuelta_por_id
    )
);

CREATE INDEX idx_control_desviacion_estado ON control_desviacion(ambito, estado, abierta_en);

-- Catalogos derivados de las plantillas historicas. Solo se normalizan etiquetas
-- inequívocas; cualquier otra magnitud/unidad queda inactiva y exige depuracion.
INSERT INTO control_magnitud (codigo, nombre, simbolo, dimension, activo)
SELECT DISTINCT
       'LEGACY_' || SUBSTRING(MD5(LOWER(TRIM(nombre))) FROM 1 FOR 12),
       TRIM(nombre),
       '?',
       'LEGACY',
       FALSE
FROM calidad_control_proceso_caracteristica
WHERE TRIM(nombre) <> ''
  AND LOWER(TRIM(nombre)) NOT IN (
      'peso', 'masa', 'ph', 'p.h.', 'viscosidad', 'viscosidad dinamica', 'viscosidad dinámica'
  )
ON CONFLICT (codigo) DO NOTHING;

INSERT INTO control_magnitud (codigo, nombre, simbolo, dimension, activo)
SELECT 'LEGACY_ID_' || id,
       '[LEGACY SIN NOMBRE #' || id || ']',
       '?',
       'LEGACY',
       FALSE
FROM calidad_control_proceso_caracteristica
WHERE TRIM(nombre) = ''
ON CONFLICT (codigo) DO NOTHING;

INSERT INTO control_unidad (codigo, nombre, simbolo, dimension, activo)
SELECT DISTINCT
       'LEGACY_' || SUBSTRING(MD5(LOWER(TRIM(unidad))) FROM 1 FOR 12),
       TRIM(unidad),
       TRIM(unidad),
       'LEGACY',
       FALSE
FROM calidad_control_proceso_caracteristica
WHERE unidad IS NOT NULL AND TRIM(unidad) <> ''
  AND LOWER(TRIM(unidad)) NOT IN (
      'g', 'kg', 'ph', 'cp', 'mpa·s', 'mpa*s', 'mpa s', 'adimensional', '1'
  )
ON CONFLICT (codigo) DO NOTHING;

-- Una unidad vacia no equivale a una magnitud adimensional. Se conserva una
-- marca inactiva por caracteristica para exigir depuracion sin inventar una
-- equivalencia metrologica.
INSERT INTO control_unidad (codigo, nombre, simbolo, dimension, activo)
SELECT 'LEGACY_SIN_UNIDAD_' || id,
       '[LEGACY SIN UNIDAD #' || id || ']',
       '?',
       'LEGACY',
       FALSE
FROM calidad_control_proceso_caracteristica
WHERE tipo = 'NUMERICA'
  AND (unidad IS NULL OR TRIM(unidad) = '')
ON CONFLICT (codigo) DO NOTHING;

-- Una identidad estable por area preserva la familia de versiones historicas.
INSERT INTO control_plan (codigo, nombre, ambito, creado_en)
SELECT 'LEGACY-PROCESO-AREA-' || p.area_operativa_id,
       'Control de proceso legado - ' || a.nombre,
       'PROCESO',
       CURRENT_TIMESTAMP
FROM (SELECT DISTINCT area_operativa_id FROM calidad_control_proceso_plantilla) p
JOIN area_operativa a ON a.area_id = p.area_operativa_id
ON CONFLICT (codigo) DO NOTHING;

INSERT INTO control_plan_version (
    plan_id, numero, estado, proposito, motivo_cambio,
    responsable_ejecucion, responsable_revision, responsable_disposicion,
    creada_en, publicada_en, retirada_en, legacy_plantilla_id
)
SELECT cp.id,
       lp.version,
       lp.estado,
       'CONTROL_PROCESO_LEGADO',
       'Migracion automatica desde la plantilla historica ' || lp.id,
       'DIRECCION_TECNICA_Y_PLANTA',
       'DIRECCION_TECNICA_Y_PLANTA',
       'DIRECCION_TECNICA_Y_PLANTA',
       CURRENT_TIMESTAMP,
       CASE WHEN lp.estado IN ('VIGENTE', 'RETIRADA') THEN CURRENT_TIMESTAMP END,
       CASE WHEN lp.estado = 'RETIRADA' THEN CURRENT_TIMESTAMP END,
       lp.id
FROM calidad_control_proceso_plantilla lp
JOIN control_plan cp ON cp.codigo = 'LEGACY-PROCESO-AREA-' || lp.area_operativa_id;

INSERT INTO control_plan_aplicabilidad (
    version_id, tipo_orden, punto_aplicacion, area_operativa_id,
    momento, punto_exigencia, legado_global
)
SELECT v.id, 'AMBAS', 'SALIDA_OPERACION', lp.area_operativa_id,
       'DURANTE_FABRICACION', 'INFORMATIVO', TRUE
FROM control_plan_version v
JOIN calidad_control_proceso_plantilla lp ON lp.id = v.legacy_plantilla_id;

DO $$
DECLARE
    dato_invalido TEXT;
BEGIN
    SELECT 'caracteristica:' || id INTO dato_invalido
    FROM calidad_control_proceso_caracteristica
    WHERE (limite_inferior IS NOT NULL AND (
               limite_inferior::TEXT IN ('Infinity', '-Infinity', 'NaN')
               OR ABS(limite_inferior) >= 1000000000000))
       OR (limite_superior IS NOT NULL AND (
               limite_superior::TEXT IN ('Infinity', '-Infinity', 'NaN')
               OR ABS(limite_superior) >= 1000000000000))
    LIMIT 1;
    IF dato_invalido IS NULL THEN
        SELECT 'lectura:' || id INTO dato_invalido
        FROM calidad_control_proceso_lectura
        WHERE valor_numerico IS NOT NULL AND (
            valor_numerico::TEXT IN ('Infinity', '-Infinity', 'NaN')
            OR ABS(valor_numerico) >= 1000000000000)
        LIMIT 1;
    END IF;
    IF dato_invalido IS NOT NULL THEN
        RAISE EXCEPTION
            'Dato legado % fuera del rango NUMERIC(20,8). Corrija el dato antes de migrar.',
            dato_invalido;
    END IF;
END $$;

INSERT INTO control_plan_caracteristica (
    version_id, magnitud_id, unidad_id, nombre, tipo, orden,
    cantidad_muestras, unidades_por_muestra, escala_visible,
    limite_inferior, limite_superior, valor_booleano_esperado,
    magnitud_codigo_snapshot, magnitud_nombre_snapshot, magnitud_simbolo_snapshot,
    unidad_codigo_snapshot, unidad_nombre_snapshot, unidad_simbolo_snapshot,
    legado_sin_limites, requiere_depuracion,
    legacy_caracteristica_id
)
SELECT v.id,
       m.id,
       CASE WHEN lc.tipo = 'NUMERICA' THEN COALESCE(u.id, sin_unidad.id) END,
       CASE
           WHEN TRIM(lc.nombre) = '' THEN '[LEGACY SIN NOMBRE #' || lc.id || ']'
           ELSE TRIM(lc.nombre)
       END,
       lc.tipo,
       lc.orden,
       lc.cantidad_muestras,
       lc.unidades_por_muestra,
       8,
       lc.limite_inferior::NUMERIC(20,8),
       lc.limite_superior::NUMERIC(20,8),
       CASE WHEN lc.tipo = 'BOOLEANA' THEN TRUE END,
       m.codigo,
       m.nombre,
       m.simbolo,
       CASE WHEN lc.tipo = 'NUMERICA' THEN COALESCE(u.codigo, sin_unidad.codigo) END,
       CASE WHEN lc.tipo = 'NUMERICA' THEN COALESCE(u.nombre, sin_unidad.nombre) END,
       CASE WHEN lc.tipo = 'NUMERICA' THEN COALESCE(u.simbolo, sin_unidad.simbolo) END,
       lc.tipo = 'NUMERICA' AND lc.limite_inferior IS NULL AND lc.limite_superior IS NULL,
       LOWER(TRIM(lc.nombre)) NOT IN (
           'peso', 'masa', 'ph', 'p.h.', 'viscosidad', 'viscosidad dinamica', 'viscosidad dinámica'
       )
           OR (lc.tipo = 'NUMERICA' AND (
               lc.unidad IS NULL OR TRIM(lc.unidad) = ''
               OR LOWER(TRIM(lc.unidad)) NOT IN (
                   'g', 'kg', 'ph', 'cp', 'mpa·s', 'mpa*s', 'mpa s', 'adimensional', '1'
               )
           ))
           OR (lc.tipo = 'NUMERICA'
               AND m.dimension <> COALESCE(u.dimension, sin_unidad.dimension))
           OR (lc.tipo = 'NUMERICA' AND lc.limite_inferior IS NULL AND lc.limite_superior IS NULL),
       lc.id
FROM calidad_control_proceso_caracteristica lc
JOIN control_plan_version v ON v.legacy_plantilla_id = lc.plantilla_id
JOIN control_magnitud m
  ON m.codigo = CASE
      WHEN TRIM(lc.nombre) = '' THEN 'LEGACY_ID_' || lc.id
      WHEN LOWER(TRIM(lc.nombre)) IN ('peso', 'masa') THEN 'PESO'
      WHEN LOWER(TRIM(lc.nombre)) IN ('ph', 'p.h.') THEN 'PH'
      WHEN LOWER(TRIM(lc.nombre)) IN ('viscosidad', 'viscosidad dinamica', 'viscosidad dinámica')
          THEN 'VISCOSIDAD_DINAMICA'
      ELSE 'LEGACY_' || SUBSTRING(MD5(LOWER(TRIM(lc.nombre))) FROM 1 FOR 12)
  END
LEFT JOIN control_unidad u
  ON lc.unidad IS NOT NULL AND TRIM(lc.unidad) <> ''
 AND u.codigo = CASE LOWER(TRIM(lc.unidad))
     WHEN 'g' THEN 'G'
     WHEN 'kg' THEN 'KG'
     WHEN 'ph' THEN 'PH'
     WHEN 'cp' THEN 'CP'
     WHEN 'mpa·s' THEN 'MPA_S'
     WHEN 'mpa*s' THEN 'MPA_S'
     WHEN 'mpa s' THEN 'MPA_S'
     WHEN 'adimensional' THEN 'ADIMENSIONAL'
     WHEN '1' THEN 'ADIMENSIONAL'
     ELSE 'LEGACY_' || SUBSTRING(MD5(LOWER(TRIM(lc.unidad))) FROM 1 FOR 12)
 END
LEFT JOIN control_unidad sin_unidad
  ON lc.tipo = 'NUMERICA'
 AND (lc.unidad IS NULL OR TRIM(lc.unidad) = '')
 AND sin_unidad.codigo = 'LEGACY_SIN_UNIDAD_' || lc.id;

-- Una ocurrencia por etapa/version captura el congelamiento existente.
INSERT INTO control_requerido (
    version_id, aplicabilidad_id, lote_id, batch_record_id, batch_record_etapa_id,
    origen, estado, plan_codigo_snapshot, plan_nombre_snapshot, ambito_snapshot,
    version_numero_snapshot, producto_id_snapshot, categoria_id_snapshot,
    tipo_orden_snapshot, punto_aplicacion_snapshot, area_operativa_id_snapshot,
    manufacturing_version_id_snapshot, momento_snapshot, punto_exigencia_snapshot
)
SELECT v.id, a.id, br.lote_resultado_id, e.batch_record_id, e.id,
       'BATCH_RECORD',
       'PENDIENTE',
       p.codigo, p.nombre, p.ambito, v.numero,
       br.producto_resultado_id, COALESCE(prod.categoria_id, prod_origen.categoria_id),
       CASE WHEN br.orden_fabricacion_id IS NULL THEN 'OP' ELSE 'OF' END,
       'SALIDA_OPERACION', e.area_operativa_id, br.manufacturing_version_id,
       'DURANTE_FABRICACION', 'INFORMATIVO'
FROM batch_record_etapa e
JOIN batch_record br ON br.id = e.batch_record_id
JOIN control_plan_version v ON v.legacy_plantilla_id = e.control_proceso_plantilla_id
JOIN control_plan p ON p.id = v.plan_id
LEFT JOIN control_plan_aplicabilidad a ON a.version_id = v.id
LEFT JOIN productos prod ON prod.producto_id = br.producto_resultado_id
LEFT JOIN orden_fabricacion ofa ON ofa.orden_fabricacion_id = br.orden_fabricacion_id
LEFT JOIN ordenes_produccion op_origen ON op_origen.orden_id = ofa.orden_produccion_origen_id
LEFT JOIN productos prod_origen ON prod_origen.producto_id = op_origen.producto_id
WHERE e.control_proceso_plantilla_id IS NOT NULL
ON CONFLICT DO NOTHING;

-- Ejecuciones sin etapa obtienen una ocurrencia independiente e identificable.
INSERT INTO control_requerido (
    version_id, aplicabilidad_id, lote_id, batch_record_id, batch_record_etapa_id, origen, estado,
    plan_codigo_snapshot, plan_nombre_snapshot, ambito_snapshot,
    version_numero_snapshot, producto_id_snapshot, categoria_id_snapshot,
    tipo_orden_snapshot, punto_aplicacion_snapshot, area_operativa_id_snapshot,
    manufacturing_version_id_snapshot, momento_snapshot, punto_exigencia_snapshot,
    legacy_ejecucion_id
)
SELECT v.id, a.id, le.lote_id, le.batch_record_id, le.batch_record_etapa_id, 'LEGACY',
       'PENDIENTE',
       p.codigo, p.nombre, p.ambito, v.numero,
       COALESCE(l.producto_id, op.producto_id, ofa.semiterminado_id),
       COALESCE(prod.categoria_id, prod_op.categoria_id, prod_origen.categoria_id),
       CASE WHEN l.orden_fabricacion_id IS NULL THEN 'OP' ELSE 'OF' END,
       'SALIDA_OPERACION', lp.area_operativa_id,
       COALESCE(op.manufacturing_version_id, ofa.manufacturing_version_id),
       'DURANTE_FABRICACION', 'INFORMATIVO', le.id
FROM calidad_control_proceso_ejecucion le
JOIN calidad_control_proceso_plantilla lp ON lp.id = le.plantilla_id
JOIN control_plan_version v ON v.legacy_plantilla_id = lp.id
JOIN control_plan p ON p.id = v.plan_id
LEFT JOIN control_plan_aplicabilidad a ON a.version_id = v.id
JOIN lote l ON l.id = le.lote_id
LEFT JOIN productos prod ON prod.producto_id = l.producto_id
LEFT JOIN ordenes_produccion op ON op.orden_id = l.orden_produccion_id
LEFT JOIN productos prod_op ON prod_op.producto_id = op.producto_id
LEFT JOIN orden_fabricacion ofa ON ofa.orden_fabricacion_id = l.orden_fabricacion_id
LEFT JOIN ordenes_produccion op_origen ON op_origen.orden_id = ofa.orden_produccion_origen_id
LEFT JOIN productos prod_origen ON prod_origen.producto_id = op_origen.producto_id
WHERE NOT EXISTS (
    SELECT 1
    FROM control_requerido existente
    WHERE existente.batch_record_etapa_id = le.batch_record_etapa_id
      AND existente.version_id = v.id
);

INSERT INTO control_ejecucion (
    control_requerido_id, usuario_id, fecha_registro, resultado,
    observaciones, legacy_ejecucion_id
)
SELECT COALESCE(ri.id, re.id), le.usuario_id, le.fecha_registro,
       CASE
           WHEN le.resultado IS NOT NULL THEN le.resultado
           WHEN (
               SELECT COUNT(*)
               FROM calidad_control_proceso_muestra lm
               JOIN calidad_control_proceso_lectura ll ON ll.muestra_id = lm.id
               WHERE lm.ejecucion_id = le.id
           ) = (
               SELECT COALESCE(SUM(lc.cantidad_muestras * lc.unidades_por_muestra), 0)
               FROM calidad_control_proceso_caracteristica lc
               WHERE lc.plantilla_id = le.plantilla_id
           )
           AND NOT EXISTS (
               SELECT 1
               FROM calidad_control_proceso_muestra lm
               JOIN calidad_control_proceso_caracteristica lc ON lc.id = lm.caracteristica_id
               JOIN calidad_control_proceso_lectura ll ON ll.muestra_id = lm.id
               WHERE lm.ejecucion_id = le.id
                 AND (
                     (lc.tipo = 'BOOLEANA' AND ll.valor_booleano IS DISTINCT FROM TRUE)
                     OR (lc.tipo = 'NUMERICA' AND (
                         ll.valor_numerico IS NULL
                         OR (lc.limite_inferior IS NOT NULL AND ll.valor_numerico < lc.limite_inferior)
                         OR (lc.limite_superior IS NOT NULL AND ll.valor_numerico > lc.limite_superior)
                     ))
                 )
           ) THEN 'CONFORME'
           ELSE 'NO_CONFORME'
       END,
       le.observaciones, le.id
FROM calidad_control_proceso_ejecucion le
LEFT JOIN control_requerido ri ON ri.legacy_ejecucion_id = le.id
LEFT JOIN control_requerido re ON re.batch_record_etapa_id = le.batch_record_etapa_id
                              AND re.version_id = (
                                  SELECT v.id FROM control_plan_version v
                                  WHERE v.legacy_plantilla_id = le.plantilla_id
                              )
WHERE COALESCE(ri.id, re.id) IS NOT NULL;

DO $$
DECLARE
    total_legacy BIGINT;
    total_neutral BIGINT;
BEGIN
    SELECT COUNT(*) INTO total_legacy FROM calidad_control_proceso_ejecucion;
    SELECT COUNT(*) INTO total_neutral
    FROM control_ejecucion
    WHERE legacy_ejecucion_id IS NOT NULL;
    IF total_legacy <> total_neutral THEN
        RAISE EXCEPTION
            'Conteo de ejecuciones inconsistente: legacy %, neutral %. La migracion no puede omitir historicos.',
            total_legacy, total_neutral;
    END IF;
END $$;

INSERT INTO control_ejecucion_muestra (ejecucion_id, caracteristica_id, numero_muestra)
SELECT ne.id, nc.id, lm.numero_muestra
FROM calidad_control_proceso_muestra lm
JOIN control_ejecucion ne ON ne.legacy_ejecucion_id = lm.ejecucion_id
JOIN control_plan_caracteristica nc ON nc.legacy_caracteristica_id = lm.caracteristica_id;

INSERT INTO control_ejecucion_lectura (
    muestra_id, indice_unidad, valor_numerico, valor_booleano
)
SELECT nm.id, ll.indice_unidad, ll.valor_numerico::NUMERIC(20,8), ll.valor_booleano
FROM calidad_control_proceso_lectura ll
JOIN calidad_control_proceso_muestra lm ON lm.id = ll.muestra_id
JOIN control_ejecucion ne ON ne.legacy_ejecucion_id = lm.ejecucion_id
JOIN control_plan_caracteristica nc ON nc.legacy_caracteristica_id = lm.caracteristica_id
JOIN control_ejecucion_muestra nm
  ON nm.ejecucion_id = ne.id
 AND nm.caracteristica_id = nc.id
 AND nm.numero_muestra = lm.numero_muestra;

DO $$
DECLARE
    plantillas_legacy BIGINT;
    versiones_neutrales BIGINT;
    caracteristicas_legacy BIGINT;
    caracteristicas_neutrales BIGINT;
    muestras_legacy BIGINT;
    muestras_neutrales BIGINT;
    lecturas_legacy BIGINT;
    lecturas_neutrales BIGINT;
BEGIN
    SELECT COUNT(*) INTO plantillas_legacy
    FROM calidad_control_proceso_plantilla;
    SELECT COUNT(*) INTO versiones_neutrales
    FROM control_plan_version
    WHERE legacy_plantilla_id IS NOT NULL;
    SELECT COUNT(*) INTO caracteristicas_legacy
    FROM calidad_control_proceso_caracteristica;
    SELECT COUNT(*) INTO caracteristicas_neutrales
    FROM control_plan_caracteristica
    WHERE legacy_caracteristica_id IS NOT NULL;
    SELECT COUNT(*) INTO muestras_legacy
    FROM calidad_control_proceso_muestra;
    SELECT COUNT(*) INTO muestras_neutrales
    FROM control_ejecucion_muestra muestra
    JOIN control_ejecucion ejecucion ON ejecucion.id = muestra.ejecucion_id
    WHERE ejecucion.legacy_ejecucion_id IS NOT NULL;
    SELECT COUNT(*) INTO lecturas_legacy
    FROM calidad_control_proceso_lectura;
    SELECT COUNT(*) INTO lecturas_neutrales
    FROM control_ejecucion_lectura lectura
    JOIN control_ejecucion_muestra muestra ON muestra.id = lectura.muestra_id
    JOIN control_ejecucion ejecucion ON ejecucion.id = muestra.ejecucion_id
    WHERE ejecucion.legacy_ejecucion_id IS NOT NULL;

    IF plantillas_legacy <> versiones_neutrales
       OR caracteristicas_legacy <> caracteristicas_neutrales
       OR muestras_legacy <> muestras_neutrales
       OR lecturas_legacy <> lecturas_neutrales THEN
        RAISE EXCEPTION
            'Conteos legacy/neutrales inconsistentes: plantillas %/%, caracteristicas %/%, muestras %/%, lecturas %/%.',
            plantillas_legacy, versiones_neutrales,
            caracteristicas_legacy, caracteristicas_neutrales,
            muestras_legacy, muestras_neutrales,
            lecturas_legacy, lecturas_neutrales;
    END IF;
END $$;

-- Cada resultado historico no conforme conserva su evento de desviacion sin
-- inventar investigacion, resolucion ni disposicion. El usuario y la fecha son
-- los de la ejecucion fuente.
INSERT INTO control_desviacion (
    control_requerido_id, ejecucion_origen_id, ambito, estado,
    abierta_en, abierta_por_id
)
SELECT ejecucion.control_requerido_id,
       ejecucion.id,
       requisito.ambito_snapshot,
       'ABIERTA',
       ejecucion.fecha_registro,
       ejecucion.usuario_id
FROM control_ejecucion ejecucion
JOIN control_requerido requisito ON requisito.id = ejecucion.control_requerido_id
WHERE ejecucion.legacy_ejecucion_id IS NOT NULL
  AND ejecucion.resultado = 'NO_CONFORME';

-- La conformidad historica nula se deriva de la matriz completa y sus limites;
-- nunca se presume. Una matriz incompleta queda conservadoramente NO_CONFORME.
UPDATE control_requerido r
SET estado = CASE
    WHEN EXISTS (
        SELECT 1 FROM control_desviacion d
        WHERE d.control_requerido_id = r.id
          AND d.estado <> 'CERRADA'
    ) THEN 'NO_CONFORME'
    ELSE (
        SELECT e.resultado
        FROM control_ejecucion e
        WHERE e.control_requerido_id = r.id
        ORDER BY e.fecha_registro DESC, e.id DESC
        LIMIT 1
    )
END
WHERE EXISTS (SELECT 1 FROM control_ejecucion e WHERE e.control_requerido_id = r.id);

-- Completa snapshots descriptivos sin depender de nombres que puedan cambiar luego.
UPDATE control_requerido r
SET producto_nombre_snapshot = p.nombre
FROM productos p
WHERE p.producto_id = r.producto_id_snapshot;

UPDATE control_requerido r
SET categoria_nombre_snapshot = c.categoria_nombre
FROM categoria c
WHERE c.categoria_id = r.categoria_id_snapshot;

UPDATE control_requerido r
SET area_operativa_nombre_snapshot = a.nombre
FROM area_operativa a
WHERE a.area_id = r.area_operativa_id_snapshot;

UPDATE control_requerido r
SET proceso_nombre_snapshot = p.nombre
FROM proceso_produccion p
WHERE p.proceso_id = r.proceso_id_snapshot;

-- Para requisitos asociados a etapas se preserva el nodo/operacion exacto que
-- existia al migrar. OP congela ruta+nodo; OF congela su operacion propia.
UPDATE control_requerido r
SET ruta_version_id_snapshot = n.ruta_proceso_cat_version_id,
    ruta_nodo_id_snapshot = n.id,
    frontend_node_id_snapshot = n.frontend_id,
    nodo_nombre_snapshot = n.label,
    proceso_id_snapshot = COALESCE(r.proceso_id_snapshot, n.proceso_produccion_id),
    proceso_nombre_snapshot = COALESCE(r.proceso_nombre_snapshot, pp.nombre)
FROM batch_record_etapa e
JOIN seguimiento_orden_area s ON s.id = e.seguimiento_orden_area_id
JOIN ruta_proceso_node n ON n.id = s.ruta_proceso_node_id
LEFT JOIN proceso_produccion pp ON pp.proceso_id = n.proceso_produccion_id
WHERE r.batch_record_etapa_id = e.id;

UPDATE control_requerido r
SET orden_fabricacion_operacion_id_snapshot = o.id,
    frontend_node_id_snapshot = o.frontend_node_id,
    nodo_nombre_snapshot = o.proceso_nombre,
    proceso_id_snapshot = COALESCE(r.proceso_id_snapshot, o.proceso_produccion_id),
    proceso_nombre_snapshot = COALESCE(r.proceso_nombre_snapshot, o.proceso_nombre)
FROM batch_record_etapa e
JOIN orden_fabricacion_operacion o ON o.id = e.orden_fabricacion_operacion_id
WHERE r.batch_record_etapa_id = e.id;

COMMENT ON TABLE control_plan IS
    'Identidad estable y neutral de un control de PROCESO o CALIDAD.';
COMMENT ON COLUMN control_plan_aplicabilidad.legado_global IS
    'Compatibilidad temporal para plantillas V077 que solo estaban clasificadas por area.';
COMMENT ON COLUMN control_requerido.legacy_ejecucion_id IS
    'Enlace no destructivo a la ejecucion historica que origino una ocurrencia independiente.';
