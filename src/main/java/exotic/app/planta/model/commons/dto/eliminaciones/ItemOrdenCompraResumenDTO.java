package exotic.app.planta.model.commons.dto.eliminaciones;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ItemOrdenCompraResumenDTO {
    private int itemOrdenId;
    private String productId;
    private int cantidad;
    private int precioUnitario;
    private int subTotal;
}
