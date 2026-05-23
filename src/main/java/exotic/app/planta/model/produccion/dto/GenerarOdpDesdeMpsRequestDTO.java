package exotic.app.planta.model.produccion.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
public class GenerarOdpDesdeMpsRequestDTO {
    private LocalDate weekStartDate;
}
