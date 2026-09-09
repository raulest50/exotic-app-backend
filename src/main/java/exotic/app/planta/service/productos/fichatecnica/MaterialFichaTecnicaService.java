package exotic.app.planta.service.productos.fichatecnica;

import exotic.app.planta.config.AppTime;
import exotic.app.planta.model.producto.Material;
import exotic.app.planta.model.producto.fichatecnica.MaterialFichaTecnicaVersion;
import exotic.app.planta.model.producto.fichatecnica.MaterialFichaTecnicaVersionResponse;
import exotic.app.planta.repo.producto.MaterialRepo;
import exotic.app.planta.repo.producto.fichatecnica.MaterialFichaTecnicaVersionRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
@Slf4j
public class MaterialFichaTecnicaService {

    private static final String PDF_CONTENT_TYPE = "application/pdf";

    private final MaterialRepo materialRepo;
    private final MaterialFichaTecnicaVersionRepo versionRepo;
    private final MaterialFichaTecnicaStorage storage;

    @Transactional(readOnly = true)
    public FichaTecnicaMetadata getMetadata(String productoId) {
        requireMaterial(productoId);
        MaterialFichaTecnicaVersion vigente = findVigente(productoId);
        long totalVersiones = versionRepo.countByMaterialProductoId(productoId);
        if (vigente == null) {
            return new FichaTecnicaMetadata(false, null, totalVersiones);
        }
        MaterialFichaTecnicaVersionResponse response = toResponse(vigente);
        return new FichaTecnicaMetadata(response.disponible(), response, totalVersiones);
    }

    @Transactional(readOnly = true)
    public List<MaterialFichaTecnicaVersionResponse> getVersiones(String productoId) {
        requireMaterial(productoId);
        return versionRepo.findAllByMaterialProductoIdOrderByVersionDesc(productoId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public TechnicalSheetDownload load(String productoId) {
        requireMaterial(productoId);
        MaterialFichaTecnicaVersion vigente = findVigente(productoId);
        if (vigente == null) {
            throw new NoSuchElementException("El material no tiene una ficha tecnica registrada.");
        }
        return loadVersionResource(vigente);
    }

    @Transactional(readOnly = true)
    public TechnicalSheetDownload loadVersion(String productoId, Long versionId) {
        requireMaterial(productoId);
        MaterialFichaTecnicaVersion version = versionRepo
                .findByIdAndMaterialProductoId(versionId, productoId)
                .orElseThrow(() -> new NoSuchElementException(
                        "No existe la version de ficha tecnica solicitada para este material."
                ));
        return loadVersionResource(version);
    }

    @Transactional
    public MaterialFichaTecnicaVersionResponse crearNuevaVersion(
            String productoId,
            MultipartFile archivo,
            String motivoCambio,
            String username
    ) {
        byte[] bytes = readBytesForHash(archivo);
        String newSha256 = sha256Hex(bytes);
        Material material = materialRepo.findByIdForUpdate(productoId)
                .orElseThrow(() -> new NoSuchElementException("Material no encontrado: " + productoId));
        MaterialFichaTecnicaVersion vigente = findVigente(productoId);

        if (vigente != null && newSha256.equals(resolveSha256(vigente))) {
            throw new MaterialFichaTecnicaDuplicadaException(
                    "El PDF seleccionado es identico a la ficha tecnica vigente."
            );
        }

        String motivo = trimToNull(motivoCambio);
        if (vigente != null && motivo == null) {
            throw new IllegalArgumentException("Debe informar el motivo de la nueva version.");
        }
        if (motivo == null) {
            motivo = "Carga inicial";
        }

        String storageKey;
        try {
            storageKey = storage.store(archivo);
        } catch (IOException exception) {
            throw new IllegalStateException("No se pudo almacenar la ficha tecnica.", exception);
        }
        registerRollbackCleanup(storageKey);

        LocalDateTime now = AppTime.now();
        if (vigente != null) {
            vigente.setEstado(MaterialFichaTecnicaVersion.Estado.RETIRADA);
            vigente.setVigenteHasta(now);
            versionRepo.save(vigente);
        }

        MaterialFichaTecnicaVersion nueva = new MaterialFichaTecnicaVersion();
        nueva.setMaterial(material);
        nueva.setVersion(versionRepo.findMaxVersionByMaterialId(productoId) + 1);
        nueva.setEstado(MaterialFichaTecnicaVersion.Estado.VIGENTE);
        nueva.setNombreArchivoOriginal(sanitizeFileName(archivo.getOriginalFilename(), productoId));
        nueva.setContentType(PDF_CONTENT_TYPE);
        nueva.setTamanoBytes((long) bytes.length);
        nueva.setSha256(newSha256);
        nueva.setStorageKey(storageKey);
        nueva.setVigenteDesde(now);
        nueva.setCreadoEn(now);
        nueva.setCreadoPor(trimToNull(username));
        nueva.setMotivoCambio(motivo);

        return toResponse(versionRepo.save(nueva));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void scheduleStorageCleanupAfterMaterialDeletion(String productoId) {
        List<String> storageKeys = versionRepo.findStorageKeysByMaterialId(productoId);
        if (storageKeys.isEmpty()) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            storageKeys.forEach(storage::delete);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                storageKeys.forEach(storage::delete);
            }
        });
    }

