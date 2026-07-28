package exotic.app.planta.model.inventarios.dto;

import exotic.app.planta.model.inventarios.CausaAjusteInventario;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * DTO para registrar ajustes de inventario.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AjusteInventarioDTO {

    /** Username del usuario que realiza el ajuste. */
    private String username;

    /** Observaciones generales del ajuste. */
    private String observaciones;

    /** Ruta o URL del documento de soporte del ajuste. */
    private String urlDocSoporte;

    /** Detalle de los ítems ajustados. */
    private List<AjusteItemDTO> items;

    /** Causa operativa común a todos los ítems de la transacción OAA. */
    private CausaAjusteInventario causaAjuste;
}
