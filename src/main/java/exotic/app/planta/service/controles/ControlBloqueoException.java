package exotic.app.planta.service.controles;

import exotic.app.planta.model.controles.dto.BloqueoControlDTO;
import lombok.Getter;

import java.util.List;

@Getter
public class ControlBloqueoException extends IllegalStateException {
    private final List<BloqueoControlDTO> bloqueos;

    public ControlBloqueoException(String message, List<BloqueoControlDTO> bloqueos) {
        super(message);
        this.bloqueos = bloqueos == null ? List.of() : List.copyOf(bloqueos);
    }
}
