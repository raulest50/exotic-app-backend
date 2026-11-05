-- Consolidated structural baseline for PostgreSQL 18 (schema-v1 profile only).
-- Source: local Docker PostgreSQL 18.1, legacy Flyway history through V118.
-- Backend revision: 80296d2; provenance: docs/flyway/consolidacion-v1.manifest.json.
-- This is NOT evidence of equivalence with Render and contains no business rows.
-- Use only against a new database. Do not combine with db/migration.
-- Technical bootstrap is separate: db/bootstrap-v1/instalacion_nueva.sql.
-- Flyway runs this script transactionally. For standalone validation, use
-- psql -v ON_ERROR_STOP=1 --single-transaction.
-- Session settings from pg_dump are replaced by transaction-local settings.

SET LOCAL search_path = public, pg_catalog;
SET LOCAL standard_conforming_strings = on;
SET LOCAL check_function_bodies = false;
SET LOCAL default_table_access_method = heap;

--
-- PostgreSQL database dump
--


-- Dumped from database version 18.1 (Debian 18.1-1.pgdg13+2)
-- Dumped by pg_dump version 18.1 (Debian 18.1-1.pgdg13+2)


--
-- Name: public; Type: SCHEMA; Schema: -; Owner: -
--

-- *not* creating schema, since initdb creates it


--
-- Name: SCHEMA public; Type: COMMENT; Schema: -; Owner: -
--

COMMENT ON SCHEMA public IS '';


--
-- Name: incrementar_receta_costos_revision(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.incrementar_receta_costos_revision() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    UPDATE receta_costos_revision
       SET version = version + 1,
           actualizado_en = CURRENT_TIMESTAMP
     WHERE id = 1;
    RETURN NULL;
END;
$$;




--
-- Name: activo; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.activo (
    id character varying(255) NOT NULL,
    brand character varying(255),
    capacidad double precision,
    disponibilidad_por_turno double precision,
    eficiencia_operativa double precision,
    estado integer NOT NULL,
    estado_opercional integer NOT NULL,
    fecha_baja timestamp(6) without time zone,
    fecha_codificacion timestamp(6) without time zone,
    metodo_despreciacion character varying(255),
    nombre character varying(255) NOT NULL,
    tiempo_ciclo_estandar double precision,
    tiempo_setup_estandar double precision,
    tipo_activo character varying(255) NOT NULL,
    ubicacion character varying(255),
    unidades_capacidad smallint,
    url character varying(255),
    valor_adquisicion numeric(38,2),
    valor_residual numeric(38,2),
    vida_util_meses integer,
    responsable_id bigint,
    tipo_recurso_id bigint,
    CONSTRAINT activo_tipo_activo_check CHECK (((tipo_activo)::text = ANY (ARRAY[('PRODUCCION'::character varying)::text, ('MOBILIARIO'::character varying)::text, ('EQUIPO'::character varying)::text]))),
    CONSTRAINT activo_unidades_capacidad_check CHECK (((unidades_capacidad >= 0) AND (unidades_capacidad <= 5)))
);


--
-- Name: area_operativa; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.area_operativa (
    area_id integer CONSTRAINT area_produccion_area_id_not_null NOT NULL,
    descripcion character varying(255),
    nombre character varying(255),
    responsable_id bigint,
    visibilidad_mps character varying(20) DEFAULT 'SOLO_OP'::character varying NOT NULL,
    alcance_mps character varying(20) DEFAULT 'TODOS'::character varying NOT NULL,
    CONSTRAINT chk_area_alcance_mps CHECK (((alcance_mps)::text = ANY ((ARRAY['TODOS'::character varying, 'SOLO_RUTA'::character varying])::text[]))),
    CONSTRAINT chk_area_visibilidad_mps CHECK (((visibilidad_mps)::text = ANY ((ARRAY['SOLO_OP'::character varying, 'SOLO_OF'::character varying, 'AMBOS'::character varying])::text[])))
);


--
-- Name: area_operativa_categoria_habilitada; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.area_operativa_categoria_habilitada (
    area_operativa_id integer NOT NULL,
    categoria_id integer NOT NULL
);


--
-- Name: area_operativa_categoria_unidad_medida; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.area_operativa_categoria_unidad_medida (
    area_operativa_categoria_unidad_medida_id bigint CONSTRAINT area_operativa_categoria_un_area_operativa_categoria_u_not_null NOT NULL,
    area_operativa_id integer CONSTRAINT area_operativa_categoria_unidad_medi_area_operativa_id_not_null NOT NULL,
    categoria_id integer NOT NULL,
    unidad_medida_area_operativa_id bigint CONSTRAINT area_operativa_categoria_un_unidad_medida_area_operati_not_null NOT NULL,
    factor_lote numeric(19,6) DEFAULT 1 NOT NULL,
    CONSTRAINT chk_aocum_factor_lote_positive CHECK ((factor_lote > (0)::numeric))
);


--
-- Name: area_operativa_categoria_unid_area_operativa_categoria_unid_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.area_operativa_categoria_unid_area_operativa_categoria_unid_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: area_operativa_categoria_unid_area_operativa_categoria_unid_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.area_operativa_categoria_unid_area_operativa_categoria_unid_seq OWNED BY public.area_operativa_categoria_unidad_medida.area_operativa_categoria_unidad_medida_id;


--
-- Name: area_operativa_ruido_muestra; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.area_operativa_ruido_muestra (
    id bigint NOT NULL,
    area_operativa_id integer NOT NULL,
    usuario_id bigint NOT NULL,
    fecha_muestra timestamp without time zone NOT NULL,
    ruido_db double precision NOT NULL,
    rms double precision NOT NULL,
    duracion_ms integer NOT NULL,
    sample_rate integer NOT NULL,
    created_at timestamp without time zone NOT NULL,
    CONSTRAINT chk_area_operativa_ruido_muestra_duracion CHECK (((duracion_ms >= 500) AND (duracion_ms <= 5000))),
    CONSTRAINT chk_area_operativa_ruido_muestra_rms CHECK (((rms >= (0)::double precision) AND (rms <= (1)::double precision))),
    CONSTRAINT chk_area_operativa_ruido_muestra_ruido_db CHECK (((ruido_db >= ('-160'::integer)::double precision) AND (ruido_db <= (20)::double precision))),
    CONSTRAINT chk_area_operativa_ruido_muestra_sample_rate CHECK (((sample_rate >= 8000) AND (sample_rate <= 192000)))
);


--
-- Name: area_operativa_ruido_muestra_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.area_operativa_ruido_muestra_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: area_operativa_ruido_muestra_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.area_operativa_ruido_muestra_id_seq OWNED BY public.area_operativa_ruido_muestra.id;


--
-- Name: area_produccion_area_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.area_produccion_area_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: area_produccion_area_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.area_produccion_area_id_seq OWNED BY public.area_operativa.area_id;


--
-- Name: asiento_contable; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.asiento_contable (
    id bigint NOT NULL,
    descripcion character varying(255),
    documento_origen character varying(255),
    estado character varying(255),
    fecha timestamp(6) without time zone,
    modulo character varying(255),
    periodo_contable_id bigint,
    CONSTRAINT asiento_contable_estado_check CHECK (((estado)::text = ANY (ARRAY[('BORRADOR'::character varying)::text, ('PUBLICADO'::character varying)::text, ('REVERSADO'::character varying)::text])))
);


--
-- Name: asiento_contable_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.asiento_contable_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: asiento_contable_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.asiento_contable_id_seq OWNED BY public.asiento_contable.id;


--
-- Name: avance_tarea; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.avance_tarea (
    id bigint NOT NULL,
    descripcion_avance character varying(255),
    url_doc_soporte_avance character varying(255),
    tarea_id bigint
);


--
-- Name: avance_tarea_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.avance_tarea_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: avance_tarea_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.avance_tarea_id_seq OWNED BY public.avance_tarea.id;


--
-- Name: batch_record; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.batch_record (
    id bigint NOT NULL,
    codigo character varying(60) NOT NULL,
    orden_produccion_id integer,
    orden_fabricacion_id bigint,
    lote_resultado_id bigint NOT NULL,
    producto_resultado_id character varying(255) NOT NULL,
    manufacturing_version_id bigint NOT NULL,
    estado character varying(30) NOT NULL,
    revision_documental integer DEFAULT 1 NOT NULL,
    cantidad_planificada numeric(18,4) NOT NULL,
    cantidad_obtenida numeric(18,4),
    unidad_medida character varying(20) NOT NULL,
    contenido_sha256 character varying(64),
    creado_en timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    creado_por_id bigint NOT NULL,
    iniciado_en timestamp without time zone,
    enviado_revision_en timestamp without time zone,
    cerrado_en timestamp without time zone,
    observaciones text,
    version bigint DEFAULT 0 NOT NULL,
    requerimientos_materiales_json text,
    ciclo_revision_actual bigint DEFAULT 0 NOT NULL,
    estado_sincronizacion character varying(20) DEFAULT 'ACTUALIZADO'::character varying NOT NULL,
    sincronizado_en timestamp without time zone,
    advertencias_documentales text,
    ultimo_error_documental text,
    CONSTRAINT chk_batch_record_cantidad_obtenida CHECK (((cantidad_obtenida IS NULL) OR (cantidad_obtenida >= (0)::numeric))),
    CONSTRAINT chk_batch_record_cantidad_planificada CHECK ((cantidad_planificada > (0)::numeric)),
    CONSTRAINT chk_batch_record_cerrado CHECK ((((estado)::text <> 'CERRADO'::text) OR (cerrado_en IS NOT NULL))),
    CONSTRAINT chk_batch_record_ciclo_revision_actual CHECK ((ciclo_revision_actual >= 0)),
    CONSTRAINT chk_batch_record_estado CHECK (((estado)::text = ANY ((ARRAY['BORRADOR'::character varying, 'EN_EJECUCION'::character varying, 'LISTO_PARA_REVISION'::character varying, 'PENDIENTE_REVISION'::character varying, 'DEVUELTO_PRODUCCION'::character varying, 'EN_CORRECCION'::character varying, 'APROBADO'::character varying, 'RECHAZADO'::character varying, 'CERRADO'::character varying, 'ANULADO'::character varying])::text[]))),
    CONSTRAINT chk_batch_record_estado_sincronizacion CHECK (((estado_sincronizacion)::text = ANY ((ARRAY['PENDIENTE'::character varying, 'SINCRONIZANDO'::character varying, 'ACTUALIZADO'::character varying, 'INCOMPLETO'::character varying, 'ERROR'::character varying])::text[]))),
    CONSTRAINT chk_batch_record_fechas_cierre CHECK (((cerrado_en IS NULL) OR (enviado_revision_en IS NULL) OR (cerrado_en >= enviado_revision_en))),
    CONSTRAINT chk_batch_record_fechas_revision CHECK (((enviado_revision_en IS NULL) OR (iniciado_en IS NULL) OR (enviado_revision_en >= iniciado_en))),
    CONSTRAINT chk_batch_record_hash CHECK (((contenido_sha256 IS NULL) OR ((contenido_sha256)::text ~ '^[0-9A-Fa-f]{64}$'::text))),
    CONSTRAINT chk_batch_record_revision CHECK ((revision_documental > 0)),
    CONSTRAINT chk_batch_record_revision_contenido CHECK ((((estado)::text <> ALL ((ARRAY['APROBADO'::character varying, 'RECHAZADO'::character varying, 'CERRADO'::character varying])::text[])) OR ((cantidad_obtenida IS NOT NULL) AND (contenido_sha256 IS NOT NULL)))),
    CONSTRAINT chk_batch_record_una_orden CHECK ((num_nonnulls(orden_produccion_id, orden_fabricacion_id) = 1))
);


--
-- Name: COLUMN batch_record.requerimientos_materiales_json; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.batch_record.requerimientos_materiales_json IS 'Requerimientos normalizados y congelados al emitir la orden; Disp V2 no recalcula recetas vigentes.';


--
-- Name: COLUMN batch_record.estado_sincronizacion; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.batch_record.estado_sincronizacion IS 'Estado tecnico e informativo de la proyeccion; nunca gobierna Calidad, produccion ni inventario.';


--
-- Name: batch_record_ciclo_revision; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.batch_record_ciclo_revision (
    id bigint NOT NULL,
    batch_record_id bigint NOT NULL,
    numero bigint NOT NULL,
    origen character varying(40) NOT NULL,
    estado character varying(30) NOT NULL,
    enviado_en timestamp without time zone NOT NULL,
    enviado_por_id bigint NOT NULL,
    motivo_envio character varying(500) NOT NULL,
    revision_envio_id bigint,
    cerrado_en timestamp without time zone,
    cerrado_por_id bigint,
    version bigint DEFAULT 0 NOT NULL,
    CONSTRAINT chk_batch_record_ciclo_cierre CHECK (((((estado)::text = ANY ((ARRAY['EN_REVISION'::character varying, 'MIGRADO_INCOMPLETO'::character varying])::text[])) AND (cerrado_en IS NULL) AND (cerrado_por_id IS NULL)) OR (((estado)::text <> ALL ((ARRAY['EN_REVISION'::character varying, 'MIGRADO_INCOMPLETO'::character varying])::text[])) AND (cerrado_en IS NOT NULL) AND (cerrado_por_id IS NOT NULL) AND (cerrado_en >= enviado_en)))),
    CONSTRAINT chk_batch_record_ciclo_estado CHECK (((estado)::text = ANY ((ARRAY['EN_REVISION'::character varying, 'DEVUELTO_PRODUCCION'::character varying, 'LIBERADO'::character varying, 'RECHAZADO'::character varying, 'MIGRADO_INCOMPLETO'::character varying])::text[]))),
    CONSTRAINT chk_batch_record_ciclo_numero CHECK ((numero > 0)),
    CONSTRAINT chk_batch_record_ciclo_origen CHECK (((origen)::text = ANY ((ARRAY['ENVIO_INICIAL'::character varying, 'REENVIO'::character varying, 'REENVIO_TRAS_REAPERTURA'::character varying])::text[])))
);


--
-- Name: TABLE batch_record_ciclo_revision; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.batch_record_ciclo_revision IS 'Cada fila conserva un envío independiente a Calidad y su decisión terminal.';


--
-- Name: batch_record_ciclo_revision_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.batch_record_ciclo_revision_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: batch_record_ciclo_revision_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.batch_record_ciclo_revision_id_seq OWNED BY public.batch_record_ciclo_revision.id;


--
-- Name: batch_record_consumo; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.batch_record_consumo (
    id bigint NOT NULL,
    batch_record_id bigint NOT NULL,
    producto_id character varying(255) NOT NULL,
    lote_origen_id bigint,
    movimiento_id integer,
    cantidad numeric(18,4) NOT NULL,
    unidad_medida character varying(20) NOT NULL,
    registrado_en timestamp without time zone NOT NULL,
    registrado_por_id bigint NOT NULL,
    observaciones character varying(500),
    tipo character varying(30) NOT NULL,
    CONSTRAINT chk_batch_record_consumo_cantidad CHECK (((cantidad <> (0)::numeric) AND ((((tipo)::text = 'EXCLUSION_AVERIA'::text) AND (cantidad < (0)::numeric)) OR (((tipo)::text <> 'EXCLUSION_AVERIA'::text) AND (cantidad > (0)::numeric))))),
    CONSTRAINT chk_batch_record_consumo_tipo CHECK (((tipo)::text = ANY ((ARRAY['DISPENSACION'::character varying, 'REPOSICION_AVERIA'::character varying, 'EXCLUSION_AVERIA'::character varying])::text[])))
);


--
-- Name: batch_record_consumo_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.batch_record_consumo_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: batch_record_consumo_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.batch_record_consumo_id_seq OWNED BY public.batch_record_consumo.id;


--
-- Name: batch_record_correccion; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.batch_record_correccion (
    id bigint NOT NULL,
    batch_record_id bigint NOT NULL,
    batch_record_etapa_id bigint,
    evento_correccion_id bigint,
    evento_revertido_id bigint,
    valor_anterior character varying(120) NOT NULL,
    valor_nuevo character varying(120) NOT NULL,
    motivo character varying(500) NOT NULL,
    corregida_en timestamp without time zone NOT NULL,
    corregida_por_id bigint NOT NULL,
    revision_id bigint,
    firma_id bigint,
    orden_fabricacion_evento_correccion_id bigint,
    orden_fabricacion_evento_revertido_id bigint,
    CONSTRAINT chk_batch_record_correccion_un_origen CHECK ((num_nonnulls(evento_correccion_id, orden_fabricacion_evento_correccion_id) = 1))
);


--
-- Name: batch_record_correccion_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.batch_record_correccion_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: batch_record_correccion_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.batch_record_correccion_id_seq OWNED BY public.batch_record_correccion.id;


--
-- Name: batch_record_decision_calidad; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.batch_record_decision_calidad (
    id bigint NOT NULL,
    batch_record_id bigint NOT NULL,
    decision character varying(30) NOT NULL,
    motivo character varying(500) NOT NULL,
    decidida_en timestamp without time zone NOT NULL,
    decidida_por_id bigint NOT NULL,
    revision_id bigint,
    firma_id bigint,
    ciclo_revision_id bigint,
    alcance_devolucion_json text,
    CONSTRAINT chk_batch_record_decision_calidad CHECK (((decision)::text = ANY ((ARRAY['LIBERAR'::character varying, 'RECHAZAR'::character varying, 'DEVOLVER_A_PRODUCCION'::character varying])::text[])))
);


--
-- Name: batch_record_decision_calidad_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.batch_record_decision_calidad_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: batch_record_decision_calidad_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.batch_record_decision_calidad_id_seq OWNED BY public.batch_record_decision_calidad.id;


--
-- Name: batch_record_desviacion; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.batch_record_desviacion (
    id bigint NOT NULL,
    batch_record_id bigint NOT NULL,
    batch_record_etapa_id bigint,
    codigo character varying(60) NOT NULL,
    descripcion text NOT NULL,
    estado character varying(30) NOT NULL,
    detectada_en timestamp without time zone NOT NULL,
    detectada_por_id bigint NOT NULL,
    evaluacion_impacto text,
    resolucion text,
    resuelta_en timestamp without time zone,
    resuelta_por_id bigint,
    version bigint DEFAULT 0 NOT NULL,
    ocurrida_en timestamp without time zone,
    origen character varying(30),
    accion_inmediata text,
    causa_raiz text,
    acciones_correctivas_preventivas text,
    CONSTRAINT chk_batch_record_desviacion_estado CHECK (((estado)::text = ANY ((ARRAY['ABIERTA'::character varying, 'EN_INVESTIGACION'::character varying, 'RESUELTA'::character varying, 'CERRADA'::character varying])::text[]))),
    CONSTRAINT chk_batch_record_desviacion_ocurrencia CHECK (((ocurrida_en IS NULL) OR (ocurrida_en <= detectada_en))),
    CONSTRAINT chk_batch_record_desviacion_origen CHECK (((origen IS NULL) OR ((origen)::text = ANY ((ARRAY['PROCESO'::character varying, 'MATERIAL'::character varying, 'EQUIPO'::character varying, 'AMBIENTE'::character varying, 'DOCUMENTACION'::character varying, 'CONTROL_CALIDAD'::character varying, 'OTRO'::character varying])::text[])))),
    CONSTRAINT chk_batch_record_desviacion_resolucion CHECK (((((estado)::text = ANY ((ARRAY['ABIERTA'::character varying, 'EN_INVESTIGACION'::character varying])::text[])) AND (resuelta_en IS NULL) AND (resuelta_por_id IS NULL)) OR (((estado)::text = ANY ((ARRAY['RESUELTA'::character varying, 'CERRADA'::character varying])::text[])) AND (resuelta_en IS NOT NULL) AND (resuelta_por_id IS NOT NULL) AND (resolucion IS NOT NULL) AND (evaluacion_impacto IS NOT NULL) AND (resuelta_en >= detectada_en))))
);


--
-- Name: batch_record_desviacion_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.batch_record_desviacion_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: batch_record_desviacion_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.batch_record_desviacion_id_seq OWNED BY public.batch_record_desviacion.id;


--
-- Name: batch_record_documento; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.batch_record_documento (
    id bigint NOT NULL,
    batch_record_id bigint NOT NULL,
    tipo character varying(30) NOT NULL,
    version_documento integer NOT NULL,
    nombre_archivo character varying(255) NOT NULL,
    content_type character varying(100) NOT NULL,
    tamano_bytes bigint NOT NULL,
    sha256 character varying(64) NOT NULL,
    storage_key character varying(500) NOT NULL,
    generado_en timestamp without time zone NOT NULL,
    generado_por_id bigint NOT NULL,
    CONSTRAINT chk_batch_record_documento_hash CHECK (((sha256)::text ~ '^[0-9A-Fa-f]{64}$'::text)),
    CONSTRAINT chk_batch_record_documento_pdf CHECK ((((tipo)::text <> 'EXPEDIENTE_PDF'::text) OR (lower((content_type)::text) = 'application/pdf'::text))),
    CONSTRAINT chk_batch_record_documento_tamano CHECK ((tamano_bytes > 0)),
    CONSTRAINT chk_batch_record_documento_tipo CHECK (((tipo)::text = ANY ((ARRAY['EXPEDIENTE_PDF'::character varying, 'ANEXO'::character varying])::text[]))),
    CONSTRAINT chk_batch_record_documento_version CHECK ((version_documento > 0))
);


--
-- Name: TABLE batch_record_documento; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.batch_record_documento IS 'Tabla legada de V095. El flujo activo no almacena PDF; usa batch_record_revision y regeneración bajo demanda.';


--
-- Name: batch_record_documento_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.batch_record_documento_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: batch_record_documento_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.batch_record_documento_id_seq OWNED BY public.batch_record_documento.id;


--
-- Name: batch_record_etapa; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.batch_record_etapa (
    id bigint NOT NULL,
    batch_record_id bigint NOT NULL,
    area_operativa_id integer NOT NULL,
    seguimiento_evento_origen_id bigint,
    nombre character varying(200) NOT NULL,
    secuencia integer NOT NULL,
    estado character varying(20) NOT NULL,
    iniciada_en timestamp without time zone,
    completada_en timestamp without time zone,
    reportada_por_id bigint,
    contenido_sha256 character varying(64),
    observaciones text,
    version bigint DEFAULT 0 NOT NULL,
    seguimiento_orden_area_id bigint,
    control_proceso_plantilla_id bigint,
    orden_fabricacion_operacion_id bigint,
    orden_fabricacion_evento_origen_id bigint,
    ciclo_correccion_habilitado bigint,
    CONSTRAINT chk_batch_record_etapa_ciclo_correccion CHECK (((ciclo_correccion_habilitado IS NULL) OR (ciclo_correccion_habilitado > 0))),
    CONSTRAINT chk_batch_record_etapa_completada CHECK ((((estado)::text <> 'COMPLETADA'::text) OR ((iniciada_en IS NOT NULL) AND (completada_en IS NOT NULL) AND (reportada_por_id IS NOT NULL)))),
    CONSTRAINT chk_batch_record_etapa_estado CHECK (((estado)::text = ANY ((ARRAY['PENDIENTE'::character varying, 'EN_EJECUCION'::character varying, 'EN_CORRECCION'::character varying, 'COMPLETADA'::character varying, 'OMITIDA'::character varying])::text[]))),
    CONSTRAINT chk_batch_record_etapa_fechas CHECK (((completada_en IS NULL) OR (iniciada_en IS NULL) OR (completada_en >= iniciada_en))),
    CONSTRAINT chk_batch_record_etapa_hash CHECK (((contenido_sha256 IS NULL) OR ((contenido_sha256)::text ~ '^[0-9A-Fa-f]{64}$'::text))),
    CONSTRAINT chk_batch_record_etapa_secuencia CHECK ((secuencia >= 0))
);


--
-- Name: batch_record_etapa_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.batch_record_etapa_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: batch_record_etapa_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.batch_record_etapa_id_seq OWNED BY public.batch_record_etapa.id;


--
-- Name: batch_record_firma; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.batch_record_firma (
    id bigint NOT NULL,
    batch_record_id bigint NOT NULL,
    firmante_id bigint NOT NULL,
    batch_record_etapa_id bigint,
    alcance character varying(30) NOT NULL,
    decision character varying(20) NOT NULL,
    metodo character varying(40) NOT NULL,
    firmado_en timestamp without time zone NOT NULL,
    autenticado_en timestamp without time zone NOT NULL,
    hash_contenido_firmado character varying(64) NOT NULL,
    algoritmo_hash character varying(20) DEFAULT 'SHA-256'::character varying NOT NULL,
    username_firmante character varying(120) NOT NULL,
    nombre_firmante character varying(200) NOT NULL,
    cedula_firmante character varying(30) NOT NULL,
    rol_firmante character varying(120) NOT NULL,
    manifestacion text NOT NULL,
    comentario character varying(500),
    ip_origen character varying(64),
    user_agent character varying(500),
    seguimiento_evento_id bigint,
    batch_record_revision_id bigint,
    firma_visual_version_id bigint,
    orden_fabricacion_evento_id bigint,
    CONSTRAINT chk_batch_record_firma_alcance CHECK (((alcance)::text = ANY ((ARRAY['CIERRE_ETAPA_AREA'::character varying, 'CORRECCION_EXPEDIENTE'::character varying, 'REVISION_PRODUCCION'::character varying, 'REVISION_CALIDAD'::character varying, 'LIBERACION_LOTE'::character varying, 'SOLICITUD_REAPERTURA_RECHAZO'::character varying, 'APROBACION_REAPERTURA_RECHAZO'::character varying])::text[]))),
    CONSTRAINT chk_batch_record_firma_decision CHECK (((decision)::text = ANY ((ARRAY['CONFIRMA'::character varying, 'APRUEBA'::character varying, 'RECHAZA'::character varying, 'DEVUELVE'::character varying, 'SOLICITA'::character varying, 'REABRE'::character varying])::text[]))),
    CONSTRAINT chk_batch_record_firma_decision_area CHECK ((((alcance)::text <> 'CIERRE_ETAPA_AREA'::text) OR ((decision)::text = 'CONFIRMA'::text))),
    CONSTRAINT chk_batch_record_firma_hash CHECK (((hash_contenido_firmado)::text ~ '^[0-9A-Fa-f]{64}$'::text)),
    CONSTRAINT chk_batch_record_firma_metodo CHECK (((metodo)::text = ANY ((ARRAY['SESION_AUTENTICADA'::character varying, 'REAUTENTICACION_CREDENCIALES'::character varying, 'SEGUNDO_FACTOR'::character varying])::text[]))),
    CONSTRAINT chk_batch_record_firma_tiempos CHECK ((autenticado_en <= firmado_en))
);


--
-- Name: batch_record_firma_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.batch_record_firma_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: batch_record_firma_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.batch_record_firma_id_seq OWNED BY public.batch_record_firma.id;


--
-- Name: batch_record_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.batch_record_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: batch_record_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.batch_record_id_seq OWNED BY public.batch_record.id;


--
-- Name: batch_record_projection_task; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.batch_record_projection_task (
    id bigint NOT NULL,
    orden_produccion_id integer,
    orden_fabricacion_id bigint,
    lote_id bigint NOT NULL,
    solicitado_por_id bigint NOT NULL,
    objetivo character varying(20) DEFAULT 'ACTIVO'::character varying NOT NULL,
    estado character varying(20) DEFAULT 'PENDIENTE'::character varying NOT NULL,
    cantidad_obtenida numeric(18,4),
    limpiar_cantidad_obtenida boolean DEFAULT false NOT NULL,
    intentos integer DEFAULT 0 NOT NULL,
    solicitud_version bigint DEFAULT 0 NOT NULL,
    proximo_intento_en timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    ultimo_error text,
    solicitado_en timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    actualizado_en timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    completado_en timestamp without time zone,
    CONSTRAINT chk_batch_record_projection_task_cantidad CHECK (((cantidad_obtenida IS NULL) OR (cantidad_obtenida >= (0)::numeric))),
    CONSTRAINT chk_batch_record_projection_task_estado CHECK (((estado)::text = ANY ((ARRAY['PENDIENTE'::character varying, 'PROCESANDO'::character varying, 'COMPLETADA'::character varying, 'ERROR'::character varying])::text[]))),
    CONSTRAINT chk_batch_record_projection_task_intentos CHECK ((intentos >= 0)),
    CONSTRAINT chk_batch_record_projection_task_objetivo CHECK (((objetivo)::text = ANY ((ARRAY['ACTIVO'::character varying, 'CERRADO'::character varying, 'ANULADO'::character varying])::text[]))),
    CONSTRAINT chk_batch_record_projection_task_orden CHECK ((num_nonnulls(orden_produccion_id, orden_fabricacion_id) = 1))
);


--
-- Name: TABLE batch_record_projection_task; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.batch_record_projection_task IS 'Solicitud reintentable para proyectar el historial operativo en un Batch Record sin bloquear la operacion fuente.';


--
-- Name: batch_record_projection_task_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.batch_record_projection_task_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: batch_record_projection_task_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.batch_record_projection_task_id_seq OWNED BY public.batch_record_projection_task.id;


--
-- Name: batch_record_revision; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.batch_record_revision (
    id bigint NOT NULL,
    batch_record_id bigint NOT NULL,
    numero integer NOT NULL,
    tipo character varying(30) NOT NULL,
    contenido_canonico text NOT NULL,
    contenido_sha256 character varying(64) NOT NULL,
    esquema_version character varying(30) NOT NULL,
    plantilla_pdf_version character varying(30) NOT NULL,
    creada_en timestamp without time zone NOT NULL,
    creada_por_id bigint NOT NULL,
    creada_por_username character varying(120) NOT NULL,
    creada_por_nombre character varying(200) NOT NULL,
    creada_por_cedula character varying(30) NOT NULL,
    motivo character varying(500),
    CONSTRAINT chk_batch_record_revision_hash CHECK (((contenido_sha256)::text ~ '^[0-9A-Fa-f]{64}$'::text)),
    CONSTRAINT chk_batch_record_revision_numero CHECK ((numero > 0)),
    CONSTRAINT chk_batch_record_revision_tipo CHECK (((tipo)::text = ANY ((ARRAY['ENVIO_CALIDAD'::character varying, 'REENVIO_CALIDAD'::character varying, 'DECISION_CALIDAD'::character varying, 'SOLICITUD_REAPERTURA_RECHAZO'::character varying, 'REAPERTURA_RECHAZO'::character varying, 'CORRECCION'::character varying, 'CIERRE'::character varying])::text[])))
);


--
-- Name: batch_record_revision_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.batch_record_revision_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: batch_record_revision_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.batch_record_revision_id_seq OWNED BY public.batch_record_revision.id;


--
-- Name: batch_record_seccion_correccion; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.batch_record_seccion_correccion (
    id bigint NOT NULL,
    batch_record_id bigint NOT NULL,
    ciclo_revision_numero bigint NOT NULL,
    seccion character varying(120) NOT NULL,
    estado character varying(20) NOT NULL,
    solicitada_en timestamp without time zone NOT NULL,
    solicitada_por_id bigint NOT NULL,
    atendida_en timestamp without time zone,
    atendida_por_id bigint,
    justificacion character varying(500),
    version bigint DEFAULT 0 NOT NULL,
    CONSTRAINT chk_batch_record_seccion_atencion CHECK (((((estado)::text = 'PENDIENTE'::text) AND (atendida_en IS NULL) AND (atendida_por_id IS NULL) AND (justificacion IS NULL)) OR (((estado)::text = 'ATENDIDA'::text) AND (atendida_en IS NOT NULL) AND (atendida_por_id IS NOT NULL) AND (justificacion IS NOT NULL)))),
    CONSTRAINT chk_batch_record_seccion_ciclo CHECK ((ciclo_revision_numero > 0)),
    CONSTRAINT chk_batch_record_seccion_estado CHECK (((estado)::text = ANY ((ARRAY['PENDIENTE'::character varying, 'ATENDIDA'::character varying])::text[])))
);


--
-- Name: batch_record_seccion_correccion_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.batch_record_seccion_correccion_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: batch_record_seccion_correccion_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.batch_record_seccion_correccion_id_seq OWNED BY public.batch_record_seccion_correccion.id;


--
-- Name: batch_record_solicitud_reapertura_rechazo; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.batch_record_solicitud_reapertura_rechazo (
    id bigint NOT NULL,
    batch_record_id bigint CONSTRAINT batch_record_solicitud_reapertura_rech_batch_record_id_not_null NOT NULL,
    ciclo_revision_numero bigint CONSTRAINT batch_record_solicitud_reapertur_ciclo_revision_numero_not_null NOT NULL,
    estado character varying(20) NOT NULL,
    solicitada_en timestamp without time zone CONSTRAINT batch_record_solicitud_reapertura_rechaz_solicitada_en_not_null NOT NULL,
    solicitada_por_id bigint CONSTRAINT batch_record_solicitud_reapertura_re_solicitada_por_id_not_null NOT NULL,
    motivo character varying(500) NOT NULL,
    evidencia text NOT NULL,
    alcance text NOT NULL,
    revision_solicitud_id bigint,
    firma_solicitud_id bigint,
    aprobada_en timestamp without time zone,
    aprobada_por_id bigint,
    motivo_aprobacion character varying(500),
    revision_aprobacion_id bigint,
    firma_aprobacion_id bigint,
    version bigint DEFAULT 0 NOT NULL,
    CONSTRAINT chk_batch_record_reapertura_aprobacion CHECK (((((estado)::text = 'PENDIENTE'::text) AND (aprobada_en IS NULL) AND (aprobada_por_id IS NULL) AND (motivo_aprobacion IS NULL) AND (revision_aprobacion_id IS NULL) AND (firma_aprobacion_id IS NULL)) OR (((estado)::text = 'APROBADA'::text) AND (aprobada_en IS NOT NULL) AND (aprobada_por_id IS NOT NULL) AND (motivo_aprobacion IS NOT NULL) AND (aprobada_en >= solicitada_en) AND (aprobada_por_id <> solicitada_por_id)))),
    CONSTRAINT chk_batch_record_reapertura_ciclo CHECK ((ciclo_revision_numero > 0)),
    CONSTRAINT chk_batch_record_reapertura_estado CHECK (((estado)::text = ANY ((ARRAY['PENDIENTE'::character varying, 'APROBADA'::character varying])::text[])))
);


--
-- Name: TABLE batch_record_solicitud_reapertura_rechazo; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.batch_record_solicitud_reapertura_rechazo IS 'Flujo excepcional con solicitante nivel 2 y aprobador nivel 3 distinto.';


--
-- Name: batch_record_solicitud_reapertura_rechazo_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.batch_record_solicitud_reapertura_rechazo_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: batch_record_solicitud_reapertura_rechazo_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.batch_record_solicitud_reapertura_rechazo_id_seq OWNED BY public.batch_record_solicitud_reapertura_rechazo.id;


--
-- Name: calidad_control_proceso_caracteristica; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.calidad_control_proceso_caracteristica (
    id bigint NOT NULL,
    plantilla_id bigint NOT NULL,
    nombre character varying(120) NOT NULL,
    tipo character varying(20) NOT NULL,
    unidad character varying(30),
    orden integer NOT NULL,
    cantidad_muestras integer CONSTRAINT calidad_control_proceso_caracteristi_cantidad_muestras_not_null NOT NULL,
    unidades_por_muestra integer CONSTRAINT calidad_control_proceso_caracteri_unidades_por_muestra_not_null NOT NULL,
    limite_inferior double precision,
    limite_superior double precision,
    CONSTRAINT chk_calidad_caracteristica_limites CHECK (((limite_inferior IS NULL) OR (limite_superior IS NULL) OR (limite_inferior <= limite_superior))),
    CONSTRAINT chk_calidad_caracteristica_muestras CHECK ((cantidad_muestras > 0)),
    CONSTRAINT chk_calidad_caracteristica_tipo CHECK (((tipo)::text = ANY ((ARRAY['NUMERICA'::character varying, 'BOOLEANA'::character varying])::text[]))),
    CONSTRAINT chk_calidad_caracteristica_unidades CHECK ((unidades_por_muestra > 0))
);


--
-- Name: calidad_control_proceso_caracteristica_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.calidad_control_proceso_caracteristica_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: calidad_control_proceso_caracteristica_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.calidad_control_proceso_caracteristica_id_seq OWNED BY public.calidad_control_proceso_caracteristica.id;


--
-- Name: calidad_control_proceso_ejecucion; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.calidad_control_proceso_ejecucion (
    id bigint NOT NULL,
    plantilla_id bigint NOT NULL,
    lote_id bigint NOT NULL,
    usuario_id bigint NOT NULL,
    fecha_registro timestamp without time zone NOT NULL,
    observaciones text,
    batch_record_id bigint,
    batch_record_etapa_id bigint,
    resultado character varying(20),
    CONSTRAINT chk_calidad_control_ejecucion_resultado CHECK (((resultado IS NULL) OR ((resultado)::text = ANY ((ARRAY['CONFORME'::character varying, 'NO_CONFORME'::character varying])::text[]))))
);


--
-- Name: calidad_control_proceso_ejecucion_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.calidad_control_proceso_ejecucion_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: calidad_control_proceso_ejecucion_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.calidad_control_proceso_ejecucion_id_seq OWNED BY public.calidad_control_proceso_ejecucion.id;


--
-- Name: calidad_control_proceso_lectura; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.calidad_control_proceso_lectura (
    id bigint NOT NULL,
    muestra_id bigint NOT NULL,
    indice_unidad integer NOT NULL,
    valor_numerico double precision,
    valor_booleano boolean,
    CONSTRAINT chk_calidad_lectura_indice CHECK ((indice_unidad > 0)),
    CONSTRAINT chk_calidad_lectura_un_valor CHECK ((((valor_numerico IS NOT NULL) AND (valor_booleano IS NULL)) OR ((valor_numerico IS NULL) AND (valor_booleano IS NOT NULL))))
);


--
-- Name: calidad_control_proceso_lectura_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.calidad_control_proceso_lectura_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: calidad_control_proceso_lectura_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.calidad_control_proceso_lectura_id_seq OWNED BY public.calidad_control_proceso_lectura.id;


--
-- Name: calidad_control_proceso_muestra; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.calidad_control_proceso_muestra (
    id bigint NOT NULL,
    ejecucion_id bigint NOT NULL,
    caracteristica_id bigint NOT NULL,
    numero_muestra integer NOT NULL,
    CONSTRAINT chk_calidad_muestra_numero CHECK ((numero_muestra > 0))
);


--
-- Name: calidad_control_proceso_muestra_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.calidad_control_proceso_muestra_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: calidad_control_proceso_muestra_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.calidad_control_proceso_muestra_id_seq OWNED BY public.calidad_control_proceso_muestra.id;


--
-- Name: calidad_control_proceso_plantilla; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.calidad_control_proceso_plantilla (
    id bigint NOT NULL,
    area_operativa_id integer NOT NULL,
    version integer NOT NULL,
    estado character varying(20) NOT NULL,
    CONSTRAINT chk_calidad_plantilla_estado CHECK (((estado)::text = ANY ((ARRAY['BORRADOR'::character varying, 'VIGENTE'::character varying, 'RETIRADA'::character varying])::text[])))
);


--
-- Name: calidad_control_proceso_plantilla_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.calidad_control_proceso_plantilla_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: calidad_control_proceso_plantilla_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.calidad_control_proceso_plantilla_id_seq OWNED BY public.calidad_control_proceso_plantilla.id;


--
-- Name: carga_costos_item; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.carga_costos_item (
    id bigint NOT NULL,
    lote_id uuid NOT NULL,
    fila_excel integer NOT NULL,
    producto_id character varying(255) NOT NULL,
    producto_nombre character varying(200),
    tipo_producto character varying(1) NOT NULL,
    descripcion_excel character varying(500),
    descripcion_coincide boolean NOT NULL,
    costo_anterior numeric(19,6) NOT NULL,
    costo_nuevo numeric(19,6) NOT NULL,
    costo_version_anterior bigint NOT NULL,
    CONSTRAINT ck_carga_costos_item_valores CHECK (((fila_excel > 1) AND (costo_anterior >= (0)::numeric) AND (costo_nuevo > (0)::numeric) AND (costo_version_anterior >= 1)))
);


--
-- Name: carga_costos_item_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.carga_costos_item_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: carga_costos_item_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.carga_costos_item_id_seq OWNED BY public.carga_costos_item.id;


--
-- Name: carga_costos_lote; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.carga_costos_lote (
    id uuid NOT NULL,
    usuario_id bigint,
    usuario_username character varying(120) NOT NULL,
    nombre_archivo character varying(255) NOT NULL,
    archivo_sha256 character varying(64) NOT NULL,
    motivo character varying(500) NOT NULL,
    estado character varying(30) NOT NULL,
    token_hash character varying(255),
    token_expira_en timestamp without time zone,
    intentos_token integer DEFAULT 0 NOT NULL,
    generaciones_token integer DEFAULT 0 NOT NULL,
    ultima_generacion_token_en timestamp without time zone,
    creado_en timestamp without time zone NOT NULL,
    expira_en timestamp without time zone NOT NULL,
    ejecutado_en timestamp without time zone,
    total_filas integer DEFAULT 0 NOT NULL,
    total_candidatas integer DEFAULT 0 NOT NULL,
    total_actualizadas integer DEFAULT 0 NOT NULL,
    total_sin_cambio integer DEFAULT 0 NOT NULL,
    total_omitidas integer DEFAULT 0 NOT NULL,
    receta_revision bigint,
    algoritmo_version character varying(40),
    propagacion_sha256 character varying(64),
    invalidado_en timestamp without time zone,
    invalidacion_codigo character varying(60),
    total_dependencias integer DEFAULT 0 NOT NULL,
    total_semiterminados integer DEFAULT 0 NOT NULL,
    total_terminados integer DEFAULT 0 NOT NULL,
    total_dependencias_actualizadas integer DEFAULT 0 NOT NULL,
    total_dependencias_sin_cambio integer DEFAULT 0 NOT NULL,
    CONSTRAINT ck_carga_costos_lote_contadores CHECK (((intentos_token >= 0) AND (generaciones_token >= 0) AND (total_filas >= 0) AND (total_candidatas >= 0) AND (total_actualizadas >= 0) AND (total_sin_cambio >= 0) AND (total_omitidas >= 0))),
    CONSTRAINT ck_carga_costos_lote_dependencias CHECK (((total_dependencias >= 0) AND (total_semiterminados >= 0) AND (total_terminados >= 0) AND (total_dependencias_actualizadas >= 0) AND (total_dependencias_sin_cambio >= 0) AND (total_dependencias = (total_semiterminados + total_terminados)) AND (total_dependencias = (total_dependencias_actualizadas + total_dependencias_sin_cambio)))),
    CONSTRAINT ck_carga_costos_lote_estado CHECK (((estado)::text = ANY ((ARRAY['PREPARADO'::character varying, 'EJECUTADO'::character varying, 'EXPIRADO'::character varying, 'BLOQUEADO'::character varying, 'CANCELADO'::character varying, 'INVALIDADO'::character varying])::text[]))),
    CONSTRAINT ck_carga_costos_lote_propagacion CHECK ((((estado)::text <> 'PREPARADO'::text) OR ((receta_revision IS NOT NULL) AND (receta_revision >= 1) AND (algoritmo_version IS NOT NULL) AND (propagacion_sha256 IS NOT NULL))))
);


--
-- Name: carga_costos_propagacion_item; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.carga_costos_propagacion_item (
    id bigint NOT NULL,
    lote_id uuid NOT NULL,
    producto_id character varying(255) NOT NULL,
    producto_nombre character varying(200),
    tipo_producto character varying(1) NOT NULL,
    nivel integer NOT NULL,
    costo_anterior numeric(19,6) NOT NULL,
    costo_nuevo numeric(19,6) NOT NULL,
    costo_version_anterior bigint NOT NULL,
    CONSTRAINT ck_carga_costos_propagacion_item_valores CHECK ((((tipo_producto)::text = ANY ((ARRAY['S'::character varying, 'T'::character varying])::text[])) AND (nivel >= 1) AND (costo_anterior >= (0)::numeric) AND (costo_nuevo >= (0)::numeric) AND (costo_version_anterior >= 1)))
);


--
-- Name: carga_costos_propagacion_item_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.carga_costos_propagacion_item_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: carga_costos_propagacion_item_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.carga_costos_propagacion_item_id_seq OWNED BY public.carga_costos_propagacion_item.id;


--
-- Name: cargo; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.cargo (
    id_cargo character varying(255) NOT NULL,
    departamento character varying(255),
    descripcion_cargo character varying(255),
    jefe_inmediato character varying(255),
    nivel integer NOT NULL,
    posicionx double precision NOT NULL,
    posiciony double precision NOT NULL,
    titulo_cargo character varying(255),
    url_doc_manual_funciones character varying(255),
    usuario_id bigint
);


--
-- Name: case_pack; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.case_pack (
    id bigint NOT NULL,
    alto_cm double precision,
    ancho_cm double precision,
    default_for_shipping boolean,
    ean14 character varying(255),
    gross_weight_kg double precision,
    largo_cm double precision,
    units_per_case integer,
    CONSTRAINT case_pack_units_per_case_check CHECK ((units_per_case >= 1))
);


--
-- Name: case_pack_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.case_pack_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: case_pack_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.case_pack_id_seq OWNED BY public.case_pack.id;


--
-- Name: categoria; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.categoria (
    categoria_id integer NOT NULL,
    categoria_descripcion character varying(255),
    categoria_nombre character varying(255),
    lote_size integer,
    tiempo_dias_fabricacion integer DEFAULT 0 NOT NULL,
    capacidad_productiva_diaria integer,
    vida_util_cantidad integer,
    vida_util_unidad character varying(10),
    CONSTRAINT chk_categoria_vida_util_consistente CHECK ((((vida_util_cantidad IS NULL) AND (vida_util_unidad IS NULL)) OR ((vida_util_cantidad IS NOT NULL) AND (vida_util_unidad IS NOT NULL) AND (vida_util_cantidad > 0) AND ((vida_util_unidad)::text = ANY ((ARRAY['DIAS'::character varying, 'MESES'::character varying, 'ANIOS'::character varying])::text[])))))
);


--
-- Name: cierre_produccion; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.cierre_produccion (
    id bigint NOT NULL,
    fecha_produccion date NOT NULL,
    cerrado_en timestamp without time zone NOT NULL,
    cerrado_por_id bigint NOT NULL,
    idempotency_key uuid NOT NULL,
    solicitud_hash character varying(64) NOT NULL
);


--
-- Name: cierre_produccion_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.cierre_produccion_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: cierre_produccion_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.cierre_produccion_id_seq OWNED BY public.cierre_produccion.id;


--
-- Name: clientes; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.clientes (
    cliente_id integer NOT NULL,
    condiciones_pago character varying(255),
    direccion character varying(255),
    email character varying(255),
    fecha_registro timestamp(6) without time zone,
    limite_credito integer,
    nombre character varying(255),
    telefono character varying(255),
    url_cam_comer character varying(255),
    url_rut character varying(255)
);


--
-- Name: clientes_cliente_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.clientes_cliente_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: clientes_cliente_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.clientes_cliente_id_seq OWNED BY public.clientes.cliente_id;


--
-- Name: contacto_proveedor; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.contacto_proveedor (
    contacto_id integer NOT NULL,
    proveedor_pk bigint NOT NULL,
    full_name character varying(255),
    cargo character varying(255),
    cel character varying(100),
    email character varying(255)
);


--
-- Name: contacto_proveedor_contacto_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.contacto_proveedor_contacto_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: contacto_proveedor_contacto_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.contacto_proveedor_contacto_id_seq OWNED BY public.contacto_proveedor.contacto_id;


--
-- Name: control_desviacion; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.control_desviacion (
    id bigint NOT NULL,
    control_requerido_id bigint NOT NULL,
    ejecucion_origen_id bigint NOT NULL,
    ambito character varying(20) NOT NULL,
    estado character varying(20) NOT NULL,
    disposicion character varying(30),
    investigacion text,
    resolucion text,
    justificacion_disposicion text,
    abierta_en timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    abierta_por_id bigint NOT NULL,
    resuelta_en timestamp without time zone,
    resuelta_por_id bigint,
    cerrada_en timestamp without time zone,
    cerrada_por_id bigint,
    CONSTRAINT chk_control_desviacion_ambito CHECK (((ambito)::text = ANY ((ARRAY['PROCESO'::character varying, 'CALIDAD'::character varying])::text[]))),
    CONSTRAINT chk_control_desviacion_cierre CHECK ((((estado)::text <> 'CERRADA'::text) OR ((cerrada_en IS NOT NULL) AND (cerrada_por_id IS NOT NULL) AND (disposicion IS NOT NULL) AND (NULLIF(btrim(justificacion_disposicion), ''::text) IS NOT NULL)))),
    CONSTRAINT chk_control_desviacion_disposicion CHECK (((disposicion IS NULL) OR ((disposicion)::text = ANY ((ARRAY['REPETIR'::character varying, 'CORREGIR_REPROCESAR'::character varying, 'ACEPTAR_JUSTIFICADAMENTE'::character varying, 'RECHAZAR'::character varying])::text[])))),
    CONSTRAINT chk_control_desviacion_estado CHECK (((estado)::text = ANY ((ARRAY['ABIERTA'::character varying, 'EN_INVESTIGACION'::character varying, 'RESUELTA'::character varying, 'CERRADA'::character varying])::text[]))),
    CONSTRAINT chk_control_desviacion_segregacion CHECK ((((ambito)::text <> 'CALIDAD'::text) OR (cerrada_por_id IS NULL) OR (resuelta_por_id IS NULL) OR (cerrada_por_id <> resuelta_por_id)))
);


--
-- Name: control_desviacion_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.control_desviacion_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: control_desviacion_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.control_desviacion_id_seq OWNED BY public.control_desviacion.id;


--
-- Name: control_ejecucion; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.control_ejecucion (
    id bigint NOT NULL,
    control_requerido_id bigint NOT NULL,
    repeticion_de_id bigint,
    usuario_id bigint NOT NULL,
    fecha_registro timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    resultado character varying(20) NOT NULL,
    observaciones text,
    motivo_repeticion character varying(500),
    legacy_ejecucion_id bigint,
    CONSTRAINT chk_control_ejecucion_repeticion CHECK ((((repeticion_de_id IS NULL) AND (motivo_repeticion IS NULL)) OR ((repeticion_de_id IS NOT NULL) AND (motivo_repeticion IS NOT NULL)))),
    CONSTRAINT chk_control_ejecucion_resultado CHECK (((resultado)::text = ANY ((ARRAY['CONFORME'::character varying, 'NO_CONFORME'::character varying])::text[])))
);


--
-- Name: control_ejecucion_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.control_ejecucion_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: control_ejecucion_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.control_ejecucion_id_seq OWNED BY public.control_ejecucion.id;


--
-- Name: control_ejecucion_lectura; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.control_ejecucion_lectura (
    id bigint NOT NULL,
    muestra_id bigint NOT NULL,
    indice_unidad integer NOT NULL,
    valor_numerico numeric(20,8),
    valor_booleano boolean,
    CONSTRAINT chk_control_lectura_indice CHECK ((indice_unidad > 0)),
    CONSTRAINT chk_control_lectura_valor CHECK ((((valor_numerico IS NOT NULL) AND (valor_booleano IS NULL)) OR ((valor_numerico IS NULL) AND (valor_booleano IS NOT NULL))))
);


--
-- Name: control_ejecucion_lectura_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.control_ejecucion_lectura_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: control_ejecucion_lectura_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.control_ejecucion_lectura_id_seq OWNED BY public.control_ejecucion_lectura.id;


--
-- Name: control_ejecucion_muestra; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.control_ejecucion_muestra (
    id bigint NOT NULL,
    ejecucion_id bigint NOT NULL,
    caracteristica_id bigint NOT NULL,
    numero_muestra integer NOT NULL,
    CONSTRAINT chk_control_muestra_numero CHECK ((numero_muestra > 0))
);


--
-- Name: control_ejecucion_muestra_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.control_ejecucion_muestra_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: control_ejecucion_muestra_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.control_ejecucion_muestra_id_seq OWNED BY public.control_ejecucion_muestra.id;


--
-- Name: control_idempotencia; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.control_idempotencia (
    id bigint NOT NULL,
    actor_id bigint NOT NULL,
    accion character varying(80) NOT NULL,
    recurso character varying(180) NOT NULL,
    clave character varying(200) NOT NULL,
    huella_payload character varying(64) NOT NULL,
    respuesta_json text,
    creada_en timestamp without time zone NOT NULL,
    completada_en timestamp without time zone,
    CONSTRAINT chk_control_idempotencia_completada CHECK ((((respuesta_json IS NULL) AND (completada_en IS NULL)) OR ((respuesta_json IS NOT NULL) AND (completada_en IS NOT NULL)))),
    CONSTRAINT chk_control_idempotencia_huella CHECK (((huella_payload)::text ~ '^[0-9a-f]{64}$'::text))
);


--
-- Name: TABLE control_idempotencia; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.control_idempotencia IS 'Registro transaccional de mutaciones idempotentes por actor, accion, recurso y clave.';


--
-- Name: COLUMN control_idempotencia.huella_payload; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.control_idempotencia.huella_payload IS 'SHA-256 del payload canonico; impide reutilizar una clave con una intencion diferente.';


--
-- Name: control_idempotencia_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.control_idempotencia_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: control_idempotencia_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.control_idempotencia_id_seq OWNED BY public.control_idempotencia.id;


--
-- Name: control_magnitud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.control_magnitud (
    id bigint NOT NULL,
    codigo character varying(40) NOT NULL,
    nombre character varying(120) NOT NULL,
    simbolo character varying(30) NOT NULL,
    dimension character varying(80) NOT NULL,
    activo boolean DEFAULT true NOT NULL,
    creado_en timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: control_magnitud_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.control_magnitud_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: control_magnitud_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.control_magnitud_id_seq OWNED BY public.control_magnitud.id;


--
-- Name: control_plan; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.control_plan (
    id bigint NOT NULL,
    codigo character varying(60) NOT NULL,
    nombre character varying(160) NOT NULL,
    ambito character varying(20) NOT NULL,
    creado_en timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    creado_por_id bigint,
    CONSTRAINT chk_control_plan_ambito CHECK (((ambito)::text = ANY ((ARRAY['PROCESO'::character varying, 'CALIDAD'::character varying])::text[])))
);


--
-- Name: TABLE control_plan; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.control_plan IS 'Identidad estable y neutral de un control de PROCESO o CALIDAD.';


--
-- Name: control_plan_aplicabilidad; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.control_plan_aplicabilidad (
    id bigint NOT NULL,
    version_id bigint NOT NULL,
    producto_id character varying(255),
    categoria_id integer,
    tipo_orden character varying(10) NOT NULL,
    punto_aplicacion character varying(30) NOT NULL,
    area_operativa_id integer,
    proceso_id integer,
    momento character varying(30) NOT NULL,
    punto_exigencia character varying(30) NOT NULL,
    legado_global boolean DEFAULT false NOT NULL,
    frontend_node_id character varying(255),
    CONSTRAINT chk_control_aplicabilidad_contexto CHECK ((legado_global OR (((punto_aplicacion)::text = 'LOTE_FINAL'::text) AND (area_operativa_id IS NULL) AND (proceso_id IS NULL)) OR (((punto_aplicacion)::text = 'SALIDA_OPERACION'::text) AND (area_operativa_id IS NOT NULL) AND (proceso_id IS NOT NULL)))),
    CONSTRAINT chk_control_aplicabilidad_etapa CHECK ((((punto_exigencia)::text <> 'CIERRE_ETAPA'::text) OR (((punto_aplicacion)::text = 'SALIDA_OPERACION'::text) AND ((momento)::text = 'DURANTE_FABRICACION'::text)))),
    CONSTRAINT chk_control_aplicabilidad_exigencia CHECK (((punto_exigencia)::text = ANY ((ARRAY['INFORMATIVO'::character varying, 'CIERRE_ETAPA'::character varying, 'ENVIO_CALIDAD'::character varying, 'LIBERACION'::character varying])::text[]))),
    CONSTRAINT chk_control_aplicabilidad_momento CHECK (((momento)::text = ANY ((ARRAY['DURANTE_FABRICACION'::character varying, 'REVISION_FINAL'::character varying])::text[]))),
    CONSTRAINT chk_control_aplicabilidad_objetivo CHECK (((legado_global AND (producto_id IS NULL) AND (categoria_id IS NULL)) OR ((NOT legado_global) AND ((producto_id IS NULL) <> (categoria_id IS NULL))))),
    CONSTRAINT chk_control_aplicabilidad_punto CHECK (((punto_aplicacion)::text = ANY ((ARRAY['LOTE_FINAL'::character varying, 'SALIDA_OPERACION'::character varying])::text[]))),
    CONSTRAINT chk_control_aplicabilidad_tipo_orden CHECK (((tipo_orden)::text = ANY ((ARRAY['OP'::character varying, 'OF'::character varying, 'AMBAS'::character varying])::text[])))
);


--
-- Name: COLUMN control_plan_aplicabilidad.legado_global; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.control_plan_aplicabilidad.legado_global IS 'Compatibilidad temporal para plantillas V077 que solo estaban clasificadas por area.';


--
-- Name: control_plan_aplicabilidad_exclusion; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.control_plan_aplicabilidad_exclusion (
    aplicabilidad_id bigint NOT NULL,
    producto_id character varying(255) NOT NULL
);


--
-- Name: control_plan_aplicabilidad_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.control_plan_aplicabilidad_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: control_plan_aplicabilidad_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.control_plan_aplicabilidad_id_seq OWNED BY public.control_plan_aplicabilidad.id;


--
-- Name: control_plan_caracteristica; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.control_plan_caracteristica (
    id bigint NOT NULL,
    version_id bigint NOT NULL,
    magnitud_id bigint NOT NULL,
    unidad_id bigint,
    nombre character varying(120) NOT NULL,
    tipo character varying(20) NOT NULL,
    orden integer NOT NULL,
    cantidad_muestras integer NOT NULL,
    unidades_por_muestra integer NOT NULL,
    escala_visible integer DEFAULT 2 NOT NULL,
    objetivo numeric(20,8),
    limite_inferior numeric(20,8),
    limite_superior numeric(20,8),
    valor_booleano_esperado boolean,
    magnitud_codigo_snapshot character varying(40) NOT NULL,
    magnitud_nombre_snapshot character varying(120) NOT NULL,
    magnitud_simbolo_snapshot character varying(30) NOT NULL,
    unidad_codigo_snapshot character varying(40),
    unidad_nombre_snapshot character varying(120),
    unidad_simbolo_snapshot character varying(30),
    legado_sin_limites boolean DEFAULT false NOT NULL,
    requiere_depuracion boolean DEFAULT false NOT NULL,
    legacy_caracteristica_id bigint,
    CONSTRAINT chk_control_caracteristica_config CHECK (((((tipo)::text = 'NUMERICA'::text) AND (unidad_id IS NOT NULL) AND (valor_booleano_esperado IS NULL) AND (legado_sin_limites OR (limite_inferior IS NOT NULL) OR (limite_superior IS NOT NULL))) OR (((tipo)::text = 'BOOLEANA'::text) AND (unidad_id IS NULL) AND (objetivo IS NULL) AND (limite_inferior IS NULL) AND (limite_superior IS NULL) AND (valor_booleano_esperado IS NOT NULL)))),
    CONSTRAINT chk_control_caracteristica_escala CHECK (((escala_visible >= 0) AND (escala_visible <= 8))),
    CONSTRAINT chk_control_caracteristica_limites CHECK (((limite_inferior IS NULL) OR (limite_superior IS NULL) OR (limite_inferior <= limite_superior))),
    CONSTRAINT chk_control_caracteristica_muestreo CHECK (((cantidad_muestras > 0) AND (unidades_por_muestra > 0))),
    CONSTRAINT chk_control_caracteristica_objetivo CHECK (((objetivo IS NULL) OR (((limite_inferior IS NULL) OR (objetivo >= limite_inferior)) AND ((limite_superior IS NULL) OR (objetivo <= limite_superior))))),
    CONSTRAINT chk_control_caracteristica_tipo CHECK (((tipo)::text = ANY ((ARRAY['NUMERICA'::character varying, 'BOOLEANA'::character varying])::text[])))
);


--
-- Name: control_plan_caracteristica_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.control_plan_caracteristica_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: control_plan_caracteristica_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.control_plan_caracteristica_id_seq OWNED BY public.control_plan_caracteristica.id;


--
-- Name: control_plan_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.control_plan_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: control_plan_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.control_plan_id_seq OWNED BY public.control_plan.id;


--
-- Name: control_plan_version; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.control_plan_version (
    id bigint NOT NULL,
    plan_id bigint NOT NULL,
    numero integer NOT NULL,
    estado character varying(20) NOT NULL,
    proposito character varying(120) NOT NULL,
    motivo_cambio character varying(500),
    responsable_ejecucion character varying(120) NOT NULL,
    responsable_revision character varying(120),
    responsable_disposicion character varying(120),
    creada_en timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    creada_por_id bigint,
    publicada_en timestamp without time zone,
    publicada_por_id bigint,
    retirada_en timestamp without time zone,
    retirada_por_id bigint,
    legacy_plantilla_id bigint,
    CONSTRAINT chk_control_plan_version_estado CHECK (((estado)::text = ANY ((ARRAY['BORRADOR'::character varying, 'VIGENTE'::character varying, 'RETIRADA'::character varying])::text[]))),
    CONSTRAINT chk_control_plan_version_fechas CHECK (((((estado)::text <> 'VIGENTE'::text) OR (publicada_en IS NOT NULL)) AND (((estado)::text <> 'RETIRADA'::text) OR (retirada_en IS NOT NULL)))),
    CONSTRAINT chk_control_plan_version_numero CHECK ((numero > 0))
);


--
-- Name: control_plan_version_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.control_plan_version_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: control_plan_version_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.control_plan_version_id_seq OWNED BY public.control_plan_version.id;


--
-- Name: control_requerido; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.control_requerido (
    id bigint NOT NULL,
    version_id bigint NOT NULL,
    aplicabilidad_id bigint,
    lote_id bigint NOT NULL,
    batch_record_id bigint,
    batch_record_etapa_id bigint,
    origen character varying(20) NOT NULL,
    estado character varying(30) DEFAULT 'PENDIENTE'::character varying NOT NULL,
    ciclo_revision_numero integer,
    requiere_repeticion boolean DEFAULT false NOT NULL,
    requiere_revalidacion boolean DEFAULT false NOT NULL,
    creado_en timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    plan_codigo_snapshot character varying(60) NOT NULL,
    plan_nombre_snapshot character varying(160) NOT NULL,
    ambito_snapshot character varying(20) NOT NULL,
    version_numero_snapshot integer NOT NULL,
    producto_id_snapshot character varying(255),
    producto_nombre_snapshot character varying(255),
    categoria_id_snapshot integer,
    categoria_nombre_snapshot character varying(255),
    tipo_orden_snapshot character varying(10) NOT NULL,
    punto_aplicacion_snapshot character varying(30) NOT NULL,
    area_operativa_id_snapshot integer,
    area_operativa_nombre_snapshot character varying(255),
    proceso_id_snapshot integer,
    proceso_nombre_snapshot character varying(255),
    manufacturing_version_id_snapshot bigint,
    ruta_version_id_snapshot bigint,
    ruta_nodo_id_snapshot bigint,
    orden_fabricacion_operacion_id_snapshot bigint,
    frontend_node_id_snapshot character varying(255),
    nodo_nombre_snapshot character varying(255),
    momento_snapshot character varying(30) NOT NULL,
    punto_exigencia_snapshot character varying(30) NOT NULL,
    legacy_ejecucion_id bigint,
    CONSTRAINT chk_control_requerido_ambito CHECK (((ambito_snapshot)::text = ANY ((ARRAY['PROCESO'::character varying, 'CALIDAD'::character varying])::text[]))),
    CONSTRAINT chk_control_requerido_batch CHECK (((((origen)::text = 'BATCH_RECORD'::text) AND (batch_record_id IS NOT NULL)) OR ((origen)::text <> 'BATCH_RECORD'::text))),
    CONSTRAINT chk_control_requerido_estado CHECK (((estado)::text = ANY ((ARRAY['PENDIENTE'::character varying, 'CONFORME'::character varying, 'NO_CONFORME'::character varying, 'ACEPTADO_POR_DESVIACION'::character varying, 'POR_REVALIDAR'::character varying])::text[]))),
    CONSTRAINT chk_control_requerido_etapa_record CHECK (((batch_record_etapa_id IS NULL) OR (batch_record_id IS NOT NULL))),
    CONSTRAINT chk_control_requerido_origen CHECK (((origen)::text = ANY ((ARRAY['BATCH_RECORD'::character varying, 'LEGACY'::character varying])::text[])))
);


--
-- Name: COLUMN control_requerido.legacy_ejecucion_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.control_requerido.legacy_ejecucion_id IS 'Enlace no destructivo a la ejecucion historica que origino una ocurrencia independiente.';


--
-- Name: control_requerido_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.control_requerido_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: control_requerido_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.control_requerido_id_seq OWNED BY public.control_requerido.id;


--
-- Name: control_revalidacion; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.control_revalidacion (
    id bigint NOT NULL,
    control_requerido_id bigint NOT NULL,
    ejecucion_revalidada_id bigint NOT NULL,
    ciclo_revision_numero integer NOT NULL,
    justificacion character varying(1000) NOT NULL,
    confirmada_en timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    confirmada_por_id bigint NOT NULL,
    CONSTRAINT chk_control_revalidacion_ciclo CHECK ((ciclo_revision_numero > 0)),
    CONSTRAINT chk_control_revalidacion_justificacion CHECK ((TRIM(BOTH FROM justificacion) <> ''::text))
);


--
-- Name: control_revalidacion_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.control_revalidacion_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: control_revalidacion_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.control_revalidacion_id_seq OWNED BY public.control_revalidacion.id;


--
-- Name: control_unidad; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.control_unidad (
    id bigint NOT NULL,
    codigo character varying(40) NOT NULL,
    nombre character varying(120) NOT NULL,
    simbolo character varying(30) NOT NULL,
    dimension character varying(80) NOT NULL,
    activo boolean DEFAULT true NOT NULL,
    creado_en timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: control_unidad_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.control_unidad_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: control_unidad_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.control_unidad_id_seq OWNED BY public.control_unidad.id;


--
-- Name: cuenta; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.cuenta (
    codigo character varying(10) NOT NULL,
    cuenta_control boolean NOT NULL,
    nombre character varying(255) NOT NULL,
    saldo_normal character varying(255) NOT NULL,
    tipo character varying(255) NOT NULL,
    CONSTRAINT cuenta_saldo_normal_check CHECK (((saldo_normal)::text = ANY (ARRAY[('DEBITO'::character varying)::text, ('CREDITO'::character varying)::text]))),
    CONSTRAINT cuenta_tipo_check CHECK (((tipo)::text = ANY (ARRAY[('ACTIVO'::character varying)::text, ('PASIVO'::character varying)::text, ('PATRIMONIO'::character varying)::text, ('INGRESO'::character varying)::text, ('GASTO'::character varying)::text])))
);


--
-- Name: depreciacion_activo; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.depreciacion_activo (
    id bigint NOT NULL,
    fecha_depreciacion date NOT NULL,
    metodo_depreciacion character varying(255),
    monto_depreciado numeric(38,2) NOT NULL,
    valor_libro_actual numeric(38,2) NOT NULL,
    activo_id character varying(255) NOT NULL,
    asiento_contable_id bigint
);


--
-- Name: depreciacion_activo_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.depreciacion_activo_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: depreciacion_activo_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.depreciacion_activo_id_seq OWNED BY public.depreciacion_activo.id;


--
-- Name: doc_tran_de_personal; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.doc_tran_de_personal (
    id bigint NOT NULL,
    descripcion character varying(1000),
    fecha_hora timestamp(6) without time zone NOT NULL,
    tipo_doc_tran character varying(255) NOT NULL,
    usuario_responsable character varying(255),
    valores_anteriores character varying(255),
    valores_nuevos character varying(255),
    integrante_id bigint,
    CONSTRAINT doc_tran_de_personal_tipo_doc_tran_check CHECK (((tipo_doc_tran)::text = ANY (ARRAY[('INGRESO'::character varying)::text, ('MODIFICACION_SALARIO'::character varying)::text, ('MODIFICACION_CARGO'::character varying)::text, ('MODIFICACION_DEPARTAMENTO'::character varying)::text, ('MODIFICACION_DATOS_PERSONALES'::character varying)::text, ('CAMBIO_ESTADO'::character varying)::text, ('SALIDA'::character varying)::text, ('OTRO'::character varying)::text])))
);


--
-- Name: doc_tran_de_personal_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.doc_tran_de_personal_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: doc_tran_de_personal_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.doc_tran_de_personal_id_seq OWNED BY public.doc_tran_de_personal.id;


--
-- Name: documento_baja_activo; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.documento_baja_activo (
    baja_id bigint NOT NULL,
    descripcion_disposicion character varying(500),
    descripcion_motivo character varying(500),
    estado integer NOT NULL,
    fecha_baja timestamp(6) without time zone,
    metodo_disposicion integer NOT NULL,
    motivo_baja integer NOT NULL,
    observaciones character varying(500),
    valor_contable_baja numeric(38,2),
    activo_id character varying(255) NOT NULL,
    asiento_contable_id bigint,
    responsable_id bigint NOT NULL
);


--
-- Name: documento_baja_activo_baja_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.documento_baja_activo_baja_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: documento_baja_activo_baja_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.documento_baja_activo_baja_id_seq OWNED BY public.documento_baja_activo.baja_id;


--
-- Name: empresa_identidad_legal_version; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.empresa_identidad_legal_version (
    id bigint NOT NULL,
    version integer NOT NULL,
    estado character varying(20) NOT NULL,
    razon_social character varying(255) NOT NULL,
    nombre_comercial character varying(160) NOT NULL,
    tipo_identificacion character varying(30) NOT NULL,
    numero_identificacion character varying(40) NOT NULL,
    digito_verificacion character varying(10) NOT NULL,
    telefono_principal character varying(80) NOT NULL,
    email_principal character varying(255) NOT NULL,
    vigente_desde timestamp without time zone NOT NULL,
    vigente_hasta timestamp without time zone,
    creado_en timestamp without time zone NOT NULL,
    creado_por character varying(120),
    motivo_cambio text,
    CONSTRAINT chk_empresa_identidad_legal_estado CHECK (((estado)::text = ANY ((ARRAY['VIGENTE'::character varying, 'RETIRADA'::character varying])::text[])))
);


--
-- Name: empresa_identidad_legal_version_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.empresa_identidad_legal_version_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: empresa_identidad_legal_version_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.empresa_identidad_legal_version_id_seq OWNED BY public.empresa_identidad_legal_version.id;


--
-- Name: empresa_logo_documental_version; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.empresa_logo_documental_version (
    id bigint NOT NULL,
    version integer NOT NULL,
    estado character varying(20) NOT NULL,
    nombre_archivo_original character varying(255) CONSTRAINT empresa_logo_documental_versio_nombre_archivo_original_not_null NOT NULL,
    content_type character varying(80) NOT NULL,
    tamano_bytes bigint NOT NULL,
    ancho_px integer NOT NULL,
    alto_px integer NOT NULL,
    sha256 character varying(64) NOT NULL,
    contenido bytea NOT NULL,
    vigente_desde timestamp without time zone NOT NULL,
    vigente_hasta timestamp without time zone,
    creado_en timestamp without time zone NOT NULL,
    creado_por character varying(120),
    motivo_cambio text,
    CONSTRAINT chk_empresa_logo_documental_estado CHECK (((estado)::text = ANY ((ARRAY['VIGENTE'::character varying, 'RETIRADA'::character varying])::text[])))
);


--
-- Name: empresa_logo_documental_version_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.empresa_logo_documental_version_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: empresa_logo_documental_version_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.empresa_logo_documental_version_id_seq OWNED BY public.empresa_logo_documental_version.id;


--
-- Name: factura_compra_activo; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.factura_compra_activo (
    factura_compra_activo_id integer NOT NULL,
    condicion_pago character varying(255),
    estado_pago integer NOT NULL,
    fecha_compra timestamp(6) without time zone,
    iva double precision NOT NULL,
    numero_factura_proveedor character varying(255),
    plazo_pago integer NOT NULL,
    sub_total double precision NOT NULL,
    total_pagar double precision NOT NULL,
    url_factura character varying(255),
    orden_compra_activo_id integer,
    proveedor_pk bigint
);


--
-- Name: factura_compra_activo_factura_compra_activo_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.factura_compra_activo_factura_compra_activo_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: factura_compra_activo_factura_compra_activo_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.factura_compra_activo_factura_compra_activo_id_seq OWNED BY public.factura_compra_activo.factura_compra_activo_id;


--
-- Name: facturas_compras; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.facturas_compras (
    factura_compra_id integer NOT NULL,
    condicion_pago character varying(255),
    estado_pago integer NOT NULL,
    fecha_compra timestamp(6) without time zone,
    iva19 integer NOT NULL,
    plazo_pago integer NOT NULL,
    sub_total integer NOT NULL,
    total_pagar integer NOT NULL,
    proveedor_pk bigint
);


--
-- Name: facturas_compras_factura_compra_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.facturas_compras_factura_compra_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: facturas_compras_factura_compra_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.facturas_compras_factura_compra_id_seq OWNED BY public.facturas_compras.factura_compra_id;


--
-- Name: facturas_venta; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.facturas_venta (
    factura_venta_id integer NOT NULL,
    estado_pago character varying(255),
    fecha_factura timestamp(6) without time zone,
    impuestos integer NOT NULL,
    sub_total integer NOT NULL,
    total_pagar integer NOT NULL,
    cliente_id integer,
    orden_venta_id integer
);


--
-- Name: facturas_venta_factura_venta_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.facturas_venta_factura_venta_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: facturas_venta_factura_venta_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.facturas_venta_factura_venta_id_seq OWNED BY public.facturas_venta.factura_venta_id;


--
-- Name: firma_visual_usuario_version; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.firma_visual_usuario_version (
    id bigint NOT NULL,
    usuario_id bigint NOT NULL,
    version integer NOT NULL,
    estado character varying(20) NOT NULL,
    nombre_archivo_original character varying(255) NOT NULL,
    content_type character varying(80) NOT NULL,
    tamano_bytes bigint NOT NULL,
    ancho_px integer NOT NULL,
    alto_px integer NOT NULL,
    sha256 character varying(64) NOT NULL,
    contenido bytea NOT NULL,
    vigente_desde timestamp without time zone NOT NULL,
    vigente_hasta timestamp without time zone,
    creado_en timestamp without time zone NOT NULL,
    configurada_por_id bigint NOT NULL,
    configurada_por_username character varying(255) NOT NULL,
    configurada_por_nombre character varying(255) NOT NULL,
    motivo_cambio text NOT NULL,
    retirada_por_id bigint,
    retirada_por_username character varying(255),
    retirada_por_nombre character varying(255),
    motivo_retiro text,
    CONSTRAINT chk_firma_visual_usuario_dimensiones CHECK ((((ancho_px >= 50) AND (ancho_px <= 2000)) AND ((alto_px >= 20) AND (alto_px <= 1000)))),
    CONSTRAINT chk_firma_visual_usuario_estado CHECK (((estado)::text = ANY ((ARRAY['VIGENTE'::character varying, 'RETIRADA'::character varying])::text[]))),
    CONSTRAINT chk_firma_visual_usuario_hash CHECK (((sha256)::text ~ '^[0-9A-Fa-f]{64}$'::text)),
    CONSTRAINT chk_firma_visual_usuario_png CHECK (((content_type)::text = 'image/png'::text)),
    CONSTRAINT chk_firma_visual_usuario_tamano CHECK (((tamano_bytes > 0) AND (tamano_bytes <= 1048576) AND (octet_length(contenido) = tamano_bytes))),
    CONSTRAINT chk_firma_visual_usuario_version_positiva CHECK ((version > 0)),
    CONSTRAINT chk_firma_visual_usuario_vigencia CHECK (((((estado)::text = 'VIGENTE'::text) AND (vigente_hasta IS NULL) AND (retirada_por_id IS NULL) AND (retirada_por_username IS NULL) AND (retirada_por_nombre IS NULL) AND (motivo_retiro IS NULL)) OR (((estado)::text = 'RETIRADA'::text) AND (vigente_hasta IS NOT NULL) AND (vigente_hasta >= vigente_desde) AND (retirada_por_id IS NOT NULL) AND (retirada_por_username IS NOT NULL) AND (retirada_por_nombre IS NOT NULL) AND (motivo_retiro IS NOT NULL))))
);


--
-- Name: firma_visual_usuario_version_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.firma_visual_usuario_version_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: firma_visual_usuario_version_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.firma_visual_usuario_version_id_seq OWNED BY public.firma_visual_usuario_version.id;


--
-- Name: incorporacion_activo_header; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.incorporacion_activo_header (
    incorporacion_id bigint NOT NULL,
    estado integer NOT NULL,
    estado_contable character varying(255),
    fecha_incorporacion timestamp(6) without time zone,
    observaciones character varying(500),
    asiento_contable_id bigint,
    factura_compra_activo_id integer,
    responsable_id bigint,
    CONSTRAINT incorporacion_activo_header_estado_contable_check CHECK (((estado_contable)::text = ANY (ARRAY[('PENDIENTE'::character varying)::text, ('CONTABILIZADA'::character varying)::text, ('NO_APLICA'::character varying)::text])))
);


--
-- Name: incorporacion_activo_header_incorporacion_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.incorporacion_activo_header_incorporacion_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: incorporacion_activo_header_incorporacion_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.incorporacion_activo_header_incorporacion_id_seq OWNED BY public.incorporacion_activo_header.incorporacion_id;


--
-- Name: incorporacion_activo_line; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.incorporacion_activo_line (
    linea_id bigint NOT NULL,
    cantidad integer,
    descripcion character varying(500),
    metodo_depreciacion character varying(255),
    ubicacion_inicial character varying(255),
    valor_total numeric(38,2),
    valor_unitario numeric(38,2),
    vida_util_meses integer,
    activo_id character varying(255),
    incorporacion_id bigint NOT NULL
);


--
-- Name: incorporacion_activo_line_linea_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.incorporacion_activo_line_linea_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: incorporacion_activo_line_linea_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.incorporacion_activo_line_linea_id_seq OWNED BY public.incorporacion_activo_line.linea_id;


--
-- Name: insumos; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.insumos (
    insumo_id integer NOT NULL,
    cantidad_requerida double precision NOT NULL,
    input_producto_id character varying(255),
    output_producto_id character varying(255),
    cantidad_receta numeric(24,12),
    unidad_receta character varying(16),
    CONSTRAINT ck_insumo_receta CHECK ((((cantidad_receta IS NULL) AND (unidad_receta IS NULL)) OR ((cantidad_receta IS NOT NULL) AND (cantidad_receta > (0)::numeric) AND (unidad_receta IS NOT NULL))))
);


--
-- Name: insumos_empaque; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.insumos_empaque (
    id bigint NOT NULL,
    cantidad double precision NOT NULL,
    nivel character varying(255),
    uom character varying(12),
    material_id character varying(255) NOT NULL,
    producto_id bigint,
    CONSTRAINT insumos_empaque_cantidad_check CHECK ((cantidad >= (0)::double precision)),
    CONSTRAINT insumos_empaque_nivel_check CHECK (((nivel)::text = ANY (ARRAY[('EACH'::character varying)::text, ('CASE'::character varying)::text])))
);


--
-- Name: insumos_empaque_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.insumos_empaque_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: insumos_empaque_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.insumos_empaque_id_seq OWNED BY public.insumos_empaque.id;


--
-- Name: insumos_insumo_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.insumos_insumo_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: insumos_insumo_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.insumos_insumo_id_seq OWNED BY public.insumos.insumo_id;


--
-- Name: integrante_personal; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.integrante_personal (
    id bigint NOT NULL,
    apellidos character varying(255) NOT NULL,
    cargo character varying(255),
    celular character varying(255) NOT NULL,
    centro_de_costo character varying(255),
    centro_de_produccion character varying(255),
    departamento smallint,
    direccion character varying(255) NOT NULL,
    email character varying(255),
    estado smallint,
    nombres character varying(255) NOT NULL,
    salario integer NOT NULL,
    usuario_id bigint,
    nombre_contacto_emergencia character varying(255),
    celular_contacto_emergencia character varying(255),
    estado_civil character varying(30),
    numero_hijos integer,
    fecha_ingreso date,
    numero_cuenta_bancaria character varying(255),
    banco character varying(255),
    CONSTRAINT chk_integrante_personal_estado_civil CHECK (((estado_civil IS NULL) OR ((estado_civil)::text = ANY ((ARRAY['SOLTERO'::character varying, 'CASADO'::character varying, 'UNION_LIBRE'::character varying, 'SEPARADO'::character varying, 'DIVORCIADO'::character varying, 'VIUDO'::character varying])::text[])))),
    CONSTRAINT chk_integrante_personal_numero_hijos CHECK (((numero_hijos IS NULL) OR (numero_hijos >= 0))),
    CONSTRAINT integrante_personal_departamento_check CHECK (((departamento >= 0) AND (departamento <= 1))),
    CONSTRAINT integrante_personal_estado_check CHECK (((estado >= 0) AND (estado <= 1)))
);


--
-- Name: item_orden_compra; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.item_orden_compra (
    item_id integer NOT NULL,
    cantidad integer NOT NULL,
    cantidad_correcta integer NOT NULL,
    ivacop integer NOT NULL,
    precio_correcto integer NOT NULL,
    precio_unitario integer NOT NULL,
    sub_total integer NOT NULL,
    producto_id character varying(255) NOT NULL,
    orden_compra_id integer
);


--
-- Name: item_orden_compra_activo; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.item_orden_compra_activo (
    item_id integer NOT NULL,
    cantidad integer NOT NULL,
    iva_percentage double precision NOT NULL,
    iva_value double precision NOT NULL,
    nombre character varying(255) NOT NULL,
    precio_unitario double precision NOT NULL,
    sub_total double precision NOT NULL,
    orden_compra_activo_id integer
);


--
-- Name: item_orden_compra_activo_item_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.item_orden_compra_activo_item_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: item_orden_compra_activo_item_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.item_orden_compra_activo_item_id_seq OWNED BY public.item_orden_compra_activo.item_id;


--
-- Name: item_orden_compra_item_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.item_orden_compra_item_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: item_orden_compra_item_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.item_orden_compra_item_id_seq OWNED BY public.item_orden_compra.item_id;


--
-- Name: items_factura_compra; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.items_factura_compra (
    item_compra_id integer NOT NULL,
    cantidad double precision NOT NULL,
    precio_compra integer NOT NULL,
    factura_compra_id integer,
    materia_prima_id character varying(255)
);


--
-- Name: items_factura_compra_item_compra_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.items_factura_compra_item_compra_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: items_factura_compra_item_compra_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.items_factura_compra_item_compra_id_seq OWNED BY public.items_factura_compra.item_compra_id;


--
-- Name: items_factura_venta; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.items_factura_venta (
    item_factura_id integer NOT NULL,
    cantidad double precision NOT NULL,
    precio_unitario integer NOT NULL,
    factura_venta_id integer,
    producto_id character varying(255)
);


--
-- Name: items_factura_venta_item_factura_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.items_factura_venta_item_factura_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: items_factura_venta_item_factura_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.items_factura_venta_item_factura_id_seq OWNED BY public.items_factura_venta.item_factura_id;


--
-- Name: items_orden_venta; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.items_orden_venta (
    item_id integer NOT NULL,
    cantidad double precision NOT NULL,
    impuestos integer NOT NULL,
    precio_unitario integer NOT NULL,
    orden_venta_id integer,
    producto_id character varying(255)
);


--
-- Name: items_orden_venta_item_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.items_orden_venta_item_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: items_orden_venta_item_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.items_orden_venta_item_id_seq OWNED BY public.items_orden_venta.item_id;


--
-- Name: jornada_laboral_bloque; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.jornada_laboral_bloque (
    id bigint NOT NULL,
    jornada_laboral_version_id bigint NOT NULL,
    dia_semana integer NOT NULL,
    orden integer NOT NULL,
    hora_inicio time without time zone NOT NULL,
    hora_fin time without time zone NOT NULL,
    CONSTRAINT chk_jornada_laboral_bloque_dia CHECK (((dia_semana >= 1) AND (dia_semana <= 7))),
    CONSTRAINT chk_jornada_laboral_bloque_horas CHECK ((hora_inicio < hora_fin)),
    CONSTRAINT chk_jornada_laboral_bloque_orden CHECK ((orden >= 0))
);


--
-- Name: jornada_laboral_bloque_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.jornada_laboral_bloque_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: jornada_laboral_bloque_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.jornada_laboral_bloque_id_seq OWNED BY public.jornada_laboral_bloque.id;


--
-- Name: jornada_laboral_version; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.jornada_laboral_version (
    id bigint NOT NULL,
    version integer NOT NULL,
    estado character varying(20) NOT NULL,
    vigente_desde timestamp without time zone NOT NULL,
    vigente_hasta timestamp without time zone,
    creado_en timestamp without time zone NOT NULL,
    creado_por character varying(120),
    motivo_cambio text,
    CONSTRAINT chk_jornada_laboral_estado CHECK (((estado)::text = ANY ((ARRAY['VIGENTE'::character varying, 'RETIRADA'::character varying])::text[])))
);


--
-- Name: jornada_laboral_version_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.jornada_laboral_version_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: jornada_laboral_version_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.jornada_laboral_version_id_seq OWNED BY public.jornada_laboral_version.id;


--
-- Name: lead_time_proveedor_kpi; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.lead_time_proveedor_kpi (
    id bigint NOT NULL,
    proveedor_pk bigint NOT NULL,
    fecha_corte date NOT NULL,
    ventana_dias integer NOT NULL,
    lead_time_mediano_dias double precision,
    observaciones integer NOT NULL,
    ordenes_consideradas integer NOT NULL,
    calculado_en timestamp without time zone,
    estado character varying(32) DEFAULT 'VIGENTE'::character varying NOT NULL,
    motivo_estado character varying(255),
    ultima_evaluacion_en timestamp without time zone,
    ultima_fecha_corte_evaluada date,
    CONSTRAINT chk_lead_time_proveedor_kpi_estado CHECK (((estado)::text = ANY ((ARRAY['VIGENTE'::character varying, 'DESACTUALIZADO'::character varying, 'SIN_INFORMACION'::character varying])::text[]))),
    CONSTRAINT chk_lead_time_proveedor_kpi_lead_time CHECK ((lead_time_mediano_dias >= (0)::double precision)),
    CONSTRAINT chk_lead_time_proveedor_kpi_observaciones CHECK ((observaciones >= 0)),
    CONSTRAINT chk_lead_time_proveedor_kpi_ordenes CHECK ((ordenes_consideradas >= 0)),
    CONSTRAINT chk_lead_time_proveedor_kpi_ventana CHECK ((ventana_dias >= 1))
);


--
-- Name: lead_time_proveedor_kpi_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.lead_time_proveedor_kpi_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: lead_time_proveedor_kpi_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.lead_time_proveedor_kpi_id_seq OWNED BY public.lead_time_proveedor_kpi.id;


--
-- Name: linea_asiento_contable; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.linea_asiento_contable (
    id bigint NOT NULL,
    credito numeric(38,2),
    cuenta_codigo character varying(20),
    debito numeric(38,2),
    descripcion character varying(255),
    asiento_id bigint NOT NULL
);


--
-- Name: linea_asiento_contable_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.linea_asiento_contable_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: linea_asiento_contable_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.linea_asiento_contable_id_seq OWNED BY public.linea_asiento_contable.id;


--
-- Name: lote; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.lote (
    id bigint NOT NULL,
    batch_number character varying(255) NOT NULL,
    expiration_date date,
    production_date date,
    orden_compra_id integer,
    orden_produccion_id integer,
    producto_id character varying(255),
    estado_calidad character varying(30) DEFAULT 'SIN_CLASIFICAR'::character varying NOT NULL,
    orden_fabricacion_id bigint,
    vida_util_cantidad_aplicada integer,
    vida_util_unidad_aplicada character varying(10),
    CONSTRAINT chk_lote_estado_calidad CHECK (((estado_calidad)::text = ANY ((ARRAY['SIN_CLASIFICAR'::character varying, 'CUARENTENA'::character varying, 'APROBADO'::character varying, 'LIBERADO'::character varying, 'RECHAZADO'::character varying, 'BLOQUEADO'::character varying, 'NO_APLICA_CALIDAD'::character varying])::text[]))),
    CONSTRAINT chk_lote_vida_util_consistente CHECK ((((vida_util_cantidad_aplicada IS NULL) AND (vida_util_unidad_aplicada IS NULL)) OR ((vida_util_cantidad_aplicada IS NOT NULL) AND (vida_util_unidad_aplicada IS NOT NULL) AND (vida_util_cantidad_aplicada > 0) AND ((vida_util_unidad_aplicada)::text = ANY ((ARRAY['DIAS'::character varying, 'MESES'::character varying, 'ANIOS'::character varying])::text[])))))
);


--
-- Name: lote_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.lote_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: lote_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.lote_id_seq OWNED BY public.lote.id;


--
-- Name: maestra_notificacion; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.maestra_notificacion (
    id integer NOT NULL,
    nombre character varying(255),
    descripcion character varying(500)
);


--
-- Name: maestra_notificacion_users; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.maestra_notificacion_users (
    notificacion_id integer NOT NULL,
    user_id bigint NOT NULL
);


--
-- Name: mantenimiento_activo; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.mantenimiento_activo (
    id bigint NOT NULL,
    costo numeric(38,2),
    descripcion character varying(500) NOT NULL,
    estado character varying(255) NOT NULL,
    fecha_mantenimiento timestamp(6) without time zone NOT NULL,
    observaciones character varying(500),
    tipo character varying(255) NOT NULL,
    activo_id character varying(255) NOT NULL,
    responsable_id bigint NOT NULL,
    CONSTRAINT mantenimiento_activo_estado_check CHECK (((estado)::text = ANY (ARRAY[('PROGRAMADO'::character varying)::text, ('EN_PROCESO'::character varying)::text, ('COMPLETADO'::character varying)::text, ('CANCELADO'::character varying)::text]))),
    CONSTRAINT mantenimiento_activo_tipo_check CHECK (((tipo)::text = ANY (ARRAY[('PREVENTIVO'::character varying)::text, ('CORRECTIVO'::character varying)::text, ('PREDICTIVO'::character varying)::text])))
);


--
-- Name: mantenimiento_activo_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.mantenimiento_activo_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: mantenimiento_activo_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.mantenimiento_activo_id_seq OWNED BY public.mantenimiento_activo.id;


--
-- Name: manufacturing_versions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.manufacturing_versions (
    id bigint NOT NULL,
    case_pack_json oid,
    insumos_json oid,
    proceso_produccion_json oid,
    version_number integer NOT NULL,
    producto_id character varying(255)
);


--
-- Name: manufacturing_versions_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.manufacturing_versions_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: manufacturing_versions_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.manufacturing_versions_id_seq OWNED BY public.manufacturing_versions.id;


--
-- Name: master_directive; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.master_directive (
    id bigint NOT NULL,
    ayuda text,
    grupo character varying(255),
    nombre character varying(255),
    resumen character varying(255),
    tipo_dato character varying(255),
    valor character varying(255),
    CONSTRAINT master_directive_grupo_check CHECK (((grupo)::text = ANY ((ARRAY['FLEXIBILIDAD_CONTROL'::character varying, 'COMPRAS_ALMACEN'::character varying])::text[]))),
    CONSTRAINT master_directive_tipo_dato_check CHECK (((tipo_dato)::text = ANY ((ARRAY['TEXTO'::character varying, 'NUMERO'::character varying, 'DECIMAL'::character varying, 'BOOLEANO'::character varying, 'FECHA'::character varying, 'JSON'::character varying])::text[])))
);


--
-- Name: master_directive_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.master_directive_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: master_directive_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.master_directive_id_seq OWNED BY public.master_directive.id;


--
-- Name: master_production_schedule_semanal; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.master_production_schedule_semanal (
    mps_id integer NOT NULL,
    week_start_date date NOT NULL,
    week_end_date date NOT NULL,
    fecha_creacion timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    estado character varying(20) DEFAULT 'BORRADOR'::character varying NOT NULL,
    fecha_actualizacion timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    fecha_aprobacion timestamp without time zone,
    aprobado_por_username character varying(100),
    fecha_generacion_odps timestamp without time zone,
    generado_por_username character varying(100),
    semana_mps_id bigint NOT NULL,
    revision_numero integer DEFAULT 1 NOT NULL
);


--
-- Name: master_production_schedule_semanal_mps_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.master_production_schedule_semanal_mps_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: master_production_schedule_semanal_mps_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.master_production_schedule_semanal_mps_id_seq OWNED BY public.master_production_schedule_semanal.mps_id;


--
-- Name: material_ficha_tecnica_version; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.material_ficha_tecnica_version (
    id bigint NOT NULL,
    material_id character varying(255) NOT NULL,
    version integer NOT NULL,
    estado character varying(20) NOT NULL,
    nombre_archivo_original character varying(255) NOT NULL,
    content_type character varying(100) NOT NULL,
    tamano_bytes bigint,
    sha256 character varying(64),
    storage_key character varying(500) NOT NULL,
    vigente_desde timestamp without time zone NOT NULL,
    vigente_hasta timestamp without time zone,
    creado_en timestamp without time zone NOT NULL,
    creado_por character varying(120),
    motivo_cambio text NOT NULL,
    CONSTRAINT chk_material_ficha_tecnica_estado CHECK (((estado)::text = ANY ((ARRAY['VIGENTE'::character varying, 'RETIRADA'::character varying])::text[]))),
    CONSTRAINT chk_material_ficha_tecnica_sha256 CHECK (((sha256 IS NULL) OR (char_length((sha256)::text) = 64))),
    CONSTRAINT chk_material_ficha_tecnica_tamano CHECK (((tamano_bytes IS NULL) OR ((tamano_bytes > 0) AND (tamano_bytes <= 10485760)))),
    CONSTRAINT chk_material_ficha_tecnica_version_positiva CHECK ((version > 0))
);


--
-- Name: material_ficha_tecnica_version_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.material_ficha_tecnica_version_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: material_ficha_tecnica_version_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.material_ficha_tecnica_version_id_seq OWNED BY public.material_ficha_tecnica_version.id;


--
-- Name: mision_vision_valor; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.mision_vision_valor (
    id bigint NOT NULL,
    mision_vision_version_id bigint NOT NULL,
    orden integer NOT NULL,
    titulo character varying(120) NOT NULL,
    descripcion_html text NOT NULL,
    CONSTRAINT chk_mision_vision_valor_orden CHECK ((orden >= 0))
);


--
-- Name: mision_vision_valor_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.mision_vision_valor_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: mision_vision_valor_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.mision_vision_valor_id_seq OWNED BY public.mision_vision_valor.id;


--
-- Name: mision_vision_version; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.mision_vision_version (
    id bigint NOT NULL,
    version integer NOT NULL,
    estado character varying(20) NOT NULL,
    mision_html text NOT NULL,
    vision_html text NOT NULL,
    vigente_desde timestamp without time zone NOT NULL,
    vigente_hasta timestamp without time zone,
    creado_en timestamp without time zone NOT NULL,
    creado_por character varying(120),
    motivo_cambio text NOT NULL,
    origen_version_id bigint,
    CONSTRAINT chk_mision_vision_estado CHECK (((estado)::text = ANY ((ARRAY['VIGENTE'::character varying, 'RETIRADA'::character varying])::text[])))
);


--
-- Name: mision_vision_version_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.mision_vision_version_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: mision_vision_version_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.mision_vision_version_id_seq OWNED BY public.mision_vision_version.id;


--
-- Name: modulo_accesos; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.modulo_accesos (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    modulo character varying(100) NOT NULL
);


--
-- Name: modulo_accesos_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.modulo_accesos_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: modulo_accesos_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.modulo_accesos_id_seq OWNED BY public.modulo_accesos.id;


--
-- Name: monitoreo_evento_error; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.monitoreo_evento_error (
    id uuid NOT NULL,
    grupo_error_id uuid NOT NULL,
    origen character varying(10) NOT NULL,
    modulo character varying(100),
    tab character varying(128),
    paso character varying(160),
    accion character varying(200),
    usuario_id bigint,
    ocurrido_en timestamp with time zone NOT NULL,
    recibido_en timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    ambiente character varying(10) NOT NULL,
    version_aplicacion character varying(120),
    correlacion_id character varying(128),
    tipo_error character varying(500) NOT NULL,
    mensaje text,
    archivo text,
    linea integer,
    stack_trace text,
    solicitud jsonb,
    payloads jsonb,
    entidades jsonb,
    acciones_previas jsonb,
    logs_relacionados jsonb,
    CONSTRAINT chk_monitoreo_evento_acciones CHECK ((jsonb_typeof(acciones_previas) = 'array'::text)),
    CONSTRAINT chk_monitoreo_evento_ambiente CHECK (((ambiente)::text = ANY ((ARRAY['LOCAL'::character varying, 'STAGING'::character varying, 'PRODUCCION'::character varying])::text[]))),
    CONSTRAINT chk_monitoreo_evento_entidades CHECK ((jsonb_typeof(entidades) = 'array'::text)),
    CONSTRAINT chk_monitoreo_evento_linea CHECK (((linea IS NULL) OR (linea > 0))),
    CONSTRAINT chk_monitoreo_evento_logs CHECK ((jsonb_typeof(logs_relacionados) = 'array'::text)),
    CONSTRAINT chk_monitoreo_evento_origen CHECK (((origen)::text = ANY ((ARRAY['FRONTEND'::character varying, 'BACKEND'::character varying])::text[]))),
    CONSTRAINT chk_monitoreo_evento_payloads CHECK ((jsonb_typeof(payloads) = 'array'::text)),
    CONSTRAINT chk_monitoreo_evento_solicitud CHECK ((jsonb_typeof(solicitud) = 'object'::text))
);


--
-- Name: TABLE monitoreo_evento_error; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.monitoreo_evento_error IS 'Evidencia de frontend o backend; depurar datos sensibles antes de persistir payloads, trazas y logs.';


--
-- Name: COLUMN monitoreo_evento_error.modulo; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.monitoreo_evento_error.modulo IS 'Identificador funcional, independiente del catalogo de permisos; NULL si no se conoce el modulo.';


--
-- Name: COLUMN monitoreo_evento_error.usuario_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.monitoreo_evento_error.usuario_id IS 'Identificador historico opcional, sin FK para no bloquear la eliminacion del usuario.';


--
-- Name: monitoreo_grupo_error; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.monitoreo_grupo_error (
    id uuid NOT NULL,
    firma character varying(64) NOT NULL,
    titulo character varying(500) NOT NULL,
    estado character varying(20) DEFAULT 'ABIERTO'::character varying NOT NULL,
    severidad character varying(10) DEFAULT 'MEDIA'::character varying NOT NULL,
    total_eventos bigint DEFAULT 0 NOT NULL,
    primera_aparicion timestamp with time zone,
    ultima_aparicion timestamp with time zone,
    version_correccion character varying(120),
    version bigint DEFAULT 0 NOT NULL,
    CONSTRAINT chk_monitoreo_grupo_estado CHECK (((estado)::text = ANY ((ARRAY['ABIERTO'::character varying, 'EN_INVESTIGACION'::character varying, 'RESUELTO'::character varying])::text[]))),
    CONSTRAINT chk_monitoreo_grupo_fechas CHECK ((((primera_aparicion IS NULL) AND (ultima_aparicion IS NULL)) OR ((primera_aparicion IS NOT NULL) AND (ultima_aparicion IS NOT NULL) AND (ultima_aparicion >= primera_aparicion)))),
    CONSTRAINT chk_monitoreo_grupo_firma CHECK (((firma)::text ~ '^[0-9a-f]{64}$'::text)),
    CONSTRAINT chk_monitoreo_grupo_severidad CHECK (((severidad)::text = ANY ((ARRAY['BAJA'::character varying, 'MEDIA'::character varying, 'ALTA'::character varying, 'CRITICA'::character varying])::text[]))),
    CONSTRAINT chk_monitoreo_grupo_total CHECK ((total_eventos >= 0))
);


--
-- Name: TABLE monitoreo_grupo_error; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.monitoreo_grupo_error IS 'Problemas agrupados por firma; sus modulos y ambientes se identifican mediante los eventos.';


--
-- Name: COLUMN monitoreo_grupo_error.total_eventos; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.monitoreo_grupo_error.total_eventos IS 'Acumulado historico de eventos unicos; preservar al purgar evidencia antigua.';


--
-- Name: movimiento_reserva; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.movimiento_reserva (
    id bigint NOT NULL,
    cantidad integer NOT NULL,
    fecha_creacion timestamp(6) without time zone,
    tipo smallint,
    producto_id character varying(255),
    CONSTRAINT movimiento_reserva_tipo_check CHECK (((tipo >= 0) AND (tipo <= 1)))
);


--
-- Name: movimientos; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.movimientos (
    movimiento_id integer NOT NULL,
    almacen smallint,
    cantidad double precision NOT NULL,
    fecha_movimiento timestamp(6) without time zone,
    tipo_movimiento smallint,
    lote_id bigint,
    producto_id character varying(255) NOT NULL,
    transaccion_id integer,
    area_operativa_id integer,
    afecta_inventario boolean DEFAULT true NOT NULL,
    CONSTRAINT movimientos_almacen_check CHECK (((almacen >= 0) AND (almacen <= 3))),
    CONSTRAINT movimientos_tipo_movimiento_check CHECK (((tipo_movimiento >= 0) AND (tipo_movimiento <= 9)))
);


--
-- Name: movimientos_movimiento_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.movimientos_movimiento_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: movimientos_movimiento_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.movimientos_movimiento_id_seq OWNED BY public.movimientos.movimiento_id;


--
-- Name: mps_fabricacion_detalle; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.mps_fabricacion_detalle (
    id bigint NOT NULL,
    mps_id bigint NOT NULL,
    semiterminado_id character varying(255) NOT NULL,
    cantidad numeric(18,4) NOT NULL,
    unidad_medida character varying(20) NOT NULL,
    fecha_inicio timestamp without time zone NOT NULL,
    fecha_final timestamp without time zone NOT NULL,
    observaciones character varying(2000),
    posicion integer NOT NULL,
    CONSTRAINT chk_mps_of_fechas CHECK ((fecha_final >= fecha_inicio)),
    CONSTRAINT mps_fabricacion_detalle_cantidad_check CHECK ((cantidad > (0)::numeric))
);


--
-- Name: mps_fabricacion_detalle_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.mps_fabricacion_detalle_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: mps_fabricacion_detalle_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.mps_fabricacion_detalle_id_seq OWNED BY public.mps_fabricacion_detalle.id;


--
-- Name: mps_fabricacion_semanal; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.mps_fabricacion_semanal (
    id bigint NOT NULL,
    week_start_date date NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    creado_en timestamp without time zone NOT NULL,
    actualizado_en timestamp without time zone NOT NULL,
    creado_por character varying(255) NOT NULL,
    actualizado_por character varying(255) NOT NULL,
    CONSTRAINT chk_mps_of_lunes CHECK ((EXTRACT(isodow FROM week_start_date) = (1)::numeric))
);


--
-- Name: mps_fabricacion_semanal_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.mps_fabricacion_semanal_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: mps_fabricacion_semanal_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.mps_fabricacion_semanal_id_seq OWNED BY public.mps_fabricacion_semanal.id;


--
-- Name: mps_semanal_dia; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.mps_semanal_dia (
    id bigint NOT NULL,
    mps_id integer NOT NULL,
    fecha date NOT NULL,
    day_index integer NOT NULL,
    display_order integer NOT NULL,
    CONSTRAINT ck_mps_sem_dia_day_index CHECK (((day_index >= 0) AND (day_index <= 5)))
);


--
-- Name: mps_semanal_dia_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.mps_semanal_dia_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: mps_semanal_dia_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.mps_semanal_dia_id_seq OWNED BY public.mps_semanal_dia.id;


--
-- Name: mps_semanal_item; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.mps_semanal_item (
    id bigint NOT NULL,
    mps_id integer NOT NULL,
    mps_dia_id bigint NOT NULL,
    terminado_id character varying(255) NOT NULL,
    terminado_nombre character varying(200) NOT NULL,
    categoria_id integer,
    categoria_nombre character varying(200),
    lote_size integer NOT NULL,
    tiempo_dias_fabricacion integer NOT NULL,
    numero_lotes integer NOT NULL,
    cantidad_total double precision NOT NULL,
    fecha_lanzamiento date NOT NULL,
    fecha_final_planificada date NOT NULL,
    observacion text,
    warning text,
    display_order integer NOT NULL,
    estado character varying(20) DEFAULT 'ACTIVO'::character varying NOT NULL,
    CONSTRAINT ck_mps_sem_item_cantidad_total_estado CHECK (((((estado)::text = 'ACTIVO'::text) AND (cantidad_total > (0)::double precision)) OR (((estado)::text = 'CANCELADO'::text) AND (cantidad_total = (0)::double precision)))),
    CONSTRAINT ck_mps_sem_item_estado CHECK (((estado)::text = ANY ((ARRAY['ACTIVO'::character varying, 'CANCELADO'::character varying])::text[]))),
    CONSTRAINT ck_mps_sem_item_lote_size CHECK ((lote_size > 0)),
    CONSTRAINT ck_mps_sem_item_numero_lotes_estado CHECK (((((estado)::text = 'ACTIVO'::text) AND (numero_lotes > 0)) OR (((estado)::text = 'CANCELADO'::text) AND (numero_lotes = 0)))),
    CONSTRAINT ck_mps_sem_item_tiempo_fabricacion CHECK ((tiempo_dias_fabricacion >= 0))
);


--
-- Name: mps_semanal_item_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.mps_semanal_item_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: mps_semanal_item_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.mps_semanal_item_id_seq OWNED BY public.mps_semanal_item.id;


--
-- Name: mps_semanal_lote_planificado; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.mps_semanal_lote_planificado (
    id bigint NOT NULL,
    mps_item_id bigint NOT NULL,
    lote_ordinal integer NOT NULL,
    cantidad_planificada double precision NOT NULL,
    estado character varying(30) DEFAULT 'PENDIENTE_ODP'::character varying NOT NULL,
    CONSTRAINT ck_mps_sem_lote_cantidad CHECK ((cantidad_planificada > (0)::double precision)),
    CONSTRAINT ck_mps_sem_lote_estado CHECK (((estado)::text = ANY ((ARRAY['PENDIENTE_ODP'::character varying, 'ODP_GENERADA'::character varying, 'CANCELADO'::character varying])::text[]))),
    CONSTRAINT ck_mps_sem_lote_ordinal CHECK ((lote_ordinal > 0))
);


--
-- Name: mps_semanal_lote_planificado_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.mps_semanal_lote_planificado_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: mps_semanal_lote_planificado_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.mps_semanal_lote_planificado_id_seq OWNED BY public.mps_semanal_lote_planificado.id;


--
-- Name: mps_semanal_observacion; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.mps_semanal_observacion (
    observacion_id bigint NOT NULL,
    mps_id integer NOT NULL,
    revision_mps integer DEFAULT 1 NOT NULL,
    autor_username character varying(100) NOT NULL,
    mensaje text NOT NULL,
    estado character varying(20) DEFAULT 'ABIERTA'::character varying NOT NULL,
    respuesta_correccion text,
    atendida_por_username character varying(100),
    fecha_atencion timestamp without time zone,
    cerrada_por_username character varying(100),
    fecha_cierre timestamp without time zone,
    fecha_creacion timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    tipo character varying(20) DEFAULT 'BLOQUEANTE'::character varying NOT NULL,
    CONSTRAINT ck_mps_sem_obs_estado CHECK (((estado)::text = ANY ((ARRAY['ABIERTA'::character varying, 'ATENDIDA'::character varying, 'CERRADA'::character varying])::text[]))),
    CONSTRAINT ck_mps_sem_obs_revision_positive CHECK ((revision_mps >= 1)),
    CONSTRAINT ck_mps_sem_obs_tipo CHECK (((tipo)::text = ANY ((ARRAY['BLOQUEANTE'::character varying, 'ADVERTENCIA'::character varying, 'INFORMATIVA'::character varying, 'OTRO'::character varying])::text[])))
);


--
-- Name: mps_semanal_observacion_observacion_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.mps_semanal_observacion_observacion_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: mps_semanal_observacion_observacion_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.mps_semanal_observacion_observacion_id_seq OWNED BY public.mps_semanal_observacion.observacion_id;


--
-- Name: orden_compra; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.orden_compra (
    orden_compra_id integer NOT NULL,
    condicion_pago character varying(255),
    divisas smallint,
    estado integer NOT NULL,
    factura_compra_id integer,
    fecha_emision timestamp(6) without time zone,
    fecha_vencimiento timestamp(6) without time zone,
    ivacop bigint NOT NULL,
    observaciones character varying(255),
    plazo_pago integer NOT NULL,
    sub_total bigint NOT NULL,
    tiempo_entrega character varying(255),
    total_pagar bigint NOT NULL,
    trm double precision NOT NULL,
    proveedor_pk bigint NOT NULL,
    fecha_envio_proveedor timestamp without time zone,
    empresa_identidad_legal_version_id bigint,
    empresa_logo_documental_version_id bigint,
    usuario_creador_id bigint,
    usuario_creador_username character varying(120),
    usuario_liberador_id bigint,
    usuario_liberador_username character varying(120),
    fecha_liberacion timestamp without time zone,
    fecha_recepcion_completa timestamp without time zone,
    fecha_cierre timestamp without time zone,
    origen_cierre character varying(40),
    usuario_cierre_username character varying(120),
    CONSTRAINT orden_compra_divisas_check CHECK (((divisas >= 0) AND (divisas <= 1)))
);


--
-- Name: orden_compra_activo; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.orden_compra_activo (
    orden_compra_activo_id integer NOT NULL,
    condicion_pago character varying(255),
    cotizacion_url character varying(255),
    divisa smallint,
    estado integer NOT NULL,
    factura_compra_activo_id integer,
    fecha_emision timestamp(6) without time zone,
    fecha_vencimiento timestamp(6) without time zone,
    iva double precision NOT NULL,
    plazo_pago integer NOT NULL,
    sub_total double precision NOT NULL,
    tiempo_entrega character varying(255),
    total_pagar double precision NOT NULL,
    trm double precision NOT NULL,
    proveedor_pk bigint,
    empresa_identidad_legal_version_id bigint,
    empresa_logo_documental_version_id bigint,
    CONSTRAINT orden_compra_activo_divisa_check CHECK (((divisa >= 0) AND (divisa <= 1)))
);


--
-- Name: orden_compra_activo_orden_compra_activo_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.orden_compra_activo_orden_compra_activo_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: orden_compra_activo_orden_compra_activo_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.orden_compra_activo_orden_compra_activo_id_seq OWNED BY public.orden_compra_activo.orden_compra_activo_id;


--
-- Name: orden_compra_orden_compra_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.orden_compra_orden_compra_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: orden_compra_orden_compra_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.orden_compra_orden_compra_id_seq OWNED BY public.orden_compra.orden_compra_id;


--
-- Name: orden_fabricacion; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.orden_fabricacion (
    orden_fabricacion_id bigint NOT NULL,
    semiterminado_id character varying(255) NOT NULL,
    manufacturing_version_id bigint NOT NULL,
    estado character varying(30) NOT NULL,
    cantidad_planificada numeric(18,4) NOT NULL,
    unidad_medida character varying(20) NOT NULL,
    fecha_creacion timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    fecha_lanzamiento timestamp without time zone,
    fecha_final_planificada timestamp without time zone,
    fecha_inicio timestamp without time zone,
    fecha_final timestamp without time zone,
    creada_por_id bigint NOT NULL,
    responsable_id bigint,
    observaciones text,
    version bigint DEFAULT 0 NOT NULL,
    orden_produccion_origen_id integer,
    liberada_en timestamp without time zone,
    politica_dispensacion_inicio character varying(30) DEFAULT 'BLOQUEANTE'::character varying NOT NULL,
    fecha_aplicacion_politica_dispensacion timestamp without time zone,
    estado_dispensacion_materiales character varying(40) DEFAULT 'PENDIENTE'::character varying NOT NULL,
    CONSTRAINT chk_of_estado_dispensacion CHECK (((estado_dispensacion_materiales)::text = ANY ((ARRAY['PENDIENTE'::character varying, 'PARCIAL'::character varying, 'COMPLETA'::character varying, 'LIBERADA_SIN_DISPENSACION'::character varying])::text[]))),
    CONSTRAINT chk_of_politica_dispensacion CHECK (((politica_dispensacion_inicio)::text = ANY ((ARRAY['BLOQUEANTE'::character varying, 'NO_BLOQUEANTE'::character varying])::text[]))),
    CONSTRAINT chk_orden_fabricacion_cantidad CHECK ((cantidad_planificada > (0)::numeric)),
    CONSTRAINT chk_orden_fabricacion_estado CHECK (((estado)::text = ANY ((ARRAY['BORRADOR'::character varying, 'PLANIFICADA'::character varying, 'LIBERADA'::character varying, 'EN_EJECUCION'::character varying, 'FABRICACION_COMPLETADA'::character varying, 'CERRADA'::character varying, 'CANCELADA'::character varying])::text[]))),
    CONSTRAINT chk_orden_fabricacion_fechas_plan CHECK (((fecha_final_planificada IS NULL) OR (fecha_lanzamiento IS NULL) OR (fecha_final_planificada >= fecha_lanzamiento))),
    CONSTRAINT chk_orden_fabricacion_fechas_reales CHECK (((fecha_final IS NULL) OR (fecha_inicio IS NULL) OR (fecha_final >= fecha_inicio)))
);


--
-- Name: orden_fabricacion_operacion; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.orden_fabricacion_operacion (
    id bigint NOT NULL,
    orden_fabricacion_id bigint NOT NULL,
    area_operativa_id integer NOT NULL,
    poe_documento_version_id bigint,
    frontend_node_id character varying(255) NOT NULL,
    proceso_produccion_id integer,
    proceso_nombre character varying(200) NOT NULL,
    posicion_secuencia integer NOT NULL,
    estado integer NOT NULL,
    fecha_estado_actual timestamp without time zone NOT NULL,
    fecha_visible timestamp without time zone,
    fecha_completado timestamp without time zone,
    usuario_reporta_id bigint,
    observaciones character varying(500),
    version bigint DEFAULT 0 NOT NULL,
    CONSTRAINT chk_of_operacion_estado CHECK ((estado = ANY (ARRAY[0, 1, 2, 3, 4]))),
    CONSTRAINT chk_of_operacion_secuencia CHECK ((posicion_secuencia >= 0))
);


--
-- Name: orden_fabricacion_operacion_dependencia; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.orden_fabricacion_operacion_dependencia (
    id bigint NOT NULL,
    operacion_predecesora_id bigint CONSTRAINT orden_fabricacion_operacion_d_operacion_predecesora_id_not_null NOT NULL,
    operacion_sucesora_id bigint CONSTRAINT orden_fabricacion_operacion_depe_operacion_sucesora_id_not_null NOT NULL,
    CONSTRAINT chk_of_operacion_dependencia_distinta CHECK ((operacion_predecesora_id <> operacion_sucesora_id))
);


--
-- Name: orden_fabricacion_operacion_dependencia_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.orden_fabricacion_operacion_dependencia_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: orden_fabricacion_operacion_dependencia_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.orden_fabricacion_operacion_dependencia_id_seq OWNED BY public.orden_fabricacion_operacion_dependencia.id;


--
-- Name: orden_fabricacion_operacion_evento; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.orden_fabricacion_operacion_evento (
    id bigint NOT NULL,
    operacion_id bigint NOT NULL,
    estado_origen integer,
    estado_destino integer NOT NULL,
    fecha_evento timestamp without time zone NOT NULL,
    actor_tipo character varying(16) NOT NULL,
    tipo_evento character varying(32) NOT NULL,
    evento_revertido_id bigint,
    usuario_id bigint,
    nota character varying(500),
    CONSTRAINT chk_of_operacion_evento_actor CHECK (((actor_tipo)::text = ANY ((ARRAY['SYSTEM'::character varying, 'USER'::character varying])::text[]))),
    CONSTRAINT chk_of_operacion_evento_estados CHECK ((((estado_origen IS NULL) OR (estado_origen = ANY (ARRAY[0, 1, 2, 3, 4]))) AND (estado_destino = ANY (ARRAY[0, 1, 2, 3, 4])))),
    CONSTRAINT chk_of_operacion_evento_tipo CHECK (((tipo_evento)::text = ANY ((ARRAY['OPERATIVO'::character varying, 'SISTEMA'::character varying, 'CORRECCION_ADMINISTRATIVA'::character varying])::text[])))
);


--
-- Name: orden_fabricacion_operacion_evento_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.orden_fabricacion_operacion_evento_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: orden_fabricacion_operacion_evento_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.orden_fabricacion_operacion_evento_id_seq OWNED BY public.orden_fabricacion_operacion_evento.id;


--
-- Name: orden_fabricacion_operacion_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.orden_fabricacion_operacion_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: orden_fabricacion_operacion_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.orden_fabricacion_operacion_id_seq OWNED BY public.orden_fabricacion_operacion.id;


--
-- Name: orden_fabricacion_orden_fabricacion_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.orden_fabricacion_orden_fabricacion_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: orden_fabricacion_orden_fabricacion_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.orden_fabricacion_orden_fabricacion_id_seq OWNED BY public.orden_fabricacion.orden_fabricacion_id;


--
-- Name: ordenes_produccion; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.ordenes_produccion (
    orden_id integer NOT NULL,
    area_operativa character varying(255),
    cantidad_producir double precision NOT NULL,
    departamento_operativo character varying(255),
    estado_orden integer NOT NULL,
    fecha_creacion timestamp(6) without time zone,
    fecha_final timestamp(6) without time zone,
    fecha_final_planificada timestamp(6) without time zone,
    fecha_inicio timestamp(6) without time zone,
    fecha_lanzamiento timestamp(6) without time zone,
    numero_pedido_comercial character varying(255),
    observaciones character varying(255),
    planificacion_id bigint,
    proceso_completo_id integer,
    producto_id character varying(255),
    responsable_id bigint,
    lote_asignado character varying(50),
    mps_id integer,
    mps_lote_planificado_id bigint,
    ruta_proceso_cat_version_id bigint,
    jornada_laboral_version_id bigint,
    politica_dispensacion_inicio character varying(30) NOT NULL,
    fecha_aplicacion_politica_dispensacion timestamp without time zone,
    estado_dispensacion_materiales character varying(40) NOT NULL,
    manufacturing_version_id bigint,
    cancelada_en timestamp without time zone,
    cancelada_por_id bigint,
    cancelada_por_username character varying(255),
    cancelada_por_nombre_completo character varying(255),
    CONSTRAINT chk_orden_produccion_cancelacion_auditoria CHECK ((((cancelada_en IS NULL) AND (cancelada_por_id IS NULL) AND (cancelada_por_username IS NULL) AND (cancelada_por_nombre_completo IS NULL)) OR ((estado_orden = '-1'::integer) AND (cancelada_en IS NOT NULL) AND (cancelada_por_id IS NOT NULL) AND (cancelada_por_username IS NOT NULL) AND (cancelada_por_nombre_completo IS NOT NULL)))),
    CONSTRAINT chk_ordenes_produccion_estado_dispensacion_materiales CHECK (((estado_dispensacion_materiales)::text = ANY ((ARRAY['PENDIENTE'::character varying, 'PARCIAL'::character varying, 'COMPLETA'::character varying, 'LIBERADA_SIN_DISPENSACION'::character varying])::text[]))),
    CONSTRAINT chk_ordenes_produccion_politica_dispensacion_inicio CHECK (((politica_dispensacion_inicio)::text = ANY ((ARRAY['BLOQUEANTE'::character varying, 'NO_BLOQUEANTE'::character varying])::text[])))
);


--
-- Name: ordenes_produccion_orden_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.ordenes_produccion_orden_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: ordenes_produccion_orden_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.ordenes_produccion_orden_id_seq OWNED BY public.ordenes_produccion.orden_id;


--
-- Name: ordenes_venta; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.ordenes_venta (
    orden_venta_id integer NOT NULL,
    condiciones_pago character varying(255),
    estado character varying(255),
    fecha_creacion timestamp(6) without time zone,
    fecha_entrega_prevista date,
    cliente_id integer
);


--
-- Name: ordenes_venta_orden_venta_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.ordenes_venta_orden_venta_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: ordenes_venta_orden_venta_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.ordenes_venta_orden_venta_id_seq OWNED BY public.ordenes_venta.orden_venta_id;


--
-- Name: organigrama_estado; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.organigrama_estado (
    id smallint NOT NULL,
    revision bigint NOT NULL,
    actualizado_en timestamp without time zone NOT NULL,
    actualizado_por character varying(120),
    CONSTRAINT chk_organigrama_estado_singleton CHECK ((id = 1)),
    CONSTRAINT chk_organigrama_revision_non_negative CHECK ((revision >= 0))
);


--
-- Name: password_reset_tokens; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.password_reset_tokens (
    id bigint NOT NULL,
    expiry_date timestamp(6) without time zone,
    token character varying(255),
    user_id bigint NOT NULL
);


--
-- Name: password_reset_tokens_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.password_reset_tokens_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: password_reset_tokens_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.password_reset_tokens_id_seq OWNED BY public.password_reset_tokens.id;


--
-- Name: periodo_contable; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.periodo_contable (
    id bigint NOT NULL,
    estado character varying(255) NOT NULL,
    fecha_fin date NOT NULL,
    fecha_inicio date NOT NULL,
    nombre character varying(255) NOT NULL,
    CONSTRAINT periodo_contable_estado_check CHECK (((estado)::text = ANY (ARRAY[('ABIERTO'::character varying)::text, ('CERRADO'::character varying)::text])))
);


--
-- Name: periodo_contable_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.periodo_contable_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: periodo_contable_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.periodo_contable_id_seq OWNED BY public.periodo_contable.id;


--
-- Name: planificacion_produccion; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.planificacion_produccion (
    id bigint NOT NULL,
    cantidad_producir double precision,
    estado integer,
    fecha_planificada timestamp(6) without time zone,
    prioridad integer,
    producto_id character varying(255)
);


--
-- Name: planificacion_produccion_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.planificacion_produccion_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: planificacion_produccion_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.planificacion_produccion_id_seq OWNED BY public.planificacion_produccion.id;


--
-- Name: planificacion_recurso; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.planificacion_recurso (
    planificacion_id bigint NOT NULL,
    recurso_id bigint NOT NULL
);


--
-- Name: proceso_fabricacion_edge; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.proceso_fabricacion_edge (
    id bigint NOT NULL,
    frontend_id character varying(255) NOT NULL,
    proceso_completo_id integer NOT NULL,
    source_node_id bigint NOT NULL,
    target_node_id bigint NOT NULL
);


--
-- Name: proceso_fabricacion_edge_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.proceso_fabricacion_edge_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: proceso_fabricacion_edge_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.proceso_fabricacion_edge_id_seq OWNED BY public.proceso_fabricacion_edge.id;


--
-- Name: proceso_fabricacion_nodo; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.proceso_fabricacion_nodo (
    id bigint NOT NULL,
    tipo_nodo character varying(31) NOT NULL,
    frontend_id character varying(255) NOT NULL,
    posicion_x double precision DEFAULT 0 NOT NULL,
    posicion_y double precision DEFAULT 0 NOT NULL,
    label character varying(255),
    proceso_completo_id integer NOT NULL,
    insumo_id integer,
    proceso_id integer,
    area_operativa_id integer
);


--
-- Name: proceso_fabricacion_nodo_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.proceso_fabricacion_nodo_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: proceso_fabricacion_nodo_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.proceso_fabricacion_nodo_id_seq OWNED BY public.proceso_fabricacion_nodo.id;


--
-- Name: proceso_produccion; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.proceso_produccion (
    proceso_id integer NOT NULL,
    batch_size double precision,
    constant_seconds double precision,
    model character varying(255),
    nivel_acceso integer NOT NULL,
    nombre character varying(255) NOT NULL,
    seconds_per_batch double precision,
    seconds_per_unit double precision,
    set_up_time double precision,
    throughput_units_per_sec double precision,
    CONSTRAINT proceso_produccion_model_check CHECK (((model)::text = ANY (ARRAY[('CONSTANT'::character varying)::text, ('THROUGHPUT_RATE'::character varying)::text, ('PER_UNIT'::character varying)::text, ('PER_BATCH'::character varying)::text])))
);


--
-- Name: proceso_produccion_documento_version; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.proceso_produccion_documento_version (
    id bigint NOT NULL,
    proceso_id integer NOT NULL,
    version integer NOT NULL,
    estado character varying(20) NOT NULL,
    nombre_archivo_original character varying(255) CONSTRAINT proceso_produccion_documento_v_nombre_archivo_original_not_null NOT NULL,
    content_type character varying(100) NOT NULL,
    tamano_bytes bigint NOT NULL,
    sha256 character varying(64) NOT NULL,
    storage_provider character varying(30) DEFAULT 'LOCAL_DISK'::character varying NOT NULL,
    storage_key character varying(500) NOT NULL,
    vigente_desde timestamp without time zone NOT NULL,
    vigente_hasta timestamp without time zone,
    creado_en timestamp without time zone NOT NULL,
    creado_por character varying(120),
    motivo_cambio text NOT NULL,
    CONSTRAINT chk_proceso_documento_estado CHECK (((estado)::text = ANY ((ARRAY['VIGENTE'::character varying, 'RETIRADA'::character varying])::text[]))),
    CONSTRAINT chk_proceso_documento_storage_provider CHECK (((storage_provider)::text = 'LOCAL_DISK'::text)),
    CONSTRAINT chk_proceso_documento_tamano CHECK (((tamano_bytes > 0) AND (tamano_bytes <= 2097152))),
    CONSTRAINT chk_proceso_documento_version_positiva CHECK ((version > 0))
);


--
-- Name: proceso_produccion_documento_version_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.proceso_produccion_documento_version_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: proceso_produccion_documento_version_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.proceso_produccion_documento_version_id_seq OWNED BY public.proceso_produccion_documento_version.id;


--
-- Name: proceso_produccion_proceso_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.proceso_produccion_proceso_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: proceso_produccion_proceso_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.proceso_produccion_proceso_id_seq OWNED BY public.proceso_produccion.proceso_id;


--
-- Name: proceso_recurso; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.proceso_recurso (
    id bigint NOT NULL,
    cantidad integer NOT NULL,
    notas character varying(255),
    proceso_id integer,
    recurso_id bigint,
    CONSTRAINT proceso_recurso_cantidad_check CHECK ((cantidad >= 1))
);


--
-- Name: proceso_recurso_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.proceso_recurso_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: proceso_recurso_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.proceso_recurso_id_seq OWNED BY public.proceso_recurso.id;


--
-- Name: procesos_produccion; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.procesos_produccion (
    proceso_completo_id integer NOT NULL,
    rendimiento_teorico double precision,
    producto_id character varying(255),
    cantidad_salida_receta numeric(24,12),
    CONSTRAINT ck_receta_salida CHECK (((cantidad_salida_receta IS NULL) OR (cantidad_salida_receta > (0)::numeric)))
);


--
-- Name: COLUMN procesos_produccion.cantidad_salida_receta; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.procesos_produccion.cantidad_salida_receta IS 'Cantidad obtenida con la receta completa en productos.tipo_unidades. NULL identifica una receta histórica sin salida explícita.';


--
-- Name: procesos_produccion_proceso_completo_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.procesos_produccion_proceso_completo_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: procesos_produccion_proceso_completo_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.procesos_produccion_proceso_completo_id_seq OWNED BY public.procesos_produccion.proceso_completo_id;


--
-- Name: producto_costo_historial; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.producto_costo_historial (
    id bigint NOT NULL,
    producto_id character varying(255) NOT NULL,
    producto_nombre character varying(200),
    tipo_producto character varying(1) NOT NULL,
    version bigint NOT NULL,
    costo_anterior numeric(19,6),
    costo_nuevo numeric(19,6) NOT NULL,
    cambiado_en timestamp without time zone NOT NULL,
    usuario_id bigint,
    usuario_username character varying(120) NOT NULL,
    origen character varying(50) NOT NULL,
    motivo character varying(500),
    referencia character varying(255),
    carga_costos_lote_id uuid,
    CONSTRAINT ck_producto_costo_historial_valores CHECK (((version >= 1) AND ((costo_anterior IS NULL) OR (costo_anterior >= (0)::numeric)) AND (costo_nuevo >= (0)::numeric)))
);


--
-- Name: producto_costo_historial_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.producto_costo_historial_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: producto_costo_historial_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.producto_costo_historial_id_seq OWNED BY public.producto_costo_historial.id;


--
-- Name: productos; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.productos (
    tipo_producto character varying(31) NOT NULL,
    producto_id character varying(255) NOT NULL,
    cantidad_unidad double precision NOT NULL,
    costo numeric(19,6) DEFAULT 0 NOT NULL,
    fecha_creacion timestamp(6) without time zone,
    inventareable boolean,
    iva_percentual double precision NOT NULL,
    nombre character varying(200),
    observaciones character varying(255),
    stock_minimo double precision NOT NULL,
    tipo_unidades character varying(4),
    foto_url character varying(255),
    status integer,
    punto_reorden double precision,
    tipo_material integer,
    case_pack_id bigint,
    categoria_id integer,
    prefijo_lote character varying(20),
    costo_version bigint DEFAULT 0 NOT NULL,
    consumo_directo boolean DEFAULT false NOT NULL,
    requiere_orden_fabricacion boolean DEFAULT false NOT NULL,
    unidad_contenido_por_envase character varying(16),
    CONSTRAINT chk_productos_consumo_directo CHECK (((consumo_directo = false) OR (((tipo_producto)::text = 'M'::text) AND (inventareable = false)))),
    CONSTRAINT ck_productos_costo_no_negativo CHECK ((costo >= (0)::numeric)),
    CONSTRAINT productos_cantidad_unidad_check CHECK ((cantidad_unidad >= (0)::double precision)),
    CONSTRAINT productos_costo_check CHECK (((costo)::double precision >= (0)::double precision))
);


--
-- Name: COLUMN productos.tipo_unidades; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.productos.tipo_unidades IS 'Unidad de inventario del producto; también expresa la cantidad de salida de receta de terminados y semiterminados.';


--
-- Name: proveedor_material_lead_time_metric; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.proveedor_material_lead_time_metric (
    id bigint NOT NULL,
    proveedor_pk bigint NOT NULL,
    producto_id character varying(255) NOT NULL,
    fecha_corte date NOT NULL,
    ventana_dias integer NOT NULL,
    lead_time_mediano_dias double precision CONSTRAINT proveedor_material_lead_time_me_lead_time_mediano_dias_not_null NOT NULL,
    observaciones integer NOT NULL,
    ordenes_consideradas integer CONSTRAINT proveedor_material_lead_time_metr_ordenes_consideradas_not_null NOT NULL,
    calculado_en timestamp without time zone NOT NULL,
    CONSTRAINT chk_proveedor_material_lead_time_metric_lead_time CHECK ((lead_time_mediano_dias >= (0)::double precision)),
    CONSTRAINT chk_proveedor_material_lead_time_metric_observaciones CHECK ((observaciones >= 1)),
    CONSTRAINT chk_proveedor_material_lead_time_metric_ordenes CHECK ((ordenes_consideradas >= observaciones)),
    CONSTRAINT chk_proveedor_material_lead_time_metric_ventana CHECK ((ventana_dias >= 1))
);


--
-- Name: proveedor_material_lead_time_metric_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.proveedor_material_lead_time_metric_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: proveedor_material_lead_time_metric_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.proveedor_material_lead_time_metric_id_seq OWNED BY public.proveedor_material_lead_time_metric.id;


--
-- Name: proveedores; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.proveedores (
    pk bigint NOT NULL,
    camara_url character varying(255),
    categorias integer[],
    ciudad character varying(255),
    condicion_pago character varying(255),
    departamento character varying(255),
    direccion character varying(255),
    fecha_registro timestamp(6) without time zone,
    id character varying(255) NOT NULL,
    nombre character varying(255),
    observacion character varying(255),
    regimen_tributario integer NOT NULL,
    rut_url character varying(255),
    tipo_identificacion integer NOT NULL,
    url character varying(255),
    limite_recepciones_parciales_ocm integer DEFAULT 2 NOT NULL,
    CONSTRAINT proveedores_limite_recepciones_parciales_ocm_check CHECK ((limite_recepciones_parciales_ocm >= 1))
);


--
-- Name: proveedores_pk_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.proveedores_pk_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: proveedores_pk_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.proveedores_pk_seq OWNED BY public.proveedores.pk;


--
-- Name: receta_costos_revision; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.receta_costos_revision (
    id smallint NOT NULL,
    version bigint NOT NULL,
    actualizado_en timestamp without time zone NOT NULL,
    CONSTRAINT ck_receta_costos_revision_singleton CHECK ((id = 1)),
    CONSTRAINT ck_receta_costos_revision_version CHECK ((version >= 1))
);


--
-- Name: recurso_produccion; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.recurso_produccion (
    id bigint NOT NULL,
    cantidad_disponible integer,
    capacidad_por_hora double precision,
    capacidad_total double precision,
    descripcion character varying(255),
    horas_por_turno double precision,
    nombre character varying(255),
    turnos integer
);


--
-- Name: recurso_produccion_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.recurso_produccion_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: recurso_produccion_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.recurso_produccion_id_seq OWNED BY public.recurso_produccion.id;


--
-- Name: registro_hora_extra; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.registro_hora_extra (
    registro_hora_extra_id bigint NOT NULL,
    integrante_id bigint NOT NULL,
    fecha date NOT NULL,
    hora_inicio time without time zone NOT NULL,
    hora_fin time without time zone NOT NULL,
    minutos integer NOT NULL,
    motivo character varying(500) NOT NULL,
    observaciones character varying(1000),
    estado character varying(20) NOT NULL,
    registrado_por_id bigint NOT NULL,
    aprobado_por_id bigint,
    fecha_registro timestamp without time zone NOT NULL,
    fecha_decision timestamp without time zone,
    motivo_rechazo_o_anulacion character varying(1000),
    CONSTRAINT chk_registro_hora_extra_estado CHECK (((estado)::text = ANY ((ARRAY['REGISTRADA'::character varying, 'APROBADA'::character varying, 'RECHAZADA'::character varying, 'ANULADA'::character varying])::text[]))),
    CONSTRAINT chk_registro_hora_extra_minutos CHECK ((minutos > 0)),
    CONSTRAINT chk_registro_hora_extra_rango CHECK ((hora_fin > hora_inicio))
);


--
-- Name: registro_hora_extra_registro_hora_extra_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.registro_hora_extra_registro_hora_extra_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: registro_hora_extra_registro_hora_extra_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.registro_hora_extra_registro_hora_extra_id_seq OWNED BY public.registro_hora_extra.registro_hora_extra_id;


--
-- Name: reporte_produccion_lote; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.reporte_produccion_lote (
    id bigint NOT NULL,
    orden_produccion_id integer NOT NULL,
    lote_id bigint NOT NULL,
    seguimiento_orden_area_id bigint NOT NULL,
    cierre_produccion_id bigint,
    transaccion_almacen_id integer,
    cantidad_reportada numeric(18,4) NOT NULL,
    cantidad_confirmada numeric(18,4),
    fecha_produccion date NOT NULL,
    reportado_en timestamp without time zone NOT NULL,
    reportado_por_id bigint NOT NULL,
    estado character varying(20) NOT NULL,
    motivo_correccion character varying(500),
    estado_orden_anterior integer NOT NULL,
    anulado_en timestamp without time zone,
    anulado_por_id bigint,
    motivo_anulacion character varying(500),
    version bigint DEFAULT 0 NOT NULL,
    CONSTRAINT chk_reporte_produccion_cantidad_confirmada CHECK (((cantidad_confirmada IS NULL) OR (cantidad_confirmada > (0)::numeric))),
    CONSTRAINT chk_reporte_produccion_cantidad_reportada CHECK ((cantidad_reportada > (0)::numeric)),
    CONSTRAINT chk_reporte_produccion_consistencia CHECK (((((estado)::text = 'PENDIENTE'::text) AND (cierre_produccion_id IS NULL) AND (transaccion_almacen_id IS NULL) AND (cantidad_confirmada IS NULL) AND (anulado_en IS NULL) AND (anulado_por_id IS NULL)) OR (((estado)::text = 'CONFIRMADO'::text) AND (cierre_produccion_id IS NOT NULL) AND (transaccion_almacen_id IS NOT NULL) AND (cantidad_confirmada IS NOT NULL) AND (anulado_en IS NULL) AND (anulado_por_id IS NULL)) OR (((estado)::text = 'ANULADO'::text) AND (cierre_produccion_id IS NULL) AND (transaccion_almacen_id IS NULL) AND (cantidad_confirmada IS NULL) AND (anulado_en IS NOT NULL) AND (anulado_por_id IS NOT NULL)))),
    CONSTRAINT chk_reporte_produccion_estado CHECK (((estado)::text = ANY ((ARRAY['PENDIENTE'::character varying, 'CONFIRMADO'::character varying, 'ANULADO'::character varying])::text[])))
);


--
-- Name: reporte_produccion_lote_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.reporte_produccion_lote_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: reporte_produccion_lote_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.reporte_produccion_lote_id_seq OWNED BY public.reporte_produccion_lote.id;


--
-- Name: ruta_proceso_cat; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.ruta_proceso_cat (
    id bigint NOT NULL,
    categoria_id integer NOT NULL,
    fecha_creacion timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    fecha_modificacion timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: ruta_proceso_cat_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.ruta_proceso_cat_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: ruta_proceso_cat_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.ruta_proceso_cat_id_seq OWNED BY public.ruta_proceso_cat.id;


--
-- Name: ruta_proceso_cat_version; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.ruta_proceso_cat_version (
    id bigint NOT NULL,
    ruta_proceso_cat_id bigint NOT NULL,
    version_number integer NOT NULL,
    estado character varying(20) NOT NULL,
    vigente_desde timestamp without time zone NOT NULL,
    vigente_hasta timestamp without time zone,
    creado_en timestamp without time zone NOT NULL,
    creado_por character varying(120),
    motivo_cambio text,
    layout_revision integer DEFAULT 0 NOT NULL,
    layout_actualizado_en timestamp without time zone,
    layout_actualizado_por character varying(120),
    CONSTRAINT chk_ruta_proceso_cat_version_estado CHECK (((estado)::text = ANY ((ARRAY['VIGENTE'::character varying, 'RETIRADA'::character varying])::text[])))
);


--
-- Name: ruta_proceso_cat_version_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.ruta_proceso_cat_version_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: ruta_proceso_cat_version_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.ruta_proceso_cat_version_id_seq OWNED BY public.ruta_proceso_cat_version.id;


--
-- Name: ruta_proceso_edge; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.ruta_proceso_edge (
    id bigint NOT NULL,
    frontend_id character varying(255),
    source_node_id bigint,
    target_node_id bigint,
    ruta_proceso_cat_version_id bigint NOT NULL
);


--
-- Name: ruta_proceso_edge_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.ruta_proceso_edge_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: ruta_proceso_edge_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.ruta_proceso_edge_id_seq OWNED BY public.ruta_proceso_edge.id;


--
-- Name: ruta_proceso_node; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.ruta_proceso_node (
    id bigint NOT NULL,
    frontend_id character varying(255),
    posicion_x double precision DEFAULT 0.0 NOT NULL,
    posicion_y double precision DEFAULT 0.0 NOT NULL,
    area_operativa_id integer,
    label character varying(255),
    has_left_handle boolean DEFAULT true NOT NULL,
    has_right_handle boolean DEFAULT true NOT NULL,
    ruta_proceso_cat_version_id bigint NOT NULL,
    duracion_estimada_minutos integer DEFAULT 0 NOT NULL,
    requiere_jornada_laboral boolean DEFAULT true NOT NULL,
    proceso_produccion_id integer,
    CONSTRAINT chk_ruta_proceso_node_duracion_estimada CHECK ((duracion_estimada_minutos >= 0))
);


--
-- Name: ruta_proceso_node_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.ruta_proceso_node_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: ruta_proceso_node_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.ruta_proceso_node_id_seq OWNED BY public.ruta_proceso_node.id;


--
-- Name: seguimiento_orden_area; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.seguimiento_orden_area (
    id bigint NOT NULL,
    orden_produccion_id integer NOT NULL,
    ruta_proceso_node_id bigint NOT NULL,
    area_operativa_id integer NOT NULL,
    estado integer DEFAULT 0 NOT NULL,
    posicion_secuencia integer,
    fecha_creacion timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    fecha_visible timestamp without time zone,
    fecha_completado timestamp without time zone,
    usuario_reporta_id bigint,
    observaciones character varying(500),
    fecha_estado_actual timestamp without time zone NOT NULL,
    duracion_estimada_minutos integer DEFAULT 0 NOT NULL,
    requiere_jornada_laboral boolean DEFAULT true NOT NULL,
    poe_documento_version_id bigint,
    CONSTRAINT chk_seguimiento_orden_area_duracion_estimada CHECK ((duracion_estimada_minutos >= 0))
);


--
-- Name: seguimiento_orden_area_evento; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.seguimiento_orden_area_evento (
    id bigint NOT NULL,
    seguimiento_orden_area_id bigint CONSTRAINT seguimiento_orden_area_event_seguimiento_orden_area_id_not_null NOT NULL,
    estado_origen integer,
    estado_destino integer NOT NULL,
    fecha_evento timestamp without time zone NOT NULL,
    actor_tipo character varying(16) NOT NULL,
    usuario_id bigint,
    nota character varying(500),
    tipo_evento character varying(32) NOT NULL,
    evento_revertido_id bigint
);


--
-- Name: seguimiento_orden_area_evento_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.seguimiento_orden_area_evento_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: seguimiento_orden_area_evento_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.seguimiento_orden_area_evento_id_seq OWNED BY public.seguimiento_orden_area_evento.id;


--
-- Name: seguimiento_orden_area_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.seguimiento_orden_area_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: seguimiento_orden_area_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.seguimiento_orden_area_id_seq OWNED BY public.seguimiento_orden_area.id;


--
-- Name: semana_mps; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.semana_mps (
    id bigint NOT NULL,
    codigo character varying(20) NOT NULL,
    anio_semana integer NOT NULL,
    numero_semana integer NOT NULL,
    start_date date NOT NULL,
    end_date date NOT NULL,
    standard character varying(40) NOT NULL
);


--
-- Name: semana_mps_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.semana_mps_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: semana_mps_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.semana_mps_id_seq OWNED BY public.semana_mps.id;


--
-- Name: super_master_config; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.super_master_config (
    id bigint NOT NULL,
    habilitar_ajustes_inventario boolean NOT NULL,
    habilitar_carga_masiva boolean NOT NULL,
    habilitar_eliminacion_forzada boolean NOT NULL
);


--
-- Name: super_master_config_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.super_master_config_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: super_master_config_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.super_master_config_id_seq OWNED BY public.super_master_config.id;


--
-- Name: super_master_verification_codes; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.super_master_verification_codes (
    id bigint NOT NULL,
    code character varying(6) NOT NULL,
    email character varying(255) NOT NULL,
    expiry_date timestamp(6) without time zone
);


--
-- Name: super_master_verification_codes_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.super_master_verification_codes_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: super_master_verification_codes_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.super_master_verification_codes_id_seq OWNED BY public.super_master_verification_codes.id;


--
-- Name: tab_accesos; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.tab_accesos (
    id bigint NOT NULL,
    modulo_acceso_id bigint NOT NULL,
    tab_id character varying(128) NOT NULL,
    nivel integer NOT NULL
);


--
-- Name: tab_accesos_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.tab_accesos_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: tab_accesos_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.tab_accesos_id_seq OWNED BY public.tab_accesos.id;


--
-- Name: tarea; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.tarea (
    id bigint NOT NULL,
    descripcion_tarea character varying(255),
    fecha_asignacion timestamp(6) without time zone,
    fecha_cierre timestamp(6) without time zone,
    fecha_reporte_cumplimiento timestamp(6) without time zone,
    usuario_responsable_id bigint,
    usuarios_seguimiento bigint
);


--
-- Name: tarea_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.tarea_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: tarea_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.tarea_id_seq OWNED BY public.tarea.id;


--
-- Name: transaccion_almacen; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.transaccion_almacen (
    transaccion_id integer NOT NULL,
    estado_contable character varying(255),
    fecha_transaccion timestamp(6) without time zone,
    id_entidad_causante integer NOT NULL,
    observaciones character varying(255),
    tipo_entidad_causante smallint,
    url_doc_soporte character varying(255),
    asiento_contable_id bigint,
    usuario_aprobador_id bigint,
    causa_ajuste character varying(64),
    CONSTRAINT transaccion_almacen_causa_ajuste_check CHECK (((causa_ajuste IS NULL) OR ((causa_ajuste)::text = ANY ((ARRAY['PRODUCCION_CONTINGENCIA'::character varying, 'DIFERENCIA_CONTEO'::character varying, 'MERMA_DANO_PERDIDA'::character varying, 'CORRECCION_REGISTRO'::character varying, 'OTRA_REGULARIZACION'::character varying])::text[])))),
    CONSTRAINT transaccion_almacen_estado_contable_check CHECK (((estado_contable)::text = ANY (ARRAY[('PENDIENTE'::character varying)::text, ('CONTABILIZADA'::character varying)::text, ('NO_APLICA'::character varying)::text]))),
    CONSTRAINT transaccion_almacen_tipo_entidad_causante_check CHECK (((tipo_entidad_causante >= 0) AND (tipo_entidad_causante <= 12)))
);


--
-- Name: transaccion_almacen_transaccion_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.transaccion_almacen_transaccion_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: transaccion_almacen_transaccion_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.transaccion_almacen_transaccion_id_seq OWNED BY public.transaccion_almacen.transaccion_id;


--
-- Name: transaccion_almacen_usuarios_realizadores; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.transaccion_almacen_usuarios_realizadores (
    transaccion_id integer CONSTRAINT transaccion_almacen_usuarios_realizador_transaccion_id_not_null NOT NULL,
    usuario_id bigint NOT NULL
);


--
-- Name: traslado_activo; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.traslado_activo (
    id bigint NOT NULL,
    fecha_traslado timestamp(6) without time zone NOT NULL,
    motivo character varying(255),
    observaciones character varying(500),
    ubicacion_destino character varying(255) NOT NULL,
    ubicacion_origen character varying(255),
    activo_id character varying(255) NOT NULL,
    responsable_anterior_id bigint,
    responsable_nuevo_id bigint NOT NULL
);


--
-- Name: traslado_activo_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.traslado_activo_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: traslado_activo_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.traslado_activo_id_seq OWNED BY public.traslado_activo.id;


--
-- Name: unidad_medida_area_operativa; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.unidad_medida_area_operativa (
    unidad_medida_area_operativa_id bigint CONSTRAINT unidad_medida_area_operativ_unidad_medida_area_operati_not_null NOT NULL,
    area_operativa_id integer NOT NULL,
    nombre character varying(120) NOT NULL,
    unidad_relacion character varying(16) CONSTRAINT unidad_medida_area_operativa_unidad_referencia_not_null NOT NULL,
    relacion_estandar numeric(19,6) CONSTRAINT unidad_medida_area_operativa_factor_a_referencia_not_null NOT NULL,
    CONSTRAINT chk_umao_relacion_estandar_positive CHECK ((relacion_estandar > (0)::numeric)),
    CONSTRAINT chk_umao_unidad_relacion CHECK (((unidad_relacion)::text = ANY ((ARRAY['ML'::character varying, 'L'::character varying, 'G'::character varying, 'KG'::character varying, 'U'::character varying])::text[])))
);


--
-- Name: unidad_medida_area_operativa_unidad_medida_area_operativa_i_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.unidad_medida_area_operativa_unidad_medida_area_operativa_i_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: unidad_medida_area_operativa_unidad_medida_area_operativa_i_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.unidad_medida_area_operativa_unidad_medida_area_operativa_i_seq OWNED BY public.unidad_medida_area_operativa.unidad_medida_area_operativa_id;


--
-- Name: unidad_medida_producto; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.unidad_medida_producto (
    codigo character varying(16) NOT NULL,
    simbolo character varying(16) NOT NULL,
    magnitud character varying(16) NOT NULL,
    factor_a_referencia numeric(24,12) NOT NULL,
    CONSTRAINT ck_ump_codigo CHECK (((codigo)::text ~ '^[A-Z][A-Z0-9_]*$'::text)),
    CONSTRAINT ck_ump_factor CHECK ((factor_a_referencia > (0)::numeric)),
    CONSTRAINT ck_ump_magnitud CHECK (((magnitud)::text = ANY ((ARRAY['MASA'::character varying, 'VOLUMEN'::character varying, 'CONTEO'::character varying])::text[]))),
    CONSTRAINT ck_ump_simbolo CHECK ((btrim((simbolo)::text) <> ''::text))
);


--
-- Name: COLUMN unidad_medida_producto.factor_a_referencia; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.unidad_medida_producto.factor_a_referencia IS 'Cantidad en kg (MASA), L (VOLUMEN) o U (CONTEO) equivalente a una unidad del código.';


--
-- Name: users; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.users (
    id bigint NOT NULL,
    cedula bigint,
    cel character varying(255),
    direccion character varying(255),
    email character varying(255),
    estado integer NOT NULL,
    fecha_nacimiento date,
    nombre_completo character varying(255),
    password character varying(255),
    username character varying(255)
);


--
-- Name: users_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.users_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: users_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.users_id_seq OWNED BY public.users.id;


--
-- Name: vendedores; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.vendedores (
    cedula bigint NOT NULL,
    apellidos character varying(255) NOT NULL,
    direccion character varying(255),
    email character varying(255),
    fecha_nacimiento date,
    nombres character varying(255) NOT NULL,
    telefono character varying(255),
    user_id bigint NOT NULL
);


--
-- Name: area_operativa area_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.area_operativa ALTER COLUMN area_id SET DEFAULT nextval('public.area_produccion_area_id_seq'::regclass);


--
-- Name: area_operativa_categoria_unidad_medida area_operativa_categoria_unidad_medida_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.area_operativa_categoria_unidad_medida ALTER COLUMN area_operativa_categoria_unidad_medida_id SET DEFAULT nextval('public.area_operativa_categoria_unid_area_operativa_categoria_unid_seq'::regclass);


--
-- Name: area_operativa_ruido_muestra id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.area_operativa_ruido_muestra ALTER COLUMN id SET DEFAULT nextval('public.area_operativa_ruido_muestra_id_seq'::regclass);


--
-- Name: asiento_contable id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.asiento_contable ALTER COLUMN id SET DEFAULT nextval('public.asiento_contable_id_seq'::regclass);


--
-- Name: avance_tarea id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.avance_tarea ALTER COLUMN id SET DEFAULT nextval('public.avance_tarea_id_seq'::regclass);


--
-- Name: batch_record id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.batch_record ALTER COLUMN id SET DEFAULT nextval('public.batch_record_id_seq'::regclass);


--
-- Name: batch_record_ciclo_revision id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.batch_record_ciclo_revision ALTER COLUMN id SET DEFAULT nextval('public.batch_record_ciclo_revision_id_seq'::regclass);


--
-- Name: batch_record_consumo id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.batch_record_consumo ALTER COLUMN id SET DEFAULT nextval('public.batch_record_consumo_id_seq'::regclass);


--
-- Name: batch_record_correccion id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.batch_record_correccion ALTER COLUMN id SET DEFAULT nextval('public.batch_record_correccion_id_seq'::regclass);


--
-- Name: batch_record_decision_calidad id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.batch_record_decision_calidad ALTER COLUMN id SET DEFAULT nextval('public.batch_record_decision_calidad_id_seq'::regclass);


--
-- Name: batch_record_desviacion id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.batch_record_desviacion ALTER COLUMN id SET DEFAULT nextval('public.batch_record_desviacion_id_seq'::regclass);


--
-- Name: batch_record_documento id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.batch_record_documento ALTER COLUMN id SET DEFAULT nextval('public.batch_record_documento_id_seq'::regclass);


--
-- Name: batch_record_etapa id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.batch_record_etapa ALTER COLUMN id SET DEFAULT nextval('public.batch_record_etapa_id_seq'::regclass);


--
-- Name: batch_record_firma id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.batch_record_firma ALTER COLUMN id SET DEFAULT nextval('public.batch_record_firma_id_seq'::regclass);


--
-- Name: batch_record_projection_task id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.batch_record_projection_task ALTER COLUMN id SET DEFAULT nextval('public.batch_record_projection_task_id_seq'::regclass);


--
-- Name: batch_record_revision id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.batch_record_revision ALTER COLUMN id SET DEFAULT nextval('public.batch_record_revision_id_seq'::regclass);


--
-- Name: batch_record_seccion_correccion id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.batch_record_seccion_correccion ALTER COLUMN id SET DEFAULT nextval('public.batch_record_seccion_correccion_id_seq'::regclass);


--
-- Name: batch_record_solicitud_reapertura_rechazo id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.batch_record_solicitud_reapertura_rechazo ALTER COLUMN id SET DEFAULT nextval('public.batch_record_solicitud_reapertura_rechazo_id_seq'::regclass);


--
-- Name: calidad_control_proceso_caracteristica id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.calidad_control_proceso_caracteristica ALTER COLUMN id SET DEFAULT nextval('public.calidad_control_proceso_caracteristica_id_seq'::regclass);


--
-- Name: calidad_control_proceso_ejecucion id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.calidad_control_proceso_ejecucion ALTER COLUMN id SET DEFAULT nextval('public.calidad_control_proceso_ejecucion_id_seq'::regclass);


--
-- Name: calidad_control_proceso_lectura id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.calidad_control_proceso_lectura ALTER COLUMN id SET DEFAULT nextval('public.calidad_control_proceso_lectura_id_seq'::regclass);


--
-- Name: calidad_control_proceso_muestra id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.calidad_control_proceso_muestra ALTER COLUMN id SET DEFAULT nextval('public.calidad_control_proceso_muestra_id_seq'::regclass);


--
-- Name: calidad_control_proceso_plantilla id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.calidad_control_proceso_plantilla ALTER COLUMN id SET DEFAULT nextval('public.calidad_control_proceso_plantilla_id_seq'::regclass);


--
-- Name: carga_costos_item id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.carga_costos_item ALTER COLUMN id SET DEFAULT nextval('public.carga_costos_item_id_seq'::regclass);


--
-- Name: carga_costos_propagacion_item id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.carga_costos_propagacion_item ALTER COLUMN id SET DEFAULT nextval('public.carga_costos_propagacion_item_id_seq'::regclass);


--
-- Name: case_pack id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.case_pack ALTER COLUMN id SET DEFAULT nextval('public.case_pack_id_seq'::regclass);


--
-- Name: cierre_produccion id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cierre_produccion ALTER COLUMN id SET DEFAULT nextval('public.cierre_produccion_id_seq'::regclass);


--
-- Name: clientes cliente_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.clientes ALTER COLUMN cliente_id SET DEFAULT nextval('public.clientes_cliente_id_seq'::regclass);


--
-- Name: contacto_proveedor contacto_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.contacto_proveedor ALTER COLUMN contacto_id SET DEFAULT nextval('public.contacto_proveedor_contacto_id_seq'::regclass);


--
-- Name: control_desviacion id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.control_desviacion ALTER COLUMN id SET DEFAULT nextval('public.control_desviacion_id_seq'::regclass);


--
-- Name: control_ejecucion id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.control_ejecucion ALTER COLUMN id SET DEFAULT nextval('public.control_ejecucion_id_seq'::regclass);


--
-- Name: control_ejecucion_lectura id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.control_ejecucion_lectura ALTER COLUMN id SET DEFAULT nextval('public.control_ejecucion_lectura_id_seq'::regclass);


--
-- Name: control_ejecucion_muestra id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.control_ejecucion_muestra ALTER COLUMN id SET DEFAULT nextval('public.control_ejecucion_muestra_id_seq'::regclass);


--
-- Name: control_idempotencia id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.control_idempotencia ALTER COLUMN id SET DEFAULT nextval('public.control_idempotencia_id_seq'::regclass);


--
-- Name: control_magnitud id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.control_magnitud ALTER COLUMN id SET DEFAULT nextval('public.control_magnitud_id_seq'::regclass);


--
-- Name: control_plan id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.control_plan ALTER COLUMN id SET DEFAULT nextval('public.control_plan_id_seq'::regclass);


--
-- Name: control_plan_aplicabilidad id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.control_plan_aplicabilidad ALTER COLUMN id SET DEFAULT nextval('public.control_plan_aplicabilidad_id_seq'::regclass);


--
-- Name: control_plan_caracteristica id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.control_plan_caracteristica ALTER COLUMN id SET DEFAULT nextval('public.control_plan_caracteristica_id_seq'::regclass);


--
-- Name: control_plan_version id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.control_plan_version ALTER COLUMN id SET DEFAULT nextval('public.control_plan_version_id_seq'::regclass);


--
-- Name: control_requerido id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.control_requerido ALTER COLUMN id SET DEFAULT nextval('public.control_requerido_id_seq'::regclass);


--
-- Name: control_revalidacion id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.control_revalidacion ALTER COLUMN id SET DEFAULT nextval('public.control_revalidacion_id_seq'::regclass);


--
-- Name: control_unidad id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.control_unidad ALTER COLUMN id SET DEFAULT nextval('public.control_unidad_id_seq'::regclass);


--
-- Name: depreciacion_activo id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.depreciacion_activo ALTER COLUMN id SET DEFAULT nextval('public.depreciacion_activo_id_seq'::regclass);


--
-- Name: doc_tran_de_personal id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.doc_tran_de_personal ALTER COLUMN id SET DEFAULT nextval('public.doc_tran_de_personal_id_seq'::regclass);


--
-- Name: documento_baja_activo baja_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.documento_baja_activo ALTER COLUMN baja_id SET DEFAULT nextval('public.documento_baja_activo_baja_id_seq'::regclass);


--
-- Name: empresa_identidad_legal_version id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.empresa_identidad_legal_version ALTER COLUMN id SET DEFAULT nextval('public.empresa_identidad_legal_version_id_seq'::regclass);


--
-- Name: empresa_logo_documental_version id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.empresa_logo_documental_version ALTER COLUMN id SET DEFAULT nextval('public.empresa_logo_documental_version_id_seq'::regclass);


--
-- Name: factura_compra_activo factura_compra_activo_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.factura_compra_activo ALTER COLUMN factura_compra_activo_id SET DEFAULT nextval('public.factura_compra_activo_factura_compra_activo_id_seq'::regclass);


--
-- Name: facturas_compras factura_compra_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.facturas_compras ALTER COLUMN factura_compra_id SET DEFAULT nextval('public.facturas_compras_factura_compra_id_seq'::regclass);


--
-- Name: facturas_venta factura_venta_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.facturas_venta ALTER COLUMN factura_venta_id SET DEFAULT nextval('public.facturas_venta_factura_venta_id_seq'::regclass);


--
-- Name: firma_visual_usuario_version id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.firma_visual_usuario_version ALTER COLUMN id SET DEFAULT nextval('public.firma_visual_usuario_version_id_seq'::regclass);


--
-- Name: incorporacion_activo_header incorporacion_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.incorporacion_activo_header ALTER COLUMN incorporacion_id SET DEFAULT nextval('public.incorporacion_activo_header_incorporacion_id_seq'::regclass);


--
-- Name: incorporacion_activo_line linea_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.incorporacion_activo_line ALTER COLUMN linea_id SET DEFAULT nextval('public.incorporacion_activo_line_linea_id_seq'::regclass);


--
-- Name: insumos insumo_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.insumos ALTER COLUMN insumo_id SET DEFAULT nextval('public.insumos_insumo_id_seq'::regclass);


--
-- Name: insumos_empaque id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.insumos_empaque ALTER COLUMN id SET DEFAULT nextval('public.insumos_empaque_id_seq'::regclass);


--
-- Name: item_orden_compra item_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.item_orden_compra ALTER COLUMN item_id SET DEFAULT nextval('public.item_orden_compra_item_id_seq'::regclass);


--
-- Name: item_orden_compra_activo item_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.item_orden_compra_activo ALTER COLUMN item_id SET DEFAULT nextval('public.item_orden_compra_activo_item_id_seq'::regclass);


--
-- Name: items_factura_compra item_compra_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.items_factura_compra ALTER COLUMN item_compra_id SET DEFAULT nextval('public.items_factura_compra_item_compra_id_seq'::regclass);


--
-- Name: items_factura_venta item_factura_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.items_factura_venta ALTER COLUMN item_factura_id SET DEFAULT nextval('public.items_factura_venta_item_factura_id_seq'::regclass);


--
-- Name: items_orden_venta item_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.items_orden_venta ALTER COLUMN item_id SET DEFAULT nextval('public.items_orden_venta_item_id_seq'::regclass);


--
-- Name: jornada_laboral_bloque id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.jornada_laboral_bloque ALTER COLUMN id SET DEFAULT nextval('public.jornada_laboral_bloque_id_seq'::regclass);


--
-- Name: jornada_laboral_version id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.jornada_laboral_version ALTER COLUMN id SET DEFAULT nextval('public.jornada_laboral_version_id_seq'::regclass);


--
-- Name: lead_time_proveedor_kpi id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lead_time_proveedor_kpi ALTER COLUMN id SET DEFAULT nextval('public.lead_time_proveedor_kpi_id_seq'::regclass);


--
-- Name: linea_asiento_contable id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.linea_asiento_contable ALTER COLUMN id SET DEFAULT nextval('public.linea_asiento_contable_id_seq'::regclass);


--
-- Name: lote id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lote ALTER COLUMN id SET DEFAULT nextval('public.lote_id_seq'::regclass);


--
-- Name: mantenimiento_activo id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.mantenimiento_activo ALTER COLUMN id SET DEFAULT nextval('public.mantenimiento_activo_id_seq'::regclass);


--
-- Name: manufacturing_versions id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.manufacturing_versions ALTER COLUMN id SET DEFAULT nextval('public.manufacturing_versions_id_seq'::regclass);


--
-- Name: master_directive id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.master_directive ALTER COLUMN id SET DEFAULT nextval('public.master_directive_id_seq'::regclass);


--
-- Name: master_production_schedule_semanal mps_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.master_production_schedule_semanal ALTER COLUMN mps_id SET DEFAULT nextval('public.master_production_schedule_semanal_mps_id_seq'::regclass);


--
-- Name: material_ficha_tecnica_version id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.material_ficha_tecnica_version ALTER COLUMN id SET DEFAULT nextval('public.material_ficha_tecnica_version_id_seq'::regclass);


--
-- Name: mision_vision_valor id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.mision_vision_valor ALTER COLUMN id SET DEFAULT nextval('public.mision_vision_valor_id_seq'::regclass);


--
-- Name: mision_vision_version id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.mision_vision_version ALTER COLUMN id SET DEFAULT nextval('public.mision_vision_version_id_seq'::regclass);


--
-- Name: modulo_accesos id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.modulo_accesos ALTER COLUMN id SET DEFAULT nextval('public.modulo_accesos_id_seq'::regclass);


--
-- Name: movimientos movimiento_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.movimientos ALTER COLUMN movimiento_id SET DEFAULT nextval('public.movimientos_movimiento_id_seq'::regclass);


--
-- Name: mps_fabricacion_detalle id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.mps_fabricacion_detalle ALTER COLUMN id SET DEFAULT nextval('public.mps_fabricacion_detalle_id_seq'::regclass);


--
-- Name: mps_fabricacion_semanal id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.mps_fabricacion_semanal ALTER COLUMN id SET DEFAULT nextval('public.mps_fabricacion_semanal_id_seq'::regclass);


--
-- Name: mps_semanal_dia id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.mps_semanal_dia ALTER COLUMN id SET DEFAULT nextval('public.mps_semanal_dia_id_seq'::regclass);


--
-- Name: mps_semanal_item id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.mps_semanal_item ALTER COLUMN id SET DEFAULT nextval('public.mps_semanal_item_id_seq'::regclass);


--
-- Name: mps_semanal_lote_planificado id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.mps_semanal_lote_planificado ALTER COLUMN id SET DEFAULT nextval('public.mps_semanal_lote_planificado_id_seq'::regclass);


--
-- Name: mps_semanal_observacion observacion_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.mps_semanal_observacion ALTER COLUMN observacion_id SET DEFAULT nextval('public.mps_semanal_observacion_observacion_id_seq'::regclass);


--
-- Name: orden_compra orden_compra_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.orden_compra ALTER COLUMN orden_compra_id SET DEFAULT nextval('public.orden_compra_orden_compra_id_seq'::regclass);


--
-- Name: orden_compra_activo orden_compra_activo_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.orden_compra_activo ALTER COLUMN orden_compra_activo_id SET DEFAULT nextval('public.orden_compra_activo_orden_compra_activo_id_seq'::regclass);


--
-- Name: orden_fabricacion orden_fabricacion_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.orden_fabricacion ALTER COLUMN orden_fabricacion_id SET DEFAULT nextval('public.orden_fabricacion_orden_fabricacion_id_seq'::regclass);


--
-- Name: orden_fabricacion_operacion id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.orden_fabricacion_operacion ALTER COLUMN id SET DEFAULT nextval('public.orden_fabricacion_operacion_id_seq'::regclass);


--
-- Name: orden_fabricacion_operacion_dependencia id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.orden_fabricacion_operacion_dependencia ALTER COLUMN id SET DEFAULT nextval('public.orden_fabricacion_operacion_dependencia_id_seq'::regclass);


--
-- Name: orden_fabricacion_operacion_evento id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.orden_fabricacion_operacion_evento ALTER COLUMN id SET DEFAULT nextval('public.orden_fabricacion_operacion_evento_id_seq'::regclass);


--
-- Name: ordenes_produccion orden_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ordenes_produccion ALTER COLUMN orden_id SET DEFAULT nextval('public.ordenes_produccion_orden_id_seq'::regclass);


--
-- Name: ordenes_venta orden_venta_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ordenes_venta ALTER COLUMN orden_venta_id SET DEFAULT nextval('public.ordenes_venta_orden_venta_id_seq'::regclass);


--
-- Name: password_reset_tokens id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.password_reset_tokens ALTER COLUMN id SET DEFAULT nextval('public.password_reset_tokens_id_seq'::regclass);


--
-- Name: periodo_contable id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.periodo_contable ALTER COLUMN id SET DEFAULT nextval('public.periodo_contable_id_seq'::regclass);


--
-- Name: planificacion_produccion id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.planificacion_produccion ALTER COLUMN id SET DEFAULT nextval('public.planificacion_produccion_id_seq'::regclass);


--
-- Name: proceso_fabricacion_edge id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.proceso_fabricacion_edge ALTER COLUMN id SET DEFAULT nextval('public.proceso_fabricacion_edge_id_seq'::regclass);


--
-- Name: proceso_fabricacion_nodo id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.proceso_fabricacion_nodo ALTER COLUMN id SET DEFAULT nextval('public.proceso_fabricacion_nodo_id_seq'::regclass);


--
-- Name: proceso_produccion proceso_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.proceso_produccion ALTER COLUMN proceso_id SET DEFAULT nextval('public.proceso_produccion_proceso_id_seq'::regclass);


--
-- Name: proceso_produccion_documento_version id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.proceso_produccion_documento_version ALTER COLUMN id SET DEFAULT nextval('public.proceso_produccion_documento_version_id_seq'::regclass);


--
-- Name: proceso_recurso id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.proceso_recurso ALTER COLUMN id SET DEFAULT nextval('public.proceso_recurso_id_seq'::regclass);


--
-- Name: procesos_produccion proceso_completo_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.procesos_produccion ALTER COLUMN proceso_completo_id SET DEFAULT nextval('public.procesos_produccion_proceso_completo_id_seq'::regclass);


--
-- Name: producto_costo_historial id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.producto_costo_historial ALTER COLUMN id SET DEFAULT nextval('public.producto_costo_historial_id_seq'::regclass);


--
-- Name: proveedor_material_lead_time_metric id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.proveedor_material_lead_time_metric ALTER COLUMN id SET DEFAULT nextval('public.proveedor_material_lead_time_metric_id_seq'::regclass);


--
-- Name: proveedores pk; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.proveedores ALTER COLUMN pk SET DEFAULT nextval('public.proveedores_pk_seq'::regclass);


--
-- Name: recurso_produccion id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.recurso_produccion ALTER COLUMN id SET DEFAULT nextval('public.recurso_produccion_id_seq'::regclass);


--
-- Name: registro_hora_extra registro_hora_extra_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.registro_hora_extra ALTER COLUMN registro_hora_extra_id SET DEFAULT nextval('public.registro_hora_extra_registro_hora_extra_id_seq'::regclass);


--
-- Name: reporte_produccion_lote id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reporte_produccion_lote ALTER COLUMN id SET DEFAULT nextval('public.reporte_produccion_lote_id_seq'::regclass);


--
-- Name: ruta_proceso_cat id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ruta_proceso_cat ALTER COLUMN id SET DEFAULT nextval('public.ruta_proceso_cat_id_seq'::regclass);


--
-- Name: ruta_proceso_cat_version id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ruta_proceso_cat_version ALTER COLUMN id SET DEFAULT nextval('public.ruta_proceso_cat_version_id_seq'::regclass);


--
-- Name: ruta_proceso_edge id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ruta_proceso_edge ALTER COLUMN id SET DEFAULT nextval('public.ruta_proceso_edge_id_seq'::regclass);


--
-- Name: ruta_proceso_node id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ruta_proceso_node ALTER COLUMN id SET DEFAULT nextval('public.ruta_proceso_node_id_seq'::regclass);


--
-- Name: seguimiento_orden_area id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.seguimiento_orden_area ALTER COLUMN id SET DEFAULT nextval('public.seguimiento_orden_area_id_seq'::regclass);


--
-- Name: seguimiento_orden_area_evento id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.seguimiento_orden_area_evento ALTER COLUMN id SET DEFAULT nextval('public.seguimiento_orden_area_evento_id_seq'::regclass);


--
-- Name: semana_mps id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.semana_mps ALTER COLUMN id SET DEFAULT nextval('public.semana_mps_id_seq'::regclass);


--
-- Name: super_master_config id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.super_master_config ALTER COLUMN id SET DEFAULT nextval('public.super_master_config_id_seq'::regclass);


--
-- Name: super_master_verification_codes id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.super_master_verification_codes ALTER COLUMN id SET DEFAULT nextval('public.super_master_verification_codes_id_seq'::regclass);


--
-- Name: tab_accesos id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tab_accesos ALTER COLUMN id SET DEFAULT nextval('public.tab_accesos_id_seq'::regclass);


--
-- Name: tarea id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tarea ALTER COLUMN id SET DEFAULT nextval('public.tarea_id_seq'::regclass);


--
-- Name: transaccion_almacen transaccion_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.transaccion_almacen ALTER COLUMN transaccion_id SET DEFAULT nextval('public.transaccion_almacen_transaccion_id_seq'::regclass);


--
-- Name: traslado_activo id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.traslado_activo ALTER COLUMN id SET DEFAULT nextval('public.traslado_activo_id_seq'::regclass);


--
-- Name: unidad_medida_area_operativa unidad_medida_area_operativa_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.unidad_medida_area_operativa ALTER COLUMN unidad_medida_area_operativa_id SET DEFAULT nextval('public.unidad_medida_area_operativa_unidad_medida_area_operativa_i_seq'::regclass);


--
-- Name: users id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users ALTER COLUMN id SET DEFAULT nextval('public.users_id_seq'::regclass);


--
-- Name: activo activo_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.activo
    ADD CONSTRAINT activo_pkey PRIMARY KEY (id);


--
-- Name: area_operativa_categoria_unidad_medida area_operativa_categoria_unidad_medida_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.area_operativa_categoria_unidad_medida
    ADD CONSTRAINT area_operativa_categoria_unidad_medida_pkey PRIMARY KEY (area_operativa_categoria_unidad_medida_id);


--
-- Name: area_operativa_ruido_muestra area_operativa_ruido_muestra_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.area_operativa_ruido_muestra
    ADD CONSTRAINT area_operativa_ruido_muestra_pkey PRIMARY KEY (id);


--
-- Name: area_operativa area_produccion_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.area_operativa
    ADD CONSTRAINT area_produccion_pkey PRIMARY KEY (area_id);


--
-- Name: asiento_contable asiento_contable_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.asiento_contable
    ADD CONSTRAINT asiento_contable_pkey PRIMARY KEY (id);


--
-- Name: avance_tarea avance_tarea_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.avance_tarea
    ADD CONSTRAINT avance_tarea_pkey PRIMARY KEY (id);


--
-- Name: batch_record_ciclo_revision batch_record_ciclo_revision_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.batch_record_ciclo_revision
    ADD CONSTRAINT batch_record_ciclo_revision_pkey PRIMARY KEY (id);


--
-- Name: batch_record batch_record_codigo_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.batch_record
    ADD CONSTRAINT batch_record_codigo_key UNIQUE (codigo);


--
-- Name: batch_record_consumo batch_record_consumo_movimiento_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.batch_record_consumo
    ADD CONSTRAINT batch_record_consumo_movimiento_id_key UNIQUE (movimiento_id);


--
-- Name: batch_record_consumo batch_record_consumo_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.batch_record_consumo
    ADD CONSTRAINT batch_record_consumo_pkey PRIMARY KEY (id);


--
-- Name: batch_record_correccion batch_record_correccion_evento_correccion_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.batch_record_correccion
    ADD CONSTRAINT batch_record_correccion_evento_correccion_id_key UNIQUE (evento_correccion_id);


--
-- Name: batch_record_correccion batch_record_correccion_firma_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.batch_record_correccion
    ADD CONSTRAINT batch_record_correccion_firma_id_key UNIQUE (firma_id);


--
-- Name: batch_record_correccion batch_record_correccion_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.batch_record_correccion
    ADD CONSTRAINT batch_record_correccion_pkey PRIMARY KEY (id);


--
-- Name: batch_record_decision_calidad batch_record_decision_calidad_firma_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.batch_record_decision_calidad
    ADD CONSTRAINT batch_record_decision_calidad_firma_id_key UNIQUE (firma_id);


--
-- Name: batch_record_decision_calidad batch_record_decision_calidad_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.batch_record_decision_calidad
    ADD CONSTRAINT batch_record_decision_calidad_pkey PRIMARY KEY (id);


--
-- Name: batch_record_desviacion batch_record_desviacion_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.batch_record_desviacion
    ADD CONSTRAINT batch_record_desviacion_pkey PRIMARY KEY (id);


--
-- Name: batch_record_documento batch_record_documento_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.batch_record_documento
    ADD CONSTRAINT batch_record_documento_pkey PRIMARY KEY (id);


--
-- Name: batch_record_documento batch_record_documento_storage_key_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.batch_record_documento
    ADD CONSTRAINT batch_record_documento_storage_key_key UNIQUE (storage_key);


--
-- Name: batch_record_etapa batch_record_etapa_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.batch_record_etapa
    ADD CONSTRAINT batch_record_etapa_pkey PRIMARY KEY (id);


--
-- Name: batch_record_etapa batch_record_etapa_seguimiento_evento_origen_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.batch_record_etapa
    ADD CONSTRAINT batch_record_etapa_seguimiento_evento_origen_id_key UNIQUE (seguimiento_evento_origen_id);


--
-- Name: batch_record_firma batch_record_firma_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.batch_record_firma
    ADD CONSTRAINT batch_record_firma_pkey PRIMARY KEY (id);


--
-- Name: batch_record batch_record_lote_resultado_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.batch_record
    ADD CONSTRAINT batch_record_lote_resultado_id_key UNIQUE (lote_resultado_id);


--
-- Name: batch_record batch_record_orden_fabricacion_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.batch_record
    ADD CONSTRAINT batch_record_orden_fabricacion_id_key UNIQUE (orden_fabricacion_id);


--
-- Name: batch_record batch_record_orden_produccion_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.batch_record
    ADD CONSTRAINT batch_record_orden_produccion_id_key UNIQUE (orden_produccion_id);


--
-- Name: batch_record batch_record_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.batch_record
    ADD CONSTRAINT batch_record_pkey PRIMARY KEY (id);


--
-- Name: batch_record_projection_task batch_record_projection_task_lote_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.batch_record_projection_task
    ADD CONSTRAINT batch_record_projection_task_lote_id_key UNIQUE (lote_id);


--
-- Name: batch_record_projection_task batch_record_projection_task_orden_fabricacion_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.batch_record_projection_task
    ADD CONSTRAINT batch_record_projection_task_orden_fabricacion_id_key UNIQUE (orden_fabricacion_id);


--
-- Name: batch_record_projection_task batch_record_projection_task_orden_produccion_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.batch_record_projection_task
    ADD CONSTRAINT batch_record_projection_task_orden_produccion_id_key UNIQUE (orden_produccion_id);


--
-- Name: batch_record_projection_task batch_record_projection_task_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.batch_record_projection_task
    ADD CONSTRAINT batch_record_projection_task_pkey PRIMARY KEY (id);


--
-- Name: batch_record_revision batch_record_revision_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.batch_record_revision
    ADD CONSTRAINT batch_record_revision_pkey PRIMARY KEY (id);


--
-- Name: batch_record_seccion_correccion batch_record_seccion_correccion_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.batch_record_seccion_correccion
    ADD CONSTRAINT batch_record_seccion_correccion_pkey PRIMARY KEY (id);


--
-- Name: batch_record_solicitud_reapertura_rechazo batch_record_solicitud_reapertura_recha_firma_aprobacion_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.batch_record_solicitud_reapertura_rechazo
    ADD CONSTRAINT batch_record_solicitud_reapertura_recha_firma_aprobacion_id_key UNIQUE (firma_aprobacion_id);


--
-- Name: batch_record_solicitud_reapertura_rechazo batch_record_solicitud_reapertura_rechaz_firma_solicitud_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.batch_record_solicitud_reapertura_rechazo
    ADD CONSTRAINT batch_record_solicitud_reapertura_rechaz_firma_solicitud_id_key UNIQUE (firma_solicitud_id);


--
-- Name: batch_record_solicitud_reapertura_rechazo batch_record_solicitud_reapertura_rechazo_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.batch_record_solicitud_reapertura_rechazo
    ADD CONSTRAINT batch_record_solicitud_reapertura_rechazo_pkey PRIMARY KEY (id);


--
-- Name: calidad_control_proceso_caracteristica calidad_control_proceso_caracteristica_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.calidad_control_proceso_caracteristica
    ADD CONSTRAINT calidad_control_proceso_caracteristica_pkey PRIMARY KEY (id);


--
-- Name: calidad_control_proceso_ejecucion calidad_control_proceso_ejecucion_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.calidad_control_proceso_ejecucion
    ADD CONSTRAINT calidad_control_proceso_ejecucion_pkey PRIMARY KEY (id);


--
-- Name: calidad_control_proceso_lectura calidad_control_proceso_lectura_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.calidad_control_proceso_lectura
    ADD CONSTRAINT calidad_control_proceso_lectura_pkey PRIMARY KEY (id);


--
-- Name: calidad_control_proceso_muestra calidad_control_proceso_muestra_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.calidad_control_proceso_muestra
    ADD CONSTRAINT calidad_control_proceso_muestra_pkey PRIMARY KEY (id);


--
-- Name: calidad_control_proceso_plantilla calidad_control_proceso_plantilla_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.calidad_control_proceso_plantilla
    ADD CONSTRAINT calidad_control_proceso_plantilla_pkey PRIMARY KEY (id);


--
-- Name: carga_costos_item carga_costos_item_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.carga_costos_item
    ADD CONSTRAINT carga_costos_item_pkey PRIMARY KEY (id);


--
-- Name: carga_costos_lote carga_costos_lote_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.carga_costos_lote
    ADD CONSTRAINT carga_costos_lote_pkey PRIMARY KEY (id);


--
-- Name: carga_costos_propagacion_item carga_costos_propagacion_item_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.carga_costos_propagacion_item
    ADD CONSTRAINT carga_costos_propagacion_item_pkey PRIMARY KEY (id);


--
-- Name: cargo cargo_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cargo
    ADD CONSTRAINT cargo_pkey PRIMARY KEY (id_cargo);


--
-- Name: case_pack case_pack_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.case_pack
    ADD CONSTRAINT case_pack_pkey PRIMARY KEY (id);


--
-- Name: categoria categoria_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.categoria
    ADD CONSTRAINT categoria_pkey PRIMARY KEY (categoria_id);


--
-- Name: batch_record_etapa chk_batch_record_etapa_un_origen_operativo; Type: CHECK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE public.batch_record_etapa
    ADD CONSTRAINT chk_batch_record_etapa_un_origen_operativo CHECK ((num_nonnulls(seguimiento_orden_area_id, orden_fabricacion_operacion_id) = 1)) NOT VALID;


--
-- Name: batch_record_firma chk_batch_record_firma_contexto; Type: CHECK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE public.batch_record_firma
    ADD CONSTRAINT chk_batch_record_firma_contexto CHECK (((((alcance)::text = 'CIERRE_ETAPA_AREA'::text) AND (batch_record_etapa_id IS NOT NULL) AND (num_nonnulls(seguimiento_evento_id, orden_fabricacion_evento_id) = 1) AND (batch_record_revision_id IS NULL)) OR (((alcance)::text <> 'CIERRE_ETAPA_AREA'::text) AND (batch_record_etapa_id IS NULL) AND (seguimiento_evento_id IS NULL) AND (orden_fabricacion_evento_id IS NULL) AND (batch_record_revision_id IS NOT NULL)))) NOT VALID;


--
-- Name: lote chk_lote_un_origen; Type: CHECK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE public.lote
    ADD CONSTRAINT chk_lote_un_origen CHECK ((num_nonnulls(orden_compra_id, orden_produccion_id, orden_fabricacion_id) <= 1)) NOT VALID;


--
-- Name: cierre_produccion cierre_produccion_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cierre_produccion
    ADD CONSTRAINT cierre_produccion_pkey PRIMARY KEY (id);


--
-- Name: clientes clientes_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.clientes
    ADD CONSTRAINT clientes_pkey PRIMARY KEY (cliente_id);


--
-- Name: contacto_proveedor contacto_proveedor_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.contacto_proveedor
    ADD CONSTRAINT contacto_proveedor_pkey PRIMARY KEY (contacto_id);


--
-- Name: control_desviacion control_desviacion_ejecucion_origen_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.control_desviacion
    ADD CONSTRAINT control_desviacion_ejecucion_origen_id_key UNIQUE (ejecucion_origen_id);


--
-- Name: control_desviacion control_desviacion_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.control_desviacion
    ADD CONSTRAINT control_desviacion_pkey PRIMARY KEY (id);


--
-- Name: control_ejecucion_lectura control_ejecucion_lectura_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.control_ejecucion_lectura
    ADD CONSTRAINT control_ejecucion_lectura_pkey PRIMARY KEY (id);


--
-- Name: control_ejecucion control_ejecucion_legacy_ejecucion_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.control_ejecucion
    ADD CONSTRAINT control_ejecucion_legacy_ejecucion_id_key UNIQUE (legacy_ejecucion_id);


--
-- Name: control_ejecucion_muestra control_ejecucion_muestra_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.control_ejecucion_muestra
    ADD CONSTRAINT control_ejecucion_muestra_pkey PRIMARY KEY (id);


--
-- Name: control_ejecucion control_ejecucion_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.control_ejecucion
    ADD CONSTRAINT control_ejecucion_pkey PRIMARY KEY (id);


--
-- Name: control_idempotencia control_idempotencia_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.control_idempotencia
    ADD CONSTRAINT control_idempotencia_pkey PRIMARY KEY (id);


--
-- Name: control_magnitud control_magnitud_codigo_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.control_magnitud
    ADD CONSTRAINT control_magnitud_codigo_key UNIQUE (codigo);


--
-- Name: control_magnitud control_magnitud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.control_magnitud
    ADD CONSTRAINT control_magnitud_pkey PRIMARY KEY (id);


--
-- Name: control_plan_aplicabilidad_exclusion control_plan_aplicabilidad_exclusion_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.control_plan_aplicabilidad_exclusion
    ADD CONSTRAINT control_plan_aplicabilidad_exclusion_pkey PRIMARY KEY (aplicabilidad_id, producto_id);


--
-- Name: control_plan_aplicabilidad control_plan_aplicabilidad_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.control_plan_aplicabilidad
    ADD CONSTRAINT control_plan_aplicabilidad_pkey PRIMARY KEY (id);


--
-- Name: control_plan_caracteristica control_plan_caracteristica_legacy_caracteristica_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.control_plan_caracteristica
    ADD CONSTRAINT control_plan_caracteristica_legacy_caracteristica_id_key UNIQUE (legacy_caracteristica_id);


--
-- Name: control_plan_caracteristica control_plan_caracteristica_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.control_plan_caracteristica
    ADD CONSTRAINT control_plan_caracteristica_pkey PRIMARY KEY (id);


--
-- Name: control_plan control_plan_codigo_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.control_plan
    ADD CONSTRAINT control_plan_codigo_key UNIQUE (codigo);


--
-- Name: control_plan control_plan_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.control_plan
    ADD CONSTRAINT control_plan_pkey PRIMARY KEY (id);


--
-- Name: control_plan_version control_plan_version_legacy_plantilla_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.control_plan_version
    ADD CONSTRAINT control_plan_version_legacy_plantilla_id_key UNIQUE (legacy_plantilla_id);


--
-- Name: control_plan_version control_plan_version_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.control_plan_version
    ADD CONSTRAINT control_plan_version_pkey PRIMARY KEY (id);


--
-- Name: control_requerido control_requerido_legacy_ejecucion_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.control_requerido
    ADD CONSTRAINT control_requerido_legacy_ejecucion_id_key UNIQUE (legacy_ejecucion_id);


--
-- Name: control_requerido control_requerido_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.control_requerido
    ADD CONSTRAINT control_requerido_pkey PRIMARY KEY (id);


--
-- Name: control_revalidacion control_revalidacion_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.control_revalidacion
    ADD CONSTRAINT control_revalidacion_pkey PRIMARY KEY (id);


--
-- Name: control_unidad control_unidad_codigo_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.control_unidad
    ADD CONSTRAINT control_unidad_codigo_key UNIQUE (codigo);


--
-- Name: control_unidad control_unidad_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.control_unidad
    ADD CONSTRAINT control_unidad_pkey PRIMARY KEY (id);


--
-- Name: cuenta cuenta_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cuenta
    ADD CONSTRAINT cuenta_pkey PRIMARY KEY (codigo);


--
-- Name: depreciacion_activo depreciacion_activo_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.depreciacion_activo
    ADD CONSTRAINT depreciacion_activo_pkey PRIMARY KEY (id);


--
-- Name: doc_tran_de_personal doc_tran_de_personal_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.doc_tran_de_personal
    ADD CONSTRAINT doc_tran_de_personal_pkey PRIMARY KEY (id);


--
-- Name: documento_baja_activo documento_baja_activo_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.documento_baja_activo
    ADD CONSTRAINT documento_baja_activo_pkey PRIMARY KEY (baja_id);


--
-- Name: empresa_identidad_legal_version empresa_identidad_legal_version_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.empresa_identidad_legal_version
    ADD CONSTRAINT empresa_identidad_legal_version_pkey PRIMARY KEY (id);


--
-- Name: empresa_identidad_legal_version empresa_identidad_legal_version_version_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.empresa_identidad_legal_version
    ADD CONSTRAINT empresa_identidad_legal_version_version_key UNIQUE (version);


--
-- Name: empresa_logo_documental_version empresa_logo_documental_version_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.empresa_logo_documental_version
    ADD CONSTRAINT empresa_logo_documental_version_pkey PRIMARY KEY (id);


--
-- Name: empresa_logo_documental_version empresa_logo_documental_version_version_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.empresa_logo_documental_version
    ADD CONSTRAINT empresa_logo_documental_version_version_key UNIQUE (version);


--
-- Name: factura_compra_activo factura_compra_activo_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.factura_compra_activo
    ADD CONSTRAINT factura_compra_activo_pkey PRIMARY KEY (factura_compra_activo_id);


--
-- Name: facturas_compras facturas_compras_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.facturas_compras
    ADD CONSTRAINT facturas_compras_pkey PRIMARY KEY (factura_compra_id);


--
-- Name: facturas_venta facturas_venta_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.facturas_venta
    ADD CONSTRAINT facturas_venta_pkey PRIMARY KEY (factura_venta_id);


--
-- Name: firma_visual_usuario_version firma_visual_usuario_version_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.firma_visual_usuario_version
    ADD CONSTRAINT firma_visual_usuario_version_pkey PRIMARY KEY (id);


--
-- Name: incorporacion_activo_header incorporacion_activo_header_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.incorporacion_activo_header
    ADD CONSTRAINT incorporacion_activo_header_pkey PRIMARY KEY (incorporacion_id);


--
-- Name: incorporacion_activo_line incorporacion_activo_line_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.incorporacion_activo_line
    ADD CONSTRAINT incorporacion_activo_line_pkey PRIMARY KEY (linea_id);


--
-- Name: insumos_empaque insumos_empaque_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.insumos_empaque
    ADD CONSTRAINT insumos_empaque_pkey PRIMARY KEY (id);


--
-- Name: insumos insumos_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.insumos
    ADD CONSTRAINT insumos_pkey PRIMARY KEY (insumo_id);


--
-- Name: integrante_personal integrante_personal_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.integrante_personal
    ADD CONSTRAINT integrante_personal_pkey PRIMARY KEY (id);


--
-- Name: item_orden_compra_activo item_orden_compra_activo_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.item_orden_compra_activo
    ADD CONSTRAINT item_orden_compra_activo_pkey PRIMARY KEY (item_id);


--
-- Name: item_orden_compra item_orden_compra_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.item_orden_compra
    ADD CONSTRAINT item_orden_compra_pkey PRIMARY KEY (item_id);


--
-- Name: items_factura_compra items_factura_compra_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.items_factura_compra
    ADD CONSTRAINT items_factura_compra_pkey PRIMARY KEY (item_compra_id);


--
-- Name: items_factura_venta items_factura_venta_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.items_factura_venta
    ADD CONSTRAINT items_factura_venta_pkey PRIMARY KEY (item_factura_id);


--
-- Name: items_orden_venta items_orden_venta_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.items_orden_venta
    ADD CONSTRAINT items_orden_venta_pkey PRIMARY KEY (item_id);


--
-- Name: jornada_laboral_bloque jornada_laboral_bloque_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.jornada_laboral_bloque
    ADD CONSTRAINT jornada_laboral_bloque_pkey PRIMARY KEY (id);


--
-- Name: jornada_laboral_version jornada_laboral_version_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.jornada_laboral_version
    ADD CONSTRAINT jornada_laboral_version_pkey PRIMARY KEY (id);


--
-- Name: jornada_laboral_version jornada_laboral_version_version_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.jornada_laboral_version
    ADD CONSTRAINT jornada_laboral_version_version_key UNIQUE (version);


--
-- Name: lead_time_proveedor_kpi lead_time_proveedor_kpi_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lead_time_proveedor_kpi
    ADD CONSTRAINT lead_time_proveedor_kpi_pkey PRIMARY KEY (id);


--
-- Name: linea_asiento_contable linea_asiento_contable_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.linea_asiento_contable
    ADD CONSTRAINT linea_asiento_contable_pkey PRIMARY KEY (id);


--
-- Name: lote lote_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lote
    ADD CONSTRAINT lote_pkey PRIMARY KEY (id);


--
-- Name: maestra_notificacion maestra_notificacion_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.maestra_notificacion
    ADD CONSTRAINT maestra_notificacion_pkey PRIMARY KEY (id);


--
-- Name: maestra_notificacion_users maestra_notificacion_users_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.maestra_notificacion_users
    ADD CONSTRAINT maestra_notificacion_users_pkey PRIMARY KEY (notificacion_id, user_id);


--
-- Name: mantenimiento_activo mantenimiento_activo_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.mantenimiento_activo
    ADD CONSTRAINT mantenimiento_activo_pkey PRIMARY KEY (id);


--
-- Name: manufacturing_versions manufacturing_versions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.manufacturing_versions
    ADD CONSTRAINT manufacturing_versions_pkey PRIMARY KEY (id);


--
-- Name: master_directive master_directive_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.master_directive
    ADD CONSTRAINT master_directive_pkey PRIMARY KEY (id);


--
-- Name: master_production_schedule_semanal master_production_schedule_semanal_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.master_production_schedule_semanal
    ADD CONSTRAINT master_production_schedule_semanal_pkey PRIMARY KEY (mps_id);


--
-- Name: material_ficha_tecnica_version material_ficha_tecnica_version_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.material_ficha_tecnica_version
    ADD CONSTRAINT material_ficha_tecnica_version_pkey PRIMARY KEY (id);


--
-- Name: mision_vision_valor mision_vision_valor_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.mision_vision_valor
    ADD CONSTRAINT mision_vision_valor_pkey PRIMARY KEY (id);


--
-- Name: mision_vision_version mision_vision_version_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.mision_vision_version
    ADD CONSTRAINT mision_vision_version_pkey PRIMARY KEY (id);


--
-- Name: mision_vision_version mision_vision_version_version_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.mision_vision_version
    ADD CONSTRAINT mision_vision_version_version_key UNIQUE (version);


--
-- Name: modulo_accesos modulo_accesos_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.modulo_accesos
    ADD CONSTRAINT modulo_accesos_pkey PRIMARY KEY (id);


--
-- Name: monitoreo_evento_error monitoreo_evento_error_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.monitoreo_evento_error
    ADD CONSTRAINT monitoreo_evento_error_pkey PRIMARY KEY (id);


--
-- Name: monitoreo_grupo_error monitoreo_grupo_error_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.monitoreo_grupo_error
    ADD CONSTRAINT monitoreo_grupo_error_pkey PRIMARY KEY (id);


--
-- Name: movimiento_reserva movimiento_reserva_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.movimiento_reserva
    ADD CONSTRAINT movimiento_reserva_pkey PRIMARY KEY (id);


--
-- Name: movimientos movimientos_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.movimientos
    ADD CONSTRAINT movimientos_pkey PRIMARY KEY (movimiento_id);


--
-- Name: mps_fabricacion_detalle mps_fabricacion_detalle_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.mps_fabricacion_detalle
    ADD CONSTRAINT mps_fabricacion_detalle_pkey PRIMARY KEY (id);


--
-- Name: mps_fabricacion_semanal mps_fabricacion_semanal_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.mps_fabricacion_semanal
    ADD CONSTRAINT mps_fabricacion_semanal_pkey PRIMARY KEY (id);


--
-- Name: mps_fabricacion_semanal mps_fabricacion_semanal_week_start_date_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.mps_fabricacion_semanal
    ADD CONSTRAINT mps_fabricacion_semanal_week_start_date_key UNIQUE (week_start_date);


--
-- Name: mps_semanal_dia mps_semanal_dia_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.mps_semanal_dia
    ADD CONSTRAINT mps_semanal_dia_pkey PRIMARY KEY (id);


--
-- Name: mps_semanal_item mps_semanal_item_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.mps_semanal_item
    ADD CONSTRAINT mps_semanal_item_pkey PRIMARY KEY (id);


--
-- Name: mps_semanal_lote_planificado mps_semanal_lote_planificado_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.mps_semanal_lote_planificado
    ADD CONSTRAINT mps_semanal_lote_planificado_pkey PRIMARY KEY (id);


--
-- Name: mps_semanal_observacion mps_semanal_observacion_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.mps_semanal_observacion
    ADD CONSTRAINT mps_semanal_observacion_pkey PRIMARY KEY (observacion_id);


--
-- Name: orden_compra_activo orden_compra_activo_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.orden_compra_activo
    ADD CONSTRAINT orden_compra_activo_pkey PRIMARY KEY (orden_compra_activo_id);


--
-- Name: orden_compra orden_compra_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.orden_compra
    ADD CONSTRAINT orden_compra_pkey PRIMARY KEY (orden_compra_id);


--
-- Name: orden_fabricacion_operacion_dependencia orden_fabricacion_operacion_dependencia_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.orden_fabricacion_operacion_dependencia
    ADD CONSTRAINT orden_fabricacion_operacion_dependencia_pkey PRIMARY KEY (id);


--
-- Name: orden_fabricacion_operacion_evento orden_fabricacion_operacion_evento_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.orden_fabricacion_operacion_evento
    ADD CONSTRAINT orden_fabricacion_operacion_evento_pkey PRIMARY KEY (id);


--
-- Name: orden_fabricacion_operacion orden_fabricacion_operacion_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.orden_fabricacion_operacion
    ADD CONSTRAINT orden_fabricacion_operacion_pkey PRIMARY KEY (id);


--
-- Name: orden_fabricacion orden_fabricacion_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.orden_fabricacion
    ADD CONSTRAINT orden_fabricacion_pkey PRIMARY KEY (orden_fabricacion_id);


--
-- Name: ordenes_produccion ordenes_produccion_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ordenes_produccion
    ADD CONSTRAINT ordenes_produccion_pkey PRIMARY KEY (orden_id);


--
-- Name: ordenes_venta ordenes_venta_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ordenes_venta
    ADD CONSTRAINT ordenes_venta_pkey PRIMARY KEY (orden_venta_id);


--
-- Name: organigrama_estado organigrama_estado_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.organigrama_estado
    ADD CONSTRAINT organigrama_estado_pkey PRIMARY KEY (id);


--
-- Name: password_reset_tokens password_reset_tokens_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.password_reset_tokens
    ADD CONSTRAINT password_reset_tokens_pkey PRIMARY KEY (id);


--
-- Name: periodo_contable periodo_contable_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.periodo_contable
    ADD CONSTRAINT periodo_contable_pkey PRIMARY KEY (id);


--
-- Name: area_operativa_categoria_habilitada pk_area_operativa_categoria_habilitada; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.area_operativa_categoria_habilitada
    ADD CONSTRAINT pk_area_operativa_categoria_habilitada PRIMARY KEY (area_operativa_id, categoria_id);


--
-- Name: planificacion_produccion planificacion_produccion_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.planificacion_produccion
    ADD CONSTRAINT planificacion_produccion_pkey PRIMARY KEY (id);


--
-- Name: proceso_fabricacion_edge proceso_fabricacion_edge_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.proceso_fabricacion_edge
    ADD CONSTRAINT proceso_fabricacion_edge_pkey PRIMARY KEY (id);


--
-- Name: proceso_fabricacion_nodo proceso_fabricacion_nodo_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.proceso_fabricacion_nodo
    ADD CONSTRAINT proceso_fabricacion_nodo_pkey PRIMARY KEY (id);


--
-- Name: proceso_produccion_documento_version proceso_produccion_documento_version_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.proceso_produccion_documento_version
    ADD CONSTRAINT proceso_produccion_documento_version_pkey PRIMARY KEY (id);


--
-- Name: proceso_produccion proceso_produccion_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.proceso_produccion
    ADD CONSTRAINT proceso_produccion_pkey PRIMARY KEY (proceso_id);


--
-- Name: proceso_recurso proceso_recurso_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.proceso_recurso
    ADD CONSTRAINT proceso_recurso_pkey PRIMARY KEY (id);


--
-- Name: procesos_produccion procesos_produccion_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.procesos_produccion
    ADD CONSTRAINT procesos_produccion_pkey PRIMARY KEY (proceso_completo_id);


--
-- Name: producto_costo_historial producto_costo_historial_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.producto_costo_historial
    ADD CONSTRAINT producto_costo_historial_pkey PRIMARY KEY (id);


--
-- Name: productos productos_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.productos
    ADD CONSTRAINT productos_pkey PRIMARY KEY (producto_id);


--
-- Name: proveedor_material_lead_time_metric proveedor_material_lead_time_metric_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.proveedor_material_lead_time_metric
    ADD CONSTRAINT proveedor_material_lead_time_metric_pkey PRIMARY KEY (id);


--
-- Name: proveedores proveedores_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.proveedores
    ADD CONSTRAINT proveedores_pkey PRIMARY KEY (pk);


--
-- Name: receta_costos_revision receta_costos_revision_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.receta_costos_revision
    ADD CONSTRAINT receta_costos_revision_pkey PRIMARY KEY (id);


--
-- Name: recurso_produccion recurso_produccion_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.recurso_produccion
    ADD CONSTRAINT recurso_produccion_pkey PRIMARY KEY (id);


--
-- Name: registro_hora_extra registro_hora_extra_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.registro_hora_extra
    ADD CONSTRAINT registro_hora_extra_pkey PRIMARY KEY (registro_hora_extra_id);


--
-- Name: reporte_produccion_lote reporte_produccion_lote_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reporte_produccion_lote
    ADD CONSTRAINT reporte_produccion_lote_pkey PRIMARY KEY (id);


--
-- Name: ruta_proceso_cat ruta_proceso_cat_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ruta_proceso_cat
    ADD CONSTRAINT ruta_proceso_cat_pkey PRIMARY KEY (id);


--
-- Name: ruta_proceso_cat_version ruta_proceso_cat_version_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ruta_proceso_cat_version
    ADD CONSTRAINT ruta_proceso_cat_version_pkey PRIMARY KEY (id);


--
-- Name: ruta_proceso_edge ruta_proceso_edge_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ruta_proceso_edge
    ADD CONSTRAINT ruta_proceso_edge_pkey PRIMARY KEY (id);


--
-- Name: ruta_proceso_node ruta_proceso_node_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ruta_proceso_node
    ADD CONSTRAINT ruta_proceso_node_pkey PRIMARY KEY (id);


--
-- Name: seguimiento_orden_area_evento seguimiento_orden_area_evento_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.seguimiento_orden_area_evento
    ADD CONSTRAINT seguimiento_orden_area_evento_pkey PRIMARY KEY (id);


--
-- Name: seguimiento_orden_area seguimiento_orden_area_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.seguimiento_orden_area
    ADD CONSTRAINT seguimiento_orden_area_pkey PRIMARY KEY (id);


--
-- Name: semana_mps semana_mps_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.semana_mps
    ADD CONSTRAINT semana_mps_pkey PRIMARY KEY (id);


--
-- Name: super_master_config super_master_config_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.super_master_config
    ADD CONSTRAINT super_master_config_pkey PRIMARY KEY (id);


--
-- Name: super_master_verification_codes super_master_verification_codes_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.super_master_verification_codes
    ADD CONSTRAINT super_master_verification_codes_pkey PRIMARY KEY (id);


--
-- Name: tab_accesos tab_accesos_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tab_accesos
    ADD CONSTRAINT tab_accesos_pkey PRIMARY KEY (id);


--
-- Name: tarea tarea_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tarea
    ADD CONSTRAINT tarea_pkey PRIMARY KEY (id);


--
-- Name: transaccion_almacen transaccion_almacen_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.transaccion_almacen
    ADD CONSTRAINT transaccion_almacen_pkey PRIMARY KEY (transaccion_id);


--
-- Name: traslado_activo traslado_activo_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.traslado_activo
    ADD CONSTRAINT traslado_activo_pkey PRIMARY KEY (id);


--
-- Name: ordenes_produccion uk_4wya8mv6x6g0ui4a35iqida9q; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ordenes_produccion
    ADD CONSTRAINT uk_4wya8mv6x6g0ui4a35iqida9q UNIQUE (proceso_completo_id);


--
-- Name: transaccion_almacen uk_57cm6l0xqddrmu5p9exwrpchp; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.transaccion_almacen
    ADD CONSTRAINT uk_57cm6l0xqddrmu5p9exwrpchp UNIQUE (asiento_contable_id);


--
-- Name: depreciacion_activo uk_5s8t5y0mfm9jj8pudq36ha67k; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.depreciacion_activo
    ADD CONSTRAINT uk_5s8t5y0mfm9jj8pudq36ha67k UNIQUE (asiento_contable_id);


--
-- Name: ordenes_produccion uk_6b3rc3ajwhpsyyhwbsggo0vp3; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ordenes_produccion
    ADD CONSTRAINT uk_6b3rc3ajwhpsyyhwbsggo0vp3 UNIQUE (planificacion_id);


--
-- Name: documento_baja_activo uk_6nbhmbo8yvlxab6cxsy98t0bp; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.documento_baja_activo
    ADD CONSTRAINT uk_6nbhmbo8yvlxab6cxsy98t0bp UNIQUE (asiento_contable_id);


--
-- Name: users uk_72nq30heq3jovypjxbe289jc; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT uk_72nq30heq3jovypjxbe289jc UNIQUE (cedula);


--
-- Name: incorporacion_activo_line uk_7cuwhut5lot4g43mbxwq0rcli; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.incorporacion_activo_line
    ADD CONSTRAINT uk_7cuwhut5lot4g43mbxwq0rcli UNIQUE (activo_id);


--
-- Name: vendedores uk_873pbbsamc3lfs74serjhapk9; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.vendedores
    ADD CONSTRAINT uk_873pbbsamc3lfs74serjhapk9 UNIQUE (user_id);


--
-- Name: productos uk_ab6fva95kdidlbtkof6f9hlum; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.productos
    ADD CONSTRAINT uk_ab6fva95kdidlbtkof6f9hlum UNIQUE (prefijo_lote);


--
-- Name: area_operativa_categoria_unidad_medida uk_aocum_area_categoria; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.area_operativa_categoria_unidad_medida
    ADD CONSTRAINT uk_aocum_area_categoria UNIQUE (area_operativa_id, categoria_id);


--
-- Name: cargo uk_bfbqihl0x5ytaqr7w0feln3xh; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cargo
    ADD CONSTRAINT uk_bfbqihl0x5ytaqr7w0feln3xh UNIQUE (usuario_id);


--
-- Name: proveedores uk_bp4g1fih5gf9h0l65eusxiru7; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.proveedores
    ADD CONSTRAINT uk_bp4g1fih5gf9h0l65eusxiru7 UNIQUE (id);


--
-- Name: categoria uk_ejd9weucwysfx3i8c2pjlsqas; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.categoria
    ADD CONSTRAINT uk_ejd9weucwysfx3i8c2pjlsqas UNIQUE (categoria_nombre);


--
-- Name: area_operativa uk_g7ov2p0f6a35xukb7qamc62w3; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.area_operativa
    ADD CONSTRAINT uk_g7ov2p0f6a35xukb7qamc62w3 UNIQUE (nombre);


--
-- Name: productos uk_ioqqmjkvexquwlwivum2ethcp; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.productos
    ADD CONSTRAINT uk_ioqqmjkvexquwlwivum2ethcp UNIQUE (case_pack_id);


--
-- Name: jornada_laboral_bloque uk_jornada_laboral_bloque_dia_orden; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.jornada_laboral_bloque
    ADD CONSTRAINT uk_jornada_laboral_bloque_dia_orden UNIQUE (jornada_laboral_version_id, dia_semana, orden);


--
-- Name: incorporacion_activo_header uk_kdmtbg2rghfoqq2xy8i08ox52; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.incorporacion_activo_header
    ADD CONSTRAINT uk_kdmtbg2rghfoqq2xy8i08ox52 UNIQUE (asiento_contable_id);


--
-- Name: password_reset_tokens uk_la2ts67g4oh2sreayswhox1i6; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.password_reset_tokens
    ADD CONSTRAINT uk_la2ts67g4oh2sreayswhox1i6 UNIQUE (user_id);


--
-- Name: lead_time_proveedor_kpi uk_lead_time_proveedor_kpi_proveedor; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lead_time_proveedor_kpi
    ADD CONSTRAINT uk_lead_time_proveedor_kpi_proveedor UNIQUE (proveedor_pk);


--
-- Name: material_ficha_tecnica_version uk_material_ficha_tecnica_version; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.material_ficha_tecnica_version
    ADD CONSTRAINT uk_material_ficha_tecnica_version UNIQUE (material_id, version);


--
-- Name: mision_vision_valor uk_mision_vision_valor_version_orden; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.mision_vision_valor
    ADD CONSTRAINT uk_mision_vision_valor_version_orden UNIQUE (mision_vision_version_id, orden);


--
-- Name: master_directive uk_mpqc14fak7mp88hpn8avotkfs; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.master_directive
    ADD CONSTRAINT uk_mpqc14fak7mp88hpn8avotkfs UNIQUE (nombre);


--
-- Name: mps_semanal_dia uk_mps_sem_dia_day_index; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.mps_semanal_dia
    ADD CONSTRAINT uk_mps_sem_dia_day_index UNIQUE (mps_id, day_index);


--
-- Name: mps_semanal_dia uk_mps_sem_dia_fecha; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.mps_semanal_dia
    ADD CONSTRAINT uk_mps_sem_dia_fecha UNIQUE (mps_id, fecha);


--
-- Name: mps_semanal_lote_planificado uk_mps_sem_lote_item_ordinal; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.mps_semanal_lote_planificado
    ADD CONSTRAINT uk_mps_sem_lote_item_ordinal UNIQUE (mps_item_id, lote_ordinal);


--
-- Name: master_production_schedule_semanal uk_mps_semana_mps_id; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.master_production_schedule_semanal
    ADD CONSTRAINT uk_mps_semana_mps_id UNIQUE (semana_mps_id);


--
-- Name: master_production_schedule_semanal uk_mps_week_start_date; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.master_production_schedule_semanal
    ADD CONSTRAINT uk_mps_week_start_date UNIQUE (week_start_date);


--
-- Name: ordenes_produccion uk_ordenes_produccion_mps_lote_planificado; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ordenes_produccion
    ADD CONSTRAINT uk_ordenes_produccion_mps_lote_planificado UNIQUE (mps_lote_planificado_id);


--
-- Name: proceso_produccion_documento_version uk_proceso_documento_storage_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.proceso_produccion_documento_version
    ADD CONSTRAINT uk_proceso_documento_storage_key UNIQUE (storage_key);


--
-- Name: proceso_produccion_documento_version uk_proceso_documento_version; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.proceso_produccion_documento_version
    ADD CONSTRAINT uk_proceso_documento_version UNIQUE (proceso_id, version);


--
-- Name: procesos_produccion uk_procesos_produccion_producto; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.procesos_produccion
    ADD CONSTRAINT uk_procesos_produccion_producto UNIQUE (producto_id);


--
-- Name: proveedor_material_lead_time_metric uk_proveedor_material_lead_time_metric_pair; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.proveedor_material_lead_time_metric
    ADD CONSTRAINT uk_proveedor_material_lead_time_metric_pair UNIQUE (proveedor_pk, producto_id);


--
-- Name: users uk_r43af9ap4edm43mmtq01oddj6; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT uk_r43af9ap4edm43mmtq01oddj6 UNIQUE (username);


--
-- Name: lote uk_s52kj75w24s5vpmrfs0oxrk8e; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lote
    ADD CONSTRAINT uk_s52kj75w24s5vpmrfs0oxrk8e UNIQUE (batch_number);


--
-- Name: semana_mps uk_semana_mps_codigo; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.semana_mps
    ADD CONSTRAINT uk_semana_mps_codigo UNIQUE (codigo);


--
-- Name: semana_mps uk_semana_mps_standard_anio_numero; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.semana_mps
    ADD CONSTRAINT uk_semana_mps_standard_anio_numero UNIQUE (standard, anio_semana, numero_semana);


--
-- Name: semana_mps uk_semana_mps_standard_start_date; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.semana_mps
    ADD CONSTRAINT uk_semana_mps_standard_start_date UNIQUE (standard, start_date);


--
-- Name: unidad_medida_area_operativa uk_umao_area_unidad_id; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.unidad_medida_area_operativa
    ADD CONSTRAINT uk_umao_area_unidad_id UNIQUE (area_operativa_id, unidad_medida_area_operativa_id);


--
-- Name: unidad_medida_area_operativa uk_umao_nombre; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.unidad_medida_area_operativa
    ADD CONSTRAINT uk_umao_nombre UNIQUE (nombre);


--
-- Name: unidad_medida_area_operativa unidad_medida_area_operativa_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.unidad_medida_area_operativa
    ADD CONSTRAINT unidad_medida_area_operativa_pkey PRIMARY KEY (unidad_medida_area_operativa_id);


--
-- Name: unidad_medida_producto unidad_medida_producto_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.unidad_medida_producto
    ADD CONSTRAINT unidad_medida_producto_pkey PRIMARY KEY (codigo);


--
-- Name: area_operativa uq_area_operativa_responsable; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.area_operativa
    ADD CONSTRAINT uq_area_operativa_responsable UNIQUE (responsable_id);


--
-- Name: batch_record_ciclo_revision uq_batch_record_ciclo_numero; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.batch_record_ciclo_revision
    ADD CONSTRAINT uq_batch_record_ciclo_numero UNIQUE (batch_record_id, numero);


--
-- Name: batch_record_desviacion uq_batch_record_desviacion_codigo; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.batch_record_desviacion
    ADD CONSTRAINT uq_batch_record_desviacion_codigo UNIQUE (batch_record_id, codigo);


--
-- Name: batch_record_documento uq_batch_record_documento_version; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.batch_record_documento
    ADD CONSTRAINT uq_batch_record_documento_version UNIQUE (batch_record_id, tipo, version_documento);


--
-- Name: batch_record_etapa uq_batch_record_etapa_secuencia; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.batch_record_etapa
    ADD CONSTRAINT uq_batch_record_etapa_secuencia UNIQUE (batch_record_id, secuencia);


--
-- Name: batch_record_revision uq_batch_record_revision_numero; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.batch_record_revision
    ADD CONSTRAINT uq_batch_record_revision_numero UNIQUE (batch_record_id, numero);


--
-- Name: batch_record_seccion_correccion uq_batch_record_seccion_ciclo; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.batch_record_seccion_correccion
    ADD CONSTRAINT uq_batch_record_seccion_ciclo UNIQUE (batch_record_id, ciclo_revision_numero, seccion);


--
-- Name: calidad_control_proceso_caracteristica uq_calidad_caracteristica_orden; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.calidad_control_proceso_caracteristica
    ADD CONSTRAINT uq_calidad_caracteristica_orden UNIQUE (plantilla_id, orden);


--
-- Name: calidad_control_proceso_lectura uq_calidad_lectura; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.calidad_control_proceso_lectura
    ADD CONSTRAINT uq_calidad_lectura UNIQUE (muestra_id, indice_unidad);


--
-- Name: calidad_control_proceso_muestra uq_calidad_muestra; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.calidad_control_proceso_muestra
    ADD CONSTRAINT uq_calidad_muestra UNIQUE (ejecucion_id, caracteristica_id, numero_muestra);


--
-- Name: calidad_control_proceso_plantilla uq_calidad_plantilla_area_version; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.calidad_control_proceso_plantilla
    ADD CONSTRAINT uq_calidad_plantilla_area_version UNIQUE (area_operativa_id, version);


--
-- Name: carga_costos_item uq_carga_costos_item_lote_producto; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.carga_costos_item
    ADD CONSTRAINT uq_carga_costos_item_lote_producto UNIQUE (lote_id, producto_id);


--
-- Name: carga_costos_propagacion_item uq_carga_costos_propagacion_lote_producto; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.carga_costos_propagacion_item
    ADD CONSTRAINT uq_carga_costos_propagacion_lote_producto UNIQUE (lote_id, producto_id);


--
-- Name: cierre_produccion uq_cierre_produccion_idempotency; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cierre_produccion
    ADD CONSTRAINT uq_cierre_produccion_idempotency UNIQUE (idempotency_key);


--
-- Name: control_plan_caracteristica uq_control_caracteristica_orden; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.control_plan_caracteristica
    ADD CONSTRAINT uq_control_caracteristica_orden UNIQUE (version_id, orden);


--
-- Name: control_idempotencia uq_control_idempotencia_actor_accion_recurso_clave; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.control_idempotencia
    ADD CONSTRAINT uq_control_idempotencia_actor_accion_recurso_clave UNIQUE (actor_id, accion, recurso, clave);


--
-- Name: control_ejecucion_lectura uq_control_lectura; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.control_ejecucion_lectura
    ADD CONSTRAINT uq_control_lectura UNIQUE (muestra_id, indice_unidad);


--
-- Name: control_ejecucion_muestra uq_control_muestra; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.control_ejecucion_muestra
    ADD CONSTRAINT uq_control_muestra UNIQUE (ejecucion_id, caracteristica_id, numero_muestra);


--
-- Name: control_plan_aplicabilidad uq_control_plan_aplicabilidad_version; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.control_plan_aplicabilidad
    ADD CONSTRAINT uq_control_plan_aplicabilidad_version UNIQUE (version_id) DEFERRABLE INITIALLY DEFERRED;


--
-- Name: CONSTRAINT uq_control_plan_aplicabilidad_version ON control_plan_aplicabilidad; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON CONSTRAINT uq_control_plan_aplicabilidad_version ON public.control_plan_aplicabilidad IS 'Garantiza una unica aplicabilidad y ubicacion por version de plan de control.';


--
-- Name: control_plan_version uq_control_plan_version_numero; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.control_plan_version
    ADD CONSTRAINT uq_control_plan_version_numero UNIQUE (plan_id, numero);


--
-- Name: control_revalidacion uq_control_revalidacion_ciclo; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.control_revalidacion
    ADD CONSTRAINT uq_control_revalidacion_ciclo UNIQUE (control_requerido_id, ciclo_revision_numero);


--
-- Name: firma_visual_usuario_version uq_firma_visual_usuario_version; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.firma_visual_usuario_version
    ADD CONSTRAINT uq_firma_visual_usuario_version UNIQUE (usuario_id, version);


--
-- Name: modulo_accesos uq_modulo_accesos_user_modulo; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.modulo_accesos
    ADD CONSTRAINT uq_modulo_accesos_user_modulo UNIQUE (user_id, modulo);


--
-- Name: monitoreo_grupo_error uq_monitoreo_grupo_error_firma; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.monitoreo_grupo_error
    ADD CONSTRAINT uq_monitoreo_grupo_error_firma UNIQUE (firma);


--
-- Name: orden_fabricacion_operacion_dependencia uq_of_operacion_dependencia; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.orden_fabricacion_operacion_dependencia
    ADD CONSTRAINT uq_of_operacion_dependencia UNIQUE (operacion_predecesora_id, operacion_sucesora_id);


--
-- Name: orden_fabricacion_operacion uq_of_operacion_frontend; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.orden_fabricacion_operacion
    ADD CONSTRAINT uq_of_operacion_frontend UNIQUE (orden_fabricacion_id, frontend_node_id);


--
-- Name: orden_fabricacion_operacion uq_of_operacion_secuencia; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.orden_fabricacion_operacion
    ADD CONSTRAINT uq_of_operacion_secuencia UNIQUE (orden_fabricacion_id, posicion_secuencia);


--
-- Name: producto_costo_historial uq_producto_costo_historial_version; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.producto_costo_historial
    ADD CONSTRAINT uq_producto_costo_historial_version UNIQUE (producto_id, version);


--
-- Name: reporte_produccion_lote uq_reporte_produccion_transaccion; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reporte_produccion_lote
    ADD CONSTRAINT uq_reporte_produccion_transaccion UNIQUE (transaccion_almacen_id);


--
-- Name: ruta_proceso_cat uq_ruta_proceso_cat_categoria; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ruta_proceso_cat
    ADD CONSTRAINT uq_ruta_proceso_cat_categoria UNIQUE (categoria_id);


--
-- Name: ruta_proceso_cat_version uq_ruta_proceso_cat_version_number; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ruta_proceso_cat_version
    ADD CONSTRAINT uq_ruta_proceso_cat_version_number UNIQUE (ruta_proceso_cat_id, version_number);


--
-- Name: seguimiento_orden_area uq_soa_orden_nodo; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.seguimiento_orden_area
    ADD CONSTRAINT uq_soa_orden_nodo UNIQUE (orden_produccion_id, ruta_proceso_node_id);


--
-- Name: tab_accesos uq_tab_accesos_modulo_tab; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tab_accesos
    ADD CONSTRAINT uq_tab_accesos_modulo_tab UNIQUE (modulo_acceso_id, tab_id);


--
-- Name: users users_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_pkey PRIMARY KEY (id);


--
-- Name: vendedores vendedores_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.vendedores
    ADD CONSTRAINT vendedores_pkey PRIMARY KEY (cedula);


--
-- Name: idx_aoch_area_operativa_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_aoch_area_operativa_id ON public.area_operativa_categoria_habilitada USING btree (area_operativa_id);


--
-- Name: idx_aoch_categoria_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_aoch_categoria_id ON public.area_operativa_categoria_habilitada USING btree (categoria_id);


--
-- Name: idx_aocum_area_operativa_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_aocum_area_operativa_id ON public.area_operativa_categoria_unidad_medida USING btree (area_operativa_id);


--
-- Name: idx_aocum_categoria_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_aocum_categoria_id ON public.area_operativa_categoria_unidad_medida USING btree (categoria_id);


--
-- Name: idx_aocum_unidad_medida_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_aocum_unidad_medida_id ON public.area_operativa_categoria_unidad_medida USING btree (unidad_medida_area_operativa_id);


--
-- Name: idx_area_operativa_ruido_muestra_area_fecha; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_area_operativa_ruido_muestra_area_fecha ON public.area_operativa_ruido_muestra USING btree (area_operativa_id, fecha_muestra);


--
-- Name: idx_area_operativa_ruido_muestra_usuario_created_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_area_operativa_ruido_muestra_usuario_created_at ON public.area_operativa_ruido_muestra USING btree (usuario_id, created_at);


--
-- Name: idx_batch_record_ciclo_record_estado; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_batch_record_ciclo_record_estado ON public.batch_record_ciclo_revision USING btree (batch_record_id, estado, numero DESC);


--
-- Name: idx_batch_record_consumo_genealogia; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_batch_record_consumo_genealogia ON public.batch_record_consumo USING btree (lote_origen_id, batch_record_id) WHERE (lote_origen_id IS NOT NULL);


--
-- Name: idx_batch_record_consumo_record; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_batch_record_consumo_record ON public.batch_record_consumo USING btree (batch_record_id, registrado_en);


--
-- Name: idx_batch_record_correccion_record; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_batch_record_correccion_record ON public.batch_record_correccion USING btree (batch_record_id, corregida_en);


--
-- Name: idx_batch_record_decision_calidad_record; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_batch_record_decision_calidad_record ON public.batch_record_decision_calidad USING btree (batch_record_id, decidida_en);


--
-- Name: idx_batch_record_desviacion_record_estado; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_batch_record_desviacion_record_estado ON public.batch_record_desviacion USING btree (batch_record_id, estado);


--
-- Name: idx_batch_record_documento_record; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_batch_record_documento_record ON public.batch_record_documento USING btree (batch_record_id, tipo, version_documento DESC);


--
-- Name: idx_batch_record_estado; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_batch_record_estado ON public.batch_record USING btree (estado, creado_en);


--
-- Name: idx_batch_record_etapa_area_estado; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_batch_record_etapa_area_estado ON public.batch_record_etapa USING btree (area_operativa_id, estado);


--
-- Name: idx_batch_record_etapa_control_plantilla; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_batch_record_etapa_control_plantilla ON public.batch_record_etapa USING btree (control_proceso_plantilla_id) WHERE (control_proceso_plantilla_id IS NOT NULL);


--
-- Name: idx_batch_record_etapa_record; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_batch_record_etapa_record ON public.batch_record_etapa USING btree (batch_record_id, secuencia);


--
-- Name: idx_batch_record_firma_firmante; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_batch_record_firma_firmante ON public.batch_record_firma USING btree (firmante_id, firmado_en);


--
-- Name: idx_batch_record_firma_record; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_batch_record_firma_record ON public.batch_record_firma USING btree (batch_record_id, firmado_en);


--
-- Name: idx_batch_record_firma_revision; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_batch_record_firma_revision ON public.batch_record_firma USING btree (batch_record_revision_id, firmado_en) WHERE (batch_record_revision_id IS NOT NULL);


--
-- Name: idx_batch_record_producto; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_batch_record_producto ON public.batch_record USING btree (producto_resultado_id, creado_en);


--
-- Name: idx_batch_record_projection_task_due; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_batch_record_projection_task_due ON public.batch_record_projection_task USING btree (estado, proximo_intento_en, id);


--
-- Name: idx_batch_record_reapertura_record; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_batch_record_reapertura_record ON public.batch_record_solicitud_reapertura_rechazo USING btree (batch_record_id, solicitada_en DESC);


--
-- Name: idx_batch_record_revision_record; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_batch_record_revision_record ON public.batch_record_revision USING btree (batch_record_id, numero DESC);


--
-- Name: idx_batch_record_seccion_pendiente; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_batch_record_seccion_pendiente ON public.batch_record_seccion_correccion USING btree (batch_record_id, ciclo_revision_numero, estado);


--
-- Name: idx_bi_item_ocm_orden_producto; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_bi_item_ocm_orden_producto ON public.item_orden_compra USING btree (orden_compra_id, producto_id, item_id);


--
-- Name: idx_bi_ocm_pendientes_fecha; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_bi_ocm_pendientes_fecha ON public.orden_compra USING btree (fecha_emision, orden_compra_id) WHERE (estado = 2);


--
-- Name: idx_bi_op_abierta_antiguedad; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_bi_op_abierta_antiguedad ON public.ordenes_produccion USING btree (COALESCE(fecha_inicio, fecha_creacion), orden_id) WHERE ((estado_orden <> 2) AND (estado_orden <> '-1'::integer));


--
-- Name: idx_calidad_control_ejecucion_batch_record; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_calidad_control_ejecucion_batch_record ON public.calidad_control_proceso_ejecucion USING btree (batch_record_id);


--
-- Name: idx_calidad_control_ejecucion_etapa; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_calidad_control_ejecucion_etapa ON public.calidad_control_proceso_ejecucion USING btree (batch_record_etapa_id, fecha_registro);


--
-- Name: idx_carga_costos_item_lote_fila; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_carga_costos_item_lote_fila ON public.carga_costos_item USING btree (lote_id, fila_excel);


--
-- Name: idx_carga_costos_lote_expira; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_carga_costos_lote_expira ON public.carga_costos_lote USING btree (estado, expira_en);


--
-- Name: idx_carga_costos_lote_usuario_estado; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_carga_costos_lote_usuario_estado ON public.carga_costos_lote USING btree (usuario_id, estado, creado_en DESC);


--
-- Name: idx_carga_costos_propagacion_lote_nivel; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_carga_costos_propagacion_lote_nivel ON public.carga_costos_propagacion_item USING btree (lote_id, nivel, producto_id);


--
-- Name: idx_cierre_produccion_fecha; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_cierre_produccion_fecha ON public.cierre_produccion USING btree (fecha_produccion, cerrado_en);


--
-- Name: idx_control_aplicabilidad_area; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_control_aplicabilidad_area ON public.control_plan_aplicabilidad USING btree (area_operativa_id);


--
-- Name: idx_control_aplicabilidad_categoria; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_control_aplicabilidad_categoria ON public.control_plan_aplicabilidad USING btree (categoria_id);


--
-- Name: idx_control_aplicabilidad_producto; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_control_aplicabilidad_producto ON public.control_plan_aplicabilidad USING btree (producto_id);


--
-- Name: idx_control_desviacion_estado; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_control_desviacion_estado ON public.control_desviacion USING btree (ambito, estado, abierta_en);


--
-- Name: idx_control_ejecucion_requerido; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_control_ejecucion_requerido ON public.control_ejecucion USING btree (control_requerido_id, fecha_registro DESC);


--
-- Name: idx_control_idempotencia_creada_en; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_control_idempotencia_creada_en ON public.control_idempotencia USING btree (creada_en);


--
-- Name: idx_control_plan_aplicabilidad_frontend_node; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_control_plan_aplicabilidad_frontend_node ON public.control_plan_aplicabilidad USING btree (frontend_node_id);


--
-- Name: idx_control_requerido_batch; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_control_requerido_batch ON public.control_requerido USING btree (batch_record_id, punto_exigencia_snapshot);


--
-- Name: idx_control_requerido_pendiente; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_control_requerido_pendiente ON public.control_requerido USING btree (ambito_snapshot, estado, creado_en);


--
-- Name: idx_doc_tran_personal_integrante; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_doc_tran_personal_integrante ON public.doc_tran_de_personal USING btree (integrante_id);


--
-- Name: idx_firma_visual_usuario_historial; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_firma_visual_usuario_historial ON public.firma_visual_usuario_version USING btree (usuario_id, version DESC);


--
-- Name: idx_insumos_input_producto; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_insumos_input_producto ON public.insumos USING btree (input_producto_id);


--
-- Name: idx_insumos_output_producto; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_insumos_output_producto ON public.insumos USING btree (output_producto_id);


--
-- Name: idx_jornada_laboral_bloque_version; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_jornada_laboral_bloque_version ON public.jornada_laboral_bloque USING btree (jornada_laboral_version_id);


--
-- Name: idx_lead_time_proveedor_kpi_proveedor; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_lead_time_proveedor_kpi_proveedor ON public.lead_time_proveedor_kpi USING btree (proveedor_pk);


--
-- Name: idx_lote_orden_fabricacion; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_lote_orden_fabricacion ON public.lote USING btree (orden_fabricacion_id);


--
-- Name: idx_lote_producto_estado_calidad; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_lote_producto_estado_calidad ON public.lote USING btree (producto_id, estado_calidad);


--
-- Name: idx_material_ficha_tecnica_historial; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_material_ficha_tecnica_historial ON public.material_ficha_tecnica_version USING btree (material_id, version DESC);


--
-- Name: idx_mision_vision_origen_version; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_mision_vision_origen_version ON public.mision_vision_version USING btree (origen_version_id);


--
-- Name: idx_mision_vision_valor_version; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_mision_vision_valor_version ON public.mision_vision_valor USING btree (mision_vision_version_id);


--
-- Name: idx_modulo_accesos_user_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_modulo_accesos_user_id ON public.modulo_accesos USING btree (user_id);


--
-- Name: idx_monitoreo_evento_correlacion; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_monitoreo_evento_correlacion ON public.monitoreo_evento_error USING btree (correlacion_id) WHERE (correlacion_id IS NOT NULL);


--
-- Name: idx_monitoreo_evento_grupo_fecha; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_monitoreo_evento_grupo_fecha ON public.monitoreo_evento_error USING btree (grupo_error_id, ocurrido_en DESC, id);


--
-- Name: idx_monitoreo_evento_modulo_grupo; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_monitoreo_evento_modulo_grupo ON public.monitoreo_evento_error USING btree (modulo, grupo_error_id, ocurrido_en DESC);


--
-- Name: idx_monitoreo_evento_recibido; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_monitoreo_evento_recibido ON public.monitoreo_evento_error USING btree (recibido_en);


--
-- Name: idx_monitoreo_grupo_estado_fecha; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_monitoreo_grupo_estado_fecha ON public.monitoreo_grupo_error USING btree (estado, ultima_aparicion DESC, id);


--
-- Name: idx_movimientos_almacen_fecha_producto; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_movimientos_almacen_fecha_producto ON public.movimientos USING btree (almacen, fecha_movimiento, producto_id);


--
-- Name: idx_movimientos_almacen_producto; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_movimientos_almacen_producto ON public.movimientos USING btree (almacen, producto_id);


--
-- Name: idx_movimientos_area_operativa; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_movimientos_area_operativa ON public.movimientos USING btree (area_operativa_id);


--
-- Name: idx_movimientos_tipo_fecha_producto; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_movimientos_tipo_fecha_producto ON public.movimientos USING btree (tipo_movimiento, fecha_movimiento, producto_id);


--
-- Name: idx_movimientos_transaccion_producto_tipo; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_movimientos_transaccion_producto_tipo ON public.movimientos USING btree (transaccion_id, producto_id, tipo_movimiento);


--
-- Name: idx_mps_of_detalle_programa; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_mps_of_detalle_programa ON public.mps_fabricacion_detalle USING btree (mps_id, posicion);


--
-- Name: idx_mps_sem_dia_fecha; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_mps_sem_dia_fecha ON public.mps_semanal_dia USING btree (fecha);


--
-- Name: idx_mps_sem_dia_mps_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_mps_sem_dia_mps_id ON public.mps_semanal_dia USING btree (mps_id);


--
-- Name: idx_mps_sem_item_dia_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_mps_sem_item_dia_id ON public.mps_semanal_item USING btree (mps_dia_id);


--
-- Name: idx_mps_sem_item_estado; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_mps_sem_item_estado ON public.mps_semanal_item USING btree (estado);


--
-- Name: idx_mps_sem_item_mps_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_mps_sem_item_mps_id ON public.mps_semanal_item USING btree (mps_id);


--
-- Name: idx_mps_sem_item_terminado; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_mps_sem_item_terminado ON public.mps_semanal_item USING btree (terminado_id);


--
-- Name: idx_mps_sem_lote_estado; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_mps_sem_lote_estado ON public.mps_semanal_lote_planificado USING btree (estado);


--
-- Name: idx_mps_sem_lote_item_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_mps_sem_lote_item_id ON public.mps_semanal_lote_planificado USING btree (mps_item_id);


--
-- Name: idx_mps_sem_obs_estado; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_mps_sem_obs_estado ON public.mps_semanal_observacion USING btree (estado);


--
-- Name: idx_mps_sem_obs_mps_estado; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_mps_sem_obs_mps_estado ON public.mps_semanal_observacion USING btree (mps_id, estado);


--
-- Name: idx_mps_sem_obs_mps_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_mps_sem_obs_mps_id ON public.mps_semanal_observacion USING btree (mps_id);


--
-- Name: idx_mps_sem_obs_mps_revision; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_mps_sem_obs_mps_revision ON public.mps_semanal_observacion USING btree (mps_id, revision_mps);


--
-- Name: idx_mps_sem_obs_tipo; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_mps_sem_obs_tipo ON public.mps_semanal_observacion USING btree (tipo);


--
-- Name: idx_mps_week_start_date; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_mps_week_start_date ON public.master_production_schedule_semanal USING btree (week_start_date);


--
-- Name: idx_ocaf_empresa_identidad_legal_version; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_ocaf_empresa_identidad_legal_version ON public.orden_compra_activo USING btree (empresa_identidad_legal_version_id);


--
-- Name: idx_ocaf_empresa_logo_documental_version; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_ocaf_empresa_logo_documental_version ON public.orden_compra_activo USING btree (empresa_logo_documental_version_id);


--
-- Name: idx_ocm_cierre_automatico; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_ocm_cierre_automatico ON public.orden_compra USING btree (fecha_recepcion_completa, orden_compra_id) WHERE ((estado = 2) AND (fecha_recepcion_completa IS NOT NULL));


--
-- Name: idx_of_estado_lanzamiento; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_of_estado_lanzamiento ON public.orden_fabricacion USING btree (estado, fecha_lanzamiento);


--
-- Name: idx_of_operacion_area_estado; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_of_operacion_area_estado ON public.orden_fabricacion_operacion USING btree (area_operativa_id, estado, fecha_estado_actual);


--
-- Name: idx_of_operacion_dependencia_sucesora; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_of_operacion_dependencia_sucesora ON public.orden_fabricacion_operacion_dependencia USING btree (operacion_sucesora_id);


--
-- Name: idx_of_operacion_evento_operacion; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_of_operacion_evento_operacion ON public.orden_fabricacion_operacion_evento USING btree (operacion_id, fecha_evento, id);


--
-- Name: idx_of_semana; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_of_semana ON public.orden_fabricacion USING btree (COALESCE(fecha_lanzamiento, fecha_creacion), orden_fabricacion_id);


--
-- Name: idx_orden_compra_empresa_identidad_legal_version; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_orden_compra_empresa_identidad_legal_version ON public.orden_compra USING btree (empresa_identidad_legal_version_id);


--
-- Name: idx_orden_compra_empresa_logo_documental_version; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_orden_compra_empresa_logo_documental_version ON public.orden_compra USING btree (empresa_logo_documental_version_id);


--
-- Name: idx_orden_compra_usuario_creador; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_orden_compra_usuario_creador ON public.orden_compra USING btree (usuario_creador_id);


--
-- Name: idx_orden_compra_usuario_liberador; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_orden_compra_usuario_liberador ON public.orden_compra USING btree (usuario_liberador_id);


--
-- Name: idx_orden_fabricacion_fechas; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_orden_fabricacion_fechas ON public.orden_fabricacion USING btree (fecha_lanzamiento, fecha_final_planificada);


--
-- Name: idx_orden_fabricacion_producto_estado; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_orden_fabricacion_producto_estado ON public.orden_fabricacion USING btree (semiterminado_id, estado);


--
-- Name: idx_orden_produccion_cancelada_por; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_orden_produccion_cancelada_por ON public.ordenes_produccion USING btree (cancelada_por_id);


--
-- Name: idx_orden_produccion_manufacturing_version; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_orden_produccion_manufacturing_version ON public.ordenes_produccion USING btree (manufacturing_version_id);


--
-- Name: idx_ordenes_produccion_estado_dispensacion_materiales; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_ordenes_produccion_estado_dispensacion_materiales ON public.ordenes_produccion USING btree (estado_dispensacion_materiales);


--
-- Name: idx_ordenes_produccion_fecha_final_planificada; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_ordenes_produccion_fecha_final_planificada ON public.ordenes_produccion USING btree (fecha_final_planificada);


--
-- Name: idx_ordenes_produccion_jornada_laboral_version; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_ordenes_produccion_jornada_laboral_version ON public.ordenes_produccion USING btree (jornada_laboral_version_id);


--
-- Name: idx_ordenes_produccion_mps_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_ordenes_produccion_mps_id ON public.ordenes_produccion USING btree (mps_id);


--
-- Name: idx_ordenes_produccion_mps_lote_planificado; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_ordenes_produccion_mps_lote_planificado ON public.ordenes_produccion USING btree (mps_lote_planificado_id);


--
-- Name: idx_ordenes_produccion_ruta_version; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_ordenes_produccion_ruta_version ON public.ordenes_produccion USING btree (ruta_proceso_cat_version_id);


--
-- Name: idx_pf_edge_frontend_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_pf_edge_frontend_id ON public.proceso_fabricacion_edge USING btree (frontend_id);


--
-- Name: idx_pf_edge_proceso_completo; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_pf_edge_proceso_completo ON public.proceso_fabricacion_edge USING btree (proceso_completo_id);


--
-- Name: idx_pf_nodo_area_operativa; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_pf_nodo_area_operativa ON public.proceso_fabricacion_nodo USING btree (area_operativa_id);


--
-- Name: idx_pf_nodo_frontend_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_pf_nodo_frontend_id ON public.proceso_fabricacion_nodo USING btree (frontend_id);


--
-- Name: idx_pf_nodo_insumo; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_pf_nodo_insumo ON public.proceso_fabricacion_nodo USING btree (insumo_id);


--
-- Name: idx_pf_nodo_proceso; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_pf_nodo_proceso ON public.proceso_fabricacion_nodo USING btree (proceso_id);


--
-- Name: idx_pf_nodo_proceso_completo; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_pf_nodo_proceso_completo ON public.proceso_fabricacion_nodo USING btree (proceso_completo_id);


--
-- Name: idx_proceso_documento_historial; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_proceso_documento_historial ON public.proceso_produccion_documento_version USING btree (proceso_id, version DESC);


--
-- Name: idx_producto_costo_historial_lote; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_producto_costo_historial_lote ON public.producto_costo_historial USING btree (carga_costos_lote_id);


--
-- Name: idx_producto_costo_historial_origen; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_producto_costo_historial_origen ON public.producto_costo_historial USING btree (origen, cambiado_en DESC);


--
-- Name: idx_producto_costo_historial_producto_fecha; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_producto_costo_historial_producto_fecha ON public.producto_costo_historial USING btree (producto_id, cambiado_en DESC);


--
-- Name: idx_proveedor_material_lead_time_metric_producto; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_proveedor_material_lead_time_metric_producto ON public.proveedor_material_lead_time_metric USING btree (producto_id);


--
-- Name: idx_proveedor_material_lead_time_metric_proveedor; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_proveedor_material_lead_time_metric_proveedor ON public.proveedor_material_lead_time_metric USING btree (proveedor_pk);


--
-- Name: idx_registro_hora_extra_estado; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_registro_hora_extra_estado ON public.registro_hora_extra USING btree (estado);


--
-- Name: idx_registro_hora_extra_fecha; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_registro_hora_extra_fecha ON public.registro_hora_extra USING btree (fecha DESC);


--
-- Name: idx_registro_hora_extra_integrante_fecha; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_registro_hora_extra_integrante_fecha ON public.registro_hora_extra USING btree (integrante_id, fecha DESC);


--
-- Name: idx_reporte_produccion_cierre; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_reporte_produccion_cierre ON public.reporte_produccion_lote USING btree (cierre_produccion_id);


--
-- Name: idx_reporte_produccion_pendiente_fecha; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_reporte_produccion_pendiente_fecha ON public.reporte_produccion_lote USING btree (fecha_produccion, reportado_en) WHERE ((estado)::text = 'PENDIENTE'::text);


--
-- Name: idx_ruta_proceso_cat_categoria; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_ruta_proceso_cat_categoria ON public.ruta_proceso_cat USING btree (categoria_id);


--
-- Name: idx_ruta_proceso_cat_version_ruta; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_ruta_proceso_cat_version_ruta ON public.ruta_proceso_cat_version USING btree (ruta_proceso_cat_id);


--
-- Name: idx_ruta_proceso_edge_frontend_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_ruta_proceso_edge_frontend_id ON public.ruta_proceso_edge USING btree (frontend_id);


--
-- Name: idx_ruta_proceso_edge_source_node; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_ruta_proceso_edge_source_node ON public.ruta_proceso_edge USING btree (source_node_id);


--
-- Name: idx_ruta_proceso_edge_target_node; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_ruta_proceso_edge_target_node ON public.ruta_proceso_edge USING btree (target_node_id);


--
-- Name: idx_ruta_proceso_edge_version; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_ruta_proceso_edge_version ON public.ruta_proceso_edge USING btree (ruta_proceso_cat_version_id);


--
-- Name: idx_ruta_proceso_node_area_operativa; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_ruta_proceso_node_area_operativa ON public.ruta_proceso_node USING btree (area_operativa_id);


--
-- Name: idx_ruta_proceso_node_frontend_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_ruta_proceso_node_frontend_id ON public.ruta_proceso_node USING btree (frontend_id);


--
-- Name: idx_ruta_proceso_node_proceso_produccion; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_ruta_proceso_node_proceso_produccion ON public.ruta_proceso_node USING btree (proceso_produccion_id);


--
-- Name: idx_ruta_proceso_node_version; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_ruta_proceso_node_version ON public.ruta_proceso_node USING btree (ruta_proceso_cat_version_id);


--
-- Name: idx_seguimiento_orden_area_poe_documento_version; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_seguimiento_orden_area_poe_documento_version ON public.seguimiento_orden_area USING btree (poe_documento_version_id);


--
-- Name: idx_semana_mps_anio_numero; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_semana_mps_anio_numero ON public.semana_mps USING btree (anio_semana, numero_semana);


--
-- Name: idx_soa_area_estado; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_soa_area_estado ON public.seguimiento_orden_area USING btree (area_operativa_id, estado);


--
-- Name: idx_soa_area_estado_completado; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_soa_area_estado_completado ON public.seguimiento_orden_area USING btree (area_operativa_id, estado, fecha_completado DESC NULLS LAST, id DESC);


--
-- Name: idx_soa_evento_revertido; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_soa_evento_revertido ON public.seguimiento_orden_area_evento USING btree (evento_revertido_id);


--
-- Name: idx_soa_evento_seguimiento_fecha; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_soa_evento_seguimiento_fecha ON public.seguimiento_orden_area_evento USING btree (seguimiento_orden_area_id, fecha_evento, id);


--
-- Name: idx_soa_evento_tipo; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_soa_evento_tipo ON public.seguimiento_orden_area_evento USING btree (tipo_evento);


--
-- Name: idx_soa_evento_usuario; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_soa_evento_usuario ON public.seguimiento_orden_area_evento USING btree (usuario_id);


--
-- Name: idx_soa_orden; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_soa_orden ON public.seguimiento_orden_area USING btree (orden_produccion_id);


--
-- Name: idx_soa_usuario; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_soa_usuario ON public.seguimiento_orden_area USING btree (usuario_reporta_id);


--
-- Name: idx_transaccion_causante; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_transaccion_causante ON public.transaccion_almacen USING btree (tipo_entidad_causante, id_entidad_causante, transaccion_id);


--
-- Name: idx_umao_area_operativa_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_umao_area_operativa_id ON public.unidad_medida_area_operativa USING btree (area_operativa_id);


--
-- Name: uk_mps_sem_item_dia_terminado_activo; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_mps_sem_item_dia_terminado_activo ON public.mps_semanal_item USING btree (mps_dia_id, terminado_id) WHERE ((estado)::text <> 'CANCELADO'::text);


--
-- Name: uq_batch_record_correccion_of_evento; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_batch_record_correccion_of_evento ON public.batch_record_correccion USING btree (orden_fabricacion_evento_correccion_id) WHERE (orden_fabricacion_evento_correccion_id IS NOT NULL);


--
-- Name: uq_batch_record_decision_ciclo; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_batch_record_decision_ciclo ON public.batch_record_decision_calidad USING btree (ciclo_revision_id) WHERE (ciclo_revision_id IS NOT NULL);


--
-- Name: uq_batch_record_etapa_of_evento; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_batch_record_etapa_of_evento ON public.batch_record_etapa USING btree (orden_fabricacion_evento_origen_id) WHERE (orden_fabricacion_evento_origen_id IS NOT NULL);


--
-- Name: uq_batch_record_etapa_of_operacion; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_batch_record_etapa_of_operacion ON public.batch_record_etapa USING btree (orden_fabricacion_operacion_id) WHERE (orden_fabricacion_operacion_id IS NOT NULL);


--
-- Name: uq_batch_record_etapa_seguimiento; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_batch_record_etapa_seguimiento ON public.batch_record_etapa USING btree (seguimiento_orden_area_id) WHERE (seguimiento_orden_area_id IS NOT NULL);


--
-- Name: uq_batch_record_firma_evento; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_batch_record_firma_evento ON public.batch_record_firma USING btree (seguimiento_evento_id) WHERE (seguimiento_evento_id IS NOT NULL);


--
-- Name: uq_batch_record_firma_of_evento; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_batch_record_firma_of_evento ON public.batch_record_firma USING btree (orden_fabricacion_evento_id) WHERE (orden_fabricacion_evento_id IS NOT NULL);


--
-- Name: uq_batch_record_reapertura_pendiente; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_batch_record_reapertura_pendiente ON public.batch_record_solicitud_reapertura_rechazo USING btree (batch_record_id) WHERE ((estado)::text = 'PENDIENTE'::text);


--
-- Name: uq_calidad_plantilla_area_borrador; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_calidad_plantilla_area_borrador ON public.calidad_control_proceso_plantilla USING btree (area_operativa_id) WHERE ((estado)::text = 'BORRADOR'::text);


--
-- Name: uq_calidad_plantilla_area_vigente; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_calidad_plantilla_area_vigente ON public.calidad_control_proceso_plantilla USING btree (area_operativa_id) WHERE ((estado)::text = 'VIGENTE'::text);


--
-- Name: uq_control_plan_version_borrador; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_control_plan_version_borrador ON public.control_plan_version USING btree (plan_id) WHERE ((estado)::text = 'BORRADOR'::text);


--
-- Name: uq_control_plan_version_vigente; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_control_plan_version_vigente ON public.control_plan_version USING btree (plan_id) WHERE ((estado)::text = 'VIGENTE'::text);


--
-- Name: uq_control_requerido_batch_aplicacion; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_control_requerido_batch_aplicacion ON public.control_requerido USING btree (batch_record_id, version_id, COALESCE(batch_record_etapa_id, (0)::bigint)) WHERE ((origen)::text = 'BATCH_RECORD'::text);


--
-- Name: uq_empresa_identidad_legal_vigente; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_empresa_identidad_legal_vigente ON public.empresa_identidad_legal_version USING btree (estado) WHERE ((estado)::text = 'VIGENTE'::text);


--
-- Name: uq_empresa_logo_documental_vigente; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_empresa_logo_documental_vigente ON public.empresa_logo_documental_version USING btree (estado) WHERE ((estado)::text = 'VIGENTE'::text);


--
-- Name: uq_firma_visual_usuario_vigente; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_firma_visual_usuario_vigente ON public.firma_visual_usuario_version USING btree (usuario_id) WHERE ((estado)::text = 'VIGENTE'::text);


--
-- Name: uq_jornada_laboral_vigente; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_jornada_laboral_vigente ON public.jornada_laboral_version USING btree (estado) WHERE ((estado)::text = 'VIGENTE'::text);


--
-- Name: uq_material_ficha_tecnica_vigente; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_material_ficha_tecnica_vigente ON public.material_ficha_tecnica_version USING btree (material_id) WHERE ((estado)::text = 'VIGENTE'::text);


--
-- Name: uq_mision_vision_vigente; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_mision_vision_vigente ON public.mision_vision_version USING btree (estado) WHERE ((estado)::text = 'VIGENTE'::text);


--
-- Name: uq_of_orden_origen_semi; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_of_orden_origen_semi ON public.orden_fabricacion USING btree (orden_produccion_origen_id, semiterminado_id) WHERE (orden_produccion_origen_id IS NOT NULL);


--
-- Name: uq_proceso_documento_vigente; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_proceso_documento_vigente ON public.proceso_produccion_documento_version USING btree (proceso_id) WHERE ((estado)::text = 'VIGENTE'::text);


--
-- Name: uq_reporte_produccion_lote_no_anulado; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_reporte_produccion_lote_no_anulado ON public.reporte_produccion_lote USING btree (lote_id) WHERE ((estado)::text <> 'ANULADO'::text);


--
-- Name: uq_reporte_produccion_op_no_anulado; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_reporte_produccion_op_no_anulado ON public.reporte_produccion_lote USING btree (orden_produccion_id) WHERE ((estado)::text <> 'ANULADO'::text);


--
-- Name: uq_reporte_produccion_seguimiento_no_anulado; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_reporte_produccion_seguimiento_no_anulado ON public.reporte_produccion_lote USING btree (seguimiento_orden_area_id) WHERE ((estado)::text <> 'ANULADO'::text);


--
-- Name: uq_ruta_proceso_cat_version_vigente; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_ruta_proceso_cat_version_vigente ON public.ruta_proceso_cat_version USING btree (ruta_proceso_cat_id) WHERE ((estado)::text = 'VIGENTE'::text);


--
-- Name: insumos trg_insumos_receta_costos_revision; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_insumos_receta_costos_revision AFTER INSERT OR DELETE OR UPDATE OR TRUNCATE ON public.insumos FOR EACH STATEMENT EXECUTE FUNCTION public.incrementar_receta_costos_revision();


--
-- Name: documento_baja_activo FK1f3m97k9e6er9ibuvu4lf9nha; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.documento_baja_activo
    ADD CONSTRAINT "FK1f3m97k9e6er9ibuvu4lf9nha" FOREIGN KEY (responsable_id) REFERENCES public.integrante_personal(id);


--
-- Name: planificacion_recurso FK1jo1o7o4xapx7yrrdvmpckdyq; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.planificacion_recurso
    ADD CONSTRAINT "FK1jo1o7o4xapx7yrrdvmpckdyq" FOREIGN KEY (planificacion_id) REFERENCES public.planificacion_produccion(id);


--
-- Name: productos FK1r61ljb1lcv988omr5may79fs; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.productos
    ADD CONSTRAINT "FK1r61ljb1lcv988omr5may79fs" FOREIGN KEY (categoria_id) REFERENCES public.categoria(categoria_id);


--
-- Name: avance_tarea FK28al3spqu7p3134ohdr5140np; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.avance_tarea
    ADD CONSTRAINT "FK28al3spqu7p3134ohdr5140np" FOREIGN KEY (tarea_id) REFERENCES public.tarea(id);


--
-- Name: movimientos FK2egr1p3qc5b0qv6al50y4284g; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.movimientos
    ADD CONSTRAINT "FK2egr1p3qc5b0qv6al50y4284g" FOREIGN KEY (area_operativa_id) REFERENCES public.area_operativa(area_id);


--
-- Name: documento_baja_activo FK413v76h8se84t6xnklyy6ro2n; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.documento_baja_activo
    ADD CONSTRAINT "FK413v76h8se84t6xnklyy6ro2n" FOREIGN KEY (activo_id) REFERENCES public.activo(id);


--
-- Name: proceso_recurso FK41iuhmo4h1y42ynvohpabbjrj; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.proceso_recurso
    ADD CONSTRAINT "FK41iuhmo4h1y42ynvohpabbjrj" FOREIGN KEY (recurso_id) REFERENCES public.recurso_produccion(id);


--
-- Name: mantenimiento_activo FK4osirhpbm3bbbck4rxvgoy2wk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.mantenimiento_activo
    ADD CONSTRAINT "FK4osirhpbm3bbbck4rxvgoy2wk" FOREIGN KEY (activo_id) REFERENCES public.activo(id);


--
-- Name: ordenes_produccion FK4y2afw491k6r2ghceryilu01n; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ordenes_produccion
    ADD CONSTRAINT "FK4y2afw491k6r2ghceryilu01n" FOREIGN KEY (proceso_completo_id) REFERENCES public.procesos_produccion(proceso_completo_id);


--
-- Name: facturas_compras FK5i9qqow2jxybkr4adl02ik52i; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.facturas_compras
    ADD CONSTRAINT "FK5i9qqow2jxybkr4adl02ik52i" FOREIGN KEY (proveedor_pk) REFERENCES public.proveedores(pk);


--
-- Name: password_reset_tokens FK60mt0gt67cab6qg27c6qb503u; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.password_reset_tokens
    ADD CONSTRAINT "FK60mt0gt67cab6qg27c6qb503u" FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: ordenes_produccion FK630qah916fbis2m8md5nljadw; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ordenes_produccion
    ADD CONSTRAINT "FK630qah916fbis2m8md5nljadw" FOREIGN KEY (responsable_id) REFERENCES public.vendedores(cedula);


--
-- Name: tarea FK6ynxtsfmo6dqai81i9v7kt38u; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tarea
    ADD CONSTRAINT "FK6ynxtsfmo6dqai81i9v7kt38u" FOREIGN KEY (usuario_responsable_id) REFERENCES public.users(id);


--
-- Name: traslado_activo FK7113628vjmiiyqumta1av0fna; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.traslado_activo
    ADD CONSTRAINT "FK7113628vjmiiyqumta1av0fna" FOREIGN KEY (activo_id) REFERENCES public.activo(id);


--
-- Name: depreciacion_activo FK7jiw0p3ufe1i7fsnk5ngqlm82; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.depreciacion_activo
    ADD CONSTRAINT "FK7jiw0p3ufe1i7fsnk5ngqlm82" FOREIGN KEY (activo_id) REFERENCES public.activo(id);


--
-- Name: integrante_personal FK7kwch3uixpy3tu61a9cb7kssq; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.integrante_personal
    ADD CONSTRAINT "FK7kwch3uixpy3tu61a9cb7kssq" FOREIGN KEY (usuario_id) REFERENCES public.users(id);


--
-- Name: items_factura_compra FK7otfcwe94gf5w3sy5ptwo7bdd; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.items_factura_compra
    ADD CONSTRAINT "FK7otfcwe94gf5w3sy5ptwo7bdd" FOREIGN KEY (factura_compra_id) REFERENCES public.facturas_compras(factura_compra_id);


--
-- Name: insumos_empaque FK7xvnpltbpa595tfm8dw4b2pje; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.insumos_empaque
    ADD CONSTRAINT "FK7xvnpltbpa595tfm8dw4b2pje" FOREIGN KEY (producto_id) REFERENCES public.case_pack(id);


--
-- Name: incorporacion_activo_line FK80ah6c0407rpcm8mk2ovcop1i; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.incorporacion_activo_line
    ADD CONSTRAINT "FK80ah6c0407rpcm8mk2ovcop1i" FOREIGN KEY (activo_id) REFERENCES public.activo(id);


--
-- Name: items_orden_venta FK8be4l6pyyro6j6hcnkas6wyot; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.items_orden_venta
    ADD CONSTRAINT "FK8be4l6pyyro6j6hcnkas6wyot" FOREIGN KEY (orden_venta_id) REFERENCES public.ordenes_venta(orden_venta_id);


--
-- Name: orden_compra FK8hklqboeevrpn4yidyf1f0pyn; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.orden_compra
    ADD CONSTRAINT "FK8hklqboeevrpn4yidyf1f0pyn" FOREIGN KEY (proveedor_pk) REFERENCES public.proveedores(pk);


--
-- Name: incorporacion_activo_header FKajp8jwoxlpsf0aowc35reri13; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.incorporacion_activo_header
    ADD CONSTRAINT "FKajp8jwoxlpsf0aowc35reri13" FOREIGN KEY (asiento_contable_id) REFERENCES public.asiento_contable(id);


--
-- Name: lote FKas1hqsqyrjtuhcgdtagft8uom; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lote
    ADD CONSTRAINT "FKas1hqsqyrjtuhcgdtagft8uom" FOREIGN KEY (orden_compra_id) REFERENCES public.orden_compra(orden_compra_id);


--
-- Name: ordenes_venta FKatw2137fcafgon4bmeylu024q; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ordenes_venta
    ADD CONSTRAINT "FKatw2137fcafgon4bmeylu024q" FOREIGN KEY (cliente_id) REFERENCES public.clientes(cliente_id);


--
-- Name: vendedores FKbh6oltkwv11cavrcvwlnfkjk0; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.vendedores
    ADD CONSTRAINT "FKbh6oltkwv11cavrcvwlnfkjk0" FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: factura_compra_activo FKbx5n8bqph41vf9ib7lcxy8y4n; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.factura_compra_activo
    ADD CONSTRAINT "FKbx5n8bqph41vf9ib7lcxy8y4n" FOREIGN KEY (orden_compra_activo_id) REFERENCES public.orden_compra_activo(orden_compra_activo_id);


--
-- Name: movimientos FKcnn8f0967g1ae8f6x18trqudf; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.movimientos
    ADD CONSTRAINT "FKcnn8f0967g1ae8f6x18trqudf" FOREIGN KEY (transaccion_id) REFERENCES public.transaccion_almacen(transaccion_id);


--
-- Name: transaccion_almacen FKd3rkcstsotxkuoficellnlcq3; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.transaccion_almacen
    ADD CONSTRAINT "FKd3rkcstsotxkuoficellnlcq3" FOREIGN KEY (usuario_aprobador_id) REFERENCES public.users(id);


--
-- Name: activo FKdnxagq6eapwuxi06gb9rlij0i; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.activo
    ADD CONSTRAINT "FKdnxagq6eapwuxi06gb9rlij0i" FOREIGN KEY (tipo_recurso_id) REFERENCES public.recurso_produccion(id);


--
-- Name: movimientos FKed2l482s42pj30ur9l4t1lorh; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.movimientos
    ADD CONSTRAINT "FKed2l482s42pj30ur9l4t1lorh" FOREIGN KEY (lote_id) REFERENCES public.lote(id);


--
-- Name: tarea FKeyvjl3bnjrf3d2ewdcwaxrr5l; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tarea
    ADD CONSTRAINT "FKeyvjl3bnjrf3d2ewdcwaxrr5l" FOREIGN KEY (usuarios_seguimiento) REFERENCES public.users(id);


--
-- Name: area_operativa FKfha2mupjkqtj33n2mocdtol4p; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.area_operativa
    ADD CONSTRAINT "FKfha2mupjkqtj33n2mocdtol4p" FOREIGN KEY (responsable_id) REFERENCES public.users(id);


--
-- Name: item_orden_compra_activo FKfv39h2lw7lbqtxog9r7bk5g3h; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.item_orden_compra_activo
    ADD CONSTRAINT "FKfv39h2lw7lbqtxog9r7bk5g3h" FOREIGN KEY (orden_compra_activo_id) REFERENCES public.orden_compra_activo(orden_compra_activo_id);


--
-- Name: orden_compra_activo FKfvp8acu88fc11wrrpe00wm8g2; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.orden_compra_activo
    ADD CONSTRAINT "FKfvp8acu88fc11wrrpe00wm8g2" FOREIGN KEY (proveedor_pk) REFERENCES public.proveedores(pk);


--
-- Name: facturas_venta FKg82vaybk1s6dj2gg1yp00a1xu; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.facturas_venta
    ADD CONSTRAINT "FKg82vaybk1s6dj2gg1yp00a1xu" FOREIGN KEY (orden_venta_id) REFERENCES public.ordenes_venta(orden_venta_id);


--
-- Name: traslado_activo FKgejcjeoyuvp7yitmhyaj91gk9; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.traslado_activo
    ADD CONSTRAINT "FKgejcjeoyuvp7yitmhyaj91gk9" FOREIGN KEY (responsable_anterior_id) REFERENCES public.integrante_personal(id);


--
-- Name: cargo FKgrmufknx9iyvs8nb7vsafvps1; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cargo
    ADD CONSTRAINT "FKgrmufknx9iyvs8nb7vsafvps1" FOREIGN KEY (usuario_id) REFERENCES public.users(id);


--
-- Name: incorporacion_activo_line FKhmrklntbkgn4lw18t8h4y24jl; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.incorporacion_activo_line
    ADD CONSTRAINT "FKhmrklntbkgn4lw18t8h4y24jl" FOREIGN KEY (incorporacion_id) REFERENCES public.incorporacion_activo_header(incorporacion_id);


--
-- Name: linea_asiento_contable FKhqqmf7ox01xx3fpnyy06p0gkd; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.linea_asiento_contable
    ADD CONSTRAINT "FKhqqmf7ox01xx3fpnyy06p0gkd" FOREIGN KEY (asiento_id) REFERENCES public.asiento_contable(id);


--
-- Name: item_orden_compra FKiacgputccnoy6ph7gee06a1n8; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.item_orden_compra
    ADD CONSTRAINT "FKiacgputccnoy6ph7gee06a1n8" FOREIGN KEY (orden_compra_id) REFERENCES public.orden_compra(orden_compra_id);


--
-- Name: items_factura_venta FKjcb18ma87m3wytcgxumvfmr9g; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.items_factura_venta
    ADD CONSTRAINT "FKjcb18ma87m3wytcgxumvfmr9g" FOREIGN KEY (factura_venta_id) REFERENCES public.facturas_venta(factura_venta_id);


--
-- Name: lote FKjmnd3phmclsg1kjltthvvykws; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lote
    ADD CONSTRAINT "FKjmnd3phmclsg1kjltthvvykws" FOREIGN KEY (orden_produccion_id) REFERENCES public.ordenes_produccion(orden_id);


--
-- Name: transaccion_almacen_usuarios_realizadores FKk4wjhjudfn8iicysisg5jlb8v; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.transaccion_almacen_usuarios_realizadores
    ADD CONSTRAINT "FKk4wjhjudfn8iicysisg5jlb8v" FOREIGN KEY (transaccion_id) REFERENCES public.transaccion_almacen(transaccion_id);


--
-- Name: transaccion_almacen_usuarios_realizadores FKkg5kao9v3fltybxyjo798y8lr; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.transaccion_almacen_usuarios_realizadores
    ADD CONSTRAINT "FKkg5kao9v3fltybxyjo798y8lr" FOREIGN KEY (usuario_id) REFERENCES public.users(id);


--
-- Name: activo FKkyhp64g2jcbct789gf5i4xjtu; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.activo
    ADD CONSTRAINT "FKkyhp64g2jcbct789gf5i4xjtu" FOREIGN KEY (responsable_id) REFERENCES public.integrante_personal(id);


--
-- Name: proceso_recurso FKl3w42su2v4uen9s2f9uoy5b4j; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.proceso_recurso
    ADD CONSTRAINT "FKl3w42su2v4uen9s2f9uoy5b4j" FOREIGN KEY (proceso_id) REFERENCES public.proceso_produccion(proceso_id);


--
-- Name: transaccion_almacen FKla2xgsfncrqd4mhd3cp3suukl; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.transaccion_almacen
    ADD CONSTRAINT "FKla2xgsfncrqd4mhd3cp3suukl" FOREIGN KEY (asiento_contable_id) REFERENCES public.asiento_contable(id);


--
-- Name: traslado_activo FKlpscvfeev1ptvh7clkr377opn; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.traslado_activo
    ADD CONSTRAINT "FKlpscvfeev1ptvh7clkr377opn" FOREIGN KEY (responsable_nuevo_id) REFERENCES public.integrante_personal(id);


--
-- Name: facturas_venta FKmbry58nyt6h2h9n2m3ojtmq45; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.facturas_venta
    ADD CONSTRAINT "FKmbry58nyt6h2h9n2m3ojtmq45" FOREIGN KEY (cliente_id) REFERENCES public.clientes(cliente_id);


--
-- Name: factura_compra_activo FKmeh38eej28nfq2uxv52jpkolp; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.factura_compra_activo
    ADD CONSTRAINT "FKmeh38eej28nfq2uxv52jpkolp" FOREIGN KEY (proveedor_pk) REFERENCES public.proveedores(pk);


--
-- Name: productos FKnmkxnfcd8w1uvnwi36mopyiht; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.productos
    ADD CONSTRAINT "FKnmkxnfcd8w1uvnwi36mopyiht" FOREIGN KEY (case_pack_id) REFERENCES public.case_pack(id);


--
-- Name: planificacion_recurso FKox8vqb0y6c5b6td5u7ic02873; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.planificacion_recurso
    ADD CONSTRAINT "FKox8vqb0y6c5b6td5u7ic02873" FOREIGN KEY (recurso_id) REFERENCES public.recurso_produccion(id);


--
-- Name: incorporacion_activo_header FKpfjq4msasqe4u2by84f9k8awn; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.incorporacion_activo_header
    ADD CONSTRAINT "FKpfjq4msasqe4u2by84f9k8awn" FOREIGN KEY (responsable_id) REFERENCES public.integrante_personal(id);


--
-- Name: ordenes_produccion FKqx7l7ll7h5s8v8rtf0bfav6g; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ordenes_produccion
    ADD CONSTRAINT "FKqx7l7ll7h5s8v8rtf0bfav6g" FOREIGN KEY (planificacion_id) REFERENCES public.planificacion_produccion(id);


--
-- Name: asiento_contable FKryrpf5mbnnlay4mpulf9whd3s; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.asiento_contable
    ADD CONSTRAINT "FKryrpf5mbnnlay4mpulf9whd3s" FOREIGN KEY (periodo_contable_id) REFERENCES public.periodo_contable(id);


--
-- Name: incorporacion_activo_header FKsr0gphapw3a2eintebp03a8re; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.incorporacion_activo_header
    ADD CONSTRAINT "FKsr0gphapw3a2eintebp03a8re" FOREIGN KEY (factura_compra_activo_id) REFERENCES public.factura_compra_activo(factura_compra_activo_id);


--
-- Name: documento_baja_activo FKt5cla2mvrw7cb0du5fq2q79q9; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.documento_baja_activo
    ADD CONSTRAINT "FKt5cla2mvrw7cb0du5fq2q79q9" FOREIGN KEY (asiento_contable_id) REFERENCES public.asiento_contable(id);


--
-- Name: depreciacion_activo FKte6rolptlydftnsj3s6c36utq; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.depreciacion_activo
    ADD CONSTRAINT "FKte6rolptlydftnsj3s6c36utq" FOREIGN KEY (asiento_contable_id) REFERENCES public.asiento_contable(id);


--
-- Name: mantenimiento_activo FKum52vu9blkflcjvlyudk42w1; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.mantenimiento_activo
    ADD CONSTRAINT "FKum52vu9blkflcjvlyudk42w1" FOREIGN KEY (responsable_id) REFERENCES public.integrante_personal(id);


--
-- Name: batch_record_ciclo_revision batch_record_ciclo_revision_batch_record_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.batch_record_ciclo_revision
    ADD CONSTRAINT batch_record_ciclo_revision_batch_record_id_fkey FOREIGN KEY (batch_record_id) REFERENCES public.batch_record(id) ON DELETE RESTRICT;


--
-- Name: batch_record_ciclo_revision batch_record_ciclo_revision_cerrado_por_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.batch_record_ciclo_revision
    ADD CONSTRAINT batch_record_ciclo_revision_cerrado_por_id_fkey FOREIGN KEY (cerrado_por_id) REFERENCES public.users(id);


--
-- Name: batch_record_ciclo_revision batch_record_ciclo_revision_enviado_por_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.batch_record_ciclo_revision
    ADD CONSTRAINT batch_record_ciclo_revision_enviado_por_id_fkey FOREIGN KEY (enviado_por_id) REFERENCES public.users(id);


--
-- Name: batch_record_ciclo_revision batch_record_ciclo_revision_revision_envio_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.batch_record_ciclo_revision
    ADD CONSTRAINT batch_record_ciclo_revision_revision_envio_id_fkey FOREIGN KEY (revision_envio_id) REFERENCES public.batch_record_revision(id);


--
-- Name: batch_record_consumo batch_record_consumo_batch_record_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.batch_record_consumo
    ADD CONSTRAINT batch_record_consumo_batch_record_id_fkey FOREIGN KEY (batch_record_id) REFERENCES public.batch_record(id) ON DELETE RESTRICT;


--
-- Name: batch_record_consumo batch_record_consumo_lote_origen_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.batch_record_consumo
    ADD CONSTRAINT batch_record_consumo_lote_origen_id_fkey FOREIGN KEY (lote_origen_id) REFERENCES public.lote(id);


--
-- Name: batch_record_consumo batch_record_consumo_movimiento_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.batch_record_consumo
    ADD CONSTRAINT batch_record_consumo_movimiento_id_fkey FOREIGN KEY (movimiento_id) REFERENCES public.movimientos(movimiento_id);


--
-- Name: batch_record_consumo batch_record_consumo_producto_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.batch_record_consumo
    ADD CONSTRAINT batch_record_consumo_producto_id_fkey FOREIGN KEY (producto_id) REFERENCES public.productos(producto_id);


--
-- Name: batch_record_consumo batch_record_consumo_registrado_por_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.batch_record_consumo
    ADD CONSTRAINT batch_record_consumo_registrado_por_id_fkey FOREIGN KEY (registrado_por_id) REFERENCES public.users(id);


--
-- Name: batch_record_correccion batch_record_correccion_batch_record_etapa_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.batch_record_correccion
    ADD CONSTRAINT batch_record_correccion_batch_record_etapa_id_fkey FOREIGN KEY (batch_record_etapa_id) REFERENCES public.batch_record_etapa(id);


--
-- Name: batch_record_correccion batch_record_correccion_batch_record_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.batch_record_correccion
    ADD CONSTRAINT batch_record_correccion_batch_record_id_fkey FOREIGN KEY (batch_record_id) REFERENCES public.batch_record(id) ON DELETE RESTRICT;


--
-- Name: batch_record_correccion batch_record_correccion_corregida_por_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.batch_record_correccion
    ADD CONSTRAINT batch_record_correccion_corregida_por_id_fkey FOREIGN KEY (corregida_por_id) REFERENCES public.users(id);


--
-- Name: batch_record_correccion batch_record_correccion_evento_correccion_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.batch_record_correccion
    ADD CONSTRAINT batch_record_correccion_evento_correccion_id_fkey FOREIGN KEY (evento_correccion_id) REFERENCES public.seguimiento_orden_area_evento(id);


--
-- Name: batch_record_correccion batch_record_correccion_evento_revertido_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.batch_record_correccion
    ADD CONSTRAINT batch_record_correccion_evento_revertido_id_fkey FOREIGN KEY (evento_revertido_id) REFERENCES public.seguimiento_orden_area_evento(id);


--
-- Name: batch_record_correccion batch_record_correccion_firma_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.batch_record_correccion
    ADD CONSTRAINT batch_record_correccion_firma_id_fkey FOREIGN KEY (firma_id) REFERENCES public.batch_record_firma(id);


--
-- Name: batch_record_correccion batch_record_correccion_orden_fabricacion_evento_correccio_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.batch_record_correccion
    ADD CONSTRAINT batch_record_correccion_orden_fabricacion_evento_correccio_fkey FOREIGN KEY (orden_fabricacion_evento_correccion_id) REFERENCES public.orden_fabricacion_operacion_evento(id);


--
-- Name: batch_record_correccion batch_record_correccion_orden_fabricacion_evento_revertido_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.batch_record_correccion
    ADD CONSTRAINT batch_record_correccion_orden_fabricacion_evento_revertido_fkey FOREIGN KEY (orden_fabricacion_evento_revertido_id) REFERENCES public.orden_fabricacion_operacion_evento(id);


--
-- Name: batch_record_correccion batch_record_correccion_revision_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.batch_record_correccion
    ADD CONSTRAINT batch_record_correccion_revision_id_fkey FOREIGN KEY (revision_id) REFERENCES public.batch_record_revision(id);


--
-- Name: batch_record batch_record_creado_por_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.batch_record
    ADD CONSTRAINT batch_record_creado_por_id_fkey FOREIGN KEY (creado_por_id) REFERENCES public.users(id);


--
-- Name: batch_record_decision_calidad batch_record_decision_calidad_batch_record_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.batch_record_decision_calidad
    ADD CONSTRAINT batch_record_decision_calidad_batch_record_id_fkey FOREIGN KEY (batch_record_id) REFERENCES public.batch_record(id) ON DELETE RESTRICT;


--
-- Name: batch_record_decision_calidad batch_record_decision_calidad_ciclo_revision_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.batch_record_decision_calidad
    ADD CONSTRAINT batch_record_decision_calidad_ciclo_revision_id_fkey FOREIGN KEY (ciclo_revision_id) REFERENCES public.batch_record_ciclo_revision(id);


--
-- Name: batch_record_decision_calidad batch_record_decision_calidad_decidida_por_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.batch_record_decision_calidad
    ADD CONSTRAINT batch_record_decision_calidad_decidida_por_id_fkey FOREIGN KEY (decidida_por_id) REFERENCES public.users(id);


--
-- Name: batch_record_decision_calidad batch_record_decision_calidad_firma_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.batch_record_decision_calidad
    ADD CONSTRAINT batch_record_decision_calidad_firma_id_fkey FOREIGN KEY (firma_id) REFERENCES public.batch_record_firma(id);


--
-- Name: batch_record_decision_calidad batch_record_decision_calidad_revision_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.batch_record_decision_calidad
    ADD CONSTRAINT batch_record_decision_calidad_revision_id_fkey FOREIGN KEY (revision_id) REFERENCES public.batch_record_revision(id);


--
-- Name: batch_record_desviacion batch_record_desviacion_batch_record_etapa_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.batch_record_desviacion
    ADD CONSTRAINT batch_record_desviacion_batch_record_etapa_id_fkey FOREIGN KEY (batch_record_etapa_id) REFERENCES public.batch_record_etapa(id);


--
-- Name: batch_record_desviacion batch_record_desviacion_batch_record_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.batch_record_desviacion
    ADD CONSTRAINT batch_record_desviacion_batch_record_id_fkey FOREIGN KEY (batch_record_id) REFERENCES public.batch_record(id) ON DELETE RESTRICT;


--
-- Name: batch_record_desviacion batch_record_desviacion_detectada_por_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.batch_record_desviacion
    ADD CONSTRAINT batch_record_desviacion_detectada_por_id_fkey FOREIGN KEY (detectada_por_id) REFERENCES public.users(id);


--
-- Name: batch_record_desviacion batch_record_desviacion_resuelta_por_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.batch_record_desviacion
    ADD CONSTRAINT batch_record_desviacion_resuelta_por_id_fkey FOREIGN KEY (resuelta_por_id) REFERENCES public.users(id);


--
-- Name: batch_record_documento batch_record_documento_batch_record_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.batch_record_documento
    ADD CONSTRAINT batch_record_documento_batch_record_id_fkey FOREIGN KEY (batch_record_id) REFERENCES public.batch_record(id) ON DELETE RESTRICT;


--
-- Name: batch_record_documento batch_record_documento_generado_por_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.batch_record_documento
    ADD CONSTRAINT batch_record_documento_generado_por_id_fkey FOREIGN KEY (generado_por_id) REFERENCES public.users(id);


--
-- Name: batch_record_etapa batch_record_etapa_area_operativa_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.batch_record_etapa
    ADD CONSTRAINT batch_record_etapa_area_operativa_id_fkey FOREIGN KEY (area_operativa_id) REFERENCES public.area_operativa(area_id);


--
-- Name: batch_record_etapa batch_record_etapa_batch_record_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.batch_record_etapa
    ADD CONSTRAINT batch_record_etapa_batch_record_id_fkey FOREIGN KEY (batch_record_id) REFERENCES public.batch_record(id) ON DELETE RESTRICT;


--
-- Name: batch_record_etapa batch_record_etapa_control_proceso_plantilla_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.batch_record_etapa
    ADD CONSTRAINT batch_record_etapa_control_proceso_plantilla_id_fkey FOREIGN KEY (control_proceso_plantilla_id) REFERENCES public.calidad_control_proceso_plantilla(id);


--
-- Name: batch_record_etapa batch_record_etapa_orden_fabricacion_evento_origen_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.batch_record_etapa
    ADD CONSTRAINT batch_record_etapa_orden_fabricacion_evento_origen_id_fkey FOREIGN KEY (orden_fabricacion_evento_origen_id) REFERENCES public.orden_fabricacion_operacion_evento(id);


--
-- Name: batch_record_etapa batch_record_etapa_orden_fabricacion_operacion_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.batch_record_etapa
    ADD CONSTRAINT batch_record_etapa_orden_fabricacion_operacion_id_fkey FOREIGN KEY (orden_fabricacion_operacion_id) REFERENCES public.orden_fabricacion_operacion(id);


--
-- Name: batch_record_etapa batch_record_etapa_reportada_por_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.batch_record_etapa
    ADD CONSTRAINT batch_record_etapa_reportada_por_id_fkey FOREIGN KEY (reportada_por_id) REFERENCES public.users(id);


--
-- Name: batch_record_etapa batch_record_etapa_seguimiento_evento_origen_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.batch_record_etapa
    ADD CONSTRAINT batch_record_etapa_seguimiento_evento_origen_id_fkey FOREIGN KEY (seguimiento_evento_origen_id) REFERENCES public.seguimiento_orden_area_evento(id);


--
-- Name: batch_record_etapa batch_record_etapa_seguimiento_orden_area_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.batch_record_etapa
    ADD CONSTRAINT batch_record_etapa_seguimiento_orden_area_id_fkey FOREIGN KEY (seguimiento_orden_area_id) REFERENCES public.seguimiento_orden_area(id);


--
-- Name: batch_record_firma batch_record_firma_batch_record_etapa_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.batch_record_firma
    ADD CONSTRAINT batch_record_firma_batch_record_etapa_id_fkey FOREIGN KEY (batch_record_etapa_id) REFERENCES public.batch_record_etapa(id);


--
-- Name: batch_record_firma batch_record_firma_batch_record_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.batch_record_firma
    ADD CONSTRAINT batch_record_firma_batch_record_id_fkey FOREIGN KEY (batch_record_id) REFERENCES public.batch_record(id) ON DELETE RESTRICT;


--
-- Name: batch_record_firma batch_record_firma_batch_record_revision_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.batch_record_firma
    ADD CONSTRAINT batch_record_firma_batch_record_revision_id_fkey FOREIGN KEY (batch_record_revision_id) REFERENCES public.batch_record_revision(id);


--
-- Name: batch_record_firma batch_record_firma_firma_visual_version_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.batch_record_firma
    ADD CONSTRAINT batch_record_firma_firma_visual_version_id_fkey FOREIGN KEY (firma_visual_version_id) REFERENCES public.firma_visual_usuario_version(id);


--
-- Name: batch_record_firma batch_record_firma_firmante_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.batch_record_firma
    ADD CONSTRAINT batch_record_firma_firmante_id_fkey FOREIGN KEY (firmante_id) REFERENCES public.users(id);


--
-- Name: batch_record_firma batch_record_firma_orden_fabricacion_evento_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.batch_record_firma
    ADD CONSTRAINT batch_record_firma_orden_fabricacion_evento_id_fkey FOREIGN KEY (orden_fabricacion_evento_id) REFERENCES public.orden_fabricacion_operacion_evento(id);


--
-- Name: batch_record_firma batch_record_firma_seguimiento_evento_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.batch_record_firma
    ADD CONSTRAINT batch_record_firma_seguimiento_evento_id_fkey FOREIGN KEY (seguimiento_evento_id) REFERENCES public.seguimiento_orden_area_evento(id);


--
-- Name: batch_record batch_record_lote_resultado_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.batch_record
    ADD CONSTRAINT batch_record_lote_resultado_id_fkey FOREIGN KEY (lote_resultado_id) REFERENCES public.lote(id);


--
-- Name: batch_record batch_record_manufacturing_version_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.batch_record
    ADD CONSTRAINT batch_record_manufacturing_version_id_fkey FOREIGN KEY (manufacturing_version_id) REFERENCES public.manufacturing_versions(id);


--
-- Name: batch_record batch_record_orden_fabricacion_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.batch_record
    ADD CONSTRAINT batch_record_orden_fabricacion_id_fkey FOREIGN KEY (orden_fabricacion_id) REFERENCES public.orden_fabricacion(orden_fabricacion_id);


--
-- Name: batch_record batch_record_orden_produccion_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.batch_record
    ADD CONSTRAINT batch_record_orden_produccion_id_fkey FOREIGN KEY (orden_produccion_id) REFERENCES public.ordenes_produccion(orden_id);


--
-- Name: batch_record batch_record_producto_resultado_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.batch_record
    ADD CONSTRAINT batch_record_producto_resultado_id_fkey FOREIGN KEY (producto_resultado_id) REFERENCES public.productos(producto_id);


--
-- Name: batch_record_projection_task batch_record_projection_task_lote_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.batch_record_projection_task
    ADD CONSTRAINT batch_record_projection_task_lote_id_fkey FOREIGN KEY (lote_id) REFERENCES public.lote(id);


--
-- Name: batch_record_projection_task batch_record_projection_task_orden_fabricacion_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.batch_record_projection_task
    ADD CONSTRAINT batch_record_projection_task_orden_fabricacion_id_fkey FOREIGN KEY (orden_fabricacion_id) REFERENCES public.orden_fabricacion(orden_fabricacion_id);


--
-- Name: batch_record_projection_task batch_record_projection_task_orden_produccion_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.batch_record_projection_task
    ADD CONSTRAINT batch_record_projection_task_orden_produccion_id_fkey FOREIGN KEY (orden_produccion_id) REFERENCES public.ordenes_produccion(orden_id);


--
-- Name: batch_record_projection_task batch_record_projection_task_solicitado_por_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.batch_record_projection_task
    ADD CONSTRAINT batch_record_projection_task_solicitado_por_id_fkey FOREIGN KEY (solicitado_por_id) REFERENCES public.users(id);


--
-- Name: batch_record_revision batch_record_revision_batch_record_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.batch_record_revision
    ADD CONSTRAINT batch_record_revision_batch_record_id_fkey FOREIGN KEY (batch_record_id) REFERENCES public.batch_record(id) ON DELETE RESTRICT;


--
-- Name: batch_record_revision batch_record_revision_creada_por_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.batch_record_revision
    ADD CONSTRAINT batch_record_revision_creada_por_id_fkey FOREIGN KEY (creada_por_id) REFERENCES public.users(id);


--
-- Name: batch_record_seccion_correccion batch_record_seccion_correccion_atendida_por_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.batch_record_seccion_correccion
    ADD CONSTRAINT batch_record_seccion_correccion_atendida_por_id_fkey FOREIGN KEY (atendida_por_id) REFERENCES public.users(id);


--
-- Name: batch_record_seccion_correccion batch_record_seccion_correccion_batch_record_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.batch_record_seccion_correccion
    ADD CONSTRAINT batch_record_seccion_correccion_batch_record_id_fkey FOREIGN KEY (batch_record_id) REFERENCES public.batch_record(id) ON DELETE RESTRICT;


--
-- Name: batch_record_seccion_correccion batch_record_seccion_correccion_solicitada_por_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.batch_record_seccion_correccion
    ADD CONSTRAINT batch_record_seccion_correccion_solicitada_por_id_fkey FOREIGN KEY (solicitada_por_id) REFERENCES public.users(id);


--
-- Name: batch_record_solicitud_reapertura_rechazo batch_record_solicitud_reapertura_r_revision_aprobacion_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.batch_record_solicitud_reapertura_rechazo
    ADD CONSTRAINT batch_record_solicitud_reapertura_r_revision_aprobacion_id_fkey FOREIGN KEY (revision_aprobacion_id) REFERENCES public.batch_record_revision(id);


--
-- Name: batch_record_solicitud_reapertura_rechazo batch_record_solicitud_reapertura_re_revision_solicitud_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.batch_record_solicitud_reapertura_rechazo
    ADD CONSTRAINT batch_record_solicitud_reapertura_re_revision_solicitud_id_fkey FOREIGN KEY (revision_solicitud_id) REFERENCES public.batch_record_revision(id);


--
-- Name: batch_record_solicitud_reapertura_rechazo batch_record_solicitud_reapertura_rech_firma_aprobacion_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.batch_record_solicitud_reapertura_rechazo
    ADD CONSTRAINT batch_record_solicitud_reapertura_rech_firma_aprobacion_id_fkey FOREIGN KEY (firma_aprobacion_id) REFERENCES public.batch_record_firma(id);


--
-- Name: batch_record_solicitud_reapertura_rechazo batch_record_solicitud_reapertura_recha_firma_solicitud_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.batch_record_solicitud_reapertura_rechazo
    ADD CONSTRAINT batch_record_solicitud_reapertura_recha_firma_solicitud_id_fkey FOREIGN KEY (firma_solicitud_id) REFERENCES public.batch_record_firma(id);


--
-- Name: batch_record_solicitud_reapertura_rechazo batch_record_solicitud_reapertura_rechaz_solicitada_por_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.batch_record_solicitud_reapertura_rechazo
    ADD CONSTRAINT batch_record_solicitud_reapertura_rechaz_solicitada_por_id_fkey FOREIGN KEY (solicitada_por_id) REFERENCES public.users(id);


--
-- Name: batch_record_solicitud_reapertura_rechazo batch_record_solicitud_reapertura_rechazo_aprobada_por_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.batch_record_solicitud_reapertura_rechazo
    ADD CONSTRAINT batch_record_solicitud_reapertura_rechazo_aprobada_por_id_fkey FOREIGN KEY (aprobada_por_id) REFERENCES public.users(id);


--
-- Name: batch_record_solicitud_reapertura_rechazo batch_record_solicitud_reapertura_rechazo_batch_record_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.batch_record_solicitud_reapertura_rechazo
    ADD CONSTRAINT batch_record_solicitud_reapertura_rechazo_batch_record_id_fkey FOREIGN KEY (batch_record_id) REFERENCES public.batch_record(id) ON DELETE RESTRICT;


--
-- Name: calidad_control_proceso_ejecucion calidad_control_proceso_ejecucion_batch_record_etapa_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.calidad_control_proceso_ejecucion
    ADD CONSTRAINT calidad_control_proceso_ejecucion_batch_record_etapa_id_fkey FOREIGN KEY (batch_record_etapa_id) REFERENCES public.batch_record_etapa(id);


--
-- Name: calidad_control_proceso_ejecucion calidad_control_proceso_ejecucion_batch_record_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.calidad_control_proceso_ejecucion
    ADD CONSTRAINT calidad_control_proceso_ejecucion_batch_record_id_fkey FOREIGN KEY (batch_record_id) REFERENCES public.batch_record(id);


--
-- Name: carga_costos_item carga_costos_item_lote_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.carga_costos_item
    ADD CONSTRAINT carga_costos_item_lote_id_fkey FOREIGN KEY (lote_id) REFERENCES public.carga_costos_lote(id) ON DELETE CASCADE;


--
-- Name: carga_costos_lote carga_costos_lote_usuario_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.carga_costos_lote
    ADD CONSTRAINT carga_costos_lote_usuario_id_fkey FOREIGN KEY (usuario_id) REFERENCES public.users(id) ON DELETE SET NULL;


--
-- Name: carga_costos_propagacion_item carga_costos_propagacion_item_lote_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.carga_costos_propagacion_item
    ADD CONSTRAINT carga_costos_propagacion_item_lote_id_fkey FOREIGN KEY (lote_id) REFERENCES public.carga_costos_lote(id) ON DELETE CASCADE;


--
-- Name: cierre_produccion cierre_produccion_cerrado_por_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cierre_produccion
    ADD CONSTRAINT cierre_produccion_cerrado_por_id_fkey FOREIGN KEY (cerrado_por_id) REFERENCES public.users(id);


--
-- Name: contacto_proveedor contacto_proveedor_proveedor_pk_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.contacto_proveedor
    ADD CONSTRAINT contacto_proveedor_proveedor_pk_fkey FOREIGN KEY (proveedor_pk) REFERENCES public.proveedores(pk) ON DELETE CASCADE;


--
-- Name: control_desviacion control_desviacion_abierta_por_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.control_desviacion
    ADD CONSTRAINT control_desviacion_abierta_por_id_fkey FOREIGN KEY (abierta_por_id) REFERENCES public.users(id);


--
-- Name: control_desviacion control_desviacion_cerrada_por_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.control_desviacion
    ADD CONSTRAINT control_desviacion_cerrada_por_id_fkey FOREIGN KEY (cerrada_por_id) REFERENCES public.users(id);


--
-- Name: control_desviacion control_desviacion_control_requerido_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.control_desviacion
    ADD CONSTRAINT control_desviacion_control_requerido_id_fkey FOREIGN KEY (control_requerido_id) REFERENCES public.control_requerido(id) ON DELETE RESTRICT;


--
-- Name: control_desviacion control_desviacion_ejecucion_origen_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.control_desviacion
    ADD CONSTRAINT control_desviacion_ejecucion_origen_id_fkey FOREIGN KEY (ejecucion_origen_id) REFERENCES public.control_ejecucion(id) ON DELETE RESTRICT;


--
-- Name: control_desviacion control_desviacion_resuelta_por_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.control_desviacion
    ADD CONSTRAINT control_desviacion_resuelta_por_id_fkey FOREIGN KEY (resuelta_por_id) REFERENCES public.users(id);


--
-- Name: control_ejecucion control_ejecucion_control_requerido_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.control_ejecucion
    ADD CONSTRAINT control_ejecucion_control_requerido_id_fkey FOREIGN KEY (control_requerido_id) REFERENCES public.control_requerido(id) ON DELETE RESTRICT;


--
-- Name: control_ejecucion_lectura control_ejecucion_lectura_muestra_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.control_ejecucion_lectura
    ADD CONSTRAINT control_ejecucion_lectura_muestra_id_fkey FOREIGN KEY (muestra_id) REFERENCES public.control_ejecucion_muestra(id) ON DELETE RESTRICT;


--
-- Name: control_ejecucion control_ejecucion_legacy_ejecucion_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.control_ejecucion
    ADD CONSTRAINT control_ejecucion_legacy_ejecucion_id_fkey FOREIGN KEY (legacy_ejecucion_id) REFERENCES public.calidad_control_proceso_ejecucion(id);


--
-- Name: control_ejecucion_muestra control_ejecucion_muestra_caracteristica_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.control_ejecucion_muestra
    ADD CONSTRAINT control_ejecucion_muestra_caracteristica_id_fkey FOREIGN KEY (caracteristica_id) REFERENCES public.control_plan_caracteristica(id) ON DELETE RESTRICT;


--
-- Name: control_ejecucion_muestra control_ejecucion_muestra_ejecucion_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.control_ejecucion_muestra
    ADD CONSTRAINT control_ejecucion_muestra_ejecucion_id_fkey FOREIGN KEY (ejecucion_id) REFERENCES public.control_ejecucion(id) ON DELETE RESTRICT;


--
-- Name: control_ejecucion control_ejecucion_repeticion_de_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.control_ejecucion
    ADD CONSTRAINT control_ejecucion_repeticion_de_id_fkey FOREIGN KEY (repeticion_de_id) REFERENCES public.control_ejecucion(id) ON DELETE RESTRICT;


--
-- Name: control_ejecucion control_ejecucion_usuario_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.control_ejecucion
    ADD CONSTRAINT control_ejecucion_usuario_id_fkey FOREIGN KEY (usuario_id) REFERENCES public.users(id);


--
-- Name: control_idempotencia control_idempotencia_actor_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.control_idempotencia
    ADD CONSTRAINT control_idempotencia_actor_id_fkey FOREIGN KEY (actor_id) REFERENCES public.users(id) ON DELETE RESTRICT;


--
-- Name: control_plan_aplicabilidad control_plan_aplicabilidad_area_operativa_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.control_plan_aplicabilidad
    ADD CONSTRAINT control_plan_aplicabilidad_area_operativa_id_fkey FOREIGN KEY (area_operativa_id) REFERENCES public.area_operativa(area_id);


--
-- Name: control_plan_aplicabilidad control_plan_aplicabilidad_categoria_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.control_plan_aplicabilidad
    ADD CONSTRAINT control_plan_aplicabilidad_categoria_id_fkey FOREIGN KEY (categoria_id) REFERENCES public.categoria(categoria_id);


--
-- Name: control_plan_aplicabilidad_exclusion control_plan_aplicabilidad_exclusion_aplicabilidad_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.control_plan_aplicabilidad_exclusion
    ADD CONSTRAINT control_plan_aplicabilidad_exclusion_aplicabilidad_id_fkey FOREIGN KEY (aplicabilidad_id) REFERENCES public.control_plan_aplicabilidad(id) ON DELETE CASCADE;


--
-- Name: control_plan_aplicabilidad_exclusion control_plan_aplicabilidad_exclusion_producto_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.control_plan_aplicabilidad_exclusion
    ADD CONSTRAINT control_plan_aplicabilidad_exclusion_producto_id_fkey FOREIGN KEY (producto_id) REFERENCES public.productos(producto_id);


--
-- Name: control_plan_aplicabilidad control_plan_aplicabilidad_proceso_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.control_plan_aplicabilidad
    ADD CONSTRAINT control_plan_aplicabilidad_proceso_id_fkey FOREIGN KEY (proceso_id) REFERENCES public.proceso_produccion(proceso_id);


--
-- Name: control_plan_aplicabilidad control_plan_aplicabilidad_producto_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.control_plan_aplicabilidad
    ADD CONSTRAINT control_plan_aplicabilidad_producto_id_fkey FOREIGN KEY (producto_id) REFERENCES public.productos(producto_id);


--
-- Name: control_plan_aplicabilidad control_plan_aplicabilidad_version_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.control_plan_aplicabilidad
    ADD CONSTRAINT control_plan_aplicabilidad_version_id_fkey FOREIGN KEY (version_id) REFERENCES public.control_plan_version(id) ON DELETE CASCADE;


--
-- Name: control_plan_caracteristica control_plan_caracteristica_legacy_caracteristica_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.control_plan_caracteristica
    ADD CONSTRAINT control_plan_caracteristica_legacy_caracteristica_id_fkey FOREIGN KEY (legacy_caracteristica_id) REFERENCES public.calidad_control_proceso_caracteristica(id);


--
-- Name: control_plan_caracteristica control_plan_caracteristica_magnitud_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.control_plan_caracteristica
    ADD CONSTRAINT control_plan_caracteristica_magnitud_id_fkey FOREIGN KEY (magnitud_id) REFERENCES public.control_magnitud(id);


--
-- Name: control_plan_caracteristica control_plan_caracteristica_unidad_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.control_plan_caracteristica
    ADD CONSTRAINT control_plan_caracteristica_unidad_id_fkey FOREIGN KEY (unidad_id) REFERENCES public.control_unidad(id);


--
-- Name: control_plan_caracteristica control_plan_caracteristica_version_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.control_plan_caracteristica
    ADD CONSTRAINT control_plan_caracteristica_version_id_fkey FOREIGN KEY (version_id) REFERENCES public.control_plan_version(id) ON DELETE CASCADE;


--
-- Name: control_plan control_plan_creado_por_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.control_plan
    ADD CONSTRAINT control_plan_creado_por_id_fkey FOREIGN KEY (creado_por_id) REFERENCES public.users(id);


--
-- Name: control_plan_version control_plan_version_creada_por_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.control_plan_version
    ADD CONSTRAINT control_plan_version_creada_por_id_fkey FOREIGN KEY (creada_por_id) REFERENCES public.users(id);


--
-- Name: control_plan_version control_plan_version_legacy_plantilla_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.control_plan_version
    ADD CONSTRAINT control_plan_version_legacy_plantilla_id_fkey FOREIGN KEY (legacy_plantilla_id) REFERENCES public.calidad_control_proceso_plantilla(id);


--
-- Name: control_plan_version control_plan_version_plan_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.control_plan_version
    ADD CONSTRAINT control_plan_version_plan_id_fkey FOREIGN KEY (plan_id) REFERENCES public.control_plan(id) ON DELETE RESTRICT;


--
-- Name: control_plan_version control_plan_version_publicada_por_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.control_plan_version
    ADD CONSTRAINT control_plan_version_publicada_por_id_fkey FOREIGN KEY (publicada_por_id) REFERENCES public.users(id);


--
-- Name: control_plan_version control_plan_version_retirada_por_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.control_plan_version
    ADD CONSTRAINT control_plan_version_retirada_por_id_fkey FOREIGN KEY (retirada_por_id) REFERENCES public.users(id);


--
-- Name: control_requerido control_requerido_aplicabilidad_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.control_requerido
    ADD CONSTRAINT control_requerido_aplicabilidad_id_fkey FOREIGN KEY (aplicabilidad_id) REFERENCES public.control_plan_aplicabilidad(id) ON DELETE RESTRICT;


--
-- Name: control_requerido control_requerido_batch_record_etapa_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.control_requerido
    ADD CONSTRAINT control_requerido_batch_record_etapa_id_fkey FOREIGN KEY (batch_record_etapa_id) REFERENCES public.batch_record_etapa(id) ON DELETE RESTRICT;


--
-- Name: control_requerido control_requerido_batch_record_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.control_requerido
    ADD CONSTRAINT control_requerido_batch_record_id_fkey FOREIGN KEY (batch_record_id) REFERENCES public.batch_record(id) ON DELETE RESTRICT;


--
-- Name: control_requerido control_requerido_legacy_ejecucion_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.control_requerido
    ADD CONSTRAINT control_requerido_legacy_ejecucion_id_fkey FOREIGN KEY (legacy_ejecucion_id) REFERENCES public.calidad_control_proceso_ejecucion(id);


--
-- Name: control_requerido control_requerido_lote_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.control_requerido
    ADD CONSTRAINT control_requerido_lote_id_fkey FOREIGN KEY (lote_id) REFERENCES public.lote(id) ON DELETE RESTRICT;


--
-- Name: control_requerido control_requerido_version_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.control_requerido
    ADD CONSTRAINT control_requerido_version_id_fkey FOREIGN KEY (version_id) REFERENCES public.control_plan_version(id) ON DELETE RESTRICT;


--
-- Name: control_revalidacion control_revalidacion_confirmada_por_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.control_revalidacion
    ADD CONSTRAINT control_revalidacion_confirmada_por_id_fkey FOREIGN KEY (confirmada_por_id) REFERENCES public.users(id);


--
-- Name: control_revalidacion control_revalidacion_control_requerido_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.control_revalidacion
    ADD CONSTRAINT control_revalidacion_control_requerido_id_fkey FOREIGN KEY (control_requerido_id) REFERENCES public.control_requerido(id) ON DELETE RESTRICT;


--
-- Name: control_revalidacion control_revalidacion_ejecucion_revalidada_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.control_revalidacion
    ADD CONSTRAINT control_revalidacion_ejecucion_revalidada_id_fkey FOREIGN KEY (ejecucion_revalidada_id) REFERENCES public.control_ejecucion(id) ON DELETE RESTRICT;


--
-- Name: firma_visual_usuario_version firma_visual_usuario_version_configurada_por_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.firma_visual_usuario_version
    ADD CONSTRAINT firma_visual_usuario_version_configurada_por_id_fkey FOREIGN KEY (configurada_por_id) REFERENCES public.users(id) ON DELETE RESTRICT;


--
-- Name: firma_visual_usuario_version firma_visual_usuario_version_retirada_por_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.firma_visual_usuario_version
    ADD CONSTRAINT firma_visual_usuario_version_retirada_por_id_fkey FOREIGN KEY (retirada_por_id) REFERENCES public.users(id) ON DELETE RESTRICT;


--
-- Name: firma_visual_usuario_version firma_visual_usuario_version_usuario_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.firma_visual_usuario_version
    ADD CONSTRAINT firma_visual_usuario_version_usuario_id_fkey FOREIGN KEY (usuario_id) REFERENCES public.users(id) ON DELETE RESTRICT;


--
-- Name: area_operativa_categoria_habilitada fk_aoch_area_operativa; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.area_operativa_categoria_habilitada
    ADD CONSTRAINT fk_aoch_area_operativa FOREIGN KEY (area_operativa_id) REFERENCES public.area_operativa(area_id) ON DELETE CASCADE;


--
-- Name: area_operativa_categoria_habilitada fk_aoch_categoria; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.area_operativa_categoria_habilitada
    ADD CONSTRAINT fk_aoch_categoria FOREIGN KEY (categoria_id) REFERENCES public.categoria(categoria_id) ON DELETE CASCADE;


--
-- Name: area_operativa_categoria_unidad_medida fk_aocum_area_categoria; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.area_operativa_categoria_unidad_medida
    ADD CONSTRAINT fk_aocum_area_categoria FOREIGN KEY (area_operativa_id, categoria_id) REFERENCES public.area_operativa_categoria_habilitada(area_operativa_id, categoria_id) ON DELETE CASCADE;


--
-- Name: area_operativa_categoria_unidad_medida fk_aocum_area_unidad; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.area_operativa_categoria_unidad_medida
    ADD CONSTRAINT fk_aocum_area_unidad FOREIGN KEY (area_operativa_id, unidad_medida_area_operativa_id) REFERENCES public.unidad_medida_area_operativa(area_operativa_id, unidad_medida_area_operativa_id) ON DELETE CASCADE;


--
-- Name: area_operativa_ruido_muestra fk_area_operativa_ruido_muestra_area; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.area_operativa_ruido_muestra
    ADD CONSTRAINT fk_area_operativa_ruido_muestra_area FOREIGN KEY (area_operativa_id) REFERENCES public.area_operativa(area_id) ON DELETE RESTRICT;


--
-- Name: area_operativa_ruido_muestra fk_area_operativa_ruido_muestra_usuario; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.area_operativa_ruido_muestra
    ADD CONSTRAINT fk_area_operativa_ruido_muestra_usuario FOREIGN KEY (usuario_id) REFERENCES public.users(id) ON DELETE RESTRICT;


--
-- Name: calidad_control_proceso_caracteristica fk_calidad_caracteristica_plantilla; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.calidad_control_proceso_caracteristica
    ADD CONSTRAINT fk_calidad_caracteristica_plantilla FOREIGN KEY (plantilla_id) REFERENCES public.calidad_control_proceso_plantilla(id) ON DELETE CASCADE;


--
-- Name: calidad_control_proceso_ejecucion fk_calidad_ejecucion_lote; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.calidad_control_proceso_ejecucion
    ADD CONSTRAINT fk_calidad_ejecucion_lote FOREIGN KEY (lote_id) REFERENCES public.lote(id);


--
-- Name: calidad_control_proceso_ejecucion fk_calidad_ejecucion_plantilla; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.calidad_control_proceso_ejecucion
    ADD CONSTRAINT fk_calidad_ejecucion_plantilla FOREIGN KEY (plantilla_id) REFERENCES public.calidad_control_proceso_plantilla(id);


--
-- Name: calidad_control_proceso_ejecucion fk_calidad_ejecucion_usuario; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.calidad_control_proceso_ejecucion
    ADD CONSTRAINT fk_calidad_ejecucion_usuario FOREIGN KEY (usuario_id) REFERENCES public.users(id);


--
-- Name: calidad_control_proceso_lectura fk_calidad_lectura_muestra; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.calidad_control_proceso_lectura
    ADD CONSTRAINT fk_calidad_lectura_muestra FOREIGN KEY (muestra_id) REFERENCES public.calidad_control_proceso_muestra(id) ON DELETE CASCADE;


--
-- Name: calidad_control_proceso_muestra fk_calidad_muestra_caracteristica; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.calidad_control_proceso_muestra
    ADD CONSTRAINT fk_calidad_muestra_caracteristica FOREIGN KEY (caracteristica_id) REFERENCES public.calidad_control_proceso_caracteristica(id);


--
-- Name: calidad_control_proceso_muestra fk_calidad_muestra_ejecucion; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.calidad_control_proceso_muestra
    ADD CONSTRAINT fk_calidad_muestra_ejecucion FOREIGN KEY (ejecucion_id) REFERENCES public.calidad_control_proceso_ejecucion(id) ON DELETE CASCADE;


--
-- Name: calidad_control_proceso_plantilla fk_calidad_plantilla_area; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.calidad_control_proceso_plantilla
    ADD CONSTRAINT fk_calidad_plantilla_area FOREIGN KEY (area_operativa_id) REFERENCES public.area_operativa(area_id);


--
-- Name: doc_tran_de_personal fk_doc_tran_personal_integrante; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.doc_tran_de_personal
    ADD CONSTRAINT fk_doc_tran_personal_integrante FOREIGN KEY (integrante_id) REFERENCES public.integrante_personal(id) ON DELETE SET NULL;


--
-- Name: insumos_empaque fk_insumos_empaque_material_id_v024; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.insumos_empaque
    ADD CONSTRAINT fk_insumos_empaque_material_id_v024 FOREIGN KEY (material_id) REFERENCES public.productos(producto_id);


--
-- Name: insumos fk_insumos_input_producto_id_v024; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.insumos
    ADD CONSTRAINT fk_insumos_input_producto_id_v024 FOREIGN KEY (input_producto_id) REFERENCES public.productos(producto_id);


--
-- Name: insumos fk_insumos_output_producto_id_v024; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.insumos
    ADD CONSTRAINT fk_insumos_output_producto_id_v024 FOREIGN KEY (output_producto_id) REFERENCES public.productos(producto_id);


--
-- Name: item_orden_compra fk_item_orden_compra_producto_id_v024; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.item_orden_compra
    ADD CONSTRAINT fk_item_orden_compra_producto_id_v024 FOREIGN KEY (producto_id) REFERENCES public.productos(producto_id);


--
-- Name: items_factura_compra fk_items_factura_compra_materia_prima_v024; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.items_factura_compra
    ADD CONSTRAINT fk_items_factura_compra_materia_prima_v024 FOREIGN KEY (materia_prima_id) REFERENCES public.productos(producto_id);


--
-- Name: items_factura_venta fk_items_factura_venta_producto_id_v024; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.items_factura_venta
    ADD CONSTRAINT fk_items_factura_venta_producto_id_v024 FOREIGN KEY (producto_id) REFERENCES public.productos(producto_id);


--
-- Name: items_orden_venta fk_items_orden_venta_producto_id_v024; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.items_orden_venta
    ADD CONSTRAINT fk_items_orden_venta_producto_id_v024 FOREIGN KEY (producto_id) REFERENCES public.productos(producto_id);


--
-- Name: jornada_laboral_bloque fk_jornada_laboral_bloque_version; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.jornada_laboral_bloque
    ADD CONSTRAINT fk_jornada_laboral_bloque_version FOREIGN KEY (jornada_laboral_version_id) REFERENCES public.jornada_laboral_version(id) ON DELETE CASCADE;


--
-- Name: lead_time_proveedor_kpi fk_lead_time_proveedor_kpi_proveedor; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lead_time_proveedor_kpi
    ADD CONSTRAINT fk_lead_time_proveedor_kpi_proveedor FOREIGN KEY (proveedor_pk) REFERENCES public.proveedores(pk) ON DELETE RESTRICT;


--
-- Name: manufacturing_versions fk_manufacturing_versions_producto_id_v024; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.manufacturing_versions
    ADD CONSTRAINT fk_manufacturing_versions_producto_id_v024 FOREIGN KEY (producto_id) REFERENCES public.productos(producto_id);


--
-- Name: material_ficha_tecnica_version fk_material_ficha_tecnica_material; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.material_ficha_tecnica_version
    ADD CONSTRAINT fk_material_ficha_tecnica_material FOREIGN KEY (material_id) REFERENCES public.productos(producto_id) ON DELETE CASCADE;


--
-- Name: mision_vision_version fk_mision_vision_origen_version; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.mision_vision_version
    ADD CONSTRAINT fk_mision_vision_origen_version FOREIGN KEY (origen_version_id) REFERENCES public.mision_vision_version(id);


--
-- Name: mision_vision_valor fk_mision_vision_valor_version; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.mision_vision_valor
    ADD CONSTRAINT fk_mision_vision_valor_version FOREIGN KEY (mision_vision_version_id) REFERENCES public.mision_vision_version(id) ON DELETE CASCADE;


--
-- Name: movimiento_reserva fk_movimiento_reserva_producto_id_v024; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.movimiento_reserva
    ADD CONSTRAINT fk_movimiento_reserva_producto_id_v024 FOREIGN KEY (producto_id) REFERENCES public.productos(producto_id);


--
-- Name: movimientos fk_movimientos_area_operativa; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.movimientos
    ADD CONSTRAINT fk_movimientos_area_operativa FOREIGN KEY (area_operativa_id) REFERENCES public.area_operativa(area_id);


--
-- Name: movimientos fk_movimientos_producto_id_v024; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.movimientos
    ADD CONSTRAINT fk_movimientos_producto_id_v024 FOREIGN KEY (producto_id) REFERENCES public.productos(producto_id);


--
-- Name: mps_semanal_dia fk_mps_sem_dia_mps; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.mps_semanal_dia
    ADD CONSTRAINT fk_mps_sem_dia_mps FOREIGN KEY (mps_id) REFERENCES public.master_production_schedule_semanal(mps_id) ON DELETE CASCADE;


--
-- Name: mps_semanal_item fk_mps_sem_item_dia; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.mps_semanal_item
    ADD CONSTRAINT fk_mps_sem_item_dia FOREIGN KEY (mps_dia_id) REFERENCES public.mps_semanal_dia(id) ON DELETE CASCADE;


--
-- Name: mps_semanal_item fk_mps_sem_item_mps; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.mps_semanal_item
    ADD CONSTRAINT fk_mps_sem_item_mps FOREIGN KEY (mps_id) REFERENCES public.master_production_schedule_semanal(mps_id) ON DELETE CASCADE;


--
-- Name: mps_semanal_item fk_mps_sem_item_terminado; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.mps_semanal_item
    ADD CONSTRAINT fk_mps_sem_item_terminado FOREIGN KEY (terminado_id) REFERENCES public.productos(producto_id);


--
-- Name: mps_semanal_lote_planificado fk_mps_sem_lote_item; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.mps_semanal_lote_planificado
    ADD CONSTRAINT fk_mps_sem_lote_item FOREIGN KEY (mps_item_id) REFERENCES public.mps_semanal_item(id) ON DELETE CASCADE;


--
-- Name: master_production_schedule_semanal fk_mps_semana_mps; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.master_production_schedule_semanal
    ADD CONSTRAINT fk_mps_semana_mps FOREIGN KEY (semana_mps_id) REFERENCES public.semana_mps(id) ON DELETE RESTRICT;


--
-- Name: mps_semanal_observacion fk_mps_semanal_observacion_mps; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.mps_semanal_observacion
    ADD CONSTRAINT fk_mps_semanal_observacion_mps FOREIGN KEY (mps_id) REFERENCES public.master_production_schedule_semanal(mps_id) ON DELETE CASCADE;


--
-- Name: orden_compra_activo fk_ocaf_empresa_identidad_legal_version; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.orden_compra_activo
    ADD CONSTRAINT fk_ocaf_empresa_identidad_legal_version FOREIGN KEY (empresa_identidad_legal_version_id) REFERENCES public.empresa_identidad_legal_version(id);


--
-- Name: orden_compra_activo fk_ocaf_empresa_logo_documental_version; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.orden_compra_activo
    ADD CONSTRAINT fk_ocaf_empresa_logo_documental_version FOREIGN KEY (empresa_logo_documental_version_id) REFERENCES public.empresa_logo_documental_version(id);


--
-- Name: orden_compra fk_orden_compra_empresa_identidad_legal_version; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.orden_compra
    ADD CONSTRAINT fk_orden_compra_empresa_identidad_legal_version FOREIGN KEY (empresa_identidad_legal_version_id) REFERENCES public.empresa_identidad_legal_version(id);


--
-- Name: orden_compra fk_orden_compra_empresa_logo_documental_version; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.orden_compra
    ADD CONSTRAINT fk_orden_compra_empresa_logo_documental_version FOREIGN KEY (empresa_logo_documental_version_id) REFERENCES public.empresa_logo_documental_version(id);


--
-- Name: orden_compra fk_orden_compra_usuario_creador; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.orden_compra
    ADD CONSTRAINT fk_orden_compra_usuario_creador FOREIGN KEY (usuario_creador_id) REFERENCES public.users(id) ON DELETE RESTRICT;


--
-- Name: orden_compra fk_orden_compra_usuario_liberador; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.orden_compra
    ADD CONSTRAINT fk_orden_compra_usuario_liberador FOREIGN KEY (usuario_liberador_id) REFERENCES public.users(id) ON DELETE RESTRICT;


--
-- Name: ordenes_produccion fk_orden_produccion_cancelada_por; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ordenes_produccion
    ADD CONSTRAINT fk_orden_produccion_cancelada_por FOREIGN KEY (cancelada_por_id) REFERENCES public.users(id) ON DELETE RESTRICT;


--
-- Name: ordenes_produccion fk_ordenes_produccion_jornada_laboral_version; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ordenes_produccion
    ADD CONSTRAINT fk_ordenes_produccion_jornada_laboral_version FOREIGN KEY (jornada_laboral_version_id) REFERENCES public.jornada_laboral_version(id);


--
-- Name: ordenes_produccion fk_ordenes_produccion_mps; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ordenes_produccion
    ADD CONSTRAINT fk_ordenes_produccion_mps FOREIGN KEY (mps_id) REFERENCES public.master_production_schedule_semanal(mps_id) ON DELETE SET NULL;


--
-- Name: ordenes_produccion fk_ordenes_produccion_mps_lote_planificado; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ordenes_produccion
    ADD CONSTRAINT fk_ordenes_produccion_mps_lote_planificado FOREIGN KEY (mps_lote_planificado_id) REFERENCES public.mps_semanal_lote_planificado(id) ON DELETE SET NULL;


--
-- Name: ordenes_produccion fk_ordenes_produccion_producto_id_v024; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ordenes_produccion
    ADD CONSTRAINT fk_ordenes_produccion_producto_id_v024 FOREIGN KEY (producto_id) REFERENCES public.productos(producto_id);


--
-- Name: ordenes_produccion fk_ordenes_produccion_ruta_proceso_cat_version; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ordenes_produccion
    ADD CONSTRAINT fk_ordenes_produccion_ruta_proceso_cat_version FOREIGN KEY (ruta_proceso_cat_version_id) REFERENCES public.ruta_proceso_cat_version(id);


--
-- Name: proceso_fabricacion_edge fk_pf_edge_proceso_completo; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.proceso_fabricacion_edge
    ADD CONSTRAINT fk_pf_edge_proceso_completo FOREIGN KEY (proceso_completo_id) REFERENCES public.procesos_produccion(proceso_completo_id) ON DELETE CASCADE;


--
-- Name: proceso_fabricacion_edge fk_pf_edge_source; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.proceso_fabricacion_edge
    ADD CONSTRAINT fk_pf_edge_source FOREIGN KEY (source_node_id) REFERENCES public.proceso_fabricacion_nodo(id) ON DELETE CASCADE;


--
-- Name: proceso_fabricacion_edge fk_pf_edge_target; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.proceso_fabricacion_edge
    ADD CONSTRAINT fk_pf_edge_target FOREIGN KEY (target_node_id) REFERENCES public.proceso_fabricacion_nodo(id) ON DELETE CASCADE;


--
-- Name: proceso_fabricacion_nodo fk_pf_nodo_area_operativa; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.proceso_fabricacion_nodo
    ADD CONSTRAINT fk_pf_nodo_area_operativa FOREIGN KEY (area_operativa_id) REFERENCES public.area_operativa(area_id) ON DELETE SET NULL;


--
-- Name: proceso_fabricacion_nodo fk_pf_nodo_insumo; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.proceso_fabricacion_nodo
    ADD CONSTRAINT fk_pf_nodo_insumo FOREIGN KEY (insumo_id) REFERENCES public.insumos(insumo_id) ON DELETE SET NULL;


--
-- Name: proceso_fabricacion_nodo fk_pf_nodo_proceso; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.proceso_fabricacion_nodo
    ADD CONSTRAINT fk_pf_nodo_proceso FOREIGN KEY (proceso_id) REFERENCES public.proceso_produccion(proceso_id) ON DELETE SET NULL;


--
-- Name: proceso_fabricacion_nodo fk_pf_nodo_proceso_completo; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.proceso_fabricacion_nodo
    ADD CONSTRAINT fk_pf_nodo_proceso_completo FOREIGN KEY (proceso_completo_id) REFERENCES public.procesos_produccion(proceso_completo_id) ON DELETE CASCADE;


--
-- Name: planificacion_produccion fk_planificacion_produccion_producto_id_v024; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.planificacion_produccion
    ADD CONSTRAINT fk_planificacion_produccion_producto_id_v024 FOREIGN KEY (producto_id) REFERENCES public.productos(producto_id);


--
-- Name: proceso_produccion_documento_version fk_proceso_documento_proceso; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.proceso_produccion_documento_version
    ADD CONSTRAINT fk_proceso_documento_proceso FOREIGN KEY (proceso_id) REFERENCES public.proceso_produccion(proceso_id) ON DELETE RESTRICT;


--
-- Name: procesos_produccion fk_procesos_produccion_producto_id_v024; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.procesos_produccion
    ADD CONSTRAINT fk_procesos_produccion_producto_id_v024 FOREIGN KEY (producto_id) REFERENCES public.productos(producto_id) ON DELETE CASCADE;


--
-- Name: proveedor_material_lead_time_metric fk_proveedor_material_lead_time_metric_material; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.proveedor_material_lead_time_metric
    ADD CONSTRAINT fk_proveedor_material_lead_time_metric_material FOREIGN KEY (producto_id) REFERENCES public.productos(producto_id) ON DELETE RESTRICT;


--
-- Name: proveedor_material_lead_time_metric fk_proveedor_material_lead_time_metric_proveedor; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.proveedor_material_lead_time_metric
    ADD CONSTRAINT fk_proveedor_material_lead_time_metric_proveedor FOREIGN KEY (proveedor_pk) REFERENCES public.proveedores(pk) ON DELETE RESTRICT;


--
-- Name: registro_hora_extra fk_registro_hora_extra_aprobado_por; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.registro_hora_extra
    ADD CONSTRAINT fk_registro_hora_extra_aprobado_por FOREIGN KEY (aprobado_por_id) REFERENCES public.users(id) ON DELETE SET NULL;


--
-- Name: registro_hora_extra fk_registro_hora_extra_integrante; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.registro_hora_extra
    ADD CONSTRAINT fk_registro_hora_extra_integrante FOREIGN KEY (integrante_id) REFERENCES public.integrante_personal(id) ON DELETE RESTRICT;


--
-- Name: registro_hora_extra fk_registro_hora_extra_registrado_por; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.registro_hora_extra
    ADD CONSTRAINT fk_registro_hora_extra_registrado_por FOREIGN KEY (registrado_por_id) REFERENCES public.users(id) ON DELETE RESTRICT;


--
-- Name: ruta_proceso_cat fk_ruta_proceso_cat_categoria; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ruta_proceso_cat
    ADD CONSTRAINT fk_ruta_proceso_cat_categoria FOREIGN KEY (categoria_id) REFERENCES public.categoria(categoria_id) ON DELETE CASCADE;


--
-- Name: ruta_proceso_cat_version fk_ruta_proceso_cat_version_ruta_cat; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ruta_proceso_cat_version
    ADD CONSTRAINT fk_ruta_proceso_cat_version_ruta_cat FOREIGN KEY (ruta_proceso_cat_id) REFERENCES public.ruta_proceso_cat(id) ON DELETE CASCADE;


--
-- Name: ruta_proceso_edge fk_ruta_proceso_edge_source_node; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ruta_proceso_edge
    ADD CONSTRAINT fk_ruta_proceso_edge_source_node FOREIGN KEY (source_node_id) REFERENCES public.ruta_proceso_node(id) ON DELETE CASCADE;


--
-- Name: ruta_proceso_edge fk_ruta_proceso_edge_target_node; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ruta_proceso_edge
    ADD CONSTRAINT fk_ruta_proceso_edge_target_node FOREIGN KEY (target_node_id) REFERENCES public.ruta_proceso_node(id) ON DELETE CASCADE;


--
-- Name: ruta_proceso_edge fk_ruta_proceso_edge_version; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ruta_proceso_edge
    ADD CONSTRAINT fk_ruta_proceso_edge_version FOREIGN KEY (ruta_proceso_cat_version_id) REFERENCES public.ruta_proceso_cat_version(id) ON DELETE CASCADE;


--
-- Name: ruta_proceso_node fk_ruta_proceso_node_area_operativa; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ruta_proceso_node
    ADD CONSTRAINT fk_ruta_proceso_node_area_operativa FOREIGN KEY (area_operativa_id) REFERENCES public.area_operativa(area_id) ON DELETE SET NULL;


--
-- Name: ruta_proceso_node fk_ruta_proceso_node_proceso_produccion; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ruta_proceso_node
    ADD CONSTRAINT fk_ruta_proceso_node_proceso_produccion FOREIGN KEY (proceso_produccion_id) REFERENCES public.proceso_produccion(proceso_id) ON DELETE RESTRICT;


--
-- Name: ruta_proceso_node fk_ruta_proceso_node_version; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ruta_proceso_node
    ADD CONSTRAINT fk_ruta_proceso_node_version FOREIGN KEY (ruta_proceso_cat_version_id) REFERENCES public.ruta_proceso_cat_version(id) ON DELETE CASCADE;


--
-- Name: seguimiento_orden_area fk_seguimiento_orden_area_poe_documento_version; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.seguimiento_orden_area
    ADD CONSTRAINT fk_seguimiento_orden_area_poe_documento_version FOREIGN KEY (poe_documento_version_id) REFERENCES public.proceso_produccion_documento_version(id) ON DELETE RESTRICT;


--
-- Name: seguimiento_orden_area_evento fk_soa_evento_revertido; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.seguimiento_orden_area_evento
    ADD CONSTRAINT fk_soa_evento_revertido FOREIGN KEY (evento_revertido_id) REFERENCES public.seguimiento_orden_area_evento(id) ON DELETE SET NULL;


--
-- Name: unidad_medida_area_operativa fk_umao_area_operativa; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.unidad_medida_area_operativa
    ADD CONSTRAINT fk_umao_area_operativa FOREIGN KEY (area_operativa_id) REFERENCES public.area_operativa(area_id) ON DELETE CASCADE;


--
-- Name: insumos insumos_unidad_receta_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.insumos
    ADD CONSTRAINT insumos_unidad_receta_fkey FOREIGN KEY (unidad_receta) REFERENCES public.unidad_medida_producto(codigo);


--
-- Name: lote lote_orden_fabricacion_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lote
    ADD CONSTRAINT lote_orden_fabricacion_id_fkey FOREIGN KEY (orden_fabricacion_id) REFERENCES public.orden_fabricacion(orden_fabricacion_id);


--
-- Name: lote lote_producto_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lote
    ADD CONSTRAINT lote_producto_id_fkey FOREIGN KEY (producto_id) REFERENCES public.productos(producto_id);


--
-- Name: maestra_notificacion_users maestra_notificacion_users_notificacion_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.maestra_notificacion_users
    ADD CONSTRAINT maestra_notificacion_users_notificacion_id_fkey FOREIGN KEY (notificacion_id) REFERENCES public.maestra_notificacion(id) ON DELETE CASCADE;


--
-- Name: maestra_notificacion_users maestra_notificacion_users_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.maestra_notificacion_users
    ADD CONSTRAINT maestra_notificacion_users_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: modulo_accesos modulo_accesos_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.modulo_accesos
    ADD CONSTRAINT modulo_accesos_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: monitoreo_evento_error monitoreo_evento_error_grupo_error_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.monitoreo_evento_error
    ADD CONSTRAINT monitoreo_evento_error_grupo_error_id_fkey FOREIGN KEY (grupo_error_id) REFERENCES public.monitoreo_grupo_error(id) ON DELETE RESTRICT;


--
-- Name: mps_fabricacion_detalle mps_fabricacion_detalle_mps_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.mps_fabricacion_detalle
    ADD CONSTRAINT mps_fabricacion_detalle_mps_id_fkey FOREIGN KEY (mps_id) REFERENCES public.mps_fabricacion_semanal(id);


--
-- Name: mps_fabricacion_detalle mps_fabricacion_detalle_semiterminado_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.mps_fabricacion_detalle
    ADD CONSTRAINT mps_fabricacion_detalle_semiterminado_id_fkey FOREIGN KEY (semiterminado_id) REFERENCES public.productos(producto_id);


--
-- Name: orden_fabricacion orden_fabricacion_creada_por_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.orden_fabricacion
    ADD CONSTRAINT orden_fabricacion_creada_por_id_fkey FOREIGN KEY (creada_por_id) REFERENCES public.users(id);


--
-- Name: orden_fabricacion orden_fabricacion_manufacturing_version_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.orden_fabricacion
    ADD CONSTRAINT orden_fabricacion_manufacturing_version_id_fkey FOREIGN KEY (manufacturing_version_id) REFERENCES public.manufacturing_versions(id);


--
-- Name: orden_fabricacion_operacion orden_fabricacion_operacion_area_operativa_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.orden_fabricacion_operacion
    ADD CONSTRAINT orden_fabricacion_operacion_area_operativa_id_fkey FOREIGN KEY (area_operativa_id) REFERENCES public.area_operativa(area_id);


--
-- Name: orden_fabricacion_operacion_dependencia orden_fabricacion_operacion_depen_operacion_predecesora_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.orden_fabricacion_operacion_dependencia
    ADD CONSTRAINT orden_fabricacion_operacion_depen_operacion_predecesora_id_fkey FOREIGN KEY (operacion_predecesora_id) REFERENCES public.orden_fabricacion_operacion(id) ON DELETE RESTRICT;


--
-- Name: orden_fabricacion_operacion_dependencia orden_fabricacion_operacion_dependen_operacion_sucesora_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.orden_fabricacion_operacion_dependencia
    ADD CONSTRAINT orden_fabricacion_operacion_dependen_operacion_sucesora_id_fkey FOREIGN KEY (operacion_sucesora_id) REFERENCES public.orden_fabricacion_operacion(id) ON DELETE RESTRICT;


--
-- Name: orden_fabricacion_operacion_evento orden_fabricacion_operacion_evento_evento_revertido_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.orden_fabricacion_operacion_evento
    ADD CONSTRAINT orden_fabricacion_operacion_evento_evento_revertido_id_fkey FOREIGN KEY (evento_revertido_id) REFERENCES public.orden_fabricacion_operacion_evento(id) ON DELETE RESTRICT;


--
-- Name: orden_fabricacion_operacion_evento orden_fabricacion_operacion_evento_operacion_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.orden_fabricacion_operacion_evento
    ADD CONSTRAINT orden_fabricacion_operacion_evento_operacion_id_fkey FOREIGN KEY (operacion_id) REFERENCES public.orden_fabricacion_operacion(id) ON DELETE RESTRICT;


--
-- Name: orden_fabricacion_operacion_evento orden_fabricacion_operacion_evento_usuario_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.orden_fabricacion_operacion_evento
    ADD CONSTRAINT orden_fabricacion_operacion_evento_usuario_id_fkey FOREIGN KEY (usuario_id) REFERENCES public.users(id);


--
-- Name: orden_fabricacion_operacion orden_fabricacion_operacion_orden_fabricacion_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.orden_fabricacion_operacion
    ADD CONSTRAINT orden_fabricacion_operacion_orden_fabricacion_id_fkey FOREIGN KEY (orden_fabricacion_id) REFERENCES public.orden_fabricacion(orden_fabricacion_id) ON DELETE RESTRICT;


--
-- Name: orden_fabricacion_operacion orden_fabricacion_operacion_poe_documento_version_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.orden_fabricacion_operacion
    ADD CONSTRAINT orden_fabricacion_operacion_poe_documento_version_id_fkey FOREIGN KEY (poe_documento_version_id) REFERENCES public.proceso_produccion_documento_version(id) ON DELETE RESTRICT;


--
-- Name: orden_fabricacion_operacion orden_fabricacion_operacion_usuario_reporta_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.orden_fabricacion_operacion
    ADD CONSTRAINT orden_fabricacion_operacion_usuario_reporta_id_fkey FOREIGN KEY (usuario_reporta_id) REFERENCES public.users(id);


--
-- Name: orden_fabricacion orden_fabricacion_orden_produccion_origen_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.orden_fabricacion
    ADD CONSTRAINT orden_fabricacion_orden_produccion_origen_id_fkey FOREIGN KEY (orden_produccion_origen_id) REFERENCES public.ordenes_produccion(orden_id);


--
-- Name: orden_fabricacion orden_fabricacion_responsable_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.orden_fabricacion
    ADD CONSTRAINT orden_fabricacion_responsable_id_fkey FOREIGN KEY (responsable_id) REFERENCES public.users(id);


--
-- Name: orden_fabricacion orden_fabricacion_semiterminado_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.orden_fabricacion
    ADD CONSTRAINT orden_fabricacion_semiterminado_id_fkey FOREIGN KEY (semiterminado_id) REFERENCES public.productos(producto_id);


--
-- Name: ordenes_produccion ordenes_produccion_manufacturing_version_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ordenes_produccion
    ADD CONSTRAINT ordenes_produccion_manufacturing_version_id_fkey FOREIGN KEY (manufacturing_version_id) REFERENCES public.manufacturing_versions(id);


--
-- Name: producto_costo_historial producto_costo_historial_carga_costos_lote_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.producto_costo_historial
    ADD CONSTRAINT producto_costo_historial_carga_costos_lote_id_fkey FOREIGN KEY (carga_costos_lote_id) REFERENCES public.carga_costos_lote(id) ON DELETE SET NULL;


--
-- Name: producto_costo_historial producto_costo_historial_usuario_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.producto_costo_historial
    ADD CONSTRAINT producto_costo_historial_usuario_id_fkey FOREIGN KEY (usuario_id) REFERENCES public.users(id) ON DELETE SET NULL;


--
-- Name: productos productos_unidad_contenido_por_envase_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.productos
    ADD CONSTRAINT productos_unidad_contenido_por_envase_fkey FOREIGN KEY (unidad_contenido_por_envase) REFERENCES public.unidad_medida_producto(codigo);


--
-- Name: reporte_produccion_lote reporte_produccion_lote_anulado_por_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reporte_produccion_lote
    ADD CONSTRAINT reporte_produccion_lote_anulado_por_id_fkey FOREIGN KEY (anulado_por_id) REFERENCES public.users(id);


--
-- Name: reporte_produccion_lote reporte_produccion_lote_cierre_produccion_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reporte_produccion_lote
    ADD CONSTRAINT reporte_produccion_lote_cierre_produccion_id_fkey FOREIGN KEY (cierre_produccion_id) REFERENCES public.cierre_produccion(id);


--
-- Name: reporte_produccion_lote reporte_produccion_lote_lote_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reporte_produccion_lote
    ADD CONSTRAINT reporte_produccion_lote_lote_id_fkey FOREIGN KEY (lote_id) REFERENCES public.lote(id);


--
-- Name: reporte_produccion_lote reporte_produccion_lote_orden_produccion_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reporte_produccion_lote
    ADD CONSTRAINT reporte_produccion_lote_orden_produccion_id_fkey FOREIGN KEY (orden_produccion_id) REFERENCES public.ordenes_produccion(orden_id);


--
-- Name: reporte_produccion_lote reporte_produccion_lote_reportado_por_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reporte_produccion_lote
    ADD CONSTRAINT reporte_produccion_lote_reportado_por_id_fkey FOREIGN KEY (reportado_por_id) REFERENCES public.users(id);


--
-- Name: reporte_produccion_lote reporte_produccion_lote_seguimiento_orden_area_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reporte_produccion_lote
    ADD CONSTRAINT reporte_produccion_lote_seguimiento_orden_area_id_fkey FOREIGN KEY (seguimiento_orden_area_id) REFERENCES public.seguimiento_orden_area(id);


--
-- Name: reporte_produccion_lote reporte_produccion_lote_transaccion_almacen_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reporte_produccion_lote
    ADD CONSTRAINT reporte_produccion_lote_transaccion_almacen_id_fkey FOREIGN KEY (transaccion_almacen_id) REFERENCES public.transaccion_almacen(transaccion_id);


--
-- Name: seguimiento_orden_area seguimiento_orden_area_area_operativa_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.seguimiento_orden_area
    ADD CONSTRAINT seguimiento_orden_area_area_operativa_id_fkey FOREIGN KEY (area_operativa_id) REFERENCES public.area_operativa(area_id) ON DELETE CASCADE;


--
-- Name: seguimiento_orden_area_evento seguimiento_orden_area_evento_seguimiento_orden_area_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.seguimiento_orden_area_evento
    ADD CONSTRAINT seguimiento_orden_area_evento_seguimiento_orden_area_id_fkey FOREIGN KEY (seguimiento_orden_area_id) REFERENCES public.seguimiento_orden_area(id) ON DELETE CASCADE;


--
-- Name: seguimiento_orden_area_evento seguimiento_orden_area_evento_usuario_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.seguimiento_orden_area_evento
    ADD CONSTRAINT seguimiento_orden_area_evento_usuario_id_fkey FOREIGN KEY (usuario_id) REFERENCES public.users(id) ON DELETE SET NULL;


--
-- Name: seguimiento_orden_area seguimiento_orden_area_orden_produccion_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.seguimiento_orden_area
    ADD CONSTRAINT seguimiento_orden_area_orden_produccion_id_fkey FOREIGN KEY (orden_produccion_id) REFERENCES public.ordenes_produccion(orden_id) ON DELETE CASCADE;


--
-- Name: seguimiento_orden_area seguimiento_orden_area_ruta_proceso_node_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.seguimiento_orden_area
    ADD CONSTRAINT seguimiento_orden_area_ruta_proceso_node_id_fkey FOREIGN KEY (ruta_proceso_node_id) REFERENCES public.ruta_proceso_node(id) ON DELETE CASCADE;


--
-- Name: seguimiento_orden_area seguimiento_orden_area_usuario_reporta_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.seguimiento_orden_area
    ADD CONSTRAINT seguimiento_orden_area_usuario_reporta_id_fkey FOREIGN KEY (usuario_reporta_id) REFERENCES public.users(id) ON DELETE SET NULL;


--
-- Name: tab_accesos tab_accesos_modulo_acceso_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tab_accesos
    ADD CONSTRAINT tab_accesos_modulo_acceso_id_fkey FOREIGN KEY (modulo_acceso_id) REFERENCES public.modulo_accesos(id) ON DELETE CASCADE;


--
-- PostgreSQL database dump complete
--
