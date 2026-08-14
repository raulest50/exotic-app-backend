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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FirmaVisualUsuarioServiceTest {

    private FirmaVisualUsuarioVersionRepo firmaRepo;
    private UserRepository userRepository;
    private FirmaVisualUsuarioService service;
    private User titular;
    private User administrador;

    @BeforeEach
    void setUp() {
        firmaRepo = mock(FirmaVisualUsuarioVersionRepo.class);
        userRepository = mock(UserRepository.class);
        service = new FirmaVisualUsuarioService(firmaRepo, userRepository);

        titular = user(7L, "operario", "Operario Uno", 1);
        administrador = user(2L, "admin", "Administrador", 1);
        when(userRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(titular));
        when(firmaRepo.save(any(FirmaVisualUsuarioVersion.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void crearNuevaVersion_normalizaPngYRegistraAuditoria() throws IOException {
        when(firmaRepo.findMaxVersionByTitularId(7L)).thenReturn(0);
        when(firmaRepo.findFirstByTitularIdAndEstadoOrderByVersionDesc(
                7L,
                FirmaVisualUsuarioVersion.Estado.VIGENTE
        )).thenReturn(Optional.empty());

        FirmaVisualUsuarioVersion created = service.crearNuevaVersion(
                7L,
                pngConTrazo("firma.png"),
                " Configuración inicial ",
                administrador
        );

        assertEquals(1, created.getVersion());
        assertEquals(FirmaVisualUsuarioVersion.Estado.VIGENTE, created.getEstado());
        assertEquals(titular, created.getTitular());
        assertEquals("admin", created.getConfiguradaPorUsername());
        assertEquals("Administrador", created.getConfiguradaPorNombre());
        assertEquals("Configuración inicial", created.getMotivoCambio());
        assertEquals(64, created.getSha256().length());
        assertNotNull(created.getContenido());
        assertTrue(created.getTamanoBytes() > 0);
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
                pngConTrazo("nueva.png"),
                "Cambio autorizado",
                administrador
        );

        assertEquals(FirmaVisualUsuarioVersion.Estado.RETIRADA, anterior.getEstado());
        assertNotNull(anterior.getVigenteHasta());
        assertEquals(administrador, anterior.getRetiradaPor());
        assertTrue(anterior.getMotivoRetiro().contains("versión 2"));
        assertEquals(2, created.getVersion());
        verify(firmaRepo).saveAndFlush(anterior);
    }

    @Test
    void retirar_conservaVersionYRegistraMotivo() {
        FirmaVisualUsuarioVersion vigente = new FirmaVisualUsuarioVersion();
        vigente.setEstado(FirmaVisualUsuarioVersion.Estado.VIGENTE);
        when(firmaRepo.findFirstByTitularIdAndEstadoOrderByVersionDesc(
                7L,
                FirmaVisualUsuarioVersion.Estado.VIGENTE
        )).thenReturn(Optional.of(vigente));

        FirmaVisualUsuarioVersion retired = service.retirar(
                7L,
                "Solicitud del titular",
                administrador
        );

        assertEquals(FirmaVisualUsuarioVersion.Estado.RETIRADA, retired.getEstado());
        assertEquals("Solicitud del titular", retired.getMotivoRetiro());
        assertEquals("admin", retired.getRetiradaPorUsername());
        assertNotNull(retired.getVigenteHasta());
    }

    @Test
    void crearNuevaVersion_rechazaUsuarioInactivo() throws IOException {
        titular.setEstado(2);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.crearNuevaVersion(
                        7L,
                        pngConTrazo("firma.png"),
                        "Configuración inicial",
                        administrador
                )
        );

        assertTrue(error.getMessage().contains("usuario activo"));
    }

    @Test
    void crearNuevaVersion_rechazaImagenUniforme() throws IOException {
        BufferedImage image = new BufferedImage(200, 80, BufferedImage.TYPE_INT_ARGB);
        byte[] png;
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", output);
            png = output.toByteArray();
        }

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.crearNuevaVersion(
                        7L,
                        new MockMultipartFile("firma", "vacia.png", "image/png", png),
                        "Configuración inicial",
                        administrador
                )
        );

        assertTrue(error.getMessage().contains("vacía"));
    }

    private static User user(Long id, String username, String nombre, int estado) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setNombreCompleto(nombre);
        user.setEstado(estado);
        return user;
    }

    private static MockMultipartFile pngConTrazo(String fileName) throws IOException {
        BufferedImage image = new BufferedImage(300, 100, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(Color.BLACK);
            graphics.drawLine(20, 60, 280, 35);
        } finally {
            graphics.dispose();
        }
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", output);
            return new MockMultipartFile("firma", fileName, "image/png", output.toByteArray());
        }
    }
}
