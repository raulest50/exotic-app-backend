package exotic.app.planta.model.bi.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProveedorMaterialRecepcionRowDTO {

    private Integer ordenCompraId;
    private String proveedorId;
    private String proveedorNombre;
    private String materialId;
    private String materialNombre;
    private LocalDateTime fechaEmision;
    private LocalDateTime fechaMovimiento;
    private Double cantidadRecibida;

}
