package exotic.app.planta.model.produccion.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class PropuestaMpsSemanalItemRequestDTO {
    private String productoId;
    private double necesidadManual;
    private double porcentajeParticipacion;
    private double cantidadVendida;
    private double valorTotal;
}
