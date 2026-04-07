package exotic.app.planta.model.commons.notificaciones;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Resumen mínimo de OCM para la campana de alertas del módulo COMPRAS (estados pendiente liberación / envío).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrdenCompraAlertaCampanaDTO {

    private int ordenCompraId;
    private LocalDateTime fechaEmision;
}
