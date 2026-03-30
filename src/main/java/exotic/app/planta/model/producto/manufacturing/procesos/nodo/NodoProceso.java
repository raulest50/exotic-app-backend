package exotic.app.planta.model.producto.manufacturing.procesos.nodo;

import exotic.app.planta.model.organizacion.AreaOperativa;
import exotic.app.planta.model.producto.manufacturing.procesos.ProcesoProduccion;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;

@Entity
@DiscriminatorValue("PROCESO")
@Getter
@Setter
public class NodoProceso extends ProcesoFabricacionNodo {

    @ManyToOne(optional = false)
    @JoinColumn(name = "proceso_id", nullable = false)
    private ProcesoProduccion procesoProduccion;

    @ManyToOne(optional = false)
    @JoinColumn(name = "area_operativa_id", nullable = false)
    private AreaOperativa areaOperativa;
}
