package exotic.app.planta.model.producto.manufacturing.receta;

import jakarta.persistence.*;
import exotic.app.planta.model.producto.Producto;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@Entity
@Table(name="insumos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Insumo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "insumo_id", unique = true, updatable = false, nullable = false)
    private int insumoId;

    @ManyToOne
    @JoinColumn(name = "input_producto_id")
    private Producto producto;

    // Compatibility coefficient, in input product units per output product unit.
    private double cantidadRequerida;
    @Column(precision = 24, scale = 12)
    private java.math.BigDecimal cantidadReceta;
    @Column(length = 16)
    private String unidadReceta;

}
