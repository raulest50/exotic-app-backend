package exotic.app.planta.service.bi.inventario;

import exotic.app.planta.model.inventarios.Movimiento;
import exotic.app.planta.model.inventarios.TransaccionAlmacen;
import exotic.app.planta.model.producto.Material;
import exotic.app.planta.model.producto.Producto;
import exotic.app.planta.model.producto.Terminado;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class AjustesInventarioAssemblerTest {
    private final AjustesInventarioAssembler assembler =
            new AjustesInventarioAssembler();

    @Test
    void summarizesAdjustmentsByGroupWithoutMixingThemWithDemand() {
        LocalDate start = LocalDate.of(2026, 7, 20);
        LocalDate end = start.plusDays(1);
        Material raw = material("MP-1", 1, "KG", "100");
        Material rawWithoutCost = material("MP-2", 1, "KG", null);
        Material packaging = material("EMP-1", 2, "UND", "200");
        Terminado finished = product(new Terminado(), "PT-1", "UND", "300");

        List<Movimiento> movements = List.of(
                adjustment(1, 10, start.atTime(8, 0), raw),
                adjustment(2, -4, start.atTime(9, 0), raw),
                adjustment(3, 5, end.atTime(8, 0), packaging),
                adjustment(4, -2, end.atTime(9, 0), finished),
                adjustment(5, -50, end.atTime(10, 0), rawWithoutCost),
                movement(
                        6,
                        100,
                        end.atTime(11, 0),
                        raw,
                        Movimiento.TipoMovimiento.COMPRA));

        var report = assembler.assemble(movements, movements, start, end);

        assertEquals(2_000, report.resumen().positivos().valorEstimado(), 0.000001);
        assertEquals(1_000, report.resumen().negativos().valorEstimado(), 0.000001);
        assertEquals(1_000, report.resumen().balanceNeto(), 0.000001);
        assertEquals(5, report.resumen().transacciones());
        assertEquals(5, report.resumen().movimientos());
        assertEquals(4, report.resumen().referencias());

        assertEquals(
                1_400,
                report.comparativo().materiaPrima().positivos().valorEstimado()
                        + report.comparativo().materiaPrima().negativos().valorEstimado(),
                0.000001);
        assertEquals(
                1_000,
                report.comparativo().empaque().positivos().valorEstimado(),
                0.000001);
        assertEquals(
                600,
                report.comparativo().otros().negativos().valorEstimado(),
                0.000001);
        assertEquals(6, report.serieDiaria().size());
        assertEquals(2, report.mayorImpacto().materiaPrima().size());
        assertFalse(report.mayorImpacto().materiaPrima().get(1).costoVigente());
    }

    @Test
    void ranksByGrossImpactInsteadOfNetBalance() {
        LocalDate date = LocalDate.of(2026, 7, 20);
        Material highActivity = material("MP-A", 1, "KG", "100");
        Material higherNet = material("MP-B", 1, "KG", "100");

        List<Movimiento> movements = List.of(
                adjustment(1, 10, date.atTime(8, 0), highActivity),
                adjustment(2, -9, date.atTime(9, 0), highActivity),
                adjustment(3, 15, date.atTime(10, 0), higherNet));

        var report = assembler.assemble(movements, List.of(), date, date);

        assertEquals(
                "MP-A",
                report.mayorImpacto().materiaPrima().get(0).productoId());
        assertEquals(
                1_900,
                report.mayorImpacto().materiaPrima().get(0).impactoEstimado(),
                0.000001);
        assertEquals(
                100,
                report.mayorImpacto().materiaPrima().get(0).balanceValor(),
                0.000001);
    }

    private Material material(
            String id,
            int materialType,
            String unit,
            String cost
    ) {
        Material material = product(new Material(), id, unit, cost);
        material.setTipoMaterial(materialType);
        return material;
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
        if (cost != null) {
            product.asignarCostoInicial(new BigDecimal(cost));
        }
        return product;
    }

    private Movimiento adjustment(
            int transactionId,
            double quantity,
            LocalDateTime date,
            Producto product
    ) {
        return movement(
                transactionId,
                quantity,
                date,
                product,
                quantity > 0
                        ? Movimiento.TipoMovimiento.AJUSTE_POSITIVO
                        : Movimiento.TipoMovimiento.AJUSTE_NEGATIVO);
    }

    private Movimiento movement(
            int transactionId,
            double quantity,
            LocalDateTime date,
            Producto product,
            Movimiento.TipoMovimiento movementType
    ) {
        TransaccionAlmacen transaction = new TransaccionAlmacen();
        transaction.setTransaccionId(transactionId);
        transaction.setTipoEntidadCausante(
                TransaccionAlmacen.TipoEntidadCausante.OAA);

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
