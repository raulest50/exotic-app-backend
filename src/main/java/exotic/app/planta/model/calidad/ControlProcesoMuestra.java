package exotic.app.planta.model.calidad;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "calidad_control_proceso_muestra")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ControlProcesoMuestra {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ejecucion_id", nullable = false)
    private ControlProcesoEjecucion ejecucion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "caracteristica_id", nullable = false)
    private ControlProcesoCaracteristica caracteristica;

    @Column(name = "numero_muestra", nullable = false)
    private Integer numeroMuestra;

    @OneToMany(mappedBy = "muestra", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("indiceUnidad ASC")
    private List<ControlProcesoLectura> lecturas = new ArrayList<>();
}
