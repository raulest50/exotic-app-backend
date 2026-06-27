package exotic.app.planta.model.producto.manufacturing.templates;

import exotic.app.planta.model.organizacion.AreaOperativa;
import exotic.app.planta.model.producto.Producto;
import exotic.app.planta.model.producto.manufacturing.procesos.ProcesoProduccion;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "categoria_template_proceso_node")
@Getter
@Setter
public class CategoriaTemplateProcesoNode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "template_id", nullable = false)
    private CategoriaManufacturingTemplate template;

    @Column(name = "node_type", nullable = false, length = 20)
    private String nodeType;

    @Column(name = "frontend_id", nullable = false)
    private String frontendId;

    @Column(name = "posicion_x", nullable = false)
    private double posicionX;

    @Column(name = "posicion_y", nullable = false)
    private double posicionY;

    @Column(name = "label")
    private String label;

    @ManyToOne
    @JoinColumn(name = "input_producto_id")
    private Producto inputProducto;

    @ManyToOne
    @JoinColumn(name = "proceso_id")
    private ProcesoProduccion procesoProduccion;

    @ManyToOne
    @JoinColumn(name = "area_operativa_id")
    private AreaOperativa areaOperativa;
}
