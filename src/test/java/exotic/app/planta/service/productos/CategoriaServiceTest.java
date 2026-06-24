package exotic.app.planta.service.productos;

import exotic.app.planta.model.producto.Categoria;
import exotic.app.planta.model.producto.dto.CategoriaResponseDTO;
import exotic.app.planta.repo.producto.CategoriaRepo;
import exotic.app.planta.repo.producto.TerminadoRepo;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CategoriaServiceTest {

    @Test
    void saveCategoria_existingCategory_preservesPlanningFields() {
        CategoriaRepo categoriaRepo = mock(CategoriaRepo.class);
        TerminadoRepo terminadoRepo = mock(TerminadoRepo.class);
        CategoriaService service = new CategoriaService(categoriaRepo, terminadoRepo);

        Categoria existing = buildCategoria(10, "Tratamiento");
        existing.setCategoriaDescripcion("Descripcion original");
        existing.setLoteSize(24);
        existing.setTiempoDiasFabricacion(3);
        existing.setCapacidadProductivaDiaria(90);

        Categoria request = buildCategoria(10, "Tratamiento Premium");
        request.setCategoriaDescripcion("Nueva descripcion");
        request.setLoteSize(0);
        request.setTiempoDiasFabricacion(0);
        request.setCapacidadProductivaDiaria(0);

        when(categoriaRepo.existsById(10)).thenReturn(true);
        when(categoriaRepo.findById(10)).thenReturn(Optional.of(existing));
        when(categoriaRepo.existsByCategoriaNombre("Tratamiento Premium")).thenReturn(false);
        when(categoriaRepo.save(any(Categoria.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CategoriaResponseDTO updated = service.saveCategoria(request);

        assertEquals("Tratamiento Premium", updated.getCategoriaNombre());
        assertEquals("Nueva descripcion", updated.getCategoriaDescripcion());
        assertEquals(24, updated.getLoteSize());
        assertEquals(3, updated.getTiempoDiasFabricacion());
        assertEquals(90, updated.getCapacidadProductivaDiaria());
    }

    @Test
    void getCategoriaById_mapsPlanningFieldsIntoResponseDto() {
        CategoriaRepo categoriaRepo = mock(CategoriaRepo.class);
        TerminadoRepo terminadoRepo = mock(TerminadoRepo.class);
        CategoriaService service = new CategoriaService(categoriaRepo, terminadoRepo);

        Categoria categoria = buildCategoria(8, "Shampoo");
        categoria.setCategoriaDescripcion("Desc");
        categoria.setLoteSize(12);
        categoria.setTiempoDiasFabricacion(2);
        categoria.setCapacidadProductivaDiaria(60);

        when(categoriaRepo.findById(8)).thenReturn(Optional.of(categoria));

        CategoriaResponseDTO response = service.getCategoriaById(8).orElseThrow();

        assertEquals(8, response.getCategoriaId());
        assertEquals("Shampoo", response.getCategoriaNombre());
        assertEquals("Desc", response.getCategoriaDescripcion());
        assertEquals(12, response.getLoteSize());
        assertEquals(2, response.getTiempoDiasFabricacion());
        assertEquals(60, response.getCapacidadProductivaDiaria());
    }

    private Categoria buildCategoria(int id, String nombre) {
        Categoria categoria = new Categoria();
        categoria.setCategoriaId(id);
        categoria.setCategoriaNombre(nombre);
        return categoria;
    }
}
