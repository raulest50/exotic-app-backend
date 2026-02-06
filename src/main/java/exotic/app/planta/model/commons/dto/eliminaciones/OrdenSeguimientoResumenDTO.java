package exotic.app.planta.model.commons.dto.eliminaciones;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OrdenSeguimientoResumenDTO {
    private int seguimientoId;
    private int estado;
    private String productoId; // insumo (material) product id
}
