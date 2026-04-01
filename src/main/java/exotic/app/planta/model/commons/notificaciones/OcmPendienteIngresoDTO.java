package exotic.app.planta.model.commons.notificaciones;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OcmPendienteIngresoDTO {

    private int ordenCompraId;
    private LocalDateTime fechaEmision;
}
