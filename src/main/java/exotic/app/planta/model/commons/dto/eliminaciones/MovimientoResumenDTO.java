package exotic.app.planta.model.commons.dto.eliminaciones;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MovimientoResumenDTO {
    private int movimientoId;
    private double cantidad;
    private String productId;
    private String tipoMovimiento;
    private String almacen;
    private LocalDateTime fechaMovimiento;
}
