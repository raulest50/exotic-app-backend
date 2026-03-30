package exotic.app.planta.model.producto.dto.manufacturing;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
public class NodoInsumoDTO extends ProcesoFabricacionNodoDTO {
    private Integer insumoId;
    private String inputProductoId;

    public NodoInsumoDTO() {
        setNodeType("INSUMO");
    }
}
