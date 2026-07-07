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
public class DispensacionV2PreparacionRequestDTO {
    private Integer areaId;
    private List<DispensacionV2OrdenRequestDTO> ordenes = new ArrayList<>();
}
