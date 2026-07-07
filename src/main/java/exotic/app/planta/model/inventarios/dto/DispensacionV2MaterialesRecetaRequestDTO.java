package exotic.app.planta.model.inventarios.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class DispensacionV2MaterialesRecetaRequestDTO {
    private Integer areaId;
    private String productoId;
    private Double cantidadBase;
}
