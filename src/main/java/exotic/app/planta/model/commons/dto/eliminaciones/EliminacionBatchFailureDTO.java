package exotic.app.planta.model.commons.dto.eliminaciones;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EliminacionBatchFailureDTO {
    private String productoId;
    private String reason;
}
