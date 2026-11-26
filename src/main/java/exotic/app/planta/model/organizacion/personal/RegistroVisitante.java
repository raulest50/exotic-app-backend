package exotic.app.planta.model.organizacion.personal;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/** Una llegada independiente; la cedula no identifica de forma unica el registro. */
@Entity
@Table(name = "registro_visitante")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RegistroVisitante {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30, updatable = false)
    private String cedula;

    @Column(name = "nombre_completo", nullable = false, length = 160, updatable = false)
    private String nombreCompleto;

    @Column(updatable = false)
    private Integer edad;

    @Enumerated(EnumType.STRING)
    @Column(length = 30, updatable = false)
    private Genero genero;

    @Enumerated(EnumType.STRING)
    @Column(length = 40, updatable = false)
    private Motivo motivo;

    @Column(name = "motivo_otro", length = 300, updatable = false)
    private String motivoOtro;

    @Column(name = "cargo_empresa", length = 160, updatable = false)
    private String cargoEmpresa;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_ingreso", length = 30, updatable = false)
    private TipoIngreso tipoIngreso;

    @Column(name = "area_persona_visita", length = 160, updatable = false)
    private String areaPersonaVisita;

    @Column(name = "consentimiento_version", length = 40, updatable = false)
    private String consentimientoVersion;

    @JsonIgnore
    @Column(name = "consentimiento_texto", columnDefinition = "TEXT", updatable = false)
    private String consentimientoTexto;

    @Column(name = "consentimiento_aceptado_en", columnDefinition = "TIMESTAMP WITH TIME ZONE", updatable = false)
    private Instant consentimientoAceptadoEn;

    @JsonIgnore
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "firma_contenido", columnDefinition = "BYTEA", updatable = false)
    private byte[] firmaContenido;

    @Column(name = "firma_sha256", length = 64, updatable = false)
    private String firmaSha256;

    @JsonIgnore
    @Column(name = "consentimiento_firma_texto", columnDefinition = "TEXT", updatable = false)
    private String consentimientoFirmaTexto;

    @Column(name = "fecha_registro", nullable = false, updatable = false,
            columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private Instant fechaRegistro;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Estado estado;

    @Column(name = "fecha_salida", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private Instant fechaSalida;

    @Column(name = "salida_registrada_en", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private Instant salidaRegistradaEn;

    @Column(name = "salida_registrada_por_id")
    private Long salidaRegistradaPorId;

    // Snapshots preserve the closing actor without loading or linking the User entity.
    @Column(name = "salida_registrada_por_username", length = 255)
    private String salidaRegistradaPorUsername;

    public RegistroVisitante(String cedula, String nombreCompleto, Integer edad,
                             Genero genero, Motivo motivo, String motivoOtro, String cargoEmpresa,
                             TipoIngreso tipoIngreso, String areaPersonaVisita, String consentimientoVersion,
                             String consentimientoTexto, byte[] firmaContenido, String firmaSha256,
                             String consentimientoFirmaTexto, Instant fechaRegistro) {
        this.cedula = cedula;
        this.nombreCompleto = nombreCompleto;
        this.edad = edad;
        this.genero = genero;
        this.motivo = motivo;
        this.motivoOtro = motivoOtro;
        this.cargoEmpresa = cargoEmpresa;
        this.tipoIngreso = tipoIngreso;
        this.areaPersonaVisita = areaPersonaVisita;
        this.consentimientoVersion = consentimientoVersion;
        this.consentimientoTexto = consentimientoTexto;
        this.consentimientoAceptadoEn = fechaRegistro;
        this.firmaContenido = firmaContenido;
        this.firmaSha256 = firmaSha256;
        this.consentimientoFirmaTexto = consentimientoFirmaTexto;
        this.fechaRegistro = fechaRegistro;
        this.estado = Estado.PENDIENTE_SALIDA;
    }

    public enum Estado { PENDIENTE_SALIDA, SALIO, SIN_SEGUIMIENTO }

    public enum TipoIngreso { TRABAJADOR, VISITANTE, CONTRATISTA, PROVEEDOR, OTRO }

    public enum Motivo {
        ENTREGA_MATERIALES,
        RECOGIDA_MERCANCIA,
        MANTENIMIENTO_EQUIPOS,
        MENSAJERIA_DOCUMENTOS,
        REUNION_COMERCIAL,
        CALIBRACION_VALIDACION,
        SERVICIOS_INSTALACIONES,
        AUDITORIA_INSPECCION,
        CAPACITACION_ASESORIA,
        GESTION_HUMANA,
        OTROS
    }

    public enum Genero {
        FEMENINO,
        MASCULINO,
        NO_BINARIO,
        OTRO,
        PREFIERO_NO_INFORMAR
    }
}
