package exotic.app.planta.service.bi.inventario;

import exotic.app.planta.model.producto.Material;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BusquedaStockMaterialServiceTest {

    @Test
    void prioritizesExactAndPrefixCodeMatchesAndLimitsTheResult() {
        InventarioStockReader stockReader = mock(InventarioStockReader.class);
        List<ProductoStockSnapshot> stock = java.util.stream.IntStream.rangeClosed(1, 12)
                .mapToObj(index -> snapshot("MP-%02d".formatted(index), "Material " + index))
                .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
        stock.add(snapshot("MP", "Código exacto"));
        when(stockReader.readGeneralStock()).thenReturn(stock);

        var result = new BusquedaStockMaterialService(stockReader).search("mp");

        assertEquals(10, result.resultados().size());
        assertEquals("MP", result.resultados().get(0).productoId());
        assertEquals("MP-01", result.resultados().get(1).productoId());
    }

    private ProductoStockSnapshot snapshot(String id, String name) {
        Material material = new Material();
        material.setProductoId(id);
        material.setNombre(name);
        material.setTipoMaterial(1);
        material.setTipoUnidades("KG");
        return new ProductoStockSnapshot(material, 5);
    }
}
