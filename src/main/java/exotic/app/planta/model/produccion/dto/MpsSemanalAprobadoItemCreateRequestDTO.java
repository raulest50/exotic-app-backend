package exotic.app.planta.model.produccion.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
public class MpsSemanalAprobadoItemCreateRequestDTO {
    private LocalDate weekStartDate;
    private int dayIndex;
    private String terminadoId;
    private int numeroLotes;
    private String observacion;
}
