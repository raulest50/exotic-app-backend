package exotic.app.planta.model.inventarios.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DispensacionV2AsignacionOrdenRequestDTO {
    private Integer ordenProduccionId;
    private Long mpsLotePlanificadoId;
    private Long mpsItemId;
    private List<DispensacionV2MaterialEditableRequestDTO> materiales = new ArrayList<>();
}
