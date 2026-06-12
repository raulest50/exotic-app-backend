package exotic.app.planta.service.empresa;

import exotic.app.planta.config.AppTime;
import exotic.app.planta.model.empresa.EmpresaLogoDocumentalVersion;
import exotic.app.planta.repo.empresa.EmpresaLogoDocumentalVersionRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EmpresaLogoDocumentalService {

    private static final long MAX_FILE_SIZE_BYTES = 1_048_576L;
    private static final int MIN_DIMENSION_PX = 100;
    private static final int MAX_DIMENSION_PX = 2000;
    private static final byte[] PNG_SIGNATURE = new byte[] {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    };

    private final EmpresaLogoDocumentalVersionRepo repo;

    @Transactional(readOnly = true)
    public EmpresaLogoDocumentalVersion getVigente() {
        return repo.findFirstByEstadoOrderByVersionDesc(EmpresaLogoDocumentalVersion.Estado.VIGENTE)
                .orElseThrow(() -> new IllegalStateException("No existe un logo documental vigente configurado."));
    }

    @Transactional(readOnly = true)
    public List<EmpresaLogoDocumentalVersion> getVersiones() {
        return repo.findAllByOrderByVersionDesc();
    }

    @Transactional(readOnly = true)
    public EmpresaLogoDocumentalVersion getVersion(Long id) {
        return repo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("No existe la version de logo documental con id: " + id));
    }

    @Transactional(readOnly = true)
    public EmpresaLogoDocumentalVersion resolveVersionForOcm(Long versionId) {
        if (versionId == null) {
            return getVigente();
        }
        return getVersion(versionId);
    }

    @Transactional
    public EmpresaLogoDocumentalVersion crearNuevaVersion(
            MultipartFile logo,
            String motivoCambio,
            String username
    ) {
        if (motivoCambio == null || motivoCambio.trim().isBlank()) {
            throw new IllegalArgumentException("Debe informar el motivo del cambio.");
        }
        LogoValidado logoValidado = validarLogo(logo);
        LocalDateTime now = AppTime.now();

        repo.findByEstadoForUpdate(EmpresaLogoDocumentalVersion.Estado.VIGENTE)
                .ifPresent(vigente -> {
                    vigente.setEstado(EmpresaLogoDocumentalVersion.Estado.RETIRADA);
                    vigente.setVigenteHasta(now);
                    repo.save(vigente);
                });

        EmpresaLogoDocumentalVersion nueva = new EmpresaLogoDocumentalVersion();
        nueva.setVersion(repo.findMaxVersion() + 1);
        nueva.setEstado(EmpresaLogoDocumentalVersion.Estado.VIGENTE);
        nueva.setNombreArchivoOriginal(resolveFileName(logo.getOriginalFilename()));
        nueva.setContentType("image/png");
        nueva.setTamanoBytes((long) logoValidado.bytes().length);
        nueva.setAnchoPx(logoValidado.width());
        nueva.setAltoPx(logoValidado.height());
        nueva.setSha256(sha256Hex(logoValidado.bytes()));
        nueva.setContenido(logoValidado.bytes());
        nueva.setVigenteDesde(now);
        nueva.setCreadoEn(now);
        nueva.setCreadoPor(trim(username));
        nueva.setMotivoCambio(trim(motivoCambio));

        return repo.save(nueva);
    }

    private LogoValidado validarLogo(MultipartFile logo) {
        if (logo == null || logo.isEmpty()) {
            throw new IllegalArgumentException("Debe adjuntar un logo PNG.");
        }
        if (!"image/png".equalsIgnoreCase(trim(logo.getContentType()))) {
            throw new IllegalArgumentException("El logo debe tener content type image/png.");
        }
        if (logo.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new IllegalArgumentException("El logo no puede superar 1 MB.");
        }

        byte[] bytes;
        try {
            bytes = logo.getBytes();
        } catch (IOException e) {
            throw new IllegalArgumentException("No se pudo leer el archivo de logo.", e);
        }

        if (!hasPngSignature(bytes)) {
            throw new IllegalArgumentException("El archivo no tiene una firma PNG valida.");
        }

        BufferedImage image;
        try {
            image = ImageIO.read(new ByteArrayInputStream(bytes));
        } catch (IOException e) {
            throw new IllegalArgumentException("No se pudo inspeccionar el PNG.", e);
        }
        if (image == null) {
            throw new IllegalArgumentException("El PNG no pudo ser decodificado.");
        }

        int width = image.getWidth();
        int height = image.getHeight();
        if (width < MIN_DIMENSION_PX || height < MIN_DIMENSION_PX) {
            throw new IllegalArgumentException("El logo debe medir al menos 100 x 100 px.");
        }
        if (width > MAX_DIMENSION_PX || height > MAX_DIMENSION_PX) {
            throw new IllegalArgumentException("El logo no puede superar 2000 x 2000 px.");
        }

        return new LogoValidado(bytes, width, height);
    }

    private static boolean hasPngSignature(byte[] bytes) {
        if (bytes == null || bytes.length < PNG_SIGNATURE.length) {
            return false;
        }
        for (int i = 0; i < PNG_SIGNATURE.length; i++) {
            if (bytes[i] != PNG_SIGNATURE[i]) {
                return false;
            }
        }
        return true;
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no esta disponible.", e);
        }
    }

    private static String resolveFileName(String originalFilename) {
        String fileName = trim(originalFilename);
        return fileName == null || fileName.isBlank() ? "logo_documental.png" : fileName;
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }

    private record LogoValidado(byte[] bytes, int width, int height) {
    }
}
