package exotic.app.planta.model.commons.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ValidationResultDTO {
    private boolean valid;
    private List<ErrorRecord> errors;
    private int rowCount;
}
