package exotic.app.planta.model.produccion.ruprocatdesigner;

import exotic.app.planta.model.organizacion.AreaOperativa;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "ruta_proceso_node")
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class RutaProcesoNode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "frontend_id")
    private String frontendId;

    @Column(name = "posicion_x")
    private double posicionX;

    @Column(name = "posicion_y")
    private double posicionY;

    @ManyToOne
    @JoinColumn(name = "area_operativa_id")
    private AreaOperativa areaOperativa;

    private String label;
}
