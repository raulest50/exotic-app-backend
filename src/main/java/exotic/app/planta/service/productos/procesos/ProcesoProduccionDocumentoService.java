package exotic.app.planta.service.productos.procesos;

import com.itextpdf.text.pdf.PdfReader;
import exotic.app.planta.config.AppTime;
import exotic.app.planta.model.producto.manufacturing.procesos.ProcesoProduccion;
import exotic.app.planta.model.producto.manufacturing.procesos.ProcesoProduccionDocumentoVersion;
import exotic.app.planta.model.producto.manufacturing.procesos.ProcesoProduccionDocumentoVersionResponse;
import exotic.app.planta.repo.producto.procesos.ProcesoProduccionDocumentoVersionRepo;
import exotic.app.planta.repo.producto.procesos.ProcesoProduccionRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProcesoProduccionDocumentoService {

    static final long MAX_FILE_SIZE_BYTES = 2_097_152L;
    static final String PDF_CONTENT_TYPE = "application/pdf";
    static final String DOCX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final int MAX_CONTENT_TYPES_XML_BYTES = 262_144;

    private final ProcesoProduccionRepo procesoRepo;
    private final ProcesoProduccionDocumentoVersionRepo documentoRepo;
    private final ProcesoProduccionDocumentoStorage storage;

    @Transactional(readOnly = true)
    public List<ProcesoProduccionDocumentoVersionResponse> getVersiones(Integer procesoId) {
        requireProceso(procesoId);
        return documentoRepo.findAllByProcesoProcesoIdOrderByVersionDesc(procesoId)
                .stream()
                .map(ProcesoProduccionDocumentoVersionResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public DescargaDocumento getDescarga(Integer procesoId, Long versionId) {
        ProcesoProduccionDocumentoVersion documento = documentoRepo
                .findByIdAndProcesoProcesoId(versionId, procesoId)
                .orElseThrow(() -> new NoSuchElementException(
                        "No existe la version documental solicitada para este proceso."
                ));

        if (documento.getStorageProvider()
                != ProcesoProduccionDocumentoVersion.StorageProvider.LOCAL_DISK) {
            throw new IllegalStateException("El proveedor de almacenamiento no esta soportado.");
        }

        return new DescargaDocumento(
                storage.load(documento.getStorageKey()),
                documento.getNombreArchivoOriginal(),
                documento.getContentType(),
                documento.getTamanoBytes(),
                documento.getSha256()
        );
    }

    @Transactional
    public ProcesoProduccionDocumentoVersionResponse crearNuevaVersion(
            Integer procesoId,
            MultipartFile archivo,
            String motivoCambio,
            String username
    ) {
        ArchivoValidado validado = validarArchivo(archivo);
        ProcesoProduccion proceso = procesoRepo.findByIdForUpdate(procesoId)
                .orElseThrow(() -> new NoSuchElementException(
                        "No existe el proceso de produccion con ID: " + procesoId
                ));

        ProcesoProduccionDocumentoVersion vigente = documentoRepo
                .findVigenteForUpdate(procesoId, ProcesoProduccionDocumentoVersion.Estado.VIGENTE)
                .orElse(null);

        String motivo = trim(motivoCambio);
        if (vigente != null && (motivo == null || motivo.isBlank())) {
            throw new IllegalArgumentException("Debe informar el motivo de la nueva version.");
        }
        if (motivo == null || motivo.isBlank()) {
            motivo = "Carga inicial";
        }

        ProcesoProduccionDocumentoStorage.StoredFile storedFile;
        try {
            storedFile = storage.store(procesoId, validado.bytes(), validado.extension());
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo almacenar el documento del proceso.", e);
        }
        registerRollbackCleanup(storedFile.storageKey());

        LocalDateTime now = AppTime.now();
        if (vigente != null) {
            vigente.setEstado(ProcesoProduccionDocumentoVersion.Estado.RETIRADA);
            vigente.setVigenteHasta(now);
            documentoRepo.save(vigente);
        }

        ProcesoProduccionDocumentoVersion nueva = new ProcesoProduccionDocumentoVersion();
        nueva.setProceso(proceso);
        nueva.setVersion(documentoRepo.findMaxVersionByProcesoId(procesoId) + 1);
        nueva.setEstado(ProcesoProduccionDocumentoVersion.Estado.VIGENTE);
        nueva.setNombreArchivoOriginal(validado.originalFileName());
        nueva.setContentType(validado.contentType());
        nueva.setTamanoBytes((long) validado.bytes().length);
        nueva.setSha256(sha256Hex(validado.bytes()));
        nueva.setStorageProvider(ProcesoProduccionDocumentoVersion.StorageProvider.LOCAL_DISK);
        nueva.setStorageKey(storedFile.storageKey());
        nueva.setVigenteDesde(now);
        nueva.setCreadoEn(now);
        nueva.setCreadoPor(trim(username));
        nueva.setMotivoCambio(motivo);

        return ProcesoProduccionDocumentoVersionResponse.from(documentoRepo.save(nueva));
    }

    @Transactional(readOnly = true)
    public long countVersiones(Integer procesoId) {
        return documentoRepo.countByProcesoProcesoId(procesoId);
    }

    private ProcesoProduccion requireProceso(Integer procesoId) {
        if (procesoId == null) {
            throw new IllegalArgumentException("El ID del proceso no puede ser nulo.");
        }
        return procesoRepo.findById(procesoId)
                .orElseThrow(() -> new NoSuchElementException(
                        "No existe el proceso de produccion con ID: " + procesoId
                ));
    }

    private ArchivoValidado validarArchivo(MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) {
            throw new IllegalArgumentException("Debe adjuntar un documento PDF o DOCX.");
        }
        if (archivo.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new IllegalArgumentException("El documento no puede superar 2 MB.");
        }

        byte[] bytes;
        try {
            bytes = archivo.getBytes();
        } catch (IOException e) {
            throw new IllegalArgumentException("No se pudo leer el documento adjunto.", e);
        }
        if (bytes.length == 0) {
            throw new IllegalArgumentException("El documento no puede estar vacio.");
        }
        if (bytes.length > MAX_FILE_SIZE_BYTES) {
            throw new IllegalArgumentException("El documento no puede superar 2 MB.");
        }

        String originalFileName = sanitizeFileName(archivo.getOriginalFilename());
        String extension = extensionOf(originalFileName);
        return switch (extension) {
            case ".pdf" -> {
                validarPdf(bytes);
                yield new ArchivoValidado(bytes, originalFileName, extension, PDF_CONTENT_TYPE);
            }
            case ".docx" -> {
                validarDocx(bytes);
                yield new ArchivoValidado(bytes, originalFileName, extension, DOCX_CONTENT_TYPE);
            }
            default -> throw new IllegalArgumentException("Solo se permiten documentos PDF o DOCX.");
        };
    }

    private static void validarPdf(byte[] bytes) {
        if (bytes.length < 5
                || bytes[0] != '%'
                || bytes[1] != 'P'
                || bytes[2] != 'D'
                || bytes[3] != 'F'
                || bytes[4] != '-') {
            throw new IllegalArgumentException("El archivo no tiene una firma PDF valida.");
        }

        try {
            PdfReader reader = new PdfReader(bytes);
            try {
                if (reader.getNumberOfPages() < 1 || reader.isEncrypted()) {
                    throw new IllegalArgumentException("El PDF debe ser legible y no estar cifrado.");
                }
            } finally {
                reader.close();
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("El contenido del PDF no es valido.", e);
        }
    }

    private static void validarDocx(byte[] bytes) {
        boolean hasContentTypes = false;
        boolean hasWordDocument = false;
        boolean hasMacro = false;
        String contentTypesXml = null;

        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                if ("[Content_Types].xml".equals(name)) {
                    hasContentTypes = true;
                    byte[] contentTypesBytes = zip.readNBytes(MAX_CONTENT_TYPES_XML_BYTES + 1);
                    if (contentTypesBytes.length > MAX_CONTENT_TYPES_XML_BYTES) {
                        throw new IllegalArgumentException("La estructura interna del DOCX es demasiado grande.");
                    }
                    contentTypesXml = new String(contentTypesBytes, StandardCharsets.UTF_8);
                } else if ("word/document.xml".equals(name)) {
                    hasWordDocument = true;
                } else if ("word/vbaProject.bin".equalsIgnoreCase(name)) {
                    hasMacro = true;
                }
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("El contenido del DOCX no es valido.", e);
        }

        if (!hasContentTypes || !hasWordDocument) {
            throw new IllegalArgumentException("El archivo no contiene una estructura DOCX valida.");
        }
        if (hasMacro || (contentTypesXml != null
                && contentTypesXml.toLowerCase(Locale.ROOT).contains("macroenabled"))) {
            throw new IllegalArgumentException("No se permiten documentos Word con macros.");
        }

        try (XWPFDocument ignored = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            // Abrir el paquete con POI verifica que sea un documento Word OOXML legible.
        } catch (Exception e) {
            throw new IllegalArgumentException("El contenido del DOCX no es valido.", e);
        }
    }

    private void registerRollbackCleanup(String storageKey) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    storage.deleteIfExists(storageKey);
                }
            }
        });
    }

    private static String sanitizeFileName(String originalFilename) {
        String candidate = trim(originalFilename);
        if (candidate == null || candidate.isBlank()) {
            throw new IllegalArgumentException("El documento debe tener un nombre de archivo.");
        }

        candidate = candidate.replace('\\', '/');
        int lastSlash = candidate.lastIndexOf('/');
        if (lastSlash >= 0) {
            candidate = candidate.substring(lastSlash + 1);
        }
        candidate = candidate.replaceAll("[\\p{Cntrl}]", "_").trim();
        if (candidate.isBlank()) {
            throw new IllegalArgumentException("El documento debe tener un nombre de archivo valido.");
        }

        String extension = extensionOf(candidate);
        if (candidate.length() > 255) {
            int baseMaxLength = 255 - extension.length();
            candidate = candidate.substring(0, Math.max(1, baseMaxLength)) + extension;
        }
        return candidate;
    }

    private static String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dot).toLowerCase(Locale.ROOT);
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no esta disponible.", e);
        }
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }

    private record ArchivoValidado(
            byte[] bytes,
            String originalFileName,
            String extension,
            String contentType
    ) {
    }

    public record DescargaDocumento(
            Resource resource,
            String fileName,
            String contentType,
            Long contentLength,
            String sha256
    ) {
    }
}
