package exotic.app.planta.service.bi.inventario;

import exotic.app.planta.model.bi.dto.BusquedaStockMaterialDTO;
import exotic.app.planta.model.producto.Material;
import exotic.app.planta.model.producto.Producto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BusquedaStockMaterialService {
    private static final int MAX_RESULTS = 10;
    private static final int MAX_SEARCH_LENGTH = 100;

    private final InventarioStockReader stockReader;

    public BusquedaStockMaterialDTO search(String rawSearch) {
        String search = normalizeAndValidate(rawSearch);
        String normalizedSearch = search.toLowerCase(Locale.ROOT);

        var results = stockReader.readGeneralStock().stream()
                .filter(snapshot -> snapshot.producto() instanceof Material)
                .filter(snapshot -> matches(snapshot.producto(), normalizedSearch))
                .sorted(byRelevance(normalizedSearch))
                .limit(MAX_RESULTS)
                .map(this::toResult)
                .toList();

        return BusquedaStockMaterialDTO.builder()
                .buscar(search)
                .resultados(results)
                .build();
    }

    private String normalizeAndValidate(String rawSearch) {
        String search = rawSearch == null ? "" : rawSearch.trim();
        if (search.isEmpty() || search.length() > MAX_SEARCH_LENGTH) {
            throw new IllegalArgumentException("buscar debe contener entre 1 y 100 caracteres.");
        }
        return search;
    }

    private boolean matches(Producto producto, String search) {
        return normalize(producto.getProductoId()).contains(search)
                || normalize(producto.getNombre()).contains(search);
    }

    private Comparator<ProductoStockSnapshot> byRelevance(String search) {
        return Comparator
                .comparingInt((ProductoStockSnapshot snapshot) -> relevance(snapshot.producto(), search))
                .thenComparing(
                        snapshot -> normalize(snapshot.producto().getProductoId()),
                        String.CASE_INSENSITIVE_ORDER);
    }

    private int relevance(Producto producto, String search) {
        String productId = normalize(producto.getProductoId());
        if (productId.equals(search)) return 0;
        if (productId.startsWith(search)) return 1;
        if (productId.contains(search)) return 2;
        return 3;
    }

    private BusquedaStockMaterialDTO.ResultadoStockMaterialDTO toResult(
            ProductoStockSnapshot snapshot
    ) {
        Producto producto = snapshot.producto();
        return BusquedaStockMaterialDTO.ResultadoStockMaterialDTO.builder()
                .productoId(producto.getProductoId())
                .nombre(producto.getNombre())
                .unidadMedida(InventarioBiUtils.unitOf(producto))
                .stockGeneral(snapshot.stockGeneral())
                .costoUnitario(InventarioBiUtils.costAsDouble(producto))
                .costoDisponible(InventarioBiUtils.hasValidCost(producto))
                .valorEstimado(InventarioBiUtils.estimatedValue(snapshot))
                .build();
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
