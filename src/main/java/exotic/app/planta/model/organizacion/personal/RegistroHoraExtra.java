package exotic.app.planta.model.organizacion.personal;

import exotic.app.planta.model.users.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "registro_hora_extra")
public class RegistroHoraExtra {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "registro_hora_extra_id", unique = true, updatable = false, nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "integrante_id", nullable = false)
    private IntegrantePersonal integrante;

    @Column(name = "fecha", nullable = false)
    private LocalDate fecha;

    @Column(name = "hora_inicio", nullable = false)
    private LocalTime horaInicio;

    @Column(name = "hora_fin", nullable = false)
    private LocalTime horaFin;

    @Column(name = "minutos", nullable = false)
    private Integer minutos;

    @Column(name = "motivo", nullable = false, length = 500)
    private String motivo;

    @Column(name = "observaciones", length = 1000)
    private String observaciones;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    private Estado estado = Estado.REGISTRADA;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "registrado_por_id", nullable = false)
    private User registradoPor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "aprobado_por_id")
    private User aprobadoPor;

    @Column(name = "fecha_registro", nullable = false)
    private LocalDateTime fechaRegistro;

    @Column(name = "fecha_decision")
    private LocalDateTime fechaDecision;

    @Column(name = "motivo_rechazo_o_anulacion", length = 1000)
    private String motivoRechazoOAnulacion;

    public enum Estado {
        REGISTRADA,
        APROBADA,
        RECHAZADA,
        ANULADA
    }
}
