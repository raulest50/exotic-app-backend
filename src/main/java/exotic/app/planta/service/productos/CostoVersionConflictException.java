package exotic.app.planta.service.productos;

public class CostoVersionConflictException extends RuntimeException {
    public CostoVersionConflictException(String productoId) {
        super("El costo de " + productoId + " cambio durante la operacion");
    }
}
