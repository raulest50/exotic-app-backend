package exotic.app.planta.model.organizacion.personal.permiso;

import exotic.app.planta.model.organizacion.personal.IntegrantePersonal;
import exotic.app.planta.model.users.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity @Table(name = "configuracion_permiso_personal")
@Getter @Setter @NoArgsConstructor
public class ConfiguracionPermisoPersonal {
    @Id private Long empleadoId;
    @OneToOne(fetch = FetchType.LAZY, optional = false) @MapsId
    @JoinColumn(name = "empleado_id", nullable = false) private IntegrantePersonal empleado;
    @Version @Column(nullable = false) private Long version;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "jefe_id") private User jefe;
    @Column(nullable = false) private LocalDateTime actualizadoEn;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actualizado_por_id", nullable = false) private User actualizadoPor;
}
