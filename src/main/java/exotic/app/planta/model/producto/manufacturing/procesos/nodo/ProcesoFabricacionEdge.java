package exotic.app.planta.model.producto.manufacturing.procesos.nodo;

import com.fasterxml.jackson.annotation.JsonBackReference;
import exotic.app.planta.model.producto.manufacturing.procesos.ProcesoProduccionCompleto;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "proceso_fabricacion_edge")
@Getter
@Setter
public class ProcesoFabricacionEdge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "frontend_id", nullable = false)
    private String frontendId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "proceso_completo_id", nullable = false)
    @JsonBackReference("proceso-completo-edges")
    private ProcesoProduccionCompleto procesoProduccionCompleto;

    @ManyToOne(optional = false)
    @JoinColumn(name = "source_node_id", nullable = false)
    private ProcesoFabricacionNodo sourceNode;

    @ManyToOne(optional = false)
    @JoinColumn(name = "target_node_id", nullable = false)
    private ProcesoFabricacionNodo targetNode;
}
