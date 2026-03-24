package exotic.app.planta.model.commons.notificaciones;

import exotic.app.planta.model.users.Acceso.Modulo;
import lombok.Data;

/**
 * Se genera para cada modulo. requireAtention = true indica
 * si
 *
 */
@Data
public class ModuleNotificationDTA {

    private Modulo modulo;
    private boolean requireAtention;
    private String message;

    /** Cantidad de órdenes de compra en estado pendiente liberación (solo módulo COMPRAS). */
    private Long ordenesPendientesLiberar;
    /** Cantidad de órdenes de compra en estado pendiente envío al proveedor (solo módulo COMPRAS). */
    private Long ordenesPendientesEnviar;

}
