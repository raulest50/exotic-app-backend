package exotic.app.planta.model.organizacion.personal.permiso;

import exotic.app.planta.model.organizacion.personal.IntegrantePersonal;
import exotic.app.planta.model.organizacion.personal.dto.PermisoPersonalDTOs.*;
import exotic.app.planta.model.users.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "solicitud_permiso_personal")
@Getter @Setter @NoArgsConstructor
public class SolicitudPermisoPersonal {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Version @Column(nullable = false) private Long version;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Estado estado;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "empleado_id", nullable = false, updatable = false) private IntegrantePersonal empleado;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "solicitante_id", nullable = false, updatable = false) private User solicitante;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "jefe_id") private User jefe;
    @Column(nullable = false, length = 255) private String empleadoNombre;
    @Column(length = 255) private String empleadoCargo;
    @Column(nullable = false, length = 255) private String solicitanteUsername;
    @Column(length = 255) private String jefeNombre;
    @Column(length = 255) private String jefeUsername;
    @Column(nullable = false) private LocalDate fechaSolicitud;
    @Column(length = 255) private String proceso;
    @Enumerated(EnumType.STRING) @Column(length = 40) private Motivo motivo;
    @Column(columnDefinition = "TEXT") private String descripcion;
    @Column(length = 1000) private String documentoSoporte;
    @Column(length = 2000) private String mecanismoRecuperacion;
    private LocalDate fechaDesde;
    private LocalDate fechaHasta;
    @Column(precision = 12, scale = 2) private BigDecimal cantidad;
    @Enumerated(EnumType.STRING) @Column(length = 10) private Unidad unidad;
    @Column(length = 255) private String soporteNombre;
    @Column(length = 80) private String soporteContentType;
    @Column(length = 64) private String soporteSha256;
    @JdbcTypeCode(SqlTypes.BINARY) @Column(columnDefinition = "BYTEA") private byte[] soporteContenido;
    @Column(length = 64) private String hashContenido;
    @Column(nullable = false, updatable = false) private LocalDateTime creadoEn;
    @Column(nullable = false) private LocalDateTime actualizadoEn;
    private LocalDateTime presentadoEn;
    private LocalDateTime resueltoEn;
    @Column(length = 2000) private String motivoDecision;
    @Column(length = 2000) private String motivoAnulacion;
}
