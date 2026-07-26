package exotic.app.planta.service.productos;

import exotic.app.planta.model.producto.Material;
import exotic.app.planta.model.producto.Producto;
import exotic.app.planta.model.producto.SemiTerminado;
import exotic.app.planta.model.producto.Terminado;
import exotic.app.planta.model.producto.costos.RecetaCostosRevision;
import exotic.app.planta.repo.producto.InsumoRepo;
import exotic.app.planta.repo.producto.ProductoRepo;
import exotic.app.planta.repo.producto.costos.RecetaCostosRevisionRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProductoCostoPropagacionServiceTest {
    @Mock
    private ProductoRepo productoRepo;
    @Mock
    private InsumoRepo insumoRepo;
    @Mock
    private RecetaCostosRevisionRepo revisionRepo;

    private ProductoCostoPropagacionService service;

    @BeforeEach
    void setUp() {
        ProductoCostoService costoService = new ProductoCostoService(null, null, null, null);
        service = new ProductoCostoPropagacionService(
                productoRepo,
                insumoRepo,
                revisionRepo,
                costoService);
        RecetaCostosRevision revision = mock(RecetaCostosRevision.class);
        when(revision.getVersion()).thenReturn(7L);
        when(revisionRepo.findById((short) 1)).thenReturn(Optional.of(revision));
    }

    @Test
    void calculaDiamanteDespuesDeResolverTodosLosPredecesores() {
        Material material = producto(Material.class, "M", "M", "10", 2);
        SemiTerminado s1 = producto(SemiTerminado.class, "S1", "S", "20", 3);
        SemiTerminado s2 = producto(SemiTerminado.class, "S2", "S", "30", 4);
        Terminado terminado = producto(Terminado.class, "T", "T", "50", 5);

        List<InsumoRepo.CostoRecetaEdgeProjection> edges = List.of(
                edge(1, "M", "S1", 2d),
                edge(2, "M", "S2", 3d),
                edge(3, "S1", "T", 1d),
                edge(4, "S2", "T", 1d));
        stubGraph(List.of(material, s1, s2, terminado), edges);

        ProductoCostoPropagacionService.PlanPropagacion plan = service.calcularPlan(List.of(
                new ProductoCostoPropagacionService.CambioCostoRaiz(
                        "M", decimal("10"), 2, decimal("20"))));

        assertThat(plan.recetaRevision()).isEqualTo(7L);
        assertThat(plan.items()).extracting(
                        ProductoCostoPropagacionService.ItemPlan::productoId,
                        ProductoCostoPropagacionService.ItemPlan::nivel,
                        ProductoCostoPropagacionService.ItemPlan::costoNuevo)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("S1", 1, decimal("40")),
                        org.assertj.core.groups.Tuple.tuple("S2", 1, decimal("60")),
                        org.assertj.core.groups.Tuple.tuple("T", 2, decimal("100")));
    }

    @Test
    void sumaLineasDuplicadasDeUnaReceta() {
        Material material = producto(Material.class, "M", "M", "5", 1);
        SemiTerminado semi = producto(SemiTerminado.class, "S", "S", "1", 1);
        List<InsumoRepo.CostoRecetaEdgeProjection> edges = List.of(
                edge(1, "M", "S", 1d),
                edge(2, "M", "S", 2d));
        stubGraph(List.of(material, semi), edges);

        ProductoCostoPropagacionService.PlanPropagacion plan = service.calcularPlan(List.of(
                new ProductoCostoPropagacionService.CambioCostoRaiz(
                        "M", decimal("5"), 1, decimal("10"))));

        assertThat(plan.items()).singleElement().satisfies(item -> {
            assertThat(item.productoId()).isEqualTo("S");
            assertThat(item.costoNuevo()).isEqualByComparingTo("30.000000");
        });
    }

    @Test
    void combinaVariasRaicesEnUnUnicoRecalculo() {
        Material m1 = producto(Material.class, "M1", "M", "2", 1);
        Material m2 = producto(Material.class, "M2", "M", "5", 1);
        SemiTerminado semi = producto(SemiTerminado.class, "S", "S", "7", 1);
        List<InsumoRepo.CostoRecetaEdgeProjection> edges = List.of(
                edge(1, "M1", "S", 1d),
                edge(2, "M2", "S", 1d));
        stubGraph(List.of(m1, m2, semi), edges);

        ProductoCostoPropagacionService.PlanPropagacion plan = service.calcularPlan(List.of(
                new ProductoCostoPropagacionService.CambioCostoRaiz(
                        "M1", decimal("2"), 1, decimal("3")),
                new ProductoCostoPropagacionService.CambioCostoRaiz(
                        "M2", decimal("5"), 1, decimal("7"))));

        assertThat(plan.items()).singleElement().satisfies(item ->
                assertThat(item.costoNuevo()).isEqualByComparingTo("10.000000"));
    }

    @Test
    void noPropagaCuandoElMaterialNoCambia() {
        Material material = producto(Material.class, "M", "M", "5", 1);
        stubGraph(List.of(material), List.of());

        ProductoCostoPropagacionService.PlanPropagacion plan = service.calcularPlan(List.of(
                new ProductoCostoPropagacionService.CambioCostoRaiz(
                        "M", decimal("5"), 1, decimal("5"))));

        assertThat(plan.items()).isEmpty();
    }

    @Test
    void rechazaCiclosEnElSubgrafoAlcanzado() {
        Material material = producto(Material.class, "M", "M", "5", 1);
        SemiTerminado s1 = producto(SemiTerminado.class, "S1", "S", "1", 1);
        SemiTerminado s2 = producto(SemiTerminado.class, "S2", "S", "1", 1);
        List<InsumoRepo.CostoRecetaEdgeProjection> edges = List.of(
                edge(1, "M", "S1", 1d),
                edge(2, "S1", "S2", 1d),
                edge(3, "S2", "S1", 1d));
        stubGraph(List.of(material, s1, s2), edges);

        assertThatThrownBy(() -> service.calcularPlan(List.of(
                new ProductoCostoPropagacionService.CambioCostoRaiz(
                        "M", decimal("5"), 1, decimal("10")))))
                .isInstanceOf(ProductoCostoPropagacionException.class)
                .hasMessageContaining("ciclo");
    }

    @Test
    void rechazaCantidadesNoPositivas() {
        Material material = producto(Material.class, "M", "M", "5", 1);
        SemiTerminado semi = producto(SemiTerminado.class, "S", "S", "1", 1);
        stubGraph(List.of(material, semi), List.of(edge(1, "M", "S", 0d)));

        assertThatThrownBy(() -> service.calcularPlan(List.of(
                new ProductoCostoPropagacionService.CambioCostoRaiz(
                        "M", decimal("5"), 1, decimal("10")))))
                .isInstanceOf(ProductoCostoPropagacionException.class)
                .hasMessageContaining("cantidad requerida");
    }

    private void stubGraph(
            List<? extends Producto> productos,
            List<InsumoRepo.CostoRecetaEdgeProjection> edges
    ) {
        Map<String, Producto> byId = new java.util.HashMap<>();
        productos.forEach(producto -> byId.put(producto.getProductoId(), producto));
        when(productoRepo.findAllById(any())).thenAnswer(invocation -> {
            Collection<String> ids = invocation.getArgument(0);
            return ids.stream().map(byId::get).filter(java.util.Objects::nonNull).toList();
        });
        when(insumoRepo.findCostoEdgesByInputProductoIds(any())).thenAnswer(invocation -> {
            Collection<String> ids = invocation.getArgument(0);
            return edges.stream().filter(edge -> ids.contains(edge.getInputProductoId())).toList();
        });
        if (!edges.isEmpty()) {
            when(insumoRepo.findCostoEdgesByOutputProductoIds(any())).thenAnswer(invocation -> {
                Collection<String> ids = invocation.getArgument(0);
                return edges.stream().filter(edge -> ids.contains(edge.getOutputProductoId())).toList();
            });
        }
    }

    private <T extends Producto> T producto(
            Class<T> type,
            String id,
            String tipo,
            String costo,
            long version
    ) {
        T producto = mock(type);
        when(producto.getProductoId()).thenReturn(id);
        when(producto.getNombre()).thenReturn(id);
        when(producto.getTipo_producto()).thenReturn(tipo);
        when(producto.getCosto()).thenReturn(decimal(costo));
        when(producto.getCostoVersion()).thenReturn(version);
        return producto;
    }

    private InsumoRepo.CostoRecetaEdgeProjection edge(
            int id,
            String input,
            String output,
            double cantidad
    ) {
        InsumoRepo.CostoRecetaEdgeProjection edge =
                mock(InsumoRepo.CostoRecetaEdgeProjection.class);
        when(edge.getInsumoId()).thenReturn(id);
        when(edge.getInputProductoId()).thenReturn(input);
        when(edge.getOutputProductoId()).thenReturn(output);
        when(edge.getCantidadRequerida()).thenReturn(cantidad);
        return edge;
    }

    private BigDecimal decimal(String value) {
        return new BigDecimal(value).setScale(ProductoCostoService.COST_SCALE);
    }
}
