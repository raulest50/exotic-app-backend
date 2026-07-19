package exotic.app.planta.service.commons;

import lombok.Getter;

@Getter
public class CargaCostosTokenException extends RuntimeException {
    private final int intentosRestantes;
    private final boolean bloqueado;

    public CargaCostosTokenException(String message, int intentosRestantes, boolean bloqueado) {
        super(message);
        this.intentosRestantes = intentosRestantes;
        this.bloqueado = bloqueado;
    }
}
