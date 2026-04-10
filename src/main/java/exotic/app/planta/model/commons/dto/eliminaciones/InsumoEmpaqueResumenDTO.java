package exotic.app.planta.model.commons.dto.eliminaciones;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class InsumoEmpaqueResumenDTO {
    private Long insumoEmpaqueId;
    private String terminadoId;
    private String terminadoNombre;
    private Integer unitsPerCase;
    private double cantidad;
    private String uom;
}
