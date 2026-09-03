package exotic.app.planta.model.controles;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "control_ejecucion_lectura", uniqueConstraints = @UniqueConstraint(
        name = "uq_control_lectura", columnNames = {"muestra_id", "indice_unidad"}))
@Getter @Setter @NoArgsConstructor
public class LecturaEjecucionControl {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "muestra_id", nullable = false, updatable = false)
    private MuestraEjecucionControl muestra;
    @Column(name = "indice_unidad", nullable = false, updatable = false) private Integer indiceUnidad;
    @Column(name = "valor_numerico", precision = 20, scale = 8, updatable = false) private BigDecimal valorNumerico;
    @Column(name = "valor_booleano", updatable = false) private Boolean valorBooleano;
}
