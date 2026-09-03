package exotic.app.planta.model.controles;

import exotic.app.planta.model.calidad.ControlProcesoEjecucion;
import exotic.app.planta.model.users.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "control_ejecucion")
@Getter @Setter @NoArgsConstructor
public class EjecucionControl {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "control_requerido_id", nullable = false, updatable = false)
    private ControlRequerido controlRequerido;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "repeticion_de_id", updatable = false)
    private EjecucionControl repeticionDe;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "usuario_id", nullable = false, updatable = false)
    private User usuario;
    @Column(name = "fecha_registro", nullable = false, updatable = false)
    private LocalDateTime fechaRegistro;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20, updatable = false)
    private ResultadoEjecucionControl resultado;
    @Column(columnDefinition = "TEXT", updatable = false) private String observaciones;
    @Column(name = "motivo_repeticion", length = 500, updatable = false) private String motivoRepeticion;
    @OneToOne(fetch = FetchType.LAZY) @JoinColumn(name = "legacy_ejecucion_id", unique = true, updatable = false)
    private ControlProcesoEjecucion legacyEjecucion;
    @OneToMany(mappedBy = "ejecucion", cascade = CascadeType.ALL)
    @OrderBy("id ASC")
    private List<MuestraEjecucionControl> muestras = new ArrayList<>();
}
