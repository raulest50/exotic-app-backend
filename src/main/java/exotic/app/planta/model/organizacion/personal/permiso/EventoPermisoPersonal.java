package exotic.app.planta.model.organizacion.personal.permiso;

import exotic.app.planta.model.users.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;

/** Append-only record, including administrative configuration changes. */
@Entity @Table(name = "evento_permiso_personal")
@Getter @Setter @NoArgsConstructor
public class EventoPermisoPersonal {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "solicitud_id", updatable = false)
    private SolicitudPermisoPersonal solicitud;
    @Column(updatable = false) private Long empleadoId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actor_id", nullable = false, updatable = false) private User actor;
    @Column(nullable = false, updatable = false, length = 255) private String actorNombre;
    @Column(nullable = false, updatable = false, length = 40) private String tipo;
    @Column(nullable = false, updatable = false) private LocalDateTime creadoEn;
    @Column(nullable = false, updatable = false, length = 4000) private String detalle;
}
