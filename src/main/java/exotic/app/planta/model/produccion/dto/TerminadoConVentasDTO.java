package exotic.app.planta.model.produccion.dto;

import exotic.app.planta.model.producto.Terminado;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TerminadoConVentasDTO {
    private Terminado terminado;
    private double cantidadVendida;
    private double valorTotal;
}
