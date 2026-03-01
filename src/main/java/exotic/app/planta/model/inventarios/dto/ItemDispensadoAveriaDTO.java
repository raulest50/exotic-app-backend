package exotic.app.planta.model.inventarios.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ItemDispensadoAveriaDTO {
    private String productoId;
    private String productoNombre;
    private String tipoUnidades;
    private double cantidadDispensada;
    private double cantidadAveriadaPrevia;
    private double cantidadDisponibleAveria;
}
