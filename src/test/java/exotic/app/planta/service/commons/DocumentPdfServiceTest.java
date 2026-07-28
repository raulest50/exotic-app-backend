package exotic.app.planta.service.commons;

import exotic.app.planta.model.compras.OrdenCompraMateriales;
import exotic.app.planta.model.compras.Proveedor;
import exotic.app.planta.model.empresa.EmpresaIdentidadLegalVersion;
import exotic.app.planta.model.empresa.EmpresaLogoDocumentalVersion;
import exotic.app.planta.repo.compras.OrdenCompraRepo;
import exotic.app.planta.service.empresa.EmpresaIdentidadLegalService;
import exotic.app.planta.service.empresa.EmpresaLogoDocumentalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocumentPdfServiceTest {

    private OrdenCompraRepo ordenCompraRepo;
    private EmpresaIdentidadLegalService identidadService;
    private EmpresaLogoDocumentalService logoService;
    private DocumentPdfService service;

    @BeforeEach
    void setUp() {
        ordenCompraRepo = mock(OrdenCompraRepo.class);
        identidadService = mock(EmpresaIdentidadLegalService.class);
        logoService = mock(EmpresaLogoDocumentalService.class);
        service = new DocumentPdfService(ordenCompraRepo, identidadService, logoService);
    }

    @Test
    void generateOrdenCompraPdf_usaVersionesHistoricasAsociadas() throws IOException {
        OrdenCompraMateriales orden = orden();
        orden.setEmpresaIdentidadLegalVersion(identidad(1L));
        orden.setEmpresaLogoDocumentalVersion(logo(1L));
        when(ordenCompraRepo.findById(101)).thenReturn(Optional.of(orden));

        byte[] pdf = service.generateOrdenCompraPdf(101);

        assertTrue(new String(pdf, 0, 4, StandardCharsets.US_ASCII).startsWith("%PDF"));
        verify(identidadService, never()).getVigente();
        verify(logoService, never()).getVigente();
    }

    @Test
    void generateOrdenCompraPdf_usaParejaVigenteCuandoNoHaySnapshot() throws IOException {
        OrdenCompraMateriales orden = orden();
        EmpresaIdentidadLegalVersion identidad = identidad(2L);
        EmpresaLogoDocumentalVersion logo = logo(3L);
        when(ordenCompraRepo.findById(101)).thenReturn(Optional.of(orden));
        when(identidadService.getVigente()).thenReturn(identidad);
        when(logoService.getVigente()).thenReturn(logo);

        byte[] pdf = service.generateOrdenCompraPdf(101);

        assertTrue(new String(pdf, 0, 4, StandardCharsets.US_ASCII).startsWith("%PDF"));
        verify(identidadService).getVigente();
        verify(logoService).getVigente();
    }

    private static OrdenCompraMateriales orden() {
        Proveedor proveedor = new Proveedor();
        proveedor.setId("900000000");
        proveedor.setNombre("Proveedor");
        proveedor.setDepartamento("Atlantico");
        proveedor.setDireccion("Direccion proveedor");
        proveedor.setCiudad("Barranquilla");

        OrdenCompraMateriales orden = new OrdenCompraMateriales();
        orden.setOrdenCompraId(101);
        orden.setProveedor(proveedor);
        orden.setItemsOrdenCompra(new ArrayList<>());
        orden.setCondicionPago("0");
        orden.setTiempoEntrega("5");
        orden.setPlazoPago(30);
        orden.setObservaciones("Prueba");
        return orden;
    }

    private static EmpresaIdentidadLegalVersion identidad(Long id) {
        EmpresaIdentidadLegalVersion identidad = new EmpresaIdentidadLegalVersion();
        identidad.setId(id);
        identidad.setRazonSocial("Laboratorios Novum S.A.S.");
        identidad.setNombreComercial("Novum");
        identidad.setTipoIdentificacion("NIT");
        identidad.setNumeroIdentificacion("902038623");
        identidad.setDigitoVerificacion("5");
        identidad.setTelefonoPrincipal("3000000000");
        identidad.setEmailPrincipal("documental@example.com");
        return identidad;
    }

    private static EmpresaLogoDocumentalVersion logo(Long id) throws IOException {
        BufferedImage image = new BufferedImage(120, 120, BufferedImage.TYPE_INT_ARGB);
        byte[] bytes;
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", output);
            bytes = output.toByteArray();
        }

        EmpresaLogoDocumentalVersion logo = new EmpresaLogoDocumentalVersion();
        logo.setId(id);
        logo.setContenido(bytes);
        return logo;
    }
}
