package exotic.app.planta.service.bi.inventario;

import exotic.app.planta.model.inventarios.Movimiento;
import exotic.app.planta.model.inventarios.TransaccionAlmacen;
import exotic.app.planta.repo.compras.ItemOrdenCompraRepo;
import exotic.app.planta.repo.inventarios.TransaccionAlmacenRepo;
import exotic.app.planta.repo.produccion.OrdenProduccionRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PendientesInventarioAssemblerTest {
    private ItemOrdenCompraRepo purchaseItemRepo;
    private TransaccionAlmacenRepo movementRepo;
    private PendientesInventarioAssembler assembler;

    @BeforeEach
    void setUp() {
        purchaseItemRepo = mock(ItemOrdenCompraRepo.class);
        movementRepo = mock(TransaccionAlmacenRepo.class);
        assembler = new PendientesInventarioAssembler(
                purchaseItemRepo,
                mock(OrdenProduccionRepo.class),
                movementRepo);
    }

    @Test
    void calculatesSummaryAndPagedDetailWithoutHydratingEntities() {
        var item = purchaseItem(10, 100);
        var receipt = receipt(4);
        when(purchaseItemRepo.findPendingRowsForBi(2)).thenReturn(List.of(item));
        when(movementRepo.findReceiptTotalsByCauseAndEntities(
                eq(Movimiento.Almacen.GENERAL),
                eq(Movimiento.TipoMovimiento.COMPRA),
                eq(TransaccionAlmacen.TipoEntidadCausante.OCM),
                anyCollection()))
                .thenReturn(List.of(receipt));

        var summary = assembler.buildPendingPurchaseOrders();

        assertEquals(1, summary.ordenes());
        assertEquals(6, summary.cantidadesPorUnidad().get(0).cantidad(), 0.000001);
        assertEquals(600, summary.valorPendienteSinIva(), 0.000001);

        var pageable = PageRequest.of(0, 10);
        when(purchaseItemRepo.findPendingOrderIdsForBi(
                eq(2),
                eq(Movimiento.Almacen.GENERAL),
                eq(Movimiento.TipoMovimiento.COMPRA),
                eq(TransaccionAlmacen.TipoEntidadCausante.OCM),
                eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(123), pageable, 1));
        when(purchaseItemRepo.findPendingRowsForBiByOrderIds(eq(2), anyCollection()))
                .thenReturn(List.of(item));

        var detail = assembler.getPendingPurchaseOrdersPage(0, 10);

        assertEquals(1, detail.totalElements());
        assertEquals(4, detail.items().get(0).lineas().get(0).recibidoAplicado(), 0.000001);
    }

    @Test
    void omitsAnOrderWhenItsLinesAreCompletelyReceived() {
        when(purchaseItemRepo.findPendingRowsForBi(2))
                .thenReturn(List.of(purchaseItem(10, 100)));
        when(movementRepo.findReceiptTotalsByCauseAndEntities(
                eq(Movimiento.Almacen.GENERAL),
                eq(Movimiento.TipoMovimiento.COMPRA),
                eq(TransaccionAlmacen.TipoEntidadCausante.OCM),
                anyCollection()))
                .thenReturn(List.of(receipt(12)));

        var report = assembler.buildPendingPurchaseOrders();

        assertEquals(0, report.ordenes());
        assertEquals(0, report.valorPendienteSinIva(), 0.000001);
    }

    private ItemOrdenCompraRepo.PendingPurchaseItemProjection purchaseItem(
            double quantity,
            double unitPrice
    ) {
        var item = mock(ItemOrdenCompraRepo.PendingPurchaseItemProjection.class);
        when(item.getItemId()).thenReturn(1);
        when(item.getOcmId()).thenReturn(123);
        when(item.getFechaEmision()).thenReturn(LocalDateTime.of(2026, 7, 1, 8, 0));
        when(item.getProveedor()).thenReturn("Proveedor");
        when(item.getProductoId()).thenReturn("MP-1");
        when(item.getProductoNombre()).thenReturn("Aceite");
        when(item.getUnidadMedida()).thenReturn("KG");
        when(item.getCantidad()).thenReturn(quantity);
        when(item.getPrecioUnitario()).thenReturn(unitPrice);
        return item;
    }

    private TransaccionAlmacenRepo.EntityProductQuantityProjection receipt(double quantity) {
        var receipt = mock(TransaccionAlmacenRepo.EntityProductQuantityProjection.class);
        when(receipt.getEntityId()).thenReturn(123);
        when(receipt.getProductId()).thenReturn("MP-1");
        when(receipt.getQuantity()).thenReturn(quantity);
        return receipt;
    }
}
