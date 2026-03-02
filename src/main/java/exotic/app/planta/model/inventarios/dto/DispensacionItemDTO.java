package exotic.app.planta.model.inventarios.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DispensacionItemDTO {
    /**
     * ID del producto a dispensar.
     */
    private String productoId;

    /**
     * Cantidad del material a dispensar.
     * Debe ser un valor positivo.
     */
    private double cantidad;

    /**
     * ID del lote del cual se tomará el material.
     * Es opcional; si no se especifica, el sistema no registrará trazabilidad por lote.
     */
    private Integer loteId;
}
