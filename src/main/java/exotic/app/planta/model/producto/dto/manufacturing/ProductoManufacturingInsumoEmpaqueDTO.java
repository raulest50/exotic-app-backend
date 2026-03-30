package exotic.app.planta.model.producto.dto.manufacturing;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductoManufacturingInsumoEmpaqueDTO {
    private Long id;
    private String materialId;
    private String materialNombre;
    private Double cantidad;
    private String uom;
}
