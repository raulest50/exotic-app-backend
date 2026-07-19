package exotic.app.planta.service.bi.inventario;

import exotic.app.planta.model.inventarios.Movimiento;
import exotic.app.planta.model.inventarios.TransaccionAlmacen;
import exotic.app.planta.model.producto.Material;
import exotic.app.planta.model.producto.Producto;
import exotic.app.planta.model.producto.Terminado;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MovimientosInventarioAssemblerTest {
    private final MovimientosInventarioAssembler assembler =
            new MovimientosInventarioAssembler();

    @Test
    void classifiesFlowsWithoutMixingUnitsAndValuesThemWithCurrentCost() {
        Material material = product(new Material(), "MP-1", "KG", "1000");
        Terminado finishedProduct = product(new Terminado(), "PT-1", "U", "500");
        LocalDateTime date = LocalDateTime.of(2026, 7, 18, 8, 0);

        Movimiento receipt = movement(
                10,
                date,
                material,
                Movimiento.TipoMovimiento.COMPRA,
                TransaccionAlmacen.TipoEntidadCausante.OCM);
        Movimiento dispensation = movement(
                -4,
                date.plusHours(1),
                material,
                Movimiento.TipoMovimiento.DISPENSACION,
                TransaccionAlmacen.TipoEntidadCausante.OD);
        Movimiento finishedReceipt = movement(
                6,
                date.plusHours(2),
                finishedProduct,
                Movimiento.TipoMovimiento.BACKFLUSH,
                TransaccionAlmacen.TipoEntidadCausante.OP);

        var report = assembler.assemble(
                List.of(receipt, dispensation, finishedReceipt),
                List.of(receipt, dispensation, finishedReceipt),
                date.toLocalDate().minusDays(1),
                date.toLocalDate());

        assertEquals(10_000, report.resumen().recepcionesOcm().valorEstimado(), 0.000001);
        assertEquals(4_000, report.resumen().dispensaciones().valorEstimado(), 0.000001);
        assertEquals(3_000, report.resumen().productoTerminado().valorEstimado(), 0.000001);
        assertEquals(2, report.porUnidad().size());
        assertEquals(4, report.serieDiaria().size());
        assertTrue(report.serieDiaria().stream()
                .filter(point -> point.fecha().equals(date.toLocalDate().minusDays(1)))
                .allMatch(point -> point.recepcionesOcm() == 0
                        && point.dispensaciones() == 0
                        && point.productoTerminado() == 0));
    }

    private <T extends Producto> T product(
            T product,
            String id,
            String unit,
            String cost
    ) {
        product.setProductoId(id);
        product.setNombre(id);
        product.setTipoUnidades(unit);
        product.asignarCostoInicial(new BigDecimal(cost));
        return product;
    }

    private Movimiento movement(
            double quantity,
            LocalDateTime date,
            Producto product,
            Movimiento.TipoMovimiento movementType,
            TransaccionAlmacen.TipoEntidadCausante cause
    ) {
        TransaccionAlmacen transaction = new TransaccionAlmacen();
        transaction.setTipoEntidadCausante(cause);

        Movimiento movement = new Movimiento();
        movement.setCantidad(quantity);
        movement.setFechaMovimiento(date);
        movement.setProducto(product);
        movement.setTipoMovimiento(movementType);
        movement.setTransaccionAlmacen(transaction);
        movement.setAlmacen(Movimiento.Almacen.GENERAL);
        return movement;
    }
}
