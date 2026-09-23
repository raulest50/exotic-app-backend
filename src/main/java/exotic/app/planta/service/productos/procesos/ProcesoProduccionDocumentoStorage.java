package exotic.app.planta.service.productos.procesos;

import org.springframework.core.io.Resource;

import java.io.IOException;

public interface ProcesoProduccionDocumentoStorage {

    StoredFile store(Integer procesoId, byte[] content, String extension) throws IOException;

    Resource load(String storageKey);

    void deleteIfExists(String storageKey);

    record StoredFile(String storageKey) {
    }

    /** Fallos documentales reconocidos; los demás errores deben propagarse. */
    class ArchivoNoDisponibleException extends IllegalStateException {
        public ArchivoNoDisponibleException() {
            super("El archivo documental no esta disponible en el almacenamiento.");
        }
    }

    class ClaveInvalidaException extends IllegalArgumentException {
        public ClaveInvalidaException(String message) {
            super(message);
        }
    }
}
