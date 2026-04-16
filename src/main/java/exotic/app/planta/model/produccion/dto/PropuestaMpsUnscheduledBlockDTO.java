package exotic.app.planta.model.produccion.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class PropuestaMpsUnscheduledBlockDTO extends PropuestaMpsCalendarBlockDTO {
    private String reason;
}
