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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PendientesInventarioAssemblerTest {
    private ItemOrdenCompraRepo purchaseItemRepo;
    private TransaccionAlmacenRepo movementRepo;
    private OrdenProduccionRepo productionOrderRepo;
    private PendientesInventarioAssembler assembler;

    @BeforeEach
    void setUp() {
        purchaseItemRepo = mock(ItemOrdenCompraRepo.class);
        movementRepo = mock(TransaccionAlmacenRepo.class);
        productionOrderRepo = mock(OrdenProduccionRepo.class);
        assembler = new PendientesInventarioAssembler(
                purchaseItemRepo,
                productionOrderRepo,
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

        var allOrders = assembler.getAllPendingPurchaseOrders();
        assertEquals(
                summary.valorPendienteSinIva(),
                allOrders.stream()
                        .mapToDouble(order -> order.valorPendienteSinIva())
                        .sum(),
                0.000001);

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

    @Test
    void returnsEveryPendingLineAndAppliesSharedMaterialReceiptsInItemOrder() {
        var firstItem = purchaseItem(1, 10, 100);
        var secondItem = purchaseItem(2, 10, 100);
        when(purchaseItemRepo.findPendingRowsForBi(2))
                .thenReturn(List.of(firstItem, secondItem));
        when(movementRepo.findReceiptTotalsByCauseAndEntities(
                eq(Movimiento.Almacen.GENERAL),
                eq(Movimiento.TipoMovimiento.COMPRA),
                eq(TransaccionAlmacen.TipoEntidadCausante.OCM),
                anyCollection()))
                .thenReturn(List.of(receipt(12)));

        var orders = assembler.getAllPendingPurchaseOrders();

        assertEquals(1, orders.size());
        assertEquals(1, orders.get(0).lineas().size());
        var pendingLine = orders.get(0).lineas().get(0);
        assertEquals(2, pendingLine.itemId());
        assertEquals(2, pendingLine.recibidoAplicado(), 0.000001);
        assertEquals(8, pendingLine.pendiente(), 0.000001);
        assertEquals(800, pendingLine.valorPendienteSinIva(), 0.000001);
        verify(purchaseItemRepo).findPendingRowsForBi(2);
        verify(movementRepo).findReceiptTotalsByCauseAndEntities(
                eq(Movimiento.Almacen.GENERAL),
                eq(Movimiento.TipoMovimiento.COMPRA),
                eq(TransaccionAlmacen.TipoEntidadCausante.OCM),
                anyCollection());
    }

    @Test
    void wipIncludesDispensationsReplacementsAndDirectConsumption() {
        var normal = materialRow(
                "MP-1",
                Movimiento.TipoMovimiento.DISPENSACION,
                TransaccionAlmacen.TipoEntidadCausante.OD,
                10,
                100,
                LocalDateTime.of(2026, 7, 1, 8, 0));
        var replacement = materialRow(
                "MP-1",
                Movimiento.TipoMovimiento.DISPENSACION,
                TransaccionAlmacen.TipoEntidadCausante.OD_RA,
                2,
                100,
                LocalDateTime.of(2026, 7, 2, 9, 0));
        var direct = materialRow(
                "MP-2",
                Movimiento.TipoMovimiento.CONSUMO,
                TransaccionAlmacen.TipoEntidadCausante.OD,
                3,
                50,
                LocalDateTime.of(2026, 7, 1, 7, 30));
        when(movementRepo.findOpenWipMaterialDetails(
                eq(Movimiento.Almacen.GENERAL),
                eq(Movimiento.TipoMovimiento.DISPENSACION),
                eq(Movimiento.TipoMovimiento.CONSUMO),
                anyCollection(),
                eq(TransaccionAlmacen.TipoEntidadCausante.OD)))
                .thenReturn(List.of(normal, replacement, direct));

        var summary = assembler.buildWipMaterialEstimate();

        assertEquals(1, summary.ordenes());
        assertEquals(2, summary.referencias());
        assertEquals(
                15,
                summary.cantidadesPorUnidad().get(0).cantidad(),
                0.000001);
        assertEquals(1_350, summary.valorEstimado(), 0.000001);
    }

    @Test
    void wipDetailUsesTheFirstFormalMaterialMovementAsItsStart() {
        var pageable = PageRequest.of(0, 10);
        when(productionOrderRepo.findOpenOrderIdsWithWipMaterialForBi(
                eq(Movimiento.Almacen.GENERAL),
                eq(Movimiento.TipoMovimiento.DISPENSACION),
                eq(Movimiento.TipoMovimiento.CONSUMO),
                anyCollection(),
                eq(TransaccionAlmacen.TipoEntidadCausante.OD),
                eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(321), pageable, 1));
        var later = materialRow(
                "MP-1",
                Movimiento.TipoMovimiento.DISPENSACION,
                TransaccionAlmacen.TipoEntidadCausante.OD,
                5,
                10,
                LocalDateTime.of(2026, 7, 3, 10, 0));
        var earlier = materialRow(
                "MP-2",
                Movimiento.TipoMovimiento.CONSUMO,
                TransaccionAlmacen.TipoEntidadCausante.OD,
                1,
                20,
                LocalDateTime.of(2026, 7, 2, 6, 30));
        when(movementRepo.findOpenWipMaterialDetailsByOrderIds(
                eq(Movimiento.Almacen.GENERAL),
                eq(Movimiento.TipoMovimiento.DISPENSACION),
                eq(Movimiento.TipoMovimiento.CONSUMO),
                anyCollection(),
                eq(TransaccionAlmacen.TipoEntidadCausante.OD),
                anyCollection()))
                .thenReturn(List.of(later, earlier));

        var page = assembler.getWipMaterialEstimatePage(0, 10);

        assertEquals(1, page.totalElements());
        assertEquals(
                LocalDateTime.of(2026, 7, 2, 6, 30),
                page.items().get(0).fechaInicioWip());
    }

    private ItemOrdenCompraRepo.PendingPurchaseItemProjection purchaseItem(
            double quantity,
            double unitPrice
    ) {
        return purchaseItem(1, quantity, unitPrice);
    }

    private ItemOrdenCompraRepo.PendingPurchaseItemProjection purchaseItem(
            int itemId,
            double quantity,
            double unitPrice
    ) {
        var item = mock(ItemOrdenCompraRepo.PendingPurchaseItemProjection.class);
        when(item.getItemId()).thenReturn(itemId);
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

    private TransaccionAlmacenRepo.OpenProductionMaterialDetailProjection
    materialRow(
            String productId,
            Movimiento.TipoMovimiento movementType,
            TransaccionAlmacen.TipoEntidadCausante cause,
            double quantity,
            double cost,
            LocalDateTime movementDate
    ) {
        var row = mock(
                TransaccionAlmacenRepo.OpenProductionMaterialDetailProjection.class);
        when(row.getOpId()).thenReturn(321);
        when(row.getLote()).thenReturn("L-321");
        when(row.getEstado()).thenReturn(11);
        when(row.getFechaReferencia())
                .thenReturn(LocalDateTime.of(2026, 7, 1, 7, 0));
        when(row.getFechaPrimerMovimiento()).thenReturn(movementDate);
        when(row.getProductoId()).thenReturn(productId);
        when(row.getProductoNombre()).thenReturn("Material " + productId);
        when(row.getUnidadMedida()).thenReturn("KG");
        when(row.getCosto()).thenReturn(BigDecimal.valueOf(cost));
        when(row.getCausa()).thenReturn(cause);
        when(row.getTipoMovimiento()).thenReturn(movementType);
        when(row.getCantidad()).thenReturn(quantity);
        return row;
    }
}
