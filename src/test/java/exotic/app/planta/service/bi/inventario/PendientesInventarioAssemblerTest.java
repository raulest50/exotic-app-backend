package exotic.app.planta.service.bi.inventario;

import exotic.app.planta.model.compras.ItemOrdenCompra;
import exotic.app.planta.model.compras.OrdenCompraMateriales;
import exotic.app.planta.model.compras.Proveedor;
import exotic.app.planta.model.inventarios.Movimiento;
import exotic.app.planta.model.inventarios.TransaccionAlmacen;
import exotic.app.planta.model.producto.Material;
import exotic.app.planta.repo.compras.ItemOrdenCompraRepo;
import exotic.app.planta.repo.inventarios.TransaccionAlmacenRepo;
import exotic.app.planta.repo.produccion.OrdenProduccionRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
    void calculatesPendingQuantityAndValueWithoutIva() {
        ItemOrdenCompra item = purchaseItem(10, 100);
        when(purchaseItemRepo.findAllByOrdenEstadoForBi(2)).thenReturn(List.of(item));
        when(movementRepo.findRecepcionesPorCausanteYEntidades(
                eq(Movimiento.Almacen.GENERAL),
                eq(Movimiento.TipoMovimiento.COMPRA),
                eq(TransaccionAlmacen.TipoEntidadCausante.OCM),
                anyCollection()))
                .thenReturn(List.of(receipt(item.getMaterial(), 4)));

        var report = assembler.buildPendingPurchaseOrders();

        assertEquals(1, report.ordenes());
        assertEquals(6, report.cantidadesPorUnidad().get(0).cantidad(), 0.000001);
        assertEquals(600, report.valorPendienteSinIva(), 0.000001);
        assertEquals(4, report.items().get(0).lineas().get(0).recibidoAplicado(), 0.000001);
    }

    @Test
    void omitsAnOrderWhenItsLinesAreCompletelyReceived() {
        ItemOrdenCompra item = purchaseItem(10, 100);
        when(purchaseItemRepo.findAllByOrdenEstadoForBi(2)).thenReturn(List.of(item));
        when(movementRepo.findRecepcionesPorCausanteYEntidades(
                eq(Movimiento.Almacen.GENERAL),
                eq(Movimiento.TipoMovimiento.COMPRA),
                eq(TransaccionAlmacen.TipoEntidadCausante.OCM),
                anyCollection()))
                .thenReturn(List.of(receipt(item.getMaterial(), 12)));

        var report = assembler.buildPendingPurchaseOrders();

        assertEquals(0, report.ordenes());
        assertEquals(0, report.valorPendienteSinIva(), 0.000001);
    }

    private ItemOrdenCompra purchaseItem(int quantity, int unitPrice) {
        Material material = new Material();
        material.setProductoId("MP-1");
        material.setNombre("Aceite");
        material.setTipoUnidades("KG");

        Proveedor supplier = new Proveedor();
        supplier.setNombre("Proveedor");

        OrdenCompraMateriales order = new OrdenCompraMateriales();
        order.setOrdenCompraId(123);
        order.setFechaEmision(LocalDateTime.of(2026, 7, 1, 8, 0));
        order.setProveedor(supplier);

        ItemOrdenCompra item = new ItemOrdenCompra();
        item.setItemOrdenId(1);
        item.setOrdenCompraMateriales(order);
        item.setMaterial(material);
        item.setCantidad(quantity);
        item.setPrecioUnitario(unitPrice);
        return item;
    }

    private Movimiento receipt(Material material, double quantity) {
        TransaccionAlmacen transaction = new TransaccionAlmacen();
        transaction.setTipoEntidadCausante(TransaccionAlmacen.TipoEntidadCausante.OCM);
        transaction.setIdEntidadCausante(123);

        Movimiento movement = new Movimiento();
        movement.setProducto(material);
        movement.setCantidad(quantity);
        movement.setTipoMovimiento(Movimiento.TipoMovimiento.COMPRA);
        movement.setAlmacen(Movimiento.Almacen.GENERAL);
        movement.setTransaccionAlmacen(transaction);
        return movement;
    }
}
