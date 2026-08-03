package exotic.app.planta.service.productos.procesos;

import org.springframework.core.io.Resource;

import java.io.IOException;

public interface ProcesoProduccionDocumentoStorage {

    StoredFile store(Integer procesoId, byte[] content, String extension) throws IOException;

    Resource load(String storageKey);

    void deleteIfExists(String storageKey);

    record StoredFile(String storageKey) {
    }
}
