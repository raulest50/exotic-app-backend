package exotic.app.planta.model.producto.costos;

import exotic.app.planta.model.users.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "carga_costos_lote")
@Getter
@Setter
@NoArgsConstructor
public class CargaCostosLote {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private User usuario;

    @Column(name = "usuario_username", nullable = false, length = 120)
    private String usuarioUsername;

    @Column(name = "nombre_archivo", nullable = false, length = 255)
    private String nombreArchivo;

    @Column(name = "archivo_sha256", nullable = false, length = 64)
    private String archivoSha256;

    @Column(nullable = false, length = 500)
    private String motivo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Estado estado;

    @Column(name = "token_hash", length = 255)
    private String tokenHash;

    @Column(name = "token_expira_en")
    private LocalDateTime tokenExpiraEn;

    @Column(name = "intentos_token", nullable = false)
    private int intentosToken;

    @Column(name = "generaciones_token", nullable = false)
    private int generacionesToken;

    @Column(name = "ultima_generacion_token_en")
    private LocalDateTime ultimaGeneracionTokenEn;

    @Column(name = "creado_en", nullable = false)
    private LocalDateTime creadoEn;

    @Column(name = "expira_en", nullable = false)
    private LocalDateTime expiraEn;

    @Column(name = "ejecutado_en")
    private LocalDateTime ejecutadoEn;

    @Column(name = "total_filas", nullable = false)
    private int totalFilas;

    @Column(name = "total_candidatas", nullable = false)
    private int totalCandidatas;

    @Column(name = "total_actualizadas", nullable = false)
    private int totalActualizadas;

    @Column(name = "total_sin_cambio", nullable = false)
    private int totalSinCambio;

    @Column(name = "total_omitidas", nullable = false)
    private int totalOmitidas;

    @OneToMany(mappedBy = "lote", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("filaExcel ASC")
    private List<CargaCostosItem> items = new ArrayList<>();

    public enum Estado { PREPARADO, EJECUTADO, EXPIRADO, BLOQUEADO, CANCELADO }
}
