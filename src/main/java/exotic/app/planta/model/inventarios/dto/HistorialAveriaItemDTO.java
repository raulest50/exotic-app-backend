package exotic.app.planta.model.inventarios.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class HistorialAveriaItemDTO {
    private String productoId;
    private String productoNombre;
    private String tipoUnidades;
    private double cantidadAveria;
}
