package exotic.app.planta.model.commons.dto.eliminaciones;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TransaccionAlmacenResumenDTO {
    private int transaccionId;
    private LocalDateTime fechaTransaccion;
    private String estadoContable;
    private String observaciones;
    private List<MovimientoResumenDTO> movimientos;
    private AsientoContableResumenDTO asientoContable;
}
