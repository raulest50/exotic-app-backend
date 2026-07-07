package exotic.app.planta.model.inventarios.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class DispensacionV2FinalizacionMaterialRequestDTO {
    private String productoId;
    private Boolean checked;
    private Double cantidadADispensar;
    private List<DispensacionV2FinalizacionLoteRequestDTO> lotesOrigen = new ArrayList<>();
}
