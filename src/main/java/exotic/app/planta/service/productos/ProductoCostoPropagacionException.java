package exotic.app.planta.service.productos;

import lombok.Getter;

@Getter
public class ProductoCostoPropagacionException extends RuntimeException {
    private final String productoId;

    public ProductoCostoPropagacionException(String productoId, String message) {
        super(message);
        this.productoId = productoId;
    }
}
