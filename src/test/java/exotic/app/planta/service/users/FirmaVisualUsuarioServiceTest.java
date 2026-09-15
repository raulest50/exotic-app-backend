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
    void crearNuevaVersion_escalaImagenGrandeConservandoProporcion() throws IOException {
        BufferedImage image = imagenConTrazo(4800, 1600, BufferedImage.TYPE_BYTE_BINARY);
        FirmaVisualUsuarioVersion created = crear(new MockMultipartFile(
                "firma",
                "firma-grande.png",
                "image/png",
                imageBytes(image, "png")));

        assertEquals(1200, created.getAnchoPx());
        assertEquals(400, created.getAltoPx());
        assertTrue(created.getTamanoBytes() <= FirmaVisualUsuarioService.MAX_STORED_SIZE_BYTES);
        assertTrue(tienePixelesDistintos(ImageIO.read(
                new java.io.ByteArrayInputStream(created.getContenido()))));
    }

    @Test
    void crearNuevaVersion_submuestreaJpegGrandeAntesDelEscaladoFinal() throws IOException {
        BufferedImage image = imagenConTrazo(4800, 1600, BufferedImage.TYPE_INT_RGB);
        FirmaVisualUsuarioVersion created = crear(new MockMultipartFile(
                "firma",
                "firma-grande.jpg",
                "image/jpeg",
                imageBytes(image, "jpg")));

        assertEquals(1200, created.getAnchoPx());
        assertEquals(400, created.getAltoPx());
        assertTrue(created.getTamanoBytes() <= FirmaVisualUsuarioService.MAX_STORED_SIZE_BYTES);
        assertTrue(tienePixelesDistintos(ImageIO.read(
                new java.io.ByteArrayInputStream(created.getContenido()))));
    }

    @Test
    void crearNuevaVersion_aceptaEntradaMayorAUnMegabyteYLaOptimiza() throws IOException {
        BufferedImage image = imagenRuido(1200, 800, 42L);
        byte[] png = imageBytes(image, "png");
        assertTrue(png.length > FirmaVisualUsuarioService.MAX_STORED_SIZE_BYTES);
        assertTrue(png.length <= FirmaVisualUsuarioService.MAX_UPLOAD_SIZE_BYTES);

        FirmaVisualUsuarioVersion created = crear(new MockMultipartFile(
                "firma",
                "firma-pesada.png",
                "image/png",
                png));

        assertTrue(created.getTamanoBytes() <= FirmaVisualUsuarioService.TARGET_STORED_SIZE_BYTES);
        assertTrue(created.getAnchoPx() <= FirmaVisualUsuarioService.TARGET_MAX_WIDTH_PX);
        assertTrue(created.getAltoPx() <= FirmaVisualUsuarioService.TARGET_MAX_HEIGHT_PX);
        assertArrayEquals(PNG_SIGNATURE, primerosBytes(created.getContenido(), PNG_SIGNATURE.length));
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
    void crearNuevaVersion_rechazaEntradaSuperiorACuatroMegabytes() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> crear(new MockMultipartFile(
                        "firma",
                        "grande.jpg",
                        "image/jpeg",
                        new byte[(int) FirmaVisualUsuarioService.MAX_UPLOAD_SIZE_BYTES + 1])));

        assertTrue(error.getMessage().contains("no puede superar 4 MB"));
    }

    @Test
    void crearNuevaVersion_reducePngNormalizadoHastaElPresupuesto() throws IOException {
        BufferedImage image = imagenRuido(1000, 1000, 84L);
        byte[] jpeg = imageBytes(image, "jpg");
        assertTrue(jpeg.length <= FirmaVisualUsuarioService.MAX_UPLOAD_SIZE_BYTES);

        FirmaVisualUsuarioVersion created = crear(new MockMultipartFile(
                "firma",
                "ruido.jpg",
                "image/jpeg",
                jpeg));

        assertTrue(created.getTamanoBytes() <= FirmaVisualUsuarioService.TARGET_STORED_SIZE_BYTES);
        assertTrue(created.getAnchoPx() <= 600);
        assertTrue(created.getAltoPx() <= 600);
    }

    @Test
    void crearNuevaVersion_rechazaImagenConMasDeVeinteMegapixeles() throws IOException {
        BufferedImage image = new BufferedImage(5000, 4001, BufferedImage.TYPE_BYTE_BINARY);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> crear(new MockMultipartFile(
                        "firma",
                        "demasiados-pixeles.png",
                        "image/png",
                        imageBytes(image, "png"))));

        assertTrue(error.getMessage().contains("20 megapíxeles"));
    }

    @Test
    void crearNuevaVersion_rechazaImagenConLadoSuperiorA8192Pixeles() throws IOException {
        BufferedImage image = new BufferedImage(8193, 20, BufferedImage.TYPE_BYTE_BINARY);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> crear(new MockMultipartFile(
                        "firma",
                        "lado-excesivo.png",
                        "image/png",
                        imageBytes(image, "png"))));

        assertTrue(error.getMessage().contains("8192 px por lado"));
    }

    @Test
    void crearNuevaVersion_conservaTransparenciaPng() throws IOException {
        BufferedImage image = new BufferedImage(2400, 800, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(Color.BLACK);
            graphics.drawLine(10, 780, 2390, 10);
        } finally {
            graphics.dispose();
        }

        FirmaVisualUsuarioVersion created = crear(new MockMultipartFile(
                "firma",
                "transparente.png",
                "image/png",
                imageBytes(image, "png")));
        BufferedImage stored = ImageIO.read(
                new java.io.ByteArrayInputStream(created.getContenido()));

        assertTrue(stored.getColorModel().hasAlpha());
        assertEquals(0, stored.getRGB(0, 0) >>> 24);
        assertEquals(1200, stored.getWidth());
        assertEquals(400, stored.getHeight());
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

    private static BufferedImage imagenRuido(int width, int height, long seed) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Random random = new Random(seed);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                image.setRGB(x, y, random.nextInt(0x1000000));
            }
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

    private static boolean tienePixelesDistintos(BufferedImage image) {
        int reference = image.getRGB(0, 0);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (image.getRGB(x, y) != reference) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
