package exotic.app.planta.model.calidad;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "calidad_control_proceso_lectura")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ControlProcesoLectura {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "muestra_id", nullable = false)
    private ControlProcesoMuestra muestra;

    @Column(name = "indice_unidad", nullable = false)
    private Integer indiceUnidad;

    @Column(name = "valor_numerico")
    private Double valorNumerico;

    @Column(name = "valor_booleano")
    private Boolean valorBooleano;
}
