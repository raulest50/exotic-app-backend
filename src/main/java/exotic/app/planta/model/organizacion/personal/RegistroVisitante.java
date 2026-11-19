package exotic.app.planta.model.organizacion.personal;

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

    @Column(name = "fecha_registro", nullable = false, updatable = false,
            columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private Instant fechaRegistro;

    public RegistroVisitante(String cedula, String nombreCompleto, Integer edad,
                             Genero genero, Motivo motivo, String motivoOtro, Instant fechaRegistro) {
        this.cedula = cedula;
        this.nombreCompleto = nombreCompleto;
        this.edad = edad;
        this.genero = genero;
        this.motivo = motivo;
        this.motivoOtro = motivoOtro;
        this.fechaRegistro = fechaRegistro;
    }

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
