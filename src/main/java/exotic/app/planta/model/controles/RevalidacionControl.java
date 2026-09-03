package exotic.app.planta.model.controles;

import exotic.app.planta.model.users.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "control_revalidacion", uniqueConstraints = @UniqueConstraint(
        name = "uq_control_revalidacion_ciclo", columnNames = {"control_requerido_id", "ciclo_revision_numero"}))
@Getter @Setter @NoArgsConstructor
public class RevalidacionControl {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "control_requerido_id", nullable = false, updatable = false)
    private ControlRequerido controlRequerido;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ejecucion_revalidada_id", nullable = false, updatable = false)
    private EjecucionControl ejecucionRevalidada;
    @Column(name = "ciclo_revision_numero", nullable = false, updatable = false)
    private Integer cicloRevisionNumero;
    @Column(nullable = false, length = 1000, updatable = false) private String justificacion;
    @Column(name = "confirmada_en", nullable = false, updatable = false) private LocalDateTime confirmadaEn;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "confirmada_por_id", nullable = false, updatable = false)
    private User confirmadaPor;
}
