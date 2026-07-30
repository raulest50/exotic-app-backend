package exotic.app.planta.service.commons;

import exotic.app.planta.model.commons.dto.CargaPuntosReordenDTOs;
import lombok.Getter;

@Getter
public class CargaPuntosReordenValidationException extends RuntimeException {

    private final CargaPuntosReordenDTOs.ValidationResponse response;
    private final boolean conflict;

    public CargaPuntosReordenValidationException(
            String message,
            CargaPuntosReordenDTOs.ValidationResponse response,
            boolean conflict
    ) {
        super(message);
        this.response = response;
        this.conflict = conflict;
    }
}
