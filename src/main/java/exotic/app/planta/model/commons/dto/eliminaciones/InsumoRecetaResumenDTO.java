package exotic.app.planta.model.commons.dto.eliminaciones;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class InsumoRecetaResumenDTO {
    private int insumoId;
    private String productoDestinoId;
    private String productoDestinoNombre;
    private String tipoProductoDestino;
    private double cantidadRequerida;
}
