package exotic.app.planta.service.activos.fijos;

import exotic.app.planta.model.activos.fijos.compras.OrdenCompraActivo;
import exotic.app.planta.model.activos.fijos.dto.UpdateEstadoOrdenCompraAFRequest;
import exotic.app.planta.model.compras.ContactoProveedor;
import exotic.app.planta.model.compras.Proveedor;
import exotic.app.planta.model.empresa.EmpresaIdentidadLegalVersion;
import exotic.app.planta.model.empresa.EmpresaLogoDocumentalVersion;
import exotic.app.planta.model.empresa.dto.EmpresaIdentidadDocumentalVigenteResponse;
import exotic.app.planta.repo.activos.fijos.ItemOrdenCompraActivoRepo;
import exotic.app.planta.repo.activos.fijos.OrdenCompraActivoRepo;
import exotic.app.planta.repo.usuarios.UserRepository;
import exotic.app.planta.service.commons.EmailService;
import exotic.app.planta.service.commons.FileStorageService;
import exotic.app.planta.service.empresa.EmpresaIdentidadDocumentalService;
import exotic.app.planta.service.empresa.EmpresaIdentidadLegalService;
import exotic.app.planta.service.empresa.EmpresaLogoDocumentalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OCAFServiceTest {

    private OrdenCompraActivoRepo ordenRepo;
    private EmailService emailService;
    private UserRepository userRepository;
    private EmpresaIdentidadDocumentalService identidadDocumentalService;
    private EmpresaIdentidadLegalService identidadLegalService;
    private EmpresaLogoDocumentalService logoDocumentalService;
    private OCAFService service;

    @BeforeEach
    void setUp() {
        ordenRepo = mock(OrdenCompraActivoRepo.class);
        emailService = mock(EmailService.class);
        userRepository = mock(UserRepository.class);
        identidadDocumentalService = mock(EmpresaIdentidadDocumentalService.class);
        identidadLegalService = mock(EmpresaIdentidadLegalService.class);
        logoDocumentalService = mock(EmpresaLogoDocumentalService.class);
        service = new OCAFService(
                ordenRepo,
                mock(ItemOrdenCompraActivoRepo.class),
                mock(FileStorageService.class),
                emailService,
                userRepository,
                identidadDocumentalService,
                identidadLegalService,
                logoDocumentalService
        );
    }

    @Test
    void manualSend_usesExplicitDocumentVersions() {
        OrdenCompraActivo orden = orden(1, false);
        EmpresaIdentidadLegalVersion identidad = identidad(7L, "Nueva Marca");
        EmpresaLogoDocumentalVersion logo = logo(11L);
        UpdateEstadoOrdenCompraAFRequest request = request(
                UpdateEstadoOrdenCompraAFRequest.TipoEnvio.MANUAL,
                7L,
                11L
        );
        when(ordenRepo.findById(101)).thenReturn(Optional.of(orden));
        when(identidadLegalService.resolveVersion(7L)).thenReturn(identidad);
        when(logoDocumentalService.resolveVersion(11L)).thenReturn(logo);
        when(ordenRepo.save(any(OrdenCompraActivo.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        OrdenCompraActivo updated = service.updateEstadoOrdenCompraActivo(101, request);

        assertEquals(2, updated.getEstado());
        assertSame(identidad, updated.getEmpresaIdentidadLegalVersion());
        assertSame(logo, updated.getEmpresaLogoDocumentalVersion());
        verify(ordenRepo).save(orden);
    }

    @Test
    void save_startsPendingReleaseAndRejectsSpoofedDocumentSnapshot() {
        OrdenCompraActivo nueva = orden(2, false);
        nueva.setOrdenCompraActivoId(0);
        nueva.setFechaVencimiento(LocalDateTime.of(2026, 8, 15, 0, 0));
        nueva.setItemsOrdenCompra(new ArrayList<>());
        nueva.setEmpresaIdentidadLegalVersion(identidad(7L, "Marca Suplantada"));
        nueva.setEmpresaLogoDocumentalVersion(logo(11L));
        when(ordenRepo.save(any(OrdenCompraActivo.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        OrdenCompraActivo saved = service.saveOrdenCompraActivo(nueva);

        assertEquals(0, saved.getEstado());
        assertNull(saved.getEmpresaIdentidadLegalVersion());
        assertNull(saved.getEmpresaLogoDocumentalVersion());
        verify(ordenRepo).save(nueva);
    }

    @Test
    void manualSend_withoutIds_usesTheCurrentDocumentPair() {
        OrdenCompraActivo orden = orden(1, false);
        EmpresaIdentidadLegalVersion identidad = identidad(4L, "Marca Vigente");
        EmpresaLogoDocumentalVersion logo = logo(6L);
        when(ordenRepo.findById(101)).thenReturn(Optional.of(orden));
        when(identidadDocumentalService.getVigente()).thenReturn(vigente(4L, 6L));
        when(identidadLegalService.resolveVersion(4L)).thenReturn(identidad);
        when(logoDocumentalService.resolveVersion(6L)).thenReturn(logo);
        when(ordenRepo.save(any(OrdenCompraActivo.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        OrdenCompraActivo updated = service.updateEstadoOrdenCompraActivo(
                101,
                request(UpdateEstadoOrdenCompraAFRequest.TipoEnvio.MANUAL, null, null)
        );

        assertSame(identidad, updated.getEmpresaIdentidadLegalVersion());
        assertSame(logo, updated.getEmpresaLogoDocumentalVersion());
        verify(identidadDocumentalService).getVigente();
    }

    @Test
    void send_rejectsAPartialDocumentPair() {
        OrdenCompraActivo orden = orden(1, false);
        when(ordenRepo.findById(101)).thenReturn(Optional.of(orden));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.updateEstadoOrdenCompraActivo(
                        101,
                        request(UpdateEstadoOrdenCompraAFRequest.TipoEnvio.MANUAL, 7L, null)
                )
        );

        assertEquals(
                "Las versiones de identidad legal y logo documental deben enviarse juntas.",
                exception.getMessage()
        );
        assertEquals(1, orden.getEstado());
        verify(ordenRepo, never()).save(any());
        verify(identidadLegalService, never()).resolveVersion(any());
        verify(logoDocumentalService, never()).resolveVersion(any());
    }

    @Test
    void send_rejectsSkippingTheReleaseState() {
        OrdenCompraActivo orden = orden(0, false);
        when(ordenRepo.findById(101)).thenReturn(Optional.of(orden));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service.updateEstadoOrdenCompraActivo(
                        101,
                        request(UpdateEstadoOrdenCompraAFRequest.TipoEnvio.MANUAL, 7L, 11L)
                )
        );

        assertEquals(
                "Solo se puede enviar una OCA que esté pendiente de envío al proveedor.",
                exception.getMessage()
        );
        assertEquals(0, orden.getEstado());
        verify(ordenRepo, never()).save(any());
        verify(identidadLegalService, never()).resolveVersion(any());
        verify(logoDocumentalService, never()).resolveVersion(any());
    }

    @Test
    void send_preservesAnExistingSnapshotAndRejectsDifferentIds() {
        OrdenCompraActivo orden = orden(1, false);
        orden.setEmpresaIdentidadLegalVersion(identidad(3L, "Marca Histórica"));
        orden.setEmpresaLogoDocumentalVersion(logo(5L));
        when(ordenRepo.findById(101)).thenReturn(Optional.of(orden));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.updateEstadoOrdenCompraActivo(
                        101,
                        request(UpdateEstadoOrdenCompraAFRequest.TipoEnvio.MANUAL, 8L, 9L)
                )
        );

        assertEquals("La OCA ya tiene una identidad documental diferente asociada.", exception.getMessage());
        assertEquals(1, orden.getEstado());
        verify(ordenRepo, never()).save(any());
    }

    @Test
    void emailSend_requiresPdfAndDoesNotAdvanceStateWhenItIsMissing() throws Exception {
        OrdenCompraActivo orden = orden(1, true);
        EmpresaIdentidadLegalVersion identidad = identidad(7L, "Nueva Marca");
        when(ordenRepo.findById(101)).thenReturn(Optional.of(orden));
        when(identidadLegalService.resolveVersion(7L)).thenReturn(identidad);
        when(logoDocumentalService.resolveVersion(11L)).thenReturn(logo(11L));
        when(userRepository.findAll()).thenReturn(List.of());

        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> service.updateEstadoOrdenCompraActivo(
                        101,
                        request(UpdateEstadoOrdenCompraAFRequest.TipoEnvio.EMAIL, 7L, 11L)
                )
        );

        assertEquals(
                "No se proporcionó archivo PDF para enviar por email para la orden: 101",
                exception.getMessage()
        );
        assertEquals(1, orden.getEstado());
        verify(emailService, never()).sendEmailWithAttachment(any(), any(), any(), any());
        verify(ordenRepo, never()).save(any());
    }

    @Test
    void emailSend_usesTheSnapshottedCommercialName() throws Exception {
        OrdenCompraActivo orden = orden(1, true);
        EmpresaIdentidadLegalVersion identidad = identidad(7L, "Nueva Marca");
        UpdateEstadoOrdenCompraAFRequest request = request(
                UpdateEstadoOrdenCompraAFRequest.TipoEnvio.EMAIL,
                7L,
                11L
        );
        request.setOCAFpdf(new MockMultipartFile(
                "OCAFpdf",
                "orden-compra-activo-101.pdf",
                "application/pdf",
                "pdf".getBytes()
        ));
        when(ordenRepo.findById(101)).thenReturn(Optional.of(orden));
        when(identidadLegalService.resolveVersion(7L)).thenReturn(identidad);
        when(logoDocumentalService.resolveVersion(11L)).thenReturn(logo(11L));
        when(userRepository.findAll()).thenReturn(List.of());
        when(ordenRepo.save(any(OrdenCompraActivo.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        OrdenCompraActivo updated = service.updateEstadoOrdenCompraActivo(101, request);

        assertEquals(2, updated.getEstado());
        verify(emailService).sendEmailWithAttachment(
                eq("proveedor@example.com"),
                contains("Nueva Marca"),
                contains("Nueva Marca - Departamento de Compras"),
                eq(request.getOCAFpdf())
        );
    }

    private static OrdenCompraActivo orden(int estado, boolean withEmail) {
        Proveedor proveedor = new Proveedor();
        proveedor.setId("PROV-1");
        proveedor.setNombre("Proveedor Uno");
        proveedor.setContactos(new ArrayList<>());
        if (withEmail) {
            ContactoProveedor contacto = new ContactoProveedor();
            contacto.setEmail("proveedor@example.com");
            proveedor.getContactos().add(contacto);
        }

        OrdenCompraActivo orden = new OrdenCompraActivo();
        orden.setOrdenCompraActivoId(101);
        orden.setEstado(estado);
        orden.setProveedor(proveedor);
        return orden;
    }

    private static UpdateEstadoOrdenCompraAFRequest request(
            UpdateEstadoOrdenCompraAFRequest.TipoEnvio tipoEnvio,
            Long identidadId,
            Long logoId
    ) {
        UpdateEstadoOrdenCompraAFRequest request = new UpdateEstadoOrdenCompraAFRequest();
        request.setNewEstado(2);
        request.setTipoEnvio(tipoEnvio);
        request.setEmpresaIdentidadLegalVersionId(identidadId);
        request.setEmpresaLogoDocumentalVersionId(logoId);
        return request;
    }

    private static EmpresaIdentidadLegalVersion identidad(Long id, String nombreComercial) {
        EmpresaIdentidadLegalVersion identidad = new EmpresaIdentidadLegalVersion();
        identidad.setId(id);
        identidad.setVersion(1);
        identidad.setEstado(EmpresaIdentidadLegalVersion.Estado.VIGENTE);
        identidad.setNombreComercial(nombreComercial);
        return identidad;
    }

    private static EmpresaLogoDocumentalVersion logo(Long id) {
        EmpresaLogoDocumentalVersion logo = new EmpresaLogoDocumentalVersion();
        logo.setId(id);
        logo.setVersion(1);
        logo.setEstado(EmpresaLogoDocumentalVersion.Estado.VIGENTE);
        return logo;
    }

    private static EmpresaIdentidadDocumentalVigenteResponse vigente(Long identidadId, Long logoId) {
        return new EmpresaIdentidadDocumentalVigenteResponse(
                "identidad-" + identidadId + "-logo-" + logoId,
                new EmpresaIdentidadDocumentalVigenteResponse.IdentidadLegal(
                        identidadId,
                        1,
                        "Nueva Razón Social",
                        "Nueva Marca",
                        "NIT",
                        "900000000",
                        "1",
                        "3000000000",
                        "compras@example.com"
                ),
                new EmpresaIdentidadDocumentalVigenteResponse.Logo(
                        logoId,
                        1,
                        "abc",
                        "image/png",
                        100L,
                        100,
                        100,
                        "/api/empresa-logo-documental/versiones/" + logoId + "/imagen"
                )
        );
    }
}
