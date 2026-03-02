package exotic.app.planta.model.inventarios.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class InsumoDesglosadoDTO {
    private String productoId;
    private String productoNombre;
    private double cantidadTotalRequerida;
    private String tipoUnidades;
    private String tipoProducto; // "MATERIAL" o "SEMITERMINADO"
    private Boolean inventareable; // Indica si el producto es inventariable (true) o no (false, como el agua)
}