    private MaterialFichaTecnicaVersionResponse toResponse(MaterialFichaTecnicaVersion version) {
        return storage.load(version.getStorageKey())
                .map(stored -> MaterialFichaTecnicaVersionResponse.from(
                        version,
                        true,
                        version.getTamanoBytes() != null
                                ? version.getTamanoBytes()
                                : stored.contentLength()
                ))
                .orElseGet(() -> MaterialFichaTecnicaVersionResponse.from(
                        version,
                        false,
                        version.getTamanoBytes()
                ));
    }

    private TechnicalSheetDownload loadVersionResource(MaterialFichaTecnicaVersion version) {
        MaterialFichaTecnicaStorage.StoredTechnicalSheet stored = storage
                .load(version.getStorageKey())
                .orElseThrow(() -> new NoSuchElementException(
                        "La ficha tecnica solicitada no esta disponible."
                ));
        return new TechnicalSheetDownload(
                stored.resource(),
                stored.contentLength(),
                version.getVersion()
        );
    }

    private String resolveSha256(MaterialFichaTecnicaVersion version) {
        if (version.getSha256() != null && !version.getSha256().isBlank()) {
            return version.getSha256();
        }
        return storage.load(version.getStorageKey())
                .map(stored -> sha256Hex(stored.resource()))
                .orElse(null);
    }

    private static byte[] readBytesForHash(MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) {
            throw new IllegalArgumentException("La ficha tecnica no puede estar vacia.");
        }
        if (archivo.getSize() > MaterialFichaTecnicaStorage.MAX_PDF_SIZE_BYTES) {
            throw new IllegalArgumentException("La ficha tecnica no puede superar 10 MB.");
        }
        try {
            byte[] bytes = archivo.getBytes();
            if (bytes.length == 0) {
                throw new IllegalArgumentException("La ficha tecnica no puede estar vacia.");
            }
            return bytes;
        } catch (IOException exception) {
            throw new IllegalArgumentException("No se pudo leer la ficha tecnica adjunta.", exception);
        }
    }

    private Material requireMaterial(String productoId) {
        return materialRepo.findById(productoId)
                .orElseThrow(() -> new NoSuchElementException("Material no encontrado: " + productoId));
    }

    private MaterialFichaTecnicaVersion findVigente(String productoId) {
        return versionRepo.findByMaterialProductoIdAndEstado(
                productoId,
                MaterialFichaTecnicaVersion.Estado.VIGENTE
        ).orElse(null);
    }

    private void registerRollbackCleanup(String storageKey) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    storage.delete(storageKey);
                }
            }
        });
    }

    private static String sha256Hex(byte[] bytes) {
        MessageDigest digest = sha256Digest();
        return HexFormat.of().formatHex(digest.digest(bytes));
    }

    private static String sha256Hex(Resource resource) {
        MessageDigest digest = sha256Digest();
        try (InputStream input = resource.getInputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException exception) {
            log.warn("No fue posible calcular el hash de una ficha tecnica heredada.", exception);
            return null;
        }
    }

    private static MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 no esta disponible.", exception);
        }
    }

    private static String sanitizeFileName(String originalFilename, String productoId) {
        String fallback = "ficha-tecnica-" + productoId + ".pdf";
        if (originalFilename == null || originalFilename.isBlank()) {
            return fallback;
        }
        String normalized = originalFilename.replace('\\', '/');
        int separator = normalized.lastIndexOf('/');
        String filename = separator >= 0 ? normalized.substring(separator + 1) : normalized;
        filename = filename.replaceAll("[\\r\\n\\u0000]", "").trim();
        if (filename.isBlank()) {
            return fallback;
        }
        return filename.length() <= 255 ? filename : filename.substring(filename.length() - 255);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public record FichaTecnicaMetadata(
            boolean disponible,
            MaterialFichaTecnicaVersionResponse versionVigente,
            long totalVersiones
    ) {
    }

    public record TechnicalSheetDownload(Resource resource, long contentLength, int version) {
    }
}
