package exotic.app.planta.model.producto.dto.manufacturing;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProcesoProduccionCompletoDTO {
    private Integer procesoCompletoId;
    private Double rendimientoTeorico;
    private List<ProcesoFabricacionNodoDTO> nodes = new ArrayList<>();
    private List<ProcesoFabricacionEdgeDTO> edges = new ArrayList<>();
}
