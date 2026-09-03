package exotic.app.planta.model.controles;

import exotic.app.planta.model.users.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "control_desviacion")
@Getter @Setter @NoArgsConstructor
public class DesviacionControl {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "control_requerido_id", nullable = false, updatable = false)
    private ControlRequerido controlRequerido;
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ejecucion_origen_id", nullable = false, unique = true, updatable = false)
    private EjecucionControl ejecucionOrigen;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20, updatable = false)
    private AmbitoControl ambito;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private EstadoDesviacionControl estado;
    @Enumerated(EnumType.STRING) @Column(length = 30)
    private DisposicionDesviacionControl disposicion;
    @Column(columnDefinition = "TEXT") private String investigacion;
    @Column(columnDefinition = "TEXT") private String resolucion;
    @Column(name = "justificacion_disposicion", columnDefinition = "TEXT")
    private String justificacionDisposicion;
    @Column(name = "abierta_en", nullable = false, updatable = false) private LocalDateTime abiertaEn;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "abierta_por_id", nullable = false, updatable = false)
    private User abiertaPor;
    @Column(name = "resuelta_en") private LocalDateTime resueltaEn;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "resuelta_por_id") private User resueltaPor;
    @Column(name = "cerrada_en") private LocalDateTime cerradaEn;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "cerrada_por_id") private User cerradaPor;
}
