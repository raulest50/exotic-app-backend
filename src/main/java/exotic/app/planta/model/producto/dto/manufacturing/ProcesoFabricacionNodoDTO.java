package exotic.app.planta.model.producto.dto.manufacturing;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Data;

@Data
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "nodeType")
@JsonSubTypes({
        @JsonSubTypes.Type(value = NodoInsumoDTO.class, name = "INSUMO"),
        @JsonSubTypes.Type(value = NodoProcesoDTO.class, name = "PROCESO"),
        @JsonSubTypes.Type(value = NodoTargetDTO.class, name = "TARGET")
})
public abstract class ProcesoFabricacionNodoDTO {
    private Long id;
    private String nodeType;
    private String frontendId;
    private double posicionX;
    private double posicionY;
    private String label;
}
