package exotic.app.planta.model.producto;


import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import exotic.app.planta.model.producto.manufacturing.receta.Insumo;
import exotic.app.planta.model.producto.manufacturing.procesos.ProcesoProduccionCompleto;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Entity
@DiscriminatorValue("S")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SemiTerminado extends Producto{

    /**
     * Define si este semiterminado debe fabricarse mediante una orden propia y
     * recibir un lote intermedio. Cuando es {@code false}, se trata como una
     * etapa integrada (phantom) cuya receta se explota dentro de la orden que
     * lo consume.
     */
    @Column(name = "requiere_orden_fabricacion", nullable = false)
    private boolean requiereOrdenFabricacion = false;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "output_producto_id")
    private List<Insumo> insumos;

    @OneToOne(mappedBy = "producto", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference("producto-proceso")
    private ProcesoProduccionCompleto procesoProduccionCompleto;

}
