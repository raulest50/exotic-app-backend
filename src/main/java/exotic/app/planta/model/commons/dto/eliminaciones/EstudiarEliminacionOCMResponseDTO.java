package exotic.app.planta.model.commons.dto.eliminaciones;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EstudiarEliminacionOCMResponseDTO {
    private int ordenCompraId;
    private List<ItemOrdenCompraResumenDTO> itemsOrdenCompra;
    private List<LoteResumenDTO> lotes;
    private List<TransaccionAlmacenResumenDTO> transaccionesAlmacen;
    private List<AsientoContableResumenDTO> asientosContables;
}
