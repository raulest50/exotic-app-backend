package exotic.app.planta.model.inventarios.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * DTO para la dispensación de materiales asociada a una orden de producción.
 * Representa una transacción de consumo de materiales para ejecutar una orden específica.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DispensacionDTO {
    /**
     * ID de la orden de producción asociada a esta dispensación.
     * Debe existir en el sistema antes de crear la dispensación.
     */
    private int ordenProduccionId;

    /**
     * ID del área operativa destino a la que se entrega la dispensación.
     * Se usa para registrar trazabilidad cuando los materiales salen de Almacén General.
     */
    private Integer areaOperativaDestinoId;

    /**
     * Notas adicionales sobre esta dispensación.
     */
    private String observaciones;

    /**
     * ID del usuario que realiza la operación.
     * Se mantiene para compatibilidad. Si se proporciona usuarioRealizadorIds, 
     * se usa el primer ID de esa lista.
     */
    private int usuarioId;

    /**
     * Lista de IDs de usuarios responsables de realizar la dispensación.
     * Puede ser null o vacía. Si se proporciona, debe tener al menos un usuario.
     */
    private List<Integer> usuarioRealizadorIds;

    /**
     * ID del usuario que aprueba la dispensación.
     * Puede ser null si no hay aprobador asignado.
     */
    private Integer usuarioAprobadorId;

    /**
     * Lista de ítems a dispensar, cada uno asociado a un seguimiento de la orden.
     */
    private List<DispensacionItemDTO> items;
}
