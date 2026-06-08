package exotic.app.planta.model.produccion.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ProgramacionProduccionSemanalItemRequestDTO {
    private String terminadoId;
    private int numeroLotes;
    private String observacion;
}
