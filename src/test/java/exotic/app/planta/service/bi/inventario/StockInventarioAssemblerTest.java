package exotic.app.planta.service.bi.inventario;

import exotic.app.planta.model.producto.Material;
import exotic.app.planta.model.producto.SemiTerminado;
import exotic.app.planta.model.producto.Terminado;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class StockInventarioAssemblerTest {
    private final StockInventarioAssembler assembler = new StockInventarioAssembler();

    @Test
    void valuesOnlyPositiveStockWithAValidCurrentMasterCost() {
        Material valued = material("MP-1", "Aceite", "KG", 1, "1000", 0, 0);
        Material withoutCost = material("MP-2", "Agua", "L", 1, "0", 0, 0);
        Terminado negative = finishedProduct("T-1", "Terminado", "U", "500");

        var report = assembler.assemble(List.of(
                new ProductoStockSnapshot(valued, 10),
                new ProductoStockSnapshot(withoutCost, 5),
                new ProductoStockSnapshot(negative, -2)));

        assertEquals(10_000, report.resumen().valorEstimado(), 0.000001);
        assertEquals(2, report.resumen().referenciasConStock());
        assertEquals(1, report.resumen().referenciasValorizadas());
        assertEquals(50, report.resumen().coberturaCostosPct(), 0.000001);
        assertEquals(1, report.resumen().referenciasNegativas());
        assertEquals(2, report.alertas().total());
        assertEquals("STOCK_NEGATIVO", report.alertas().items().get(0).tipo());
        assertEquals("SIN_COSTO", report.alertas().items().get(1).tipo());
    }

    @Test
    void separatesValuationAndCostCoverageByInventoryGroup() {
        Material rawMaterial = material("MP-1", "Aceite", "KG", 1, "1000", 0, 0);
        Material valuedPackaging = material("ME-1", "Envase", "U", 2, "100", 0, 0);
        Material packagingWithoutCost = material("ME-2", "Tapa", "U", 2, "0", 0, 0);
        Terminado valuedFinished = finishedProduct("T-1", "Terminado 1", "U", "500");
        Terminado finishedWithoutCost = finishedProduct("T-2", "Terminado 2", "U", "0");
        SemiTerminado other = semiFinishedProduct("S-1", "Semiterminado", "KG", "200");

        var report = assembler.assemble(List.of(
                new ProductoStockSnapshot(rawMaterial, 10),
                new ProductoStockSnapshot(valuedPackaging, 4),
                new ProductoStockSnapshot(packagingWithoutCost, 1),
                new ProductoStockSnapshot(valuedFinished, 3),
                new ProductoStockSnapshot(finishedWithoutCost, 2),
                new ProductoStockSnapshot(other, 5)));

        assertEquals(12_900, report.resumen().valorEstimado(), 0.000001);
        assertEquals(10_400, report.resumen().valorizacion().materiales().total(), 0.000001);
        assertEquals(10_000, report.resumen().valorizacion().materiales().materiaPrima(), 0.000001);
        assertEquals(400, report.resumen().valorizacion().materiales().empaque(), 0.000001);
        assertEquals(1_500, report.resumen().valorizacion().terminados(), 0.000001);
        assertEquals(66.666666, report.resumen().coberturaCostosDetalle().globalPct(), 0.000001);
        assertEquals(66.666666, report.resumen().coberturaCostosDetalle().materialesPct(), 0.000001);
        assertEquals(50, report.resumen().coberturaCostosDetalle().terminadosPct(), 0.000001);
    }

    @Test
    void usesTheHighestConfiguredThresholdAndReportsEveryReachedThreshold() {
        Material material = material("MP-1", "Aceite", "KG", 1, "1000", 6, 10);

        var report = assembler.assemble(List.of(new ProductoStockSnapshot(material, 5)));
        var alert = report.alertas().items().get(0);

        assertEquals("BAJO_UMBRAL", alert.tipo());
        assertEquals(10, alert.umbral(), 0.000001);
        assertEquals(List.of("STOCK_MINIMO", "PUNTO_REORDEN"), alert.umbralesIncumplidos());
    }

    @Test
    void keepsUnitsSeparateAndReturnsNullCoverageWithoutPositiveStock() {
        Material negative = material("MP-1", "Aceite", "KG", 1, "1000", 0, -1);

        var report = assembler.assemble(List.of(new ProductoStockSnapshot(negative, -5)));

        assertEquals(1, report.porUnidad().size());
        assertEquals("KG", report.porUnidad().get(0).unidadMedida());
        assertEquals(-5, report.porUnidad().get(0).cantidadNeta(), 0.000001);
        assertNull(report.resumen().coberturaCostosPct());
        assertNull(report.resumen().coberturaCostosDetalle().globalPct());
        assertNull(report.resumen().coberturaCostosDetalle().materialesPct());
        assertNull(report.resumen().coberturaCostosDetalle().terminadosPct());
    }

    private Material material(
            String id,
            String name,
            String unit,
            int type,
            String cost,
            double minimumStock,
            double reorderPoint
    ) {
        Material material = new Material();
        material.setProductoId(id);
        material.setNombre(name);
        material.setTipoUnidades(unit);
        material.setTipoMaterial(type);
        material.asignarCostoInicial(new BigDecimal(cost));
        material.setStockMinimo(minimumStock);
        material.setPuntoReorden(reorderPoint);
        return material;
    }

    private Terminado finishedProduct(String id, String name, String unit, String cost) {
        Terminado product = new Terminado();
        product.setProductoId(id);
        product.setNombre(name);
        product.setTipoUnidades(unit);
        product.asignarCostoInicial(new BigDecimal(cost));
        return product;
    }

    private SemiTerminado semiFinishedProduct(String id, String name, String unit, String cost) {
        SemiTerminado product = new SemiTerminado();
        product.setProductoId(id);
        product.setNombre(name);
        product.setTipoUnidades(unit);
        product.asignarCostoInicial(new BigDecimal(cost));
        return product;
    }
}
