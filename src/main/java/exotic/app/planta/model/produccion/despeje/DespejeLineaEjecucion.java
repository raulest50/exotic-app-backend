package exotic.app.planta.model.produccion.despeje;

import exotic.app.planta.model.inventarios.Lote;
import exotic.app.planta.model.organizacion.AreaOperativa;
import exotic.app.planta.model.produccion.SeguimientoOrdenArea;
import exotic.app.planta.model.produccion.fabricacion.OrdenFabricacionOperacion;
import exotic.app.planta.model.users.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "despeje_linea_ejecucion", uniqueConstraints =
        @UniqueConstraint(name = "uq_despeje_ejecucion_version", columnNames = {"id", "plantilla_id"}))
@Getter @Setter @NoArgsConstructor
public class DespejeLineaEjecucion {
    public enum Estado { BORRADOR, PENDIENTE_REVISION, APROBADA, RECHAZADA, INVALIDADA, UTILIZADA }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plantilla_id", nullable = false, updatable = false)
    private PlantillaDespejeVersion plantilla;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "area_operativa_id", nullable = false, updatable = false)
    private AreaOperativa areaOperativa;
    @Column(name = "area_nombre", nullable = false, length = 200, updatable = false)
    private String areaNombre;
    @Column(name = "contexto_json", nullable = false, columnDefinition = "TEXT", updatable = false)
    private String contextoJson;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seguimiento_id", updatable = false)
    private SeguimientoOrdenArea seguimiento;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "operacion_id", updatable = false)
    private OrdenFabricacionOperacion operacion;
    @Column(nullable = false, updatable = false)
    private int intento;
    @Column(name = "revision_contexto", nullable = false, updatable = false)
    private long revisionContexto;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30)
    private Estado estado = Estado.BORRADOR;
    @Column(name = "requiere_revision", nullable = false, updatable = false)
    private boolean requiereRevision;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lote_actual_id", nullable = false, updatable = false)
    private Lote loteActual;
    @Column(name = "lote_actual_numero", nullable = false, length = 255, updatable = false)
    private String loteActualNumero;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lote_anterior_id", updatable = false)
    private Lote loteAnterior;
    @Column(name = "lote_anterior_numero", length = 255, updatable = false)
    private String loteAnteriorNumero;
    @Column(name = "antecedente_conocido", nullable = false, updatable = false)
    private boolean antecedenteConocido;
    @Column(name = "confirma_antecedente_desconocido", nullable = false)
    private boolean confirmaAntecedenteDesconocido;
    @Column(name = "observacion_antecedente", columnDefinition = "TEXT")
    private String observacionAntecedente;
    @Column(columnDefinition = "TEXT")
    private String observaciones;
    @Column(name = "motivo_revision", columnDefinition = "TEXT")
    private String motivoRevision;
    @Column(name = "motivo_invalidacion", columnDefinition = "TEXT")
    private String motivoInvalidacion;
    @Column(name = "creada_en", nullable = false, updatable = false)
    private LocalDateTime creadaEn;
    @Column(name = "enviada_en") private LocalDateTime enviadaEn;
    @Column(name = "revisada_en") private LocalDateTime revisadaEn;
    @Column(name = "utilizada_en") private LocalDateTime utilizadaEn;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "creada_por_id", nullable = false, updatable = false)
    private User creadaPor;
    @Column(name = "creada_por_username", nullable = false, length = 120, updatable = false)
    private String creadaPorUsername;
    @Column(name = "creada_por_nombre", nullable = false, length = 200, updatable = false)
    private String creadaPorNombre;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "enviada_por_id")
    private User enviadaPor;
    @Column(name = "enviada_por_username", length = 120) private String enviadaPorUsername;
    @Column(name = "enviada_por_nombre", length = 200) private String enviadaPorNombre;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "revisada_por_id")
    private User revisadaPor;
    @Column(name = "revisada_por_username", length = 120) private String revisadaPorUsername;
    @Column(name = "revisada_por_nombre", length = 200) private String revisadaPorNombre;
    @Column(name = "contenido_sha256", length = 64)
    private String contenidoSha256;
    @OneToMany(mappedBy = "ejecucion", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<RespuestaDespeje> respuestas = new ArrayList<>();
    @Version @Column(nullable = false) private long version;

    @Transient private LocalDateTime envioCargado;
    @Transient private Long enviadorCargado;
    @Transient private String hashCargado;
    @Transient private String observacionesCargadas;
    @Transient private String antecedenteCargado;
    @Transient private boolean confirmacionCargada;
    @PostLoad @PostPersist @PostUpdate
    private void recordarEnvio() {
        envioCargado = enviadaEn;
        enviadorCargado = enviadaPor == null ? null : enviadaPor.getId();
        hashCargado = contenidoSha256;
        observacionesCargadas = observaciones;
        antecedenteCargado = observacionAntecedente;
        confirmacionCargada = confirmaAntecedenteDesconocido;
    }

    @PrePersist @PreUpdate
    private void validarOrigen() {
        if ((seguimiento == null) == (operacion == null))
            throw new IllegalStateException("El despeje pertenece exactamente a un seguimiento OP o una operación OF.");
        if (intento < 1 || revisionContexto < 0)
            throw new IllegalStateException("El intento y el contexto del despeje no son válidos.");
        if (envioCargado != null && (estado == Estado.BORRADOR
                || !java.util.Objects.equals(envioCargado, enviadaEn)
                || !java.util.Objects.equals(enviadorCargado, enviadaPor == null ? null : enviadaPor.getId())
                || !java.util.Objects.equals(hashCargado, contenidoSha256)
                || !java.util.Objects.equals(observacionesCargadas, observaciones)
                || !java.util.Objects.equals(antecedenteCargado, observacionAntecedente)
                || confirmacionCargada != confirmaAntecedenteDesconocido))
            throw new IllegalStateException("El contenido enviado del despeje es inmutable.");
    }
    @PreRemove
    private void preservarEvidencia() {
        throw new IllegalStateException("Los intentos de despeje se conservan como evidencia.");
    }
}
