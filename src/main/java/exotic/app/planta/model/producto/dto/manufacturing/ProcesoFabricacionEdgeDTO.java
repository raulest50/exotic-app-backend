package exotic.app.planta.model.producto.dto.manufacturing;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProcesoFabricacionEdgeDTO {
    private Long id;
    private String frontendId;
    private String sourceFrontendId;
    private String targetFrontendId;
}
