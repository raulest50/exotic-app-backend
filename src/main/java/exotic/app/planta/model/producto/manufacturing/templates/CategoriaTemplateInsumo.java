package exotic.app.planta.model.producto.manufacturing.templates;

import exotic.app.planta.model.producto.Producto;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "categoria_template_insumo")
@Getter
@Setter
public class CategoriaTemplateInsumo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "template_id", nullable = false)
    private CategoriaManufacturingTemplate template;

    @ManyToOne(optional = false)
    @JoinColumn(name = "input_producto_id", nullable = false)
    private Producto producto;

    @Column(name = "cantidad_requerida", nullable = false)
    private double cantidadRequerida;

    @Column(name = "orden", nullable = false)
    private int orden;
}
