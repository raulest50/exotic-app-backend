package exotic.app.planta.model.producto.dto.manufacturing;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
public class NodoTargetDTO extends ProcesoFabricacionNodoDTO {
    public NodoTargetDTO() {
        setNodeType("TARGET");
    }
}
