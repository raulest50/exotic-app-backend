package exotic.app.planta.model.producto.manufacturing.procesos;


import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import exotic.app.planta.model.producto.Producto;
import exotic.app.planta.model.producto.manufacturing.procesos.nodo.ProcesoProduccionNode;
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

    @ManyToOne
    @JoinColumn(name = "producto_id")
    @JsonBackReference("producto-proceso")
    private Producto producto;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "proceso_completo_id")
    private List<ProcesoProduccionNode> procesosProduccion;

    @Column(name = "rendimiento_teorico")
    private double rendimientoTeorico;

    @ManyToOne
    @JoinColumn(name = "area_produccion_id")
    private AreaProduccion areaProduccion;

    @Lob
    private String diagramaJson;
}
