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
public class DispensacionV2FinalizacionResponseDTO {
    private DispensacionV2AreaDTO area;
    private List<DispensacionV2FinalizacionOrdenResponseDTO> ordenes = new ArrayList<>();
    private List<String> warnings = new ArrayList<>();
}
