package exotic.app.planta.model.producto;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import exotic.app.planta.model.producto.manufacturing.packaging.CasePack;
import exotic.app.planta.model.producto.manufacturing.receta.Insumo;
import exotic.app.planta.model.producto.manufacturing.procesos.ProcesoProduccionCompleto;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Entity
@DiscriminatorValue("T")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Terminado extends Producto{

    // 0: standard, active ,   1: obsoleto, deprecated
    private int status;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "output_producto_id")
    private List<Insumo> insumos;

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "proceso_prod_id")
    @JsonManagedReference("producto-proceso")
    private ProcesoProduccionCompleto procesoProduccionCompleto;

    @ManyToOne
    @JoinColumn(name = "categoria_id")
    private Categoria categoria;

    /**
     * Una imagen o ilustracion del producto
     */
    private String fotoUrl;

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true)
    private CasePack casePack;

    /**
     * Lo ideal: si el producto se llama Tratamiento Rizo Adulto,
     * el prefijo ideal seria TRA. Debe ser unico entre todos los terminados.
     */
    @Column(unique = true, nullable = true, length = 20)
    private String prefijoLote;

}
