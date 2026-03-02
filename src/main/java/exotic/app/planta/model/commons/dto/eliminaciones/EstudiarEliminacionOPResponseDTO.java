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
public class EstudiarEliminacionOPResponseDTO {
    private int ordenProduccionId;
    /** Solo se puede ejecutar eliminación si no hay transacciones asociadas. */
    private boolean eliminable;
    private List<LoteResumenDTO> lotes;
    private List<TransaccionAlmacenResumenDTO> transaccionesAlmacen;
    private List<AsientoContableResumenDTO> asientosContables;
}
