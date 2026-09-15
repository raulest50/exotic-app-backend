package exotic.app.planta.service.users;

import exotic.app.planta.model.users.User;
import exotic.app.planta.model.users.firma.FirmaVisualUsuarioVersion;
import exotic.app.planta.repo.usuarios.FirmaVisualUsuarioVersionRepo;
import exotic.app.planta.repo.usuarios.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FirmaVisualUsuarioServiceTest {

    private static final byte[] PNG_SIGNATURE = new byte[] {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    };

    private FirmaVisualUsuarioVersionRepo firmaRepo;
    private FirmaVisualUsuarioService service;
    private User titular;
    private User administrador;

    @BeforeEach
    void setUp() {
        firmaRepo = mock(FirmaVisualUsuarioVersionRepo.class);
        UserRepository userRepository = mock(UserRepository.class);
        service = new FirmaVisualUsuarioService(firmaRepo, userRepository);

        titular = user(7L, "operario", "Operario Uno", 1);
        administrador = user(2L, "admin", "Administrador", 1);
        when(userRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(titular));
        when(firmaRepo.findMaxVersionByTitularId(7L)).thenReturn(0);
        when(firmaRepo.findFirstByTitularIdAndEstadoOrderByVersionDesc(
                7L,
                FirmaVisualUsuarioVersion.Estado.VIGENTE
        )).thenReturn(Optional.empty());
        when(firmaRepo.save(any(FirmaVisualUsuarioVersion.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void crearNuevaVersion_aceptaPngYConservaSalidaCanonica() throws IOException {
        FirmaVisualUsuarioVersion created = crear(pngConTrazo("firma.png", "image/png"));

        assertEquals("firma.png", created.getNombreArchivoOriginal());
        assertEquals("image/png", created.getContentType());
        assertArrayEquals(PNG_SIGNATURE, primerosBytes(created.getContenido(), PNG_SIGNATURE.length));
        assertEquals(sha256(created.getContenido()), created.getSha256());
        assertEquals(created.getContenido().length, created.getTamanoBytes());
        assertEquals(300, created.getAnchoPx());
        assertEquals(100, created.getAltoPx());
    }

    @Test
    void crearNuevaVersion_aceptaJpegYLoNormalizaComoPng() throws IOException {
        byte[] original = jpegConTrazo();

        FirmaVisualUsuarioVersion created = crear(new MockMultipartFile(
                "firma",
                "firma.jpeg",
                "image/jpeg",
                original));

        assertEquals("firma.jpeg", created.getNombreArchivoOriginal());
        assertEquals("image/png", created.getContentType());
        assertArrayEquals(PNG_SIGNATURE, primerosBytes(created.getContenido(), PNG_SIGNATURE.length));
        assertEquals(sha256(created.getContenido()), created.getSha256());
        assertNotNull(ImageIO.read(new java.io.ByteArrayInputStream(created.getContenido())));
    }

    @Test
    void crearNuevaVersion_aceptaTipoGenericoCuandoElContenidoEsJpeg() throws IOException {
        FirmaVisualUsuarioVersion created = crear(new MockMultipartFile(
                "firma",
                "firma.jpg",
                "application/octet-stream",
                jpegConTrazo()));

        assertEquals("image/png", created.getContentType());
        assertArrayEquals(PNG_SIGNATURE, primerosBytes(created.getContenido(), PNG_SIGNATURE.length));
    }

    @Test
    void crearNuevaVersion_rechazaMimeQueNoCoincideConElContenido() throws IOException {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> crear(new MockMultipartFile(
                        "firma",
                        "firma.jpg",
                        "image/png",
                        jpegConTrazo())));

        assertTrue(error.getMessage().contains("no coincide"));
    }

    @Test
    void crearNuevaVersion_rechazaFormatoNoPermitidoAunqueSeaImagen() throws IOException {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> crear(imagenConTrazo("firma.gif", "image/gif", "gif", 300, 100)));

        assertTrue(error.getMessage().contains("Solo se permiten"));
    }

    @Test
    void crearNuevaVersion_rechazaArchivoCorrupto() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> crear(new MockMultipartFile(
                        "firma",
                        "firma.jpg",
                        "image/jpeg",
                        "esto no es una imagen".getBytes())));

        assertTrue(error.getMessage().contains("no corresponde"));
    }

    @Test
    void crearNuevaVersion_rechazaImagenFueraDeDimensionesAntesDePersistir() throws IOException {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> crear(imagenConTrazo("firma.png", "image/png", "png", 49, 20)));

        assertTrue(error.getMessage().contains("al menos 50 x 20"));
    }

    @Test
    void crearNuevaVersion_rechazaImagenUniformeJpeg() throws IOException {
        BufferedImage image = new BufferedImage(300, 100, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        } finally {
            graphics.dispose();
        }

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> crear(new MockMultipartFile(
                        "firma",
                        "vacia.jpg",
                        "image/jpeg",
                        imageBytes(image, "jpg"))));

        assertTrue(error.getMessage().contains("vacía"));
    }

    @Test
    void crearNuevaVersion_rechazaEntradaSuperiorAUnMegabyte() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> crear(new MockMultipartFile(
                        "firma",
                        "grande.jpg",
                        "image/jpeg",
                        new byte[(int) FirmaVisualUsuarioService.MAX_FILE_SIZE_BYTES + 1])));

        assertTrue(error.getMessage().contains("no puede superar 1 MB"));
    }

    @Test
    void crearNuevaVersion_rechazaPngNormalizadoSuperiorAUnMegabyte() throws IOException {
        BufferedImage image = new BufferedImage(1000, 1000, BufferedImage.TYPE_INT_RGB);
        Random random = new Random(42L);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                image.setRGB(x, y, random.nextInt(0x1000000));
            }
        }
        byte[] jpeg = imageBytes(image, "jpg");
        assertTrue(jpeg.length <= FirmaVisualUsuarioService.MAX_FILE_SIZE_BYTES);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> crear(new MockMultipartFile(
                        "firma",
                        "ruido.jpg",
                        "image/jpeg",
                        jpeg)));

        assertTrue(error.getMessage().contains("normalizada no puede superar 1 MB"));
    }

    @Test
    void crearNuevaVersion_retiraAnteriorAntesDeGuardarNueva() throws IOException {
        FirmaVisualUsuarioVersion anterior = new FirmaVisualUsuarioVersion();
        anterior.setVersion(1);
        anterior.setEstado(FirmaVisualUsuarioVersion.Estado.VIGENTE);
        when(firmaRepo.findMaxVersionByTitularId(7L)).thenReturn(1);
        when(firmaRepo.findFirstByTitularIdAndEstadoOrderByVersionDesc(
                7L,
                FirmaVisualUsuarioVersion.Estado.VIGENTE
        )).thenReturn(Optional.of(anterior));
        when(firmaRepo.saveAndFlush(anterior)).thenReturn(anterior);

        FirmaVisualUsuarioVersion created = service.crearNuevaVersion(
                7L,
                new MockMultipartFile("firma", "nueva.jpg", "image/jpeg", jpegConTrazo()),
                "Cambio autorizado",
                administrador);

        assertEquals(FirmaVisualUsuarioVersion.Estado.RETIRADA, anterior.getEstado());
        assertNotNull(anterior.getVigenteHasta());
        assertEquals(administrador, anterior.getRetiradaPor());
        assertTrue(anterior.getMotivoRetiro().contains("versión 2"));
        assertEquals(2, created.getVersion());
        verify(firmaRepo).saveAndFlush(anterior);
    }

    private FirmaVisualUsuarioVersion crear(MockMultipartFile file) {
        return service.crearNuevaVersion(
                7L,
                file,
                "Configuración de prueba",
                administrador);
    }

    private static User user(Long id, String username, String nombre, int estado) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setNombreCompleto(nombre);
        user.setEstado(estado);
        return user;
    }

    private static MockMultipartFile pngConTrazo(
            String fileName,
            String contentType
    ) throws IOException {
        return imagenConTrazo(fileName, contentType, "png", 300, 100);
    }

    private static byte[] jpegConTrazo() throws IOException {
        BufferedImage image = imagenConTrazo(300, 100, BufferedImage.TYPE_INT_RGB);
        return imageBytes(image, "jpg");
    }

    private static MockMultipartFile imagenConTrazo(
            String fileName,
            String contentType,
            String format,
            int width,
            int height
    ) throws IOException {
        int imageType = "jpg".equals(format)
                ? BufferedImage.TYPE_INT_RGB
                : BufferedImage.TYPE_INT_ARGB;
        return new MockMultipartFile(
                "firma",
                fileName,
                contentType,
                imageBytes(imagenConTrazo(width, height, imageType), format));
    }

    private static BufferedImage imagenConTrazo(int width, int height, int imageType) {
        BufferedImage image = new BufferedImage(width, height, imageType);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, width, height);
            graphics.setColor(Color.BLACK);
            graphics.drawLine(5, Math.max(1, height - 5), Math.max(6, width - 5), 5);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private static byte[] imageBytes(BufferedImage image, String format) throws IOException {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            assertTrue(ImageIO.write(image, format, output));
            return output.toByteArray();
        }
    }

    private static byte[] primerosBytes(byte[] bytes, int length) {
        byte[] result = new byte[length];
        System.arraycopy(bytes, 0, result, 0, length);
        return result;
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
