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
public class DispensacionV2MaterialesRecetaResponseDTO {
    private DispensacionV2AreaDTO area;
    private String productoId;
    private String productoNombre;
    private double cantidadBase;
    private List<DispensacionV2MaterialDTO> materiales = new ArrayList<>();
    private List<String> warnings = new ArrayList<>();
}
