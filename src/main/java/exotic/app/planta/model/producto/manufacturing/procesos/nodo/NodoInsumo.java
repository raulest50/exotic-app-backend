package exotic.app.planta.model.producto.manufacturing.procesos.nodo;

import exotic.app.planta.model.producto.manufacturing.receta.Insumo;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;

@Entity
@DiscriminatorValue("INSUMO")
@Getter
@Setter
public class NodoInsumo extends ProcesoFabricacionNodo {

    @ManyToOne(optional = false)
    @JoinColumn(name = "insumo_id", nullable = false)
    private Insumo insumo;
}
