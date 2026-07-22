package exotic.app.planta.service.bi.inventario;

import exotic.app.planta.model.inventarios.Movimiento;
import exotic.app.planta.model.producto.Material;
import exotic.app.planta.repo.inventarios.TransaccionAlmacenRepo;
import exotic.app.planta.repo.producto.MaterialRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BusquedaStockMaterialServiceTest {
    private MaterialRepo materialRepo;
    private TransaccionAlmacenRepo movementRepo;
    private BusquedaStockMaterialService service;

    @BeforeEach
    void setUp() {
        materialRepo = mock(MaterialRepo.class);
        movementRepo = mock(TransaccionAlmacenRepo.class);
        service = new BusquedaStockMaterialService(materialRepo, movementRepo);
    }

    @Test
    void calculatesStockOnlyForTheLimitedCandidatesAndPreservesTheirOrder() {
        Material exactMatch = material("ME-100", "Envase", "U", "250");
        Material withoutMovements = material("ME-101", "Envase alterno", "U", "100");
        when(materialRepo.findInventariablesForStockSearch(eq("me-1"), any(Pageable.class)))
                .thenReturn(List.of(exactMatch, withoutMovements));
        when(movementRepo.findStockByAlmacenAndProductoIds(
                Movimiento.Almacen.GENERAL,
                List.of("ME-100", "ME-101")))
                .thenReturn(List.<Object[]>of(new Object[]{"ME-100", 7d}));

        var response = service.search("  ME-1  ");

        assertEquals("ME-1", response.buscar());
        assertEquals(List.of("ME-100", "ME-101"), response.resultados().stream()
                .map(result -> result.productoId())
                .toList());
        assertEquals(7, response.resultados().get(0).stockGeneral(), 0.000001);
        assertEquals(1_750, response.resultados().get(0).valorEstimado(), 0.000001);
        assertEquals(0, response.resultados().get(1).stockGeneral(), 0.000001);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(materialRepo).findInventariablesForStockSearch(eq("me-1"), pageable.capture());
        assertEquals(10, pageable.getValue().getPageSize());
    }

    @Test
    void preservesRepositoryRelevanceOrderAndLimitsTheResultDefensively() {
        List<Material> rankedCandidates = new ArrayList<>();
        rankedCandidates.add(material("MP", "Código exacto", "KG", "0"));
        java.util.stream.IntStream.rangeClosed(1, 12)
                .mapToObj(index -> material(
                        "MP-%02d".formatted(index),
                        "Material " + index,
                        "KG",
                        "0"))
                .forEach(rankedCandidates::add);
        when(materialRepo.findInventariablesForStockSearch(eq("mp"), any(Pageable.class)))
                .thenReturn(rankedCandidates);
        when(movementRepo.findStockByAlmacenAndProductoIds(
                eq(Movimiento.Almacen.GENERAL),
                any()))
                .thenReturn(List.of());

        var response = service.search("mp");

        assertEquals(10, response.resultados().size());
        assertEquals("MP", response.resultados().get(0).productoId());
        assertEquals("MP-01", response.resultados().get(1).productoId());
    }

    @Test
    void skipsTheStockAggregationWhenThereAreNoCandidates() {
        when(materialRepo.findInventariablesForStockSearch(eq("inexistente"), any(Pageable.class)))
                .thenReturn(List.of());

        var response = service.search("inexistente");

        assertEquals(0, response.resultados().size());
        verify(movementRepo, never()).findStockByAlmacenAndProductoIds(any(), any());
    }

    private Material material(String id, String name, String unit, String cost) {
        Material material = new Material();
        material.setProductoId(id);
        material.setNombre(name);
        material.setTipoUnidades(unit);
        material.setInventareable(true);
        material.asignarCostoInicial(new BigDecimal(cost));
        return material;
    }
}
