package exotic.app.planta.model.produccion.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
public class GuardarMpsSemanalDraftRequestDTO {
    private LocalDate weekStartDate;
    private PropuestaMpsSemanalSummaryDTO summary = new PropuestaMpsSemanalSummaryDTO();
    private List<PropuestaMpsSemanalItemDTO> items = new ArrayList<>();
    private PropuestaMpsSemanalCalendarDTO calendar = new PropuestaMpsSemanalCalendarDTO();
}
