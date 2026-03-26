package exotic.app.planta.model.produccion.ruprocatdesigner;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "ruta_proceso_edge")
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class RutaProcesoEdge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "ruta_proceso_cat_id")
    private RutaProcesoCat rutaProcesoCat;

    @Column(name = "frontend_id")
    private String frontendId;

    @ManyToOne
    @JoinColumn(name = "source_node_id")
    private RutaProcesoNode sourceNode;

    @ManyToOne
    @JoinColumn(name = "target_node_id")
    private RutaProcesoNode targetNode;
}
