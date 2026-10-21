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
    @com.fasterxml.jackson.annotation.JsonFormat(shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
    private java.math.BigDecimal cantidadReceta;
    private String unidadReceta;

    public ProductoManufacturingInsumoDTO(Integer id, String productoId, String nombre,
            Double costo, String unidad, Double cantidad, Double subtotal) {
        this(id, productoId, nombre, costo, unidad, cantidad, subtotal, null, null);
    }
}
