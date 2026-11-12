package exotic.app.planta.model.organizacion.personal.permiso;

import exotic.app.planta.model.users.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;

/** Immutable evidence, detached from later edits of a user's visual signature. */
@Entity
@Table(name = "firma_permiso_personal", uniqueConstraints = @UniqueConstraint(columnNames = {"solicitud_id", "rol"}))
@Getter @Setter @NoArgsConstructor
public class FirmaPermisoPersonal {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "solicitud_id", nullable = false, updatable = false) private SolicitudPermisoPersonal solicitud;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "firmante_id", nullable = false, updatable = false) private User firmante;
    @Column(nullable = false, updatable = false, length = 20) private String rol;
    @Column(nullable = false, updatable = false, length = 255) private String username;
    @Column(nullable = false, updatable = false, length = 255) private String nombre;
    @Column(nullable = false, updatable = false, length = 30) private String cedula;
    @Column(nullable = false, updatable = false) private LocalDateTime firmadoEn;
    @Column(nullable = false, updatable = false, length = 64) private String hashContenido;
    @Column(nullable = false, updatable = false, length = 64) private String sha256;
    @Column(updatable = false) private Long firmaVisualVersionId;
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(nullable = false, updatable = false, columnDefinition = "BYTEA") private byte[] contenido;
}
