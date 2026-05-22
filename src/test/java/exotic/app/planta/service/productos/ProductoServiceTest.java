package exotic.app.planta.service.productos;

import exotic.app.planta.model.inventarios.Movimiento;
import exotic.app.planta.model.producto.Material;
import exotic.app.planta.model.producto.SemiTerminado;
import exotic.app.planta.model.producto.Terminado;
import exotic.app.planta.repo.compras.ItemOrdenCompraRepo;
import exotic.app.planta.repo.inventarios.TransaccionAlmacenRepo;
import exotic.app.planta.repo.produccion.OrdenProduccionRepo;
import exotic.app.planta.repo.producto.CategoriaRepo;
import exotic.app.planta.repo.producto.InsumoRepo;
import exotic.app.planta.repo.producto.MaterialRepo;
import exotic.app.planta.repo.producto.ProductoRepo;
import exotic.app.planta.repo.producto.SemiTerminadoRepo;
import exotic.app.planta.repo.producto.TerminadoRepo;
import exotic.app.planta.service.commons.FileStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ProductoServiceTest {

    private ProductoRepo productoRepo;
    private MaterialRepo materialRepo;
    private TransaccionAlmacenRepo transaccionAlmacenRepo;
    private ItemOrdenCompraRepo itemOrdenCompraRepo;
    private ProductoService service;

    @BeforeEach
    void setUp() {
        productoRepo = mock(ProductoRepo.class);
        materialRepo = mock(MaterialRepo.class);
        transaccionAlmacenRepo = mock(TransaccionAlmacenRepo.class);
        itemOrdenCompraRepo = mock(ItemOrdenCompraRepo.class);

        service = new ProductoService(
                productoRepo,
                materialRepo,
                mock(SemiTerminadoRepo.class),
                mock(TerminadoRepo.class),
                mock(CategoriaRepo.class),
                mock(InsumoRepo.class),
                transaccionAlmacenRepo,
                mock(OrdenProduccionRepo.class),
                itemOrdenCompraRepo,
                mock(FileStorageService.class)
        );
    }

    @Test
    void updateMaterialInventareableEnablesMaterialWithoutOperationalChecks() {
        Material material = material("M-1", false);
        when(productoRepo.findById("M-1")).thenReturn(Optional.of(material));
        when(materialRepo.save(any(Material.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Material updated = service.updateMaterialInventareable("M-1", true);

        assertTrue(updated.isInventareable());
        verify(materialRepo).save(material);
        verifyNoInteractions(transaccionAlmacenRepo, itemOrdenCompraRepo);
    }

    @Test
    void updateMaterialInventareableDisablesMaterialWithZeroStockAndNoOpenOrders() {
        Material material = material("M-2", true);
        when(productoRepo.findById("M-2")).thenReturn(Optional.of(material));
        when(transaccionAlmacenRepo.findNonZeroStockGroupsByProductoId(eq("M-2"), eq(0.0001d)))
                .thenReturn(List.of());
        when(itemOrdenCompraRepo.existsByMaterialProductoIdAndOrdenCompraEstadoIn("M-2", List.of(0, 1, 2)))
                .thenReturn(false);
        when(materialRepo.save(any(Material.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Material updated = service.updateMaterialInventareable("M-2", false);

        assertFalse(updated.isInventareable());
        verify(materialRepo).save(material);
    }

    @Test
    void updateMaterialInventareableBlocksDisableWhenAnyWarehouseLotGroupHasStock() {
        Material material = material("M-3", true);
        when(productoRepo.findById("M-3")).thenReturn(Optional.of(material));
        when(transaccionAlmacenRepo.findNonZeroStockGroupsByProductoId(eq("M-3"), eq(0.0001d)))
                .thenReturn(List.of(new Object[]{Movimiento.Almacen.GENERAL, 10L, 2.0d}));

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> service.updateMaterialInventareable("M-3", false)
        );

        assertTrue(error.getMessage().contains("stock"));
        verify(materialRepo, never()).save(any(Material.class));
        verifyNoInteractions(itemOrdenCompraRepo);
    }

    @Test
    void updateMaterialInventareableBlocksDisableWhenMaterialHasOpenPurchaseOrder() {
        Material material = material("M-4", true);
        when(productoRepo.findById("M-4")).thenReturn(Optional.of(material));
        when(transaccionAlmacenRepo.findNonZeroStockGroupsByProductoId(eq("M-4"), eq(0.0001d)))
                .thenReturn(List.of());
        when(itemOrdenCompraRepo.existsByMaterialProductoIdAndOrdenCompraEstadoIn("M-4", List.of(0, 1, 2)))
                .thenReturn(true);

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> service.updateMaterialInventareable("M-4", false)
        );

        assertTrue(error.getMessage().contains("orden de compra abierta"));
        verify(materialRepo, never()).save(any(Material.class));
        verify(itemOrdenCompraRepo).existsByMaterialProductoIdAndOrdenCompraEstadoIn("M-4", List.of(0, 1, 2));
    }

    @Test
    void updateMaterialInventareableReturnsSameMaterialWhenRequestedValueIsCurrentValue() {
        Material material = material("M-5", false);
        when(productoRepo.findById("M-5")).thenReturn(Optional.of(material));

        Material updated = service.updateMaterialInventareable("M-5", false);

        assertSame(material, updated);
        verify(materialRepo, never()).save(any(Material.class));
        verifyNoInteractions(transaccionAlmacenRepo, itemOrdenCompraRepo);
    }

    @Test
    void updateMaterialInventareableRejectsSemiTerminado() {
        SemiTerminado semiTerminado = new SemiTerminado();
        semiTerminado.setProductoId("S-1");
        when(productoRepo.findById("S-1")).thenReturn(Optional.of(semiTerminado));

        assertThrows(
                IllegalArgumentException.class,
                () -> service.updateMaterialInventareable("S-1", true)
        );
        verify(materialRepo, never()).save(any(Material.class));
    }

    @Test
    void updateMaterialInventareableRejectsTerminado() {
        Terminado terminado = new Terminado();
        terminado.setProductoId("T-1");
        when(productoRepo.findById("T-1")).thenReturn(Optional.of(terminado));

        assertThrows(
                IllegalArgumentException.class,
                () -> service.updateMaterialInventareable("T-1", false)
        );
        verify(materialRepo, never()).save(any(Material.class));
    }

    private static Material material(String productoId, boolean inventareable) {
        Material material = new Material();
        material.setProductoId(productoId);
        material.setInventareable(inventareable);
        return material;
    }
}
