package exotic.app.planta.service.productos;

import exotic.app.planta.model.producto.Categoria;
import exotic.app.planta.model.producto.Material;
import exotic.app.planta.model.producto.Producto;
import exotic.app.planta.model.producto.Terminado;
import exotic.app.planta.model.producto.dto.ProductoBasicUpdateDTO;
import exotic.app.planta.model.producto.dto.ProductoCategoriaEditabilityDTO;
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
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductoServiceTest {

    @Test
    void getCategoriaEditabilityReturnsEditableForTerminadoWithoutBlockingOrders() {
        ProductoRepo productoRepo = mock(ProductoRepo.class);
        OrdenProduccionRepo ordenProduccionRepo = mock(OrdenProduccionRepo.class);
        ProductoService service = buildService(productoRepo, ordenProduccionRepo, mock(CategoriaRepo.class));

        Terminado terminado = terminado("T-001", categoria(10, "Capilar"));
        when(productoRepo.findById("T-001")).thenReturn(Optional.of(terminado));
        when(ordenProduccionRepo.countByProducto_ProductoIdAndEstadoOrdenNotIn("T-001", List.of(2, -1)))
                .thenReturn(0L);

        ProductoCategoriaEditabilityDTO result = service.getCategoriaEditability("T-001");

        assertTrue(result.isEditable());
        assertEquals(0L, result.getBlockingOrdersCount());
        assertNull(result.getReason());
    }

    @Test
    void getCategoriaEditabilityReturnsBlockedForTerminadoWithBlockingOrders() {
        ProductoRepo productoRepo = mock(ProductoRepo.class);
        OrdenProduccionRepo ordenProduccionRepo = mock(OrdenProduccionRepo.class);
        ProductoService service = buildService(productoRepo, ordenProduccionRepo, mock(CategoriaRepo.class));

        Terminado terminado = terminado("T-001", categoria(10, "Capilar"));
        when(productoRepo.findById("T-001")).thenReturn(Optional.of(terminado));
        when(ordenProduccionRepo.countByProducto_ProductoIdAndEstadoOrdenNotIn("T-001", List.of(2, -1)))
                .thenReturn(3L);

        ProductoCategoriaEditabilityDTO result = service.getCategoriaEditability("T-001");

        assertFalse(result.isEditable());
        assertEquals(3L, result.getBlockingOrdersCount());
        assertEquals(
                "No se puede cambiar la categoria mientras existan ordenes de produccion no terminadas o no canceladas asociadas a este terminado.",
                result.getReason()
        );
    }

    @Test
    void getCategoriaEditabilityReturnsNotEditableForNonTerminado() {
        ProductoRepo productoRepo = mock(ProductoRepo.class);
        OrdenProduccionRepo ordenProduccionRepo = mock(OrdenProduccionRepo.class);
        ProductoService service = buildService(productoRepo, ordenProduccionRepo, mock(CategoriaRepo.class));

        Material material = new Material();
        material.setProductoId("M-001");
        when(productoRepo.findById("M-001")).thenReturn(Optional.of(material));

        ProductoCategoriaEditabilityDTO result = service.getCategoriaEditability("M-001");

        assertFalse(result.isEditable());
        assertEquals(0L, result.getBlockingOrdersCount());
        assertEquals("Solo los productos terminados pueden cambiar categoria.", result.getReason());
    }

    @Test
    void updateProductoBasicAllowsOtherFieldsWhenCategoryUnchangedEvenIfBlockingOrdersExist() {
        ProductoRepo productoRepo = mock(ProductoRepo.class);
        OrdenProduccionRepo ordenProduccionRepo = mock(OrdenProduccionRepo.class);
        CategoriaRepo categoriaRepo = mock(CategoriaRepo.class);
        TerminadoRepo terminadoRepo = mock(TerminadoRepo.class);
        ProductoService service = buildService(productoRepo, ordenProduccionRepo, categoriaRepo, terminadoRepo);

        Categoria categoriaActual = categoria(10, "Capilar");
        Terminado original = terminado("T-001", categoriaActual);
        original.setNombre("Producto Original");
        original.setCantidadUnidad(1.0);
        original.setIvaPercentual(19.0);
        original.setPrefijoLote("ABC");

        ProductoBasicUpdateDTO request = basicUpdate("T-001");
        request.setNombre("Producto Editado");
        request.setCantidadUnidad(2.0);
        request.setIvaPercentual(5.0);
        request.setObservaciones("Cambio permitido");
        request.setPrefijoLote("XYZ");

        when(productoRepo.existsById("T-001")).thenReturn(true);
        when(productoRepo.findById("T-001")).thenReturn(Optional.of(original));
        when(terminadoRepo.save(any(Terminado.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Producto updated = service.updateProductoBasic("T-001", request);

        Terminado updatedTerminado = (Terminado) updated;
        assertEquals("Producto Editado", updatedTerminado.getNombre());
        assertEquals(2.0, updatedTerminado.getCantidadUnidad());
        assertEquals(5.0, updatedTerminado.getIvaPercentual());
        assertEquals("Cambio permitido", updatedTerminado.getObservaciones());
        assertEquals("XYZ", updatedTerminado.getPrefijoLote());
        assertEquals(10, updatedTerminado.getCategoria().getCategoriaId());

        verify(ordenProduccionRepo, never())
                .countByProducto_ProductoIdAndEstadoOrdenNotIn(any(), any());
        verify(categoriaRepo, never()).findById(any());
    }

    @Test
    void updateProductoBasicThrowsConflictWhenCategoryChangesAndBlockingOrdersExist() {
        ProductoRepo productoRepo = mock(ProductoRepo.class);
        OrdenProduccionRepo ordenProduccionRepo = mock(OrdenProduccionRepo.class);
        CategoriaRepo categoriaRepo = mock(CategoriaRepo.class);
        TerminadoRepo terminadoRepo = mock(TerminadoRepo.class);
        ProductoService service = buildService(productoRepo, ordenProduccionRepo, categoriaRepo, terminadoRepo);

        Terminado original = terminado("T-001", categoria(10, "Capilar"));
        ProductoBasicUpdateDTO request = basicUpdate("T-001");
        request.setNombre("Producto Editado");
        request.setCantidadUnidad(2.0);
        request.setIvaPercentual(19.0);
        request.setCategoriaId(20);

        when(productoRepo.existsById("T-001")).thenReturn(true);
        when(productoRepo.findById("T-001")).thenReturn(Optional.of(original));
        when(ordenProduccionRepo.countByProducto_ProductoIdAndEstadoOrdenNotIn("T-001", List.of(2, -1)))
                .thenReturn(2L);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service.updateProductoBasic("T-001", request)
        );

        assertEquals(
                "No se puede cambiar la categoria mientras existan ordenes de produccion no terminadas o no canceladas asociadas a este terminado.",
                exception.getMessage()
        );
        verify(terminadoRepo, never()).save(any(Terminado.class));
        verify(categoriaRepo, never()).findById(any());
    }

    @Test
    void updateProductoBasicChangesCategoriaWhenNoBlockingOrders() {
        ProductoRepo productoRepo = mock(ProductoRepo.class);
        OrdenProduccionRepo ordenProduccionRepo = mock(OrdenProduccionRepo.class);
        CategoriaRepo categoriaRepo = mock(CategoriaRepo.class);
        TerminadoRepo terminadoRepo = mock(TerminadoRepo.class);
        ProductoService service = buildService(productoRepo, ordenProduccionRepo, categoriaRepo, terminadoRepo);

        Categoria categoriaActual = categoria(10, "Capilar");
        Categoria categoriaNueva = categoria(20, "Corpora");
        Terminado original = terminado("T-001", categoriaActual);
        ProductoBasicUpdateDTO request = basicUpdate("T-001");
        request.setNombre("Producto Editado");
        request.setCantidadUnidad(3.0);
        request.setIvaPercentual(19.0);
        request.setCategoriaId(20);

        when(productoRepo.existsById("T-001")).thenReturn(true);
        when(productoRepo.findById("T-001")).thenReturn(Optional.of(original));
        when(ordenProduccionRepo.countByProducto_ProductoIdAndEstadoOrdenNotIn("T-001", List.of(2, -1)))
                .thenReturn(0L);
        when(categoriaRepo.findById(20)).thenReturn(Optional.of(categoriaNueva));
        when(terminadoRepo.save(any(Terminado.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Producto updated = service.updateProductoBasic("T-001", request);

        Terminado updatedTerminado = (Terminado) updated;
        assertEquals(20, updatedTerminado.getCategoria().getCategoriaId());
        assertEquals("Corpora", updatedTerminado.getCategoria().getCategoriaNombre());
        verify(categoriaRepo).findById(20);
    }

    @Test
    void updateProductoBasicThrowsValidationWhenRequestedCategoriaDoesNotExist() {
        ProductoRepo productoRepo = mock(ProductoRepo.class);
        OrdenProduccionRepo ordenProduccionRepo = mock(OrdenProduccionRepo.class);
        CategoriaRepo categoriaRepo = mock(CategoriaRepo.class);
        TerminadoRepo terminadoRepo = mock(TerminadoRepo.class);
        ProductoService service = buildService(productoRepo, ordenProduccionRepo, categoriaRepo, terminadoRepo);

        Terminado original = terminado("T-001", categoria(10, "Capilar"));
        ProductoBasicUpdateDTO request = basicUpdate("T-001");
        request.setCategoriaId(99);

        when(productoRepo.existsById("T-001")).thenReturn(true);
        when(productoRepo.findById("T-001")).thenReturn(Optional.of(original));
        when(ordenProduccionRepo.countByProducto_ProductoIdAndEstadoOrdenNotIn("T-001", List.of(2, -1)))
                .thenReturn(0L);
        when(categoriaRepo.findById(99)).thenReturn(Optional.empty());

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.updateProductoBasic("T-001", request)
        );

        assertEquals("Categoria no encontrada: 99", exception.getMessage());
        verify(terminadoRepo, never()).save(any(Terminado.class));
    }

    @Test
    void updateProductoBasicUpdatesMaterialSpecificFields() {
        ProductoRepo productoRepo = mock(ProductoRepo.class);
        OrdenProduccionRepo ordenProduccionRepo = mock(OrdenProduccionRepo.class);
        CategoriaRepo categoriaRepo = mock(CategoriaRepo.class);
        MaterialRepo materialRepo = mock(MaterialRepo.class);
        ProductoService service = buildService(productoRepo, ordenProduccionRepo, categoriaRepo, materialRepo);

        Material original = new Material();
        original.setProductoId("M-001");
        original.setNombre("Material");
        original.setCantidadUnidad(1.0);
        original.setIvaPercentual(19.0);
        original.setTipoMaterial(1);
        original.setPuntoReorden(2.0);

        ProductoBasicUpdateDTO request = basicUpdate("M-001");
        request.setNombre("Material Editado");
        request.setCantidadUnidad(4.0);
        request.setIvaPercentual(5.0);
        request.setTipoMaterial(2);
        request.setPuntoReorden(7.0);

        when(productoRepo.existsById("M-001")).thenReturn(true);
        when(productoRepo.findById("M-001")).thenReturn(Optional.of(original));
        when(materialRepo.save(any(Material.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Producto updated = service.updateProductoBasic("M-001", request);

        Material updatedMaterial = (Material) updated;
        assertEquals("Material Editado", updatedMaterial.getNombre());
        assertEquals(4.0, updatedMaterial.getCantidadUnidad());
        assertEquals(5.0, updatedMaterial.getIvaPercentual());
        assertEquals(2, updatedMaterial.getTipoMaterial());
        assertEquals(7.0, updatedMaterial.getPuntoReorden());
    }

    private ProductoService buildService(
            ProductoRepo productoRepo,
            OrdenProduccionRepo ordenProduccionRepo,
            CategoriaRepo categoriaRepo
    ) {
        return buildService(
                productoRepo,
                ordenProduccionRepo,
                categoriaRepo,
                mock(TerminadoRepo.class)
        );
    }

    private ProductoService buildService(
            ProductoRepo productoRepo,
            OrdenProduccionRepo ordenProduccionRepo,
            CategoriaRepo categoriaRepo,
            TerminadoRepo terminadoRepo
    ) {
        return new ProductoService(
                productoRepo,
                mock(MaterialRepo.class),
                mock(SemiTerminadoRepo.class),
                terminadoRepo,
                categoriaRepo,
                mock(InsumoRepo.class),
                mock(TransaccionAlmacenRepo.class),
                ordenProduccionRepo,
                mock(ItemOrdenCompraRepo.class),
                mock(FileStorageService.class)
        );
    }

    private ProductoService buildService(
            ProductoRepo productoRepo,
            OrdenProduccionRepo ordenProduccionRepo,
            CategoriaRepo categoriaRepo,
            MaterialRepo materialRepo
    ) {
        return new ProductoService(
                productoRepo,
                materialRepo,
                mock(SemiTerminadoRepo.class),
                mock(TerminadoRepo.class),
                categoriaRepo,
                mock(InsumoRepo.class),
                mock(TransaccionAlmacenRepo.class),
                ordenProduccionRepo,
                mock(ItemOrdenCompraRepo.class),
                mock(FileStorageService.class)
        );
    }

    private Terminado terminado(String productoId, Categoria categoria) {
        Terminado terminado = new Terminado();
        terminado.setProductoId(productoId);
        terminado.setNombre("Producto");
        terminado.setCantidadUnidad(1.0);
        terminado.setIvaPercentual(19.0);
        terminado.setCategoria(categoria);
        terminado.setPrefijoLote("PRF");
        return terminado;
    }

    private ProductoBasicUpdateDTO basicUpdate(String productoId) {
        ProductoBasicUpdateDTO dto = new ProductoBasicUpdateDTO();
        dto.setProductoId(productoId);
        dto.setNombre("Producto");
        dto.setCantidadUnidad(1.0);
        dto.setObservaciones("");
        dto.setIvaPercentual(19.0);
        dto.setPrefijoLote("PRF");
        return dto;
    }

    private Categoria categoria(int categoriaId, String nombre) {
        Categoria categoria = new Categoria();
        categoria.setCategoriaId(categoriaId);
        categoria.setCategoriaNombre(nombre);
        categoria.setCategoriaDescripcion(nombre + " desc");
        return categoria;
    }
}
