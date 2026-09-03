package exotic.app.planta.model.controles;

import exotic.app.planta.model.calidad.ControlProcesoPlantilla;
import exotic.app.planta.model.users.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "control_plan_version", uniqueConstraints =
        @UniqueConstraint(name = "uq_control_plan_version_numero", columnNames = {"plan_id", "numero"}))
@Getter @Setter @NoArgsConstructor
public class VersionPlanControl {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    private PlanControl plan;
    @Column(nullable = false)
    private Integer numero;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoVersionPlanControl estado;
    @Column(nullable = false, length = 120)
    private String proposito;
    @Column(name = "motivo_cambio", length = 500)
    private String motivoCambio;
    @Column(name = "responsable_ejecucion", nullable = false, length = 120)
    private String responsableEjecucion;
    @Column(name = "responsable_revision", length = 120)
    private String responsableRevision;
    @Column(name = "responsable_disposicion", length = 120)
    private String responsableDisposicion;
    @Column(name = "creada_en", nullable = false, updatable = false)
    private LocalDateTime creadaEn;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "creada_por_id", updatable = false)
    private User creadaPor;
    @Column(name = "publicada_en") private LocalDateTime publicadaEn;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "publicada_por_id") private User publicadaPor;
    @Column(name = "retirada_en") private LocalDateTime retiradaEn;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "retirada_por_id") private User retiradaPor;
    @OneToOne(fetch = FetchType.LAZY) @JoinColumn(name = "legacy_plantilla_id", unique = true)
    private ControlProcesoPlantilla legacyPlantilla;
    @OneToMany(mappedBy = "version", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<AplicabilidadPlanControl> aplicabilidades = new ArrayList<>();
    @OneToMany(mappedBy = "version", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orden ASC")
    private List<CaracteristicaPlanControl> caracteristicas = new ArrayList<>();
}
