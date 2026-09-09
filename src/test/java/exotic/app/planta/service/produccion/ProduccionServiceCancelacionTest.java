package exotic.app.planta.service.produccion;

import exotic.app.planta.model.producto.Producto;
import exotic.app.planta.model.produccion.OrdenProduccion;
import exotic.app.planta.model.produccion.dto.OrdenProduccionDTO;
import exotic.app.planta.model.users.User;
import exotic.app.planta.repo.inventarios.LoteRepo;
import exotic.app.planta.repo.inventarios.TransaccionAlmacenHeaderRepo;
import exotic.app.planta.repo.inventarios.TransaccionAlmacenRepo;
import exotic.app.planta.repo.producto.ProductoRepo;
import exotic.app.planta.repo.producto.TerminadoRepo;
import exotic.app.planta.repo.producto.manufacturing.snapshots.ManufacturingVersionRepo;
import exotic.app.planta.repo.produccion.OrdenProduccionRepo;
import exotic.app.planta.repo.usuarios.UserRepository;
import exotic.app.planta.repo.ventas.VendedorRepository;
import exotic.app.planta.service.contabilidad.ContabilidadService;
import exotic.app.planta.service.master.configs.MasterDirectiveService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProduccionServiceCancelacionTest {

    @Mock private OrdenProduccionRepo ordenProduccionRepo;
    @Mock private TerminadoRepo terminadoRepo;
    @Mock private TransaccionAlmacenRepo transaccionAlmacenRepo;
    @Mock private TransaccionAlmacenHeaderRepo transaccionAlmacenHeaderRepo;
    @Mock private ContabilidadService contabilidadService;
    @Mock private ProductoRepo productoRepo;
    @Mock private LoteRepo loteRepo;
    @Mock private VendedorRepository vendedorRepository;
    @Mock private SeguimientoOrdenAreaService seguimientoOrdenAreaService;
    @Mock private MasterDirectiveService masterDirectiveService;
    @Mock private VencimientoLoteService vencimientoLoteService;
    @Mock private ManufacturingVersionRepo manufacturingVersionRepo;
    @Mock private UserRepository userRepository;
    @Mock private BatchRecordService batchRecordService;
    @Mock private OrdenFabricacionAutoGenerationService ordenFabricacionAutoGenerationService;
    @Mock private OrdenFabricacionService ordenFabricacionService;
    @Mock private Clock applicationClock;

    @InjectMocks private ProduccionService service;

    @Test
    void cancelarRegistraActorFechaYSnapshotsEnLaMismaOrden() {
        LocalDateTime esperado = fixedNow();
        OrdenProduccion orden = openOrder(41);
        User actor = User.builder()
                .id(9L)
                .username("  aprobador.mps  ")
                .nombreCompleto("  Ana Pérez  ")
                .build();
        when(ordenProduccionRepo.findByIdForUpdate(41)).thenReturn(Optional.of(orden));
        when(ordenProduccionRepo.save(any(OrdenProduccion.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        OrdenProduccionDTO result = service.cancelarOrdenProduccion(41, actor);

        assertEquals(-1, orden.getEstadoOrden());
        assertEquals(esperado, orden.getCanceladaEn());
        assertEquals(esperado, orden.getFechaFinal());
        assertSame(actor, orden.getCanceladaPor());
        assertEquals("aprobador.mps", orden.getCanceladaPorUsername());
        assertEquals("Ana Pérez", orden.getCanceladaPorNombreCompleto());
        assertEquals(esperado, result.getCanceladaEn());
        assertEquals("aprobador.mps", result.getCanceladaPorUsername());
        assertEquals("Ana Pérez", result.getCanceladaPorNombreCompleto());
        verify(batchRecordService).anularPorCancelacion(orden, actor);
        verify(ordenFabricacionService).cancelarVinculadasPorCancelacionOp(orden, actor);
    }

    @Test
    void cancelarUsaUsernameCuandoNoExisteNombreCompleto() {
        fixedNow();
        OrdenProduccion orden = openOrder(42);
        User actor = User.builder().id(10L).username("planeador").nombreCompleto("  ").build();
        when(ordenProduccionRepo.findByIdForUpdate(42)).thenReturn(Optional.of(orden));
        when(ordenProduccionRepo.save(any(OrdenProduccion.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.cancelarOrdenProduccion(42, actor);

        assertEquals("planeador", orden.getCanceladaPorNombreCompleto());
    }

    @Test
    void unaCancelacionRepetidaNoSobrescribeLaAuditoria() {
        OrdenProduccion orden = openOrder(43);
        orden.setEstadoOrden(-1);
        orden.setCanceladaPorUsername("primer.usuario");
        when(ordenProduccionRepo.findByIdForUpdate(43)).thenReturn(Optional.of(orden));

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> service.cancelarOrdenProduccion(
                        43, User.builder().username("segundo.usuario").build()));

        assertTrue(error.getMessage().contains("estado abierto"));
        assertEquals("primer.usuario", orden.getCanceladaPorUsername());
        verify(ordenProduccionRepo, never()).save(any());
        verify(batchRecordService, never()).anularPorCancelacion(
                any(OrdenProduccion.class), any(User.class));
        verify(ordenFabricacionService, never()).cancelarVinculadasPorCancelacionOp(any(), any());
    }

    @Test
    void updateEstadoNoPuedeReabrirUnaOrdenCancelada() {
        OrdenProduccion orden = openOrder(44);
        orden.setEstadoOrden(-1);
        when(ordenProduccionRepo.findByIdForUpdate(44)).thenReturn(Optional.of(orden));

        assertThrows(IllegalStateException.class, () -> service.updateEstadoOrdenProduccion(44, 0));

        assertEquals(-1, orden.getEstadoOrden());
        verify(ordenProduccionRepo, never()).save(any());
    }

    private LocalDateTime fixedNow() {
        Instant instant = Instant.parse("2026-09-09T15:30:00Z");
        ZoneId zone = ZoneId.of("America/Bogota");
        when(applicationClock.instant()).thenReturn(instant);
        when(applicationClock.getZone()).thenReturn(zone);
        return LocalDateTime.ofInstant(instant, zone);
    }

    private OrdenProduccion openOrder(int id) {
        Producto producto = mock(Producto.class);
        OrdenProduccion orden = new OrdenProduccion(producto, "", 10);
        orden.setOrdenId(id);
        return orden;
    }
}
