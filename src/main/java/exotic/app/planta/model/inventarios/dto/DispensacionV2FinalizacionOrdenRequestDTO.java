package exotic.app.planta.model.inventarios.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class DispensacionV2FinalizacionOrdenRequestDTO {
    private Integer ordenProduccionId;
    private Long mpsLotePlanificadoId;
    private Long mpsItemId;
    private List<DispensacionV2FinalizacionMaterialRequestDTO> materiales = new ArrayList<>();
}
