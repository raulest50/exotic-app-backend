package exotic.app.planta.model.produccion.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
public class PropuestaMpsSemanalCalendarDTO {
    private List<PropuestaMpsCalendarDayDTO> days = new ArrayList<>();
    private List<PropuestaMpsCalendarRowDTO> rows = new ArrayList<>();
    private List<PropuestaMpsUnscheduledBlockDTO> unscheduled = new ArrayList<>();
}
