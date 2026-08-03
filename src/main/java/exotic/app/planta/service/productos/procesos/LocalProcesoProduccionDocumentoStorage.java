package exotic.app.planta.service.productos.procesos;

import exotic.app.planta.config.StorageProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Component
@Slf4j
public class LocalProcesoProduccionDocumentoStorage implements ProcesoProduccionDocumentoStorage {

    private final Path baseDirectory;

    public LocalProcesoProduccionDocumentoStorage(StorageProperties storageProperties) {
        this.baseDirectory = Paths.get(storageProperties.getUPLOAD_DIR()).toAbsolutePath().normalize();
    }

    @Override
    public StoredFile store(Integer procesoId, byte[] content, String extension) throws IOException {
        String fileName = UUID.randomUUID() + extension;
        Path relativePath = Paths.get(
                "procesos-produccion",
                String.valueOf(procesoId),
                "documentos",
                fileName
        );
        Path destination = resolveSecurely(relativePath.toString());
        Path directory = destination.getParent();
        Files.createDirectories(directory);

        Path temporary = Files.createTempFile(directory, ".upload-", ".tmp");
        try {
            Files.write(temporary, content);
            try {
                Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, destination);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }

        return new StoredFile(toStorageKey(relativePath));
    }

    @Override
    public Resource load(String storageKey) {
        Path path = resolveSecurely(storageKey);
        if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
            throw new IllegalStateException("El archivo documental no esta disponible en el almacenamiento.");
        }
        return new FileSystemResource(path);
    }

    @Override
    public void deleteIfExists(String storageKey) {
        try {
            Files.deleteIfExists(resolveSecurely(storageKey));
        } catch (IOException | RuntimeException e) {
            log.warn("No fue posible limpiar el archivo documental {}", storageKey, e);
        }
    }

    private Path resolveSecurely(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            throw new IllegalArgumentException("La clave de almacenamiento no puede estar vacia.");
        }
        Path resolved = baseDirectory.resolve(storageKey).normalize();
        if (!resolved.startsWith(baseDirectory)) {
            throw new IllegalArgumentException("La clave de almacenamiento no es valida.");
        }
        return resolved;
    }

    private static String toStorageKey(Path relativePath) {
        return relativePath.toString().replace(relativePath.getFileSystem().getSeparator(), "/");
    }
}
