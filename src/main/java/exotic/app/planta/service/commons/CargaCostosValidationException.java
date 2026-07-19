package exotic.app.planta.service.commons;

import exotic.app.planta.model.commons.dto.CargaCostosDTOs;
import lombok.Getter;

import java.util.List;

@Getter
public class CargaCostosValidationException extends RuntimeException {
    private final List<CargaCostosDTOs.ErrorFila> errores;
    private final List<String> advertencias;

    public CargaCostosValidationException(
            String message,
            List<CargaCostosDTOs.ErrorFila> errores,
            List<String> advertencias
    ) {
        super(message);
        this.errores = List.copyOf(errores);
        this.advertencias = List.copyOf(advertencias);
    }
}
