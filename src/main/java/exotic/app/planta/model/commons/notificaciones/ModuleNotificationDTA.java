package exotic.app.planta.model.commons.notificaciones;

import exotic.app.planta.model.users.ModuloSistema;
import lombok.Data;

import java.util.List;

/**
 * Se genera para cada modulo. requireAtention = true indica
 * si
 *
 */
@Data
public class ModuleNotificationDTA {

    private ModuloSistema modulo;
    private boolean requireAtention;
    private String message;

    /** Cantidad de órdenes de compra en estado pendiente liberación (solo módulo COMPRAS). */
    private Long ordenesPendientesLiberar;
    /** Cantidad de órdenes de compra en estado pendiente envío al proveedor (solo módulo COMPRAS). */
    private Long ordenesPendientesEnviar;

    /** Detalle de OCM pendientes por liberar (estado 0), solo módulo COMPRAS. */
    private List<OrdenCompraAlertaCampanaDTO> detalleOrdenesPendientesLiberar;
    /** Detalle de OCM pendientes por enviar al proveedor (estado 1), solo módulo COMPRAS. */
    private List<OrdenCompraAlertaCampanaDTO> detalleOrdenesPendientesEnviar;

    /** Cantidad de materiales en o bajo punto de reorden (solo módulo STOCK). */
    private Long materialesEnPuntoReorden;

}
