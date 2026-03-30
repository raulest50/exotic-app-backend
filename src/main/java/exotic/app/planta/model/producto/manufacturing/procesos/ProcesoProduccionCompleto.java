package exotic.app.planta.model.producto.manufacturing.procesos;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import exotic.app.planta.model.producto.Producto;
import exotic.app.planta.model.producto.manufacturing.procesos.nodo.ProcesoFabricacionEdge;
import exotic.app.planta.model.producto.manufacturing.procesos.nodo.ProcesoFabricacionNodo;
import lombok.*;

import java.util.List;

@Entity
@Table(name="procesos_produccion")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProcesoProduccionCompleto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "proceso_completo_id", unique = true, updatable = true, nullable = false)
    private int procesoCompletoId;

    @OneToOne
    @JoinColumn(name = "producto_id", unique = true)
    @JsonBackReference("producto-proceso")
    private Producto producto;

    @OneToMany(mappedBy = "procesoProduccionCompleto", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    @JsonManagedReference("proceso-completo-nodes")
    private List<ProcesoFabricacionNodo> nodes;

    @OneToMany(mappedBy = "procesoProduccionCompleto", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    @JsonManagedReference("proceso-completo-edges")
    private List<ProcesoFabricacionEdge> edges;

    @Column(name = "rendimiento_teorico")
    private double rendimientoTeorico;
}
