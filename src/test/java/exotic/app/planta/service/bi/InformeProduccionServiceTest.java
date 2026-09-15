package exotic.app.planta.service.bi;

import exotic.app.planta.model.bi.dto.PaginaDesviacionesProduccionDTO;
import exotic.app.planta.model.inventarios.Movimiento;
import exotic.app.planta.model.producto.Terminado;
import exotic.app.planta.model.produccion.MasterProductionScheduleSemanal;
import exotic.app.planta.model.produccion.MpsSemanalDia;
import exotic.app.planta.model.produccion.MpsSemanalItem;
import exotic.app.planta.repo.inventarios.TransaccionAlmacenRepo;
import exotic.app.planta.repo.producto.CategoriaRepo;
import exotic.app.planta.repo.produccion.MasterProductionScheduleSemanalRepo;
import exotic.app.planta.repo.produccion.MpsSemanalDiaRepo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InformeProduccionServiceTest {
    private static final LocalDate DATE = LocalDate.of(2026, 9, 15);

    @Mock private TransaccionAlmacenRepo movementRepo;
    @Mock private MasterProductionScheduleSemanalRepo mpsRepo;
    @Mock private MpsSemanalDiaRepo mpsDayRepo;
    @Mock private CategoriaRepo categoryRepo;

    @InjectMocks private InformeProduccionService service;

    @Test
    void paginaDesviacionesOrdenadasPorImpacto() {
        when(mpsRepo.findAllOverlappingRange(DATE, DATE)).thenReturn(List.of());
        List<Movimiento> movements = List.of(
                movement("P-10", 10),
                movement("P-20", 20),
                movement("P-30", 30),
                movement("P-40", 40),
                movement("P-50", 50),
                movement("P-60", 60));
        when(movementRepo.findIngresosTerminadoPorFechaEfectiva(
                DATE,
                DATE,
                DATE.atStartOfDay(),
                DATE.atTime(LocalTime.MAX),
                Movimiento.TipoMovimiento.BACKFLUSH))
                .thenReturn(movements);

        PaginaDesviacionesProduccionDTO firstPage =
                service.obtenerDesviaciones(DATE, DATE, 0, 5);
        PaginaDesviacionesProduccionDTO lastPage =
                service.obtenerDesviaciones(DATE, DATE, 1, 5);

        assertEquals(6, firstPage.totalElements());
        assertEquals(2, firstPage.totalPages());
        assertEquals(5, firstPage.items().size());
        assertEquals("P-60", firstPage.items().getFirst().reference().getProductoId());
        assertEquals("P-20", firstPage.items().getLast().reference().getProductoId());
        assertEquals(6, firstPage.counts().noPlaneada());
        assertTrue(firstPage.first());
        assertFalse(firstPage.last());

        assertEquals(1, lastPage.items().size());
        assertEquals("P-10", lastPage.items().getFirst().reference().getProductoId());
        assertFalse(lastPage.first());
        assertTrue(lastPage.last());
    }

    @Test
    void clasificaLosCuatroTiposDeDesviacion() {
        MasterProductionScheduleSemanal mps = mock(MasterProductionScheduleSemanal.class);
        when(mps.getMpsId()).thenReturn(7);
        when(mps.getWeekStartDate()).thenReturn(DATE);
        when(mps.getWeekEndDate()).thenReturn(DATE);
        when(mpsRepo.findAllOverlappingRange(DATE, DATE)).thenReturn(List.of(mps));

        MpsSemanalDia day = mock(MpsSemanalDia.class);
        when(day.getMpsSemanal()).thenReturn(mps);
        when(day.getFecha()).thenReturn(DATE);
        List<MpsSemanalItem> plannedItems = List.of(
                plannedItem("SIN", 100),
                plannedItem("DEF", 100),
                plannedItem("SOBRE", 100));
        when(day.getItems()).thenReturn(plannedItems);
        when(mpsDayRepo.findAllByMpsIdsAndDateRange(List.of(7), DATE, DATE))
                .thenReturn(List.of(day));
        List<Movimiento> movements = List.of(
                movement("DEF", 40),
                movement("SOBRE", 150),
                movement("NUEVA", 25));
        when(movementRepo.findIngresosTerminadoPorFechaEfectiva(
                DATE,
                DATE,
                DATE.atStartOfDay(),
                DATE.atTime(LocalTime.MAX),
                Movimiento.TipoMovimiento.BACKFLUSH))
                .thenReturn(movements);

        PaginaDesviacionesProduccionDTO result =
                service.obtenerDesviaciones(DATE, DATE, 0, 5);

        assertEquals(4, result.totalElements());
        assertEquals(1, result.counts().sinProduccion());
        assertEquals(1, result.counts().deficit());
        assertEquals(1, result.counts().noPlaneada());
        assertEquals(1, result.counts().sobreproduccion());
    }

    @Test
    void rechazaPaginaOTamanoInvalidosAntesDeConsultarRepositorios() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.obtenerDesviaciones(DATE, DATE, -1, 5));
        assertThrows(
                IllegalArgumentException.class,
                () -> service.obtenerDesviaciones(DATE, DATE, 0, 25));

        verify(movementRepo, never()).findIngresosTerminadoPorFechaEfectiva(
                eq(DATE),
                eq(DATE),
                eq(LocalDateTime.of(DATE, LocalTime.MIN)),
                eq(LocalDateTime.of(DATE, LocalTime.MAX)),
                eq(Movimiento.TipoMovimiento.BACKFLUSH));
    }

    private static Movimiento movement(String productId, double quantity) {
        Movimiento movement = mock(Movimiento.class);
        Terminado product = product(productId);
        when(movement.getProducto()).thenReturn(product);
        when(movement.getCantidad()).thenReturn(quantity);
        return movement;
    }

    private static MpsSemanalItem plannedItem(String productId, double quantity) {
        MpsSemanalItem item = mock(MpsSemanalItem.class);
        Terminado product = product(productId);
        when(item.getTerminado()).thenReturn(product);
        when(item.getTerminadoNombre()).thenReturn("Producto " + productId);
        when(item.getCategoriaNombre()).thenReturn("Categoría");
        when(item.getCantidadTotal()).thenReturn(quantity);
        return item;
    }

    private static Terminado product(String productId) {
        Terminado product = mock(Terminado.class);
        when(product.getProductoId()).thenReturn(productId);
        when(product.getNombre()).thenReturn("Producto " + productId);
        return product;
    }
}
