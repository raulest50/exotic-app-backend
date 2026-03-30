package exotic.app.planta.model.producto.dto.manufacturing;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductoManufacturingCasePackDTO {
    private Long id;
    private Integer unitsPerCase;
    private String ean14;
    private Double largoCm;
    private Double anchoCm;
    private Double altoCm;
    private Double grossWeightKg;
    private Boolean defaultForShipping;
    private List<ProductoManufacturingInsumoEmpaqueDTO> insumosEmpaque = new ArrayList<>();
}
