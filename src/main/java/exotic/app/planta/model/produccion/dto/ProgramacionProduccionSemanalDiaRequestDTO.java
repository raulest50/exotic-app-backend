package exotic.app.planta.model.produccion.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
public class ProgramacionProduccionSemanalDiaRequestDTO {
    private LocalDate fecha;
    private int dayIndex;
    private List<ProgramacionProduccionSemanalItemRequestDTO> items = new ArrayList<>();
}
