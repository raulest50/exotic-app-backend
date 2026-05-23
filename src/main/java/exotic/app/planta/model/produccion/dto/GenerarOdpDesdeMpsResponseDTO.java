package exotic.app.planta.model.produccion.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
public class GenerarOdpDesdeMpsResponseDTO {
    private Integer mpsId;
    private LocalDate weekStartDate;
    private int totalBloquesProgramados;
    private int totalLotesProgramados;
    private int totalOrdenesCreadas;
    private List<Integer> ordenesIds = new ArrayList<>();
}
