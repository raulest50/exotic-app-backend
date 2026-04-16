package exotic.app.planta.model.produccion.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class PropuestaMpsCalendarBlockDTO {
    private String blockId;
    private String productoId;
    private String productoNombre;
    private Integer categoriaId;
    private String categoriaNombre;
    private int loteSize;
    private int lotesAsignados;
    private double cantidadAsignada;
    private String warning;
}
