package exotic.app.planta.model.controles;

import exotic.app.planta.model.users.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "control_idempotencia", uniqueConstraints = @UniqueConstraint(
        name = "uq_control_idempotencia_actor_accion_recurso_clave",
        columnNames = {"actor_id", "accion", "recurso", "clave"}))
@Getter
@Setter
@NoArgsConstructor
public class RegistroIdempotenciaControl {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actor_id", nullable = false, updatable = false)
    private User actor;

    @Column(nullable = false, length = 80, updatable = false)
    private String accion;

    @Column(nullable = false, length = 180, updatable = false)
    private String recurso;

    @Column(nullable = false, length = 200, updatable = false)
    private String clave;

    @Column(name = "huella_payload", nullable = false, length = 64, updatable = false)
    private String huellaPayload;

    @Column(name = "respuesta_json", columnDefinition = "TEXT")
    private String respuestaJson;

    @Column(name = "creada_en", nullable = false, updatable = false)
    private LocalDateTime creadaEn;

    @Column(name = "completada_en")
    private LocalDateTime completadaEn;

    public boolean completada() {
        return respuestaJson != null && completadaEn != null;
    }
}
