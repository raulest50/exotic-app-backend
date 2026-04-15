package exotic.app.planta.model.produccion.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
public class PropuestaMpsSemanalRequestDTO {
    private LocalDate weekStartDate;
    private List<PropuestaMpsSemanalItemRequestDTO> items = new ArrayList<>();
}
