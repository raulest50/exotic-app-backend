package exotic.app.planta.service.bi.inventario;

import exotic.app.planta.model.producto.Material;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AlertasInventarioDetalleServiceTest {
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-07-28T15:00:00Z"),
            ZoneId.of("America/Bogota"));

    private final InventarioStockReader stockReader = mock(InventarioStockReader.class);
    private final AlertasInventarioClassifier classifier =
            new AlertasInventarioClassifier();
    private final AlertasInventarioDetalleService service =
            new AlertasInventarioDetalleService(stockReader, classifier, CLOCK);

    @Test
    void separatesExhaustedFromBelowThresholdAndFiltersByGroup() {
        when(stockReader.readGeneralStock()).thenReturn(List.of(
                snapshot("MP-1", "Alcohol", 1, "KG", -5, 0, 0, true),
                snapshot("MP-2", "Extracto agotado", 1, "KG", 0, 5, 10, true),
                snapshot("ME-1", "Envase bajo", 2, "U", 4, 5, 10, true),
                snapshot("ME-2", "Tapa sin costo", 2, "U", 20, 5, 10, false)));

        var result = service.getAlerts(
                "AGOTADO",
                "MATERIA_PRIMA",
                "",
                "PRIORIDAD",
                "extracto",
                0,
                10);

        assertEquals(4, result.resumen().total());
        assertEquals(1, result.resumen().negativas());
        assertEquals(1, result.resumen().agotadas());
        assertEquals(1, result.resumen().bajoUmbral());
        assertEquals(1, result.resumen().sinCosto());
        assertEquals(1, result.pagina().totalElements());
        assertEquals("MP-2", result.pagina().items().get(0).productoId());
        assertEquals(10, result.pagina().items().get(0).umbral(), 0.000001);
        assertEquals(
                10,
                result.pagina().items().get(0).brechaUmbral(),
                0.000001);
    }

    @Test
    void clampsPageAndSupportsRelativeGapOrdering() {
        when(stockReader.readGeneralStock()).thenReturn(List.of(
                snapshot("MP-1", "Uno", 1, "KG", 5, 0, 10, true),
                snapshot("MP-2", "Dos", 1, "KG", 1, 0, 10, true)));

        var result = service.getAlerts(
                "BAJO_UMBRAL",
                "TODOS",
                "",
                "MAYOR_BRECHA_RELATIVA",
                "",
                8,
                10);

        assertEquals(0, result.pagina().page());
        assertEquals("MP-2", result.pagina().items().get(0).productoId());
        assertEquals(90, result.pagina().items().get(0).brechaPct(), 0.000001);
    }

    @Test
    void requiresUnitForStockOrderAndRejectsUnsupportedSize() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.getAlerts(
                        "TODAS",
                        "TODOS",
                        "",
                        "STOCK_ASC",
                        "",
                        0,
                        10));
        assertThrows(
                IllegalArgumentException.class,
                () -> service.getAlerts(
                        "TODAS",
                        "TODOS",
                        "KG",
                        "PRIORIDAD",
                        "",
                        0,
                        50));
    }

    @Test
    void keepsPriorityViewAtTenWithoutTruncatingExplorationTotals() {
        List<ProductoStockSnapshot> snapshots = new ArrayList<>();
        for (int index = 0; index < 12; index++) {
            snapshots.add(snapshot(
                    "MP-" + index,
                    "Material " + index,
                    1,
                    "KG",
                    index + 1,
                    0,
                    20,
                    true));
        }
        when(stockReader.readGeneralStock()).thenReturn(snapshots);

        var result = service.getAlerts(
                "TODAS",
                "TODOS",
                "",
                "PRIORIDAD",
                "",
                0,
                10);

        assertEquals(12, result.resumen().total());
        assertEquals(10, result.prioritarios().size());
        assertEquals(12, result.pagina().totalElements());
        assertEquals(2, result.pagina().totalPages());
    }

    private ProductoStockSnapshot snapshot(
            String id,
            String name,
            int group,
            String unit,
            double stock,
            double minimumStock,
            double reorderPoint,
            boolean validCost
    ) {
        Material material = new Material();
        material.setProductoId(id);
        material.setNombre(name);
        material.setTipoMaterial(group);
        material.setTipoUnidades(unit);
        material.setStockMinimo(minimumStock);
        material.setPuntoReorden(reorderPoint);
        material.asignarCostoInicial(new BigDecimal(validCost ? "100" : "0"));
        return new ProductoStockSnapshot(material, stock);
    }
}
