package exotic.app.planta.service.productos.fichatecnica;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import exotic.app.planta.config.StorageProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Almacenamiento local exclusivo para fichas tecnicas de materiales.
 */
@Service
@Slf4j
public class MaterialFichaTecnicaStorage {

    static final long MAX_PDF_SIZE_BYTES = 10L * 1024L * 1024L;
    private static final byte[] PDF_SIGNATURE = "%PDF-".getBytes(StandardCharsets.US_ASCII);
    private static final Pattern URI_SCHEME = Pattern.compile("^[a-zA-Z][a-zA-Z0-9+.-]*:.*");

    private final Path uploadRoot;
    private final Path technicalSheetRoot;
    private final String technicalSheetDirectory;

    @Autowired
    public MaterialFichaTecnicaStorage(StorageProperties storageProperties) {
        this(Path.of(storageProperties.getUPLOAD_DIR()), storageProperties.getDS_MATERIALES());
    }

    MaterialFichaTecnicaStorage(Path uploadRoot, String technicalSheetDirectory) {
        this.uploadRoot = uploadRoot.toAbsolutePath().normalize();
        this.technicalSheetDirectory = technicalSheetDirectory;
        this.technicalSheetRoot = this.uploadRoot.resolve(technicalSheetDirectory).normalize();
    }

    public String store(MultipartFile file) throws IOException {
        validatePdf(file);
        Files.createDirectories(technicalSheetRoot);

        String storedName = UUID.randomUUID() + ".pdf";
        Path destination = technicalSheetRoot.resolve(storedName).normalize();
        if (!destination.startsWith(technicalSheetRoot)) {
            throw new IOException("No fue posible construir una ruta segura para la ficha tecnica.");
        }

        try (InputStream input = file.getInputStream()) {
            Files.copy(input, destination);
        } catch (IOException exception) {
            Files.deleteIfExists(destination);
            throw exception;
        }
        return technicalSheetDirectory + "/" + storedName;
    }

    public Optional<StoredTechnicalSheet> load(String storedReference) {
        Optional<Path> trustedPath = resolveTrustedPath(storedReference);
        if (trustedPath.isEmpty()) {
            return Optional.empty();
        }

        Path path = trustedPath.get();
        try {
            return Optional.of(new StoredTechnicalSheet(new FileSystemResource(path), Files.size(path)));
        } catch (IOException exception) {
            log.warn("No fue posible leer una ficha tecnica declarada.", exception);
            return Optional.empty();
        }
    }

    public boolean isAvailable(String storedReference) {
        return resolveTrustedPath(storedReference).isPresent();
    }

    public void delete(String storedReference) {
        resolveTrustedPath(storedReference).ifPresent(path -> {
            try {
                Files.deleteIfExists(path);
            } catch (IOException exception) {
                log.warn("No fue posible eliminar una ficha tecnica creada por una transaccion fallida.", exception);
            }
        });
    }

    private void validatePdf(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("La ficha tecnica no puede estar vacia.");
        }
        if (file.getSize() > MAX_PDF_SIZE_BYTES) {
            throw new IllegalArgumentException("La ficha tecnica no puede superar 10 MB.");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            throw new IllegalArgumentException("La ficha tecnica debe tener extension PDF.");
        }

        byte[] signature = new byte[PDF_SIGNATURE.length];
        try (InputStream input = file.getInputStream()) {
            if (input.readNBytes(signature, 0, signature.length) != PDF_SIGNATURE.length) {
                throw new IllegalArgumentException("El archivo cargado no es un PDF valido.");
            }
        }
        for (int index = 0; index < PDF_SIGNATURE.length; index++) {
            if (signature[index] != PDF_SIGNATURE[index]) {
                throw new IllegalArgumentException("El archivo cargado no es un PDF valido.");
            }
        }

        try (InputStream input = file.getInputStream();
             PdfReader reader = new PdfReader(input);
             PdfDocument document = new PdfDocument(reader)) {
            if (document.getNumberOfPages() < 1) {
                throw new IllegalArgumentException("La ficha tecnica debe contener al menos una pagina.");
            }
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("El archivo cargado no es un PDF valido.", exception);
        }
    }

    private Optional<Path> resolveTrustedPath(String storedReference) {
        if (storedReference == null || storedReference.isBlank()) {
            return Optional.empty();
        }

        String trimmedReference = storedReference.trim();
        boolean windowsAbsolutePath = trimmedReference.length() >= 3
                && Character.isLetter(trimmedReference.charAt(0))
                && trimmedReference.charAt(1) == ':'
                && (trimmedReference.charAt(2) == '\\' || trimmedReference.charAt(2) == '/');
        if (URI_SCHEME.matcher(trimmedReference).matches() && !windowsAbsolutePath) {
            return Optional.empty();
        }

        try {
            Path rawPath = Path.of(trimmedReference);
            if (rawPath.isAbsolute()) {
                return validateExistingPath(rawPath);
            }

            Optional<Path> legacyRelativePath = validateExistingPath(
                    Path.of("").toAbsolutePath().resolve(rawPath)
            );
            if (legacyRelativePath.isPresent()) {
                return legacyRelativePath;
            }
            return validateExistingPath(uploadRoot.resolve(rawPath));
        } catch (InvalidPathException exception) {
            return Optional.empty();
        }
    }

    private Optional<Path> validateExistingPath(Path candidate) {
        Path normalizedCandidate = candidate.toAbsolutePath().normalize();
        if (!normalizedCandidate.startsWith(technicalSheetRoot)
                || !Files.isRegularFile(normalizedCandidate)
                || !Files.isReadable(normalizedCandidate)) {
            return Optional.empty();
        }

        try {
            Path realRoot = technicalSheetRoot.toRealPath();
            Path realCandidate = normalizedCandidate.toRealPath();
            if (!realCandidate.startsWith(realRoot)
                    || !Files.isRegularFile(realCandidate)
                    || !Files.isReadable(realCandidate)) {
                return Optional.empty();
            }
            return Optional.of(realCandidate);
        } catch (IOException exception) {
            return Optional.empty();
        }
    }

    public record StoredTechnicalSheet(Resource resource, long contentLength) {
    }
}
