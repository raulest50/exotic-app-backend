package exotic.app.planta.service.compras;

import exotic.app.planta.model.compras.OrdenCompraMateriales;
import exotic.app.planta.model.compras.Proveedor;
import exotic.app.planta.model.compras.dto.UpdateEstadoOrdenCompraRequest;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ComprasServiceTest {

    private OrdenCompraRepo ordenCompraRepo;
    private ComprasService service;

    @BeforeEach
    void setUp() {
        ordenCompraRepo = mock(OrdenCompraRepo.class);
        service = new ComprasService(
                mock(FacturaCompraRepo.class),
                mock(TransaccionAlmacenRepo.class),
                mock(ProveedorRepo.class),
                mock(MaterialRepo.class),
                mock(SemiTerminadoRepo.class),
                mock(TerminadoRepo.class),
                ordenCompraRepo,
                mock(EmailService.class),
                mock(UserRepository.class),
                mock(TransaccionAlmacenHeaderRepo.class)
        );
    }

    @Test
    void updateEstadoOrdenCompra_manualSendFromStateOneToTwo_setsFechaEnvioProveedor() {
        OrdenCompraMateriales orden = orden(1, null);
        when(ordenCompraRepo.findById(101)).thenReturn(Optional.of(orden));
        when(ordenCompraRepo.save(any(OrdenCompraMateriales.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrdenCompraMateriales updated = service.updateEstadoOrdenCompra(101, request(2, UpdateEstadoOrdenCompraRequest.TipoEnvio.MANUAL));

        assertEquals(2, updated.getEstado());
        assertNotNull(updated.getFechaEnvioProveedor());
        verify(ordenCompraRepo).save(orden);
    }

    @Test
    void updateEstadoOrdenCompra_manualSendDoesNotOverwriteExistingFechaEnvioProveedor() {
        LocalDateTime existing = LocalDateTime.of(2026, 1, 2, 9, 30);
        OrdenCompraMateriales orden = orden(1, existing);
        when(ordenCompraRepo.findById(101)).thenReturn(Optional.of(orden));
        when(ordenCompraRepo.save(any(OrdenCompraMateriales.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrdenCompraMateriales updated = service.updateEstadoOrdenCompra(101, request(2, UpdateEstadoOrdenCompraRequest.TipoEnvio.MANUAL));

        assertEquals(existing, updated.getFechaEnvioProveedor());
        assertEquals(2, updated.getEstado());
    }

    @Test
    void updateEstadoOrdenCompra_emailFailureDoesNotSetFechaEnvioProveedor() {
        OrdenCompraMateriales orden = orden(1, null);
        when(ordenCompraRepo.findById(101)).thenReturn(Optional.of(orden));

        assertThrows(RuntimeException.class,
                () -> service.updateEstadoOrdenCompra(101, request(2, UpdateEstadoOrdenCompraRequest.TipoEnvio.EMAIL)));

        assertNull(orden.getFechaEnvioProveedor());
        assertEquals(1, orden.getEstado());
        verify(ordenCompraRepo, never()).save(any());
    }

    @Test
    void updateEstadoOrdenCompra_whatsappFailureDoesNotSetFechaEnvioProveedor() {
        OrdenCompraMateriales orden = orden(1, null);
        when(ordenCompraRepo.findById(101)).thenReturn(Optional.of(orden));

        assertThrows(UnsupportedOperationException.class,
                () -> service.updateEstadoOrdenCompra(101, request(2, UpdateEstadoOrdenCompraRequest.TipoEnvio.WHATSAPP)));

        assertNull(orden.getFechaEnvioProveedor());
        assertEquals(1, orden.getEstado());
        verify(ordenCompraRepo, never()).save(any());
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
