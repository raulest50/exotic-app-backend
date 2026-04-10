package exotic.app.planta.model.commons.dto.eliminaciones;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ItemOrdenCompraMaterialResumenDTO {
    private int itemOrdenId;
    private int ordenCompraId;
    private String proveedorNombre;
    private Integer estadoOrdenCompra;
    private int cantidad;
    private int precioUnitario;
    private int subTotal;
}
