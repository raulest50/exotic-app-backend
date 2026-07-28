package exotic.app.planta.service.compras;

import exotic.app.planta.model.compras.OrdenCompraMateriales;
import exotic.app.planta.model.compras.Proveedor;
import exotic.app.planta.model.compras.dto.UpdateEstadoOrdenCompraRequest;
import exotic.app.planta.model.empresa.EmpresaIdentidadLegalVersion;
import exotic.app.planta.model.empresa.EmpresaLogoDocumentalVersion;
import exotic.app.planta.model.users.User;
import exotic.app.planta.repo.compras.FacturaCompraRepo;
import exotic.app.planta.repo.compras.OrdenCompraRepo;
import exotic.app.planta.repo.compras.ProveedorRepo;
import exotic.app.planta.repo.inventarios.TransaccionAlmacenHeaderRepo;
import exotic.app.planta.repo.inventarios.TransaccionAlmacenRepo;
import exotic.app.planta.repo.producto.MaterialRepo;
import exotic.app.planta.repo.producto.SemiTerminadoRepo;
import exotic.app.planta.repo.producto.TerminadoRepo;
import exotic.app.planta.repo.usuarios.UserRepository;
import exotic.app.planta.service.commons.EmailService;
import exotic.app.planta.service.empresa.EmpresaIdentidadLegalService;
import exotic.app.planta.service.empresa.EmpresaLogoDocumentalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ComprasServiceTest {

    private OrdenCompraRepo ordenCompraRepo;
    private ProveedorRepo proveedorRepo;
    private EmpresaIdentidadLegalService empresaIdentidadLegalService;
    private EmpresaLogoDocumentalService empresaLogoDocumentalService;
    private ComprasService service;

    @BeforeEach
    void setUp() {
        ordenCompraRepo = mock(OrdenCompraRepo.class);
        proveedorRepo = mock(ProveedorRepo.class);
        empresaIdentidadLegalService = mock(EmpresaIdentidadLegalService.class);
        empresaLogoDocumentalService = mock(EmpresaLogoDocumentalService.class);
        service = new ComprasService(
                mock(FacturaCompraRepo.class),
                mock(TransaccionAlmacenRepo.class),
                proveedorRepo,
                mock(MaterialRepo.class),
                mock(SemiTerminadoRepo.class),
                mock(TerminadoRepo.class),
                ordenCompraRepo,
                mock(EmailService.class),
                mock(UserRepository.class),
                mock(TransaccionAlmacenHeaderRepo.class),
                empresaIdentidadLegalService,
                empresaLogoDocumentalService
        );
    }

    @Test
    void saveOrdenCompra_startsPendingReleaseAndUsesAuthenticatedCreator() {
        Proveedor proveedor = new Proveedor();
        proveedor.setId("PROV-1");
        User creator = user(10L, "creador.real");

        OrdenCompraMateriales nuevaOrden = new OrdenCompraMateriales();
        nuevaOrden.setProveedor(proveedor);
        nuevaOrden.setEstado(1);
        nuevaOrden.setItemsOrdenCompra(new ArrayList<>());
        nuevaOrden.setUsuarioCreadorUsername("usuario.suplantado");
        nuevaOrden.setUsuarioLiberadorUsername("liberador.suplantado");
        nuevaOrden.setFechaLiberacion(LocalDateTime.of(2026, 1, 1, 10, 0));

        when(ordenCompraRepo.save(any(OrdenCompraMateriales.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(proveedorRepo.findById("PROV-1")).thenReturn(Optional.of(proveedor));

        OrdenCompraMateriales saved = service.saveOrdenCompra(nuevaOrden, creator);

        assertEquals(0, saved.getEstado());
        assertSame(creator, saved.getUsuarioCreador());
        assertEquals("creador.real", saved.getUsuarioCreadorUsername());
        assertNull(saved.getUsuarioLiberador());
        assertNull(saved.getUsuarioLiberadorUsername());
        assertNull(saved.getFechaLiberacion());
        verify(ordenCompraRepo).save(nuevaOrden);
    }

    @Test
    void updateEstadoOrdenCompra_releaseFromStateZero_updatesToStateOne() {
        OrdenCompraMateriales orden = orden(0, null);
        User creator = user(10L, "creador.original");
        orden.setUsuarioCreador(creator);
        orden.setUsuarioCreadorUsername(creator.getUsername());
        User releaser = user(20L, "liberador.real");
        when(ordenCompraRepo.findById(101)).thenReturn(Optional.of(orden));
        when(ordenCompraRepo.save(any(OrdenCompraMateriales.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrdenCompraMateriales updated = service.updateEstadoOrdenCompra(
                101,
                request(1, null),
                releaser
        );

        assertEquals(1, updated.getEstado());
        assertSame(releaser, updated.getUsuarioLiberador());
        assertEquals("liberador.real", updated.getUsuarioLiberadorUsername());
        assertNotNull(updated.getFechaLiberacion());
        assertSame(creator, updated.getUsuarioCreador());
        assertEquals("creador.original", updated.getUsuarioCreadorUsername());
        verify(ordenCompraRepo).save(orden);
    }

    @Test
    void updateEstadoOrdenCompra_rejectsReleaseWhenOrderIsNotInStateZero() {
        OrdenCompraMateriales orden = orden(1, null);
        when(ordenCompraRepo.findById(101)).thenReturn(Optional.of(orden));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service.updateEstadoOrdenCompra(101, request(1, null), user(20L, "liberador.real"))
        );

        assertEquals(
                "Solo se puede liberar una orden de compra que esté pendiente de liberación.",
                exception.getMessage()
        );
        assertEquals(1, orden.getEstado());
        assertNull(orden.getUsuarioLiberador());
        assertNull(orden.getUsuarioLiberadorUsername());
        assertNull(orden.getFechaLiberacion());
        verify(ordenCompraRepo, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 3})
    void updateEstadoOrdenCompra_rejectsSkippingReleaseFromStateZero(int newEstado) {
        OrdenCompraMateriales orden = orden(0, null);
        when(ordenCompraRepo.findById(101)).thenReturn(Optional.of(orden));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service.updateEstadoOrdenCompra(101, request(newEstado, null), user(20L, "usuario.actual"))
        );

        assertEquals(
                "Una orden pendiente de liberación debe pasar primero al estado liberado.",
                exception.getMessage()
        );
        assertEquals(0, orden.getEstado());
        verify(ordenCompraRepo, never()).save(any());
    }

    @Test
    void updateEstadoOrdenCompra_manualSendFromStateOneToTwo_setsFechaEnvioProveedorAndLegalIdentity() {
        OrdenCompraMateriales orden = orden(1, null);
        User originalReleaser = user(20L, "liberador.original");
        LocalDateTime releaseDate = LocalDateTime.of(2026, 1, 1, 8, 0);
        orden.setUsuarioLiberador(originalReleaser);
        orden.setUsuarioLiberadorUsername(originalReleaser.getUsername());
        orden.setFechaLiberacion(releaseDate);
        EmpresaIdentidadLegalVersion identidad = identidadLegal(5L);
        EmpresaLogoDocumentalVersion logo = logoDocumental(8L);
        when(ordenCompraRepo.findById(101)).thenReturn(Optional.of(orden));
        when(empresaIdentidadLegalService.resolveVersion(null)).thenReturn(identidad);
        when(empresaLogoDocumentalService.resolveVersion(null)).thenReturn(logo);
        when(ordenCompraRepo.save(any(OrdenCompraMateriales.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrdenCompraMateriales updated = service.updateEstadoOrdenCompra(
                101,
                request(2, UpdateEstadoOrdenCompraRequest.TipoEnvio.MANUAL),
                user(30L, "usuario.envio")
        );

        assertEquals(2, updated.getEstado());
        assertNotNull(updated.getFechaEnvioProveedor());
        assertEquals(identidad, updated.getEmpresaIdentidadLegalVersion());
        assertEquals(logo, updated.getEmpresaLogoDocumentalVersion());
        assertSame(originalReleaser, updated.getUsuarioLiberador());
        assertEquals("liberador.original", updated.getUsuarioLiberadorUsername());
        assertEquals(releaseDate, updated.getFechaLiberacion());
        verify(ordenCompraRepo).save(orden);
    }

    @Test
    void updateEstadoOrdenCompra_manualSendDoesNotOverwriteExistingFechaEnvioProveedor() {
        LocalDateTime existing = LocalDateTime.of(2026, 1, 2, 9, 30);
        OrdenCompraMateriales orden = orden(1, existing);
        EmpresaIdentidadLegalVersion identidad = identidadLegal(5L);
        EmpresaLogoDocumentalVersion logo = logoDocumental(8L);
        when(ordenCompraRepo.findById(101)).thenReturn(Optional.of(orden));
        when(empresaIdentidadLegalService.resolveVersion(null)).thenReturn(identidad);
        when(empresaLogoDocumentalService.resolveVersion(null)).thenReturn(logo);
        when(ordenCompraRepo.save(any(OrdenCompraMateriales.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrdenCompraMateriales updated = service.updateEstadoOrdenCompra(
                101,
                request(2, UpdateEstadoOrdenCompraRequest.TipoEnvio.MANUAL),
                user(30L, "usuario.envio")
        );

        assertEquals(existing, updated.getFechaEnvioProveedor());
        assertEquals(2, updated.getEstado());
    }

    @Test
    void updateEstadoOrdenCompra_manualSendUsesExplicitLegalIdentityVersion() {
        OrdenCompraMateriales orden = orden(1, null);
        EmpresaIdentidadLegalVersion identidad = identidadLegal(7L);
        EmpresaLogoDocumentalVersion logo = logoDocumental(11L);
        UpdateEstadoOrdenCompraRequest request = request(2, UpdateEstadoOrdenCompraRequest.TipoEnvio.MANUAL);
        request.setEmpresaIdentidadLegalVersionId(7L);
        request.setEmpresaLogoDocumentalVersionId(11L);
        when(ordenCompraRepo.findById(101)).thenReturn(Optional.of(orden));
        when(empresaIdentidadLegalService.resolveVersion(7L)).thenReturn(identidad);
        when(empresaLogoDocumentalService.resolveVersion(11L)).thenReturn(logo);
        when(ordenCompraRepo.save(any(OrdenCompraMateriales.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrdenCompraMateriales updated = service.updateEstadoOrdenCompra(
                101,
                request,
                user(30L, "usuario.envio")
        );

        assertEquals(identidad, updated.getEmpresaIdentidadLegalVersion());
        assertEquals(logo, updated.getEmpresaLogoDocumentalVersion());
        verify(empresaIdentidadLegalService).resolveVersion(7L);
        verify(empresaLogoDocumentalService).resolveVersion(11L);
    }

    @Test
    void updateEstadoOrdenCompra_manualSendPreservesExistingDocumentVersions() {
        OrdenCompraMateriales orden = orden(1, null);
        EmpresaIdentidadLegalVersion identidadHistorica = identidadLegal(3L);
        EmpresaLogoDocumentalVersion logoHistorico = logoDocumental(4L);
        orden.setEmpresaIdentidadLegalVersion(identidadHistorica);
        orden.setEmpresaLogoDocumentalVersion(logoHistorico);
        when(ordenCompraRepo.findById(101)).thenReturn(Optional.of(orden));
        when(ordenCompraRepo.save(any(OrdenCompraMateriales.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        OrdenCompraMateriales updated = service.updateEstadoOrdenCompra(
                101,
                request(2, UpdateEstadoOrdenCompraRequest.TipoEnvio.MANUAL),
                user(30L, "usuario.envio")
        );

        assertSame(identidadHistorica, updated.getEmpresaIdentidadLegalVersion());
        assertSame(logoHistorico, updated.getEmpresaLogoDocumentalVersion());
        verify(empresaIdentidadLegalService, never()).resolveVersion(any());
        verify(empresaLogoDocumentalService, never()).resolveVersion(any());
    }

    @Test
    void updateEstadoOrdenCompra_emailFailureDoesNotSetFechaEnvioProveedor() {
        OrdenCompraMateriales orden = orden(1, null);
        when(ordenCompraRepo.findById(101)).thenReturn(Optional.of(orden));

        assertThrows(RuntimeException.class,
                () -> service.updateEstadoOrdenCompra(
                        101,
                        request(2, UpdateEstadoOrdenCompraRequest.TipoEnvio.EMAIL),
                        user(30L, "usuario.envio")
                ));

        assertNull(orden.getFechaEnvioProveedor());
        assertNull(orden.getEmpresaIdentidadLegalVersion());
        assertEquals(1, orden.getEstado());
        verify(empresaIdentidadLegalService, never()).resolveVersion(any());
        verify(empresaLogoDocumentalService, never()).resolveVersion(any());
        verify(ordenCompraRepo, never()).save(any());
    }

    @Test
    void updateEstadoOrdenCompra_whatsappFailureDoesNotSetFechaEnvioProveedor() {
        OrdenCompraMateriales orden = orden(1, null);
        when(ordenCompraRepo.findById(101)).thenReturn(Optional.of(orden));

        assertThrows(UnsupportedOperationException.class,
                () -> service.updateEstadoOrdenCompra(
                        101,
                        request(2, UpdateEstadoOrdenCompraRequest.TipoEnvio.WHATSAPP),
                        user(30L, "usuario.envio")
                ));

        assertNull(orden.getFechaEnvioProveedor());
        assertNull(orden.getEmpresaIdentidadLegalVersion());
        assertEquals(1, orden.getEstado());
        verify(empresaIdentidadLegalService, never()).resolveVersion(any());
        verify(empresaLogoDocumentalService, never()).resolveVersion(any());
        verify(ordenCompraRepo, never()).save(any());
    }

    private static User user(Long id, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        return user;
    }

    private static OrdenCompraMateriales orden(int estado, LocalDateTime fechaEnvioProveedor) {
        Proveedor proveedor = new Proveedor();
        proveedor.setId("PROV-1");
        proveedor.setNombre("Proveedor Uno");

        OrdenCompraMateriales orden = new OrdenCompraMateriales();
        orden.setOrdenCompraId(101);
        orden.setEstado(estado);
        orden.setProveedor(proveedor);
        orden.setFechaEnvioProveedor(fechaEnvioProveedor);
        return orden;
    }

    private static EmpresaIdentidadLegalVersion identidadLegal(Long id) {
        EmpresaIdentidadLegalVersion identidad = new EmpresaIdentidadLegalVersion();
        identidad.setId(id);
        identidad.setEstado(EmpresaIdentidadLegalVersion.Estado.VIGENTE);
        identidad.setVersion(1);
        identidad.setRazonSocial("Napolitana J.P S.A.S.");
        identidad.setNombreComercial("EXOTIC EXPERT");
        identidad.setTipoIdentificacion("NIT");
        identidad.setNumeroIdentificacion("901751897");
        identidad.setDigitoVerificacion("1");
        identidad.setTelefonoPrincipal("301 711 51 81");
        identidad.setEmailPrincipal("produccion.exotic@gmail.com");
        return identidad;
    }

    private static EmpresaLogoDocumentalVersion logoDocumental(Long id) {
        EmpresaLogoDocumentalVersion logo = new EmpresaLogoDocumentalVersion();
        logo.setId(id);
        logo.setVersion(1);
        logo.setEstado(EmpresaLogoDocumentalVersion.Estado.VIGENTE);
        logo.setContentType("image/png");
        logo.setSha256("abc");
        return logo;
    }

    private static UpdateEstadoOrdenCompraRequest request(
            int newEstado,
            UpdateEstadoOrdenCompraRequest.TipoEnvio tipoEnvio
    ) {
        UpdateEstadoOrdenCompraRequest request = new UpdateEstadoOrdenCompraRequest();
        request.setNewEstado(newEstado);
        request.setTipoEnvio(tipoEnvio);
        return request;
    }
}
