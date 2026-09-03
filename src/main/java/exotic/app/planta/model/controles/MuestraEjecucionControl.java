package exotic.app.planta.model.controles;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "control_ejecucion_muestra", uniqueConstraints = @UniqueConstraint(
        name = "uq_control_muestra", columnNames = {"ejecucion_id", "caracteristica_id", "numero_muestra"}))
@Getter @Setter @NoArgsConstructor
public class MuestraEjecucionControl {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "ejecucion_id", nullable = false, updatable = false)
    private EjecucionControl ejecucion;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "caracteristica_id", nullable = false, updatable = false)
    private CaracteristicaPlanControl caracteristica;
    @Column(name = "numero_muestra", nullable = false, updatable = false) private Integer numeroMuestra;
    @OneToMany(mappedBy = "muestra", cascade = CascadeType.ALL)
    @OrderBy("indiceUnidad ASC")
    private List<LecturaEjecucionControl> lecturas = new ArrayList<>();
}
