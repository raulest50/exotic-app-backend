package exotic.app.planta.service.commons;

import lombok.Getter;

@Getter
public class CargaCostosStateException extends RuntimeException {
    private final String codigo;

    public CargaCostosStateException(String codigo, String message) {
        super(message);
        this.codigo = codigo;
    }
}
