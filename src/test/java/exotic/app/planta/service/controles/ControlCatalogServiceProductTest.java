package exotic.app.planta.service.controles;

import exotic.app.planta.model.producto.Categoria;
import exotic.app.planta.model.producto.Producto;
import exotic.app.planta.model.producto.SemiTerminado;
import exotic.app.planta.model.producto.Terminado;
import exotic.app.planta.repo.controles.CaracteristicaPlanControlRepo;
import exotic.app.planta.repo.controles.MagnitudControlRepo;
import exotic.app.planta.repo.controles.UnidadControlRepo;
import exotic.app.planta.repo.producto.ProductoRepo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ControlCatalogServiceProductTest {

    @Mock private MagnitudControlRepo magnitudRepo;
    @Mock private UnidadControlRepo unidadRepo;
    @Mock private CaracteristicaPlanControlRepo caracteristicaRepo;
    @Mock private ProductoRepo productoRepo;

    @InjectMocks private ControlCatalogService service;

    @Test
    void devuelveOpcionesCompactasParaTerminadoYSemiterminado() {
        Categoria category = new Categoria();
        category.setCategoriaId(7);
        category.setCategoriaNombre("Cremas");
        Terminado terminado = new Terminado();
        terminado.setProductoId("T-1");
        terminado.setNombre("Crema uno");
        terminado.setCategoria(category);
        SemiTerminado semiterminado = new SemiTerminado();
        semiterminado.setProductoId("S-1");
        semiterminado.setNombre("Base uno");
        semiterminado.setRequiereOrdenFabricacion(true);

        when(productoRepo.findAll(org.mockito.ArgumentMatchers.<Specification<Producto>>any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(terminado, semiterminado)));

        var result = service.buscarProductos("", "NOMBRE", null, 0, 10);

        assertEquals(2, result.getTotalElements());
        assertEquals("T", result.getContent().get(0).tipoProducto());
        assertEquals(7, result.getContent().get(0).categoriaId());
        assertEquals("S", result.getContent().get(1).tipoProducto());
        assertNull(result.getContent().get(1).categoriaId());
    }

    @Test
    void rechazaCriteriosYPaginacionInvalidosAntesDeConsultar() {
        assertThrows(IllegalArgumentException.class,
                () -> service.buscarProductos("", "DESCONOCIDO", null, 0, 10));
        assertThrows(IllegalArgumentException.class,
                () -> service.buscarProductos("", "NOMBRE", null, -1, 10));
        assertThrows(IllegalArgumentException.class,
                () -> service.buscarProductos("", "NOMBRE", null, 0, 51));
        verifyNoInteractions(productoRepo);
    }
}
