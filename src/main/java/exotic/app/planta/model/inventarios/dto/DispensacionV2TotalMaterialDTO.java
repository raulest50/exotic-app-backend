package exotic.app.planta.model.inventarios.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DispensacionV2TotalMaterialDTO {
    private String productoId;
    private String productoNombre;
    private String tipoUnidades;
    private double cantidadRecetaTotal;
    private double cantidadADispensarTotal;
    private double cantidadHistoricaTotal;
    private double totalConHistorico;
    private boolean excedeReceta;
    private String warning;
}
