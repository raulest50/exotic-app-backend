package exotic.app.planta.model.produccion.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FilaInfVentasDTO {
    private String codigo;
    private double cantidadVendida;
    private double valorTotal;
}
