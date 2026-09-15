package exotic.app.planta.service.users;

import exotic.app.planta.config.AppTime;
import exotic.app.planta.model.users.User;
import exotic.app.planta.model.users.firma.FirmaVisualUsuarioVersion;
import exotic.app.planta.model.users.firma.dto.FirmaVisualUsuarioActualResponse;
import exotic.app.planta.model.users.firma.dto.FirmaVisualUsuarioMetadata;
import exotic.app.planta.model.users.firma.dto.FirmaVisualUsuarioVersionResponse;
import exotic.app.planta.repo.usuarios.FirmaVisualUsuarioVersionRepo;
import exotic.app.planta.repo.usuarios.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class FirmaVisualUsuarioService {

    static final long MAX_FILE_SIZE_BYTES = 1_048_576L;
    static final int MIN_WIDTH_PX = 50;
    static final int MIN_HEIGHT_PX = 20;
    static final int MAX_WIDTH_PX = 2000;
    static final int MAX_HEIGHT_PX = 1000;

    private final FirmaVisualUsuarioVersionRepo firmaRepo;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public FirmaVisualUsuarioActualResponse getActual(Long usuarioId) {
        requireUsuario(usuarioId);
        return firmaRepo.findMetadataByTitularIdAndEstadoOrderByVersionDesc(
                        usuarioId,
                        FirmaVisualUsuarioVersion.Estado.VIGENTE
                )
                .stream()
                .findFirst()
                .map(FirmaVisualUsuarioActualResponse::configurada)
                .orElseGet(() -> FirmaVisualUsuarioActualResponse.sinConfigurar(usuarioId));
    }

    @Transactional(readOnly = true)
    public List<FirmaVisualUsuarioVersionResponse> getVersiones(Long usuarioId) {
        requireUsuario(usuarioId);
        return firmaRepo.findAllMetadataByTitularIdOrderByVersionDesc(usuarioId)
                .stream()
                .map(FirmaVisualUsuarioVersionResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public FirmaVisualUsuarioVersion getVigente(Long usuarioId) {
        requireUsuario(usuarioId);
        return firmaRepo.findFirstByTitularIdAndEstadoOrderByVersionDesc(
                        usuarioId,
                        FirmaVisualUsuarioVersion.Estado.VIGENTE
                )
                .orElseThrow(() -> new NoSuchElementException(
                        "El usuario no tiene una firma visual vigente."
                ));
    }

    @Transactional(readOnly = true)
    public FirmaVisualUsuarioVersion getVersion(Long usuarioId, Long versionId) {
        requireUsuario(usuarioId);
        return firmaRepo.findByIdAndTitularId(versionId, usuarioId)
                .orElseThrow(() -> new NoSuchElementException(
                        "No existe la versión de firma visual solicitada para este usuario."
                ));
    }

    @Transactional
    public FirmaVisualUsuarioVersion crearNuevaVersion(
            Long usuarioId,
            MultipartFile firma,
            String motivoCambio,
            User administrador
    ) {
        String motivo = requireTexto(motivoCambio, "Debe informar el motivo del cambio.");
        User titular = requireUsuarioForUpdate(usuarioId);
        if (titular.getEstado() != 1) {
            throw new IllegalArgumentException(
                    "Solo se puede configurar la firma visual de un usuario activo."
            );
        }
        User actor = requireAdministrador(administrador);
        FirmaNormalizada normalizada = validarYNormalizar(firma);
        LocalDateTime now = AppTime.now();

        int nuevaVersion = firmaRepo.findMaxVersionByTitularId(usuarioId) + 1;
        firmaRepo.findFirstByTitularIdAndEstadoOrderByVersionDesc(
                        usuarioId,
                        FirmaVisualUsuarioVersion.Estado.VIGENTE
                )
                .ifPresent(vigente -> {
                    retirarVersion(
                            vigente,
                            now,
                            actor,
                            "Reemplazada por la versión " + nuevaVersion + ": " + motivo
                    );
                    // Hibernate suele ejecutar INSERT antes de UPDATE al hacer flush.
                    // Se retira primero para no violar el índice único de versión vigente.
                    firmaRepo.saveAndFlush(vigente);
                });

        FirmaVisualUsuarioVersion nueva = new FirmaVisualUsuarioVersion();
        nueva.setTitular(titular);
        nueva.setVersion(nuevaVersion);
        nueva.setEstado(FirmaVisualUsuarioVersion.Estado.VIGENTE);
        nueva.setNombreArchivoOriginal(resolveFileName(firma.getOriginalFilename()));
        nueva.setContentType("image/png");
        nueva.setTamanoBytes((long) normalizada.bytes().length);
        nueva.setAnchoPx(normalizada.width());
        nueva.setAltoPx(normalizada.height());
        nueva.setSha256(sha256Hex(normalizada.bytes()));
        nueva.setContenido(normalizada.bytes());
        nueva.setVigenteDesde(now);
        nueva.setCreadoEn(now);
        nueva.setConfiguradaPor(actor);
        nueva.setConfiguradaPorUsername(requireTexto(actor.getUsername(), "El administrador no tiene username."));
        nueva.setConfiguradaPorNombre(snapshotNombre(actor));
        nueva.setMotivoCambio(motivo);

        return firmaRepo.save(nueva);
    }

    @Transactional
    public FirmaVisualUsuarioVersion retirar(
            Long usuarioId,
            String motivoRetiro,
            User administrador
    ) {
        String motivo = requireTexto(motivoRetiro, "Debe informar el motivo del retiro.");
        requireUsuarioForUpdate(usuarioId);
        User actor = requireAdministrador(administrador);

        FirmaVisualUsuarioVersion vigente = firmaRepo
                .findFirstByTitularIdAndEstadoOrderByVersionDesc(
                        usuarioId,
                        FirmaVisualUsuarioVersion.Estado.VIGENTE
                )
                .orElseThrow(() -> new IllegalStateException(
                        "El usuario no tiene una firma visual vigente para retirar."
                ));

        retirarVersion(vigente, AppTime.now(), actor, motivo);
        return firmaRepo.save(vigente);
    }

    private User requireUsuario(Long usuarioId) {
        return userRepository.findById(usuarioId)
                .orElseThrow(() -> new NoSuchElementException(
                        "No existe el usuario con id: " + usuarioId
                ));
    }

    private User requireUsuarioForUpdate(Long usuarioId) {
        return userRepository.findByIdForUpdate(usuarioId)
                .orElseThrow(() -> new NoSuchElementException(
                        "No existe el usuario con id: " + usuarioId
                ));
    }

    private static User requireAdministrador(User administrador) {
        if (administrador == null || administrador.getId() == null) {
            throw new IllegalArgumentException("No fue posible identificar al administrador autenticado.");
        }
        return administrador;
    }

    private static void retirarVersion(
            FirmaVisualUsuarioVersion version,
            LocalDateTime now,
            User administrador,
            String motivo
    ) {
        version.setEstado(FirmaVisualUsuarioVersion.Estado.RETIRADA);
        version.setVigenteHasta(now);
        version.setRetiradaPor(administrador);
        version.setRetiradaPorUsername(requireTexto(
                administrador.getUsername(),
                "El administrador no tiene username."
        ));
        version.setRetiradaPorNombre(snapshotNombre(administrador));
        version.setMotivoRetiro(motivo);
    }

    private static FirmaNormalizada validarYNormalizar(MultipartFile firma) {
        if (firma == null || firma.isEmpty()) {
            throw new IllegalArgumentException("Debe adjuntar una firma visual PNG o JPG/JPEG.");
        }
        if (firma.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new IllegalArgumentException("La firma visual no puede superar 1 MB.");
        }

        byte[] original;
        try {
            original = firma.getBytes();
        } catch (IOException exception) {
            throw new IllegalArgumentException("No se pudo leer el archivo de firma visual.", exception);
        }

        ImagenDecodificada decodificada;
        try (ImageInputStream input = ImageIO.createImageInputStream(
                new ByteArrayInputStream(original))) {
            if (input == null) {
                throw new IllegalArgumentException("No se pudo inspeccionar la imagen de firma visual.");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new IllegalArgumentException(
                        "El archivo no corresponde a una imagen PNG o JPG/JPEG válida.");
            }

            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                FormatoFirmaEntrada formato = FormatoFirmaEntrada.fromReaderFormat(
                        reader.getFormatName());
                validarContentType(firma.getContentType(), formato);

                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                validarDimensiones(width, height);

                BufferedImage image = reader.read(0);
                if (image == null) {
                    throw new IllegalArgumentException("La imagen no pudo ser decodificada.");
                }
                validarDimensiones(image.getWidth(), image.getHeight());
                decodificada = new ImagenDecodificada(
                        image,
                        image.getWidth(),
                        image.getHeight());
            } finally {
                reader.dispose();
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException("No se pudo inspeccionar la imagen de firma visual.", exception);
        }

        BufferedImage image = decodificada.image();
        int width = decodificada.width();
        int height = decodificada.height();
        if (esImagenUniforme(image)) {
            throw new IllegalArgumentException("La imagen de firma visual está vacía o no contiene trazos distinguibles.");
        }

        byte[] normalizada;
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (!ImageIO.write(image, "png", output)) {
                throw new IllegalArgumentException("No fue posible normalizar la imagen como PNG.");
            }
            normalizada = output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalArgumentException("No fue posible normalizar la imagen como PNG.", exception);
        }
        if (normalizada.length > MAX_FILE_SIZE_BYTES) {
            throw new IllegalArgumentException("La firma visual normalizada no puede superar 1 MB.");
        }
        return new FirmaNormalizada(normalizada, width, height);
    }

    private static void validarDimensiones(int width, int height) {
        if (width < MIN_WIDTH_PX || height < MIN_HEIGHT_PX) {
            throw new IllegalArgumentException("La firma visual debe medir al menos 50 x 20 px.");
        }
        if (width > MAX_WIDTH_PX || height > MAX_HEIGHT_PX) {
            throw new IllegalArgumentException("La firma visual no puede superar 2000 x 1000 px.");
        }
    }

    private static void validarContentType(
            String contentType,
            FormatoFirmaEntrada formato
    ) {
        String declarado = trim(contentType);
        if (declarado == null || declarado.isBlank()
                || "application/octet-stream".equalsIgnoreCase(declarado)) {
            return;
        }
        if (!formato.contentType().equalsIgnoreCase(declarado)) {
            throw new IllegalArgumentException(
                    "El content type declarado no coincide con el formato real de la imagen.");
        }
    }

    private static boolean esImagenUniforme(BufferedImage image) {
        int referencia = image.getRGB(0, 0);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (image.getRGB(x, y) != referencia) {
                    return false;
                }
            }
        }
        return true;
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 no está disponible.", exception);
        }
    }

    private static String resolveFileName(String originalFilename) {
        String fileName = trim(originalFilename);
        if (fileName == null || fileName.isBlank()) {
            return "firma_visual.png";
        }
        fileName = fileName.replace(String.valueOf((char) 0), "").replace('\\', '/');
        int slash = fileName.lastIndexOf('/');
        if (slash >= 0) {
            fileName = fileName.substring(slash + 1);
        }
        if (fileName.isBlank()) {
            return "firma_visual.png";
        }
        return fileName.length() <= 255 ? fileName : fileName.substring(fileName.length() - 255);
    }

    private static String snapshotNombre(User user) {
        String nombre = trim(user.getNombreCompleto());
        return nombre == null || nombre.isBlank()
                ? requireTexto(user.getUsername(), "El administrador no tiene nombre ni username.")
                : nombre;
    }

    private static String requireTexto(String value, String message) {
        String normalized = trim(value);
        if (normalized == null || normalized.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }

    private record FirmaNormalizada(byte[] bytes, int width, int height) {
    }

    private record ImagenDecodificada(BufferedImage image, int width, int height) {
    }

    private enum FormatoFirmaEntrada {
        PNG("image/png"),
        JPEG("image/jpeg");

        private final String contentType;

        FormatoFirmaEntrada(String contentType) {
            this.contentType = contentType;
        }

        private String contentType() {
            return contentType;
        }

        private static FormatoFirmaEntrada fromReaderFormat(String readerFormat) {
            String normalizado = readerFormat == null
                    ? ""
                    : readerFormat.trim().toUpperCase(Locale.ROOT);
            return switch (normalizado) {
                case "PNG" -> PNG;
                case "JPEG", "JPG" -> JPEG;
                default -> throw new IllegalArgumentException(
                        "Solo se permiten imágenes PNG o JPG/JPEG.");
            };
        }
    }
}
