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
public class DispensacionV2OrdenDTO {
    private int ordenProduccionId;
    private String loteAsignado;
    private String productoTerminadoId;
    private String productoTerminadoNombre;
    private double cantidadProducir;
    private Long mpsLotePlanificadoId;
    private Long mpsItemId;
    private DispensacionV2AreaDTO area;
    private List<DispensacionV2MaterialDTO> materiales = new ArrayList<>();
    private List<String> warnings = new ArrayList<>();
}
