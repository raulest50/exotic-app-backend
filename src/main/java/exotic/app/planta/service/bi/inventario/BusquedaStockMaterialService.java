package exotic.app.planta.service.bi.inventario;

import exotic.app.planta.model.bi.dto.BusquedaStockMaterialDTO;
import exotic.app.planta.model.inventarios.Movimiento;
import exotic.app.planta.model.producto.Material;
import exotic.app.planta.model.producto.Producto;
import exotic.app.planta.repo.inventarios.TransaccionAlmacenRepo;
import exotic.app.planta.repo.producto.MaterialRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BusquedaStockMaterialService {
    private static final int MAX_RESULTS = 10;
    private static final int MAX_SEARCH_LENGTH = 100;

    private final MaterialRepo materialRepo;
    private final TransaccionAlmacenRepo movementRepo;

    public BusquedaStockMaterialDTO search(String rawSearch) {
        String search = normalizeAndValidate(rawSearch);
        String normalizedSearch = search.toLowerCase(Locale.ROOT);

        List<Material> candidates = materialRepo.findInventariablesForStockSearch(
                        normalizedSearch,
                        PageRequest.of(0, MAX_RESULTS))
                .stream()
                .limit(MAX_RESULTS)
                .toList();
        Map<String, Double> stockByProduct = loadGeneralStock(candidates);

        var results = candidates.stream()
                .map(material -> new ProductoStockSnapshot(
                        material,
                        stockByProduct.getOrDefault(material.getProductoId(), 0d)))
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

    private Map<String, Double> loadGeneralStock(List<Material> candidates) {
        if (candidates.isEmpty()) return Map.of();

        List<String> productIds = candidates.stream()
                .map(Material::getProductoId)
                .toList();
        Map<String, Double> stockByProduct = new HashMap<>();
        movementRepo.findStockByAlmacenAndProductoIds(
                        Movimiento.Almacen.GENERAL,
                        productIds)
                .forEach(row -> stockByProduct.put(
                        String.valueOf(row[0]),
                        InventarioBiUtils.numberValue(row[1])));
        return stockByProduct;
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
}
