package exotic.app.planta.model.producto.dto.manufacturing;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductoManufacturingInsumoDTO {
    private Integer insumoId;
    private String productoId;
    private String productoNombre;
    private Double costoUnitario;
    private String tipoUnidades;
    private Double cantidadRequerida;
    private Double subtotal;
}
