package exotic.app.planta.model.inventarios.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class DispensacionV2FinalizacionRequestDTO {
    private Integer areaId;
    private List<DispensacionV2FinalizacionOrdenRequestDTO> ordenes = new ArrayList<>();
}
