package exotic.app.planta.service.compras;

import exotic.app.planta.model.compras.ItemOrdenCompra;
import exotic.app.planta.model.compras.OrdenCompraMateriales;
import exotic.app.planta.model.inventarios.Movimiento;
import exotic.app.planta.model.inventarios.TransaccionAlmacen;
import exotic.app.planta.model.producto.Material;
import exotic.app.planta.repo.inventarios.TransaccionAlmacenRepo;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RecepcionCompletaOcmServiceTest {
    @Test
    void agrupaItemsRepetidosYNoCompensaUnFaltanteConOtroMaterial() {
        var items = List.of(item("A", 3), item("A", 2), item("B", 2));
        assertFalse(RecepcionCompletaOcmService.cubreTodosLosMateriales(items,
                Map.of("A", new BigDecimal("20"), "B", BigDecimal.ONE)));
        assertTrue(RecepcionCompletaOcmService.cubreTodosLosMateriales(items,
                Map.of("A", new BigDecimal("5"), "B", new BigDecimal("2"))));
    }

    @Test
    void sumaRecepcionesDecimalesSinAcumularErrorBinario() {
        var repo = mock(TransaccionAlmacenRepo.class);
        var orden = new OrdenCompraMateriales();
        orden.setOrdenCompraId(1);
        orden.setItemsOrdenCompra(List.of(item("A", 1)));
        // In binary floating point 0.1 + 0.7 + 0.2 is slightly less than 1.
        when(repo.findReceiptQuantities(TransaccionAlmacen.TipoEntidadCausante.OCM,
                Movimiento.TipoMovimiento.COMPRA, Movimiento.Almacen.GENERAL, List.of(1)))
                .thenReturn(List.of(recibido(0.1), recibido(0.7), recibido(0.2)));
        assertTrue(new RecepcionCompletaOcmService(repo).estaCompleta(orden));
    }

    @Test
    void ordenVaciaOCantidadesInvalidasNoSeConsideranCompletas() {
        assertFalse(RecepcionCompletaOcmService.cubreTodosLosMateriales(List.of(), Map.of()));
        assertFalse(RecepcionCompletaOcmService.cubreTodosLosMateriales(List.of(item("A", 0)), Map.of()));
        assertFalse(RecepcionCompletaOcmService.cubreTodosLosMateriales(List.of(item("A", -1)), Map.of()));
    }

    private ItemOrdenCompra item(String id, int cantidad) {
        Material material = new Material();
        material.setProductoId(id);
        ItemOrdenCompra item = new ItemOrdenCompra();
        item.setMaterial(material);
        item.setCantidad(cantidad);
        return item;
    }

    private TransaccionAlmacenRepo.EntityProductQuantityProjection recibido(double cantidad) {
        return new TransaccionAlmacenRepo.EntityProductQuantityProjection() {
            public int getEntityId() { return 1; }
            public String getProductId() { return "A"; }
            public double getQuantity() { return cantidad; }
        };
    }
}
