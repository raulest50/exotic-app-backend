package exotic.app.planta.model.users.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TabAccesoAssignmentDTO {
    private String tabId;
    private int nivel;
}
