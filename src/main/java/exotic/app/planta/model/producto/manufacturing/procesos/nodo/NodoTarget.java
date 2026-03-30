package exotic.app.planta.model.producto.manufacturing.procesos.nodo;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

@Entity
@DiscriminatorValue("TARGET")
public class NodoTarget extends ProcesoFabricacionNodo {
}
