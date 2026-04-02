package exotic.app.planta.model.producto.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductoCategoriaEditabilityDTO {
    private boolean editable;
    private long blockingOrdersCount;
    private String reason;
}
