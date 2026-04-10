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
public class EstudiarEliminacionMaterialResponseDTO {
    private MaterialEliminacionResumenDTO material;
    private boolean eliminable;
    private List<ItemOrdenCompraMaterialResumenDTO> itemsOrdenCompra;
    private List<LoteResumenDTO> lotes;
    private List<TransaccionAlmacenResumenDTO> transaccionesAlmacen;
    private List<AsientoContableResumenDTO> asientosContables;
    private List<InsumoRecetaResumenDTO> insumosReceta;
    private List<InsumoEmpaqueResumenDTO> insumosEmpaque;
}
