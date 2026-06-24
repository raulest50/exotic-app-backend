package exotic.app.planta.model.produccion.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class MpsSemanalAprobadoItemEditRequestDTO {
    private int dayIndex;
    private int numeroLotes;
    private String observacion;
}
