package exotic.app.planta.service.empresa;

import exotic.app.planta.model.empresa.EmpresaLogoDocumentalVersion;
import exotic.app.planta.repo.empresa.EmpresaLogoDocumentalVersionRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmpresaLogoDocumentalServiceTest {

    private EmpresaLogoDocumentalVersionRepo repo;
    private EmpresaLogoDocumentalService service;

    @BeforeEach
    void setUp() {
        repo = mock(EmpresaLogoDocumentalVersionRepo.class);
        service = new EmpresaLogoDocumentalService(repo);
    }

    @Test
    void crearNuevaVersion_validaPngRetiraAnteriorYCalculaMetadatos() throws IOException {
        EmpresaLogoDocumentalVersion anterior = new EmpresaLogoDocumentalVersion();
        anterior.setId(1L);
        anterior.setVersion(1);
        anterior.setEstado(EmpresaLogoDocumentalVersion.Estado.VIGENTE);
        when(repo.findByEstadoForUpdate(EmpresaLogoDocumentalVersion.Estado.VIGENTE))
                .thenReturn(Optional.of(anterior));
        when(repo.findMaxVersion()).thenReturn(1);
        when(repo.save(any(EmpresaLogoDocumentalVersion.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        byte[] png = png(120, 140);
        EmpresaLogoDocumentalVersion created = service.crearNuevaVersion(
                new MockMultipartFile("logo", "nuevo.png", "image/png", png),
                " Nueva identidad visual ",
                " admin "
        );

        assertEquals(EmpresaLogoDocumentalVersion.Estado.RETIRADA, anterior.getEstado());
        assertNotNull(anterior.getVigenteHasta());
        assertEquals(2, created.getVersion());
        assertEquals(120, created.getAnchoPx());
        assertEquals(140, created.getAltoPx());
        assertEquals((long) png.length, created.getTamanoBytes());
        assertEquals(64, created.getSha256().length());
        assertEquals("admin", created.getCreadoPor());
        assertEquals("Nueva identidad visual", created.getMotivoCambio());
        verify(repo).save(anterior);
        verify(repo).save(created);
    }

    @Test
    void crearNuevaVersion_rechazaContenidoQueNoEsPng() {
        MockMultipartFile file = new MockMultipartFile(
                "logo",
                "falso.png",
                "image/png",
                "no-es-png".getBytes()
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.crearNuevaVersion(file, "cambio", "admin")
        );

        assertEquals("El archivo no tiene una firma PNG valida.", exception.getMessage());
    }

    @Test
    void crearNuevaVersion_requiereMotivo() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "logo",
                "logo.png",
                "image/png",
                png(100, 100)
        );

        assertThrows(IllegalArgumentException.class, () -> service.crearNuevaVersion(file, " ", "admin"));
    }

    @Test
    void resolveVersion_usesCurrentWhenIdIsNull() {
        EmpresaLogoDocumentalVersion vigente = new EmpresaLogoDocumentalVersion();
        vigente.setId(1L);
        when(repo.findFirstByEstadoOrderByVersionDesc(EmpresaLogoDocumentalVersion.Estado.VIGENTE))
                .thenReturn(Optional.of(vigente));

        assertEquals(vigente, service.resolveVersion(null));
    }

    @Test
    void resolveVersion_usesExplicitVersionWhenIdIsProvided() {
        EmpresaLogoDocumentalVersion historica = new EmpresaLogoDocumentalVersion();
        historica.setId(9L);
        when(repo.findById(9L)).thenReturn(Optional.of(historica));

        assertEquals(historica, service.resolveVersion(9L));
    }

    private static byte[] png(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", output);
            return output.toByteArray();
        }
    }
}
