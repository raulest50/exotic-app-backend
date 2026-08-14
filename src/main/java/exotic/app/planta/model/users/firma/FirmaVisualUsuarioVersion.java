package exotic.app.planta.model.users.firma;

import com.fasterxml.jackson.annotation.JsonIgnore;
import exotic.app.planta.model.users.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "firma_visual_usuario_version")
@Getter
@Setter
@NoArgsConstructor
public class FirmaVisualUsuarioVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false, updatable = false)
    private User titular;

    @Column(nullable = false, updatable = false)
    private Integer version;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Estado estado;

    @Column(name = "nombre_archivo_original", nullable = false, length = 255, updatable = false)
    private String nombreArchivoOriginal;

    @Column(name = "content_type", nullable = false, length = 80, updatable = false)
    private String contentType;

    @Column(name = "tamano_bytes", nullable = false, updatable = false)
    private Long tamanoBytes;

    @Column(name = "ancho_px", nullable = false, updatable = false)
    private Integer anchoPx;

    @Column(name = "alto_px", nullable = false, updatable = false)
    private Integer altoPx;

    @Column(nullable = false, length = 64, updatable = false)
    private String sha256;

    @JsonIgnore
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(nullable = false, columnDefinition = "BYTEA", updatable = false)
    private byte[] contenido;

    @Column(name = "vigente_desde", nullable = false, updatable = false)
    private LocalDateTime vigenteDesde;

    @Column(name = "vigente_hasta")
    private LocalDateTime vigenteHasta;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private LocalDateTime creadoEn;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "configurada_por_id", nullable = false, updatable = false)
    private User configuradaPor;

    @Column(name = "configurada_por_username", nullable = false, length = 255, updatable = false)
    private String configuradaPorUsername;

    @Column(name = "configurada_por_nombre", nullable = false, length = 255, updatable = false)
    private String configuradaPorNombre;

    @Column(name = "motivo_cambio", nullable = false, columnDefinition = "TEXT", updatable = false)
    private String motivoCambio;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "retirada_por_id")
    private User retiradaPor;

    @Column(name = "retirada_por_username", length = 255)
    private String retiradaPorUsername;

    @Column(name = "retirada_por_nombre", length = 255)
    private String retiradaPorNombre;

    @Column(name = "motivo_retiro", columnDefinition = "TEXT")
    private String motivoRetiro;

    public enum Estado {
        VIGENTE,
        RETIRADA
    }
}
