package exotic.app.planta.model.inventarios.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DispensacionV2MaterialEditableRequestDTO {
    private String productoId;
    private Boolean checked;
    private Double cantidadADispensar;
}
