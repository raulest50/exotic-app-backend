package exotic.app.planta.service.productos;

import exotic.app.planta.model.producto.Categoria;
import exotic.app.planta.model.producto.PoolCapacidad;
import exotic.app.planta.model.producto.dto.CategoriaResponseDTO;
import exotic.app.planta.repo.producto.CategoriaRepo;
import exotic.app.planta.repo.producto.PoolCapacidadRepo;
import exotic.app.planta.repo.producto.TerminadoRepo;
import exotic.app.planta.resource.productos.exceptions.CategoriaExceptions.ValidationException;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CategoriaServiceTest {

    @Test
    void updatePoolCapacidad_assignsExistingPool() {
        CategoriaRepo categoriaRepo = mock(CategoriaRepo.class);
        TerminadoRepo terminadoRepo = mock(TerminadoRepo.class);
        PoolCapacidadRepo poolCapacidadRepo = mock(PoolCapacidadRepo.class);
        CategoriaService service = new CategoriaService(categoriaRepo, terminadoRepo, poolCapacidadRepo);

        Categoria categoria = buildCategoria(10, "Shampoo");
        PoolCapacidad pool = new PoolCapacidad(4, "Neutro", 150, null, true);

        when(categoriaRepo.findById(10)).thenReturn(Optional.of(categoria));
        when(poolCapacidadRepo.findById(4)).thenReturn(Optional.of(pool));
        when(categoriaRepo.save(any(Categoria.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CategoriaResponseDTO updated = service.updatePoolCapacidad(10, 4);

        assertEquals(4, updated.getPoolCapacidadId());
        assertEquals("Neutro", updated.getPoolCapacidadNombre());
        assertEquals(150, updated.getPoolCapacidadCapacidadDiaria());
    }

    @Test
    void updatePoolCapacidad_nullValue_unassignsPool() {
        CategoriaRepo categoriaRepo = mock(CategoriaRepo.class);
        TerminadoRepo terminadoRepo = mock(TerminadoRepo.class);
        PoolCapacidadRepo poolCapacidadRepo = mock(PoolCapacidadRepo.class);
        CategoriaService service = new CategoriaService(categoriaRepo, terminadoRepo, poolCapacidadRepo);

        Categoria categoria = buildCategoria(10, "Tratamiento");
        categoria.setPoolCapacidad(new PoolCapacidad(6, "Pool Mezcla", 200, null, true));

        when(categoriaRepo.findById(10)).thenReturn(Optional.of(categoria));
        when(categoriaRepo.save(any(Categoria.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CategoriaResponseDTO updated = service.updatePoolCapacidad(10, null);

        assertNull(updated.getPoolCapacidadId());
        assertNull(updated.getPoolCapacidadNombre());
        assertNull(updated.getPoolCapacidadCapacidadDiaria());
    }

    @Test
    void updatePoolCapacidad_poolDoesNotExist_throwsValidationError() {
        CategoriaRepo categoriaRepo = mock(CategoriaRepo.class);
        TerminadoRepo terminadoRepo = mock(TerminadoRepo.class);
        PoolCapacidadRepo poolCapacidadRepo = mock(PoolCapacidadRepo.class);
        CategoriaService service = new CategoriaService(categoriaRepo, terminadoRepo, poolCapacidadRepo);

        Categoria categoria = buildCategoria(10, "Tratamiento");
        when(categoriaRepo.findById(10)).thenReturn(Optional.of(categoria));
        when(poolCapacidadRepo.findById(999)).thenReturn(Optional.empty());

        ValidationException error = assertThrows(ValidationException.class, () -> service.updatePoolCapacidad(10, 999));

        assertEquals("No se encontro pool de capacidad con ID: 999", error.getMessage());
    }

    @Test
    void saveCategoria_existingCategory_preservesPlanningFieldsAndPool() {
        CategoriaRepo categoriaRepo = mock(CategoriaRepo.class);
        TerminadoRepo terminadoRepo = mock(TerminadoRepo.class);
        PoolCapacidadRepo poolCapacidadRepo = mock(PoolCapacidadRepo.class);
        CategoriaService service = new CategoriaService(categoriaRepo, terminadoRepo, poolCapacidadRepo);

        PoolCapacidad pool = new PoolCapacidad(6, "Pool Base", 180, null, true);
        Categoria existing = buildCategoria(10, "Tratamiento");
        existing.setCategoriaDescripcion("Descripcion original");
        existing.setLoteSize(24);
        existing.setTiempoDiasFabricacion(3);
        existing.setCapacidadProductivaDiaria(90);
        existing.setPoolCapacidad(pool);

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
        assertEquals(6, updated.getPoolCapacidadId());
        assertEquals("Pool Base", updated.getPoolCapacidadNombre());
        assertEquals(180, updated.getPoolCapacidadCapacidadDiaria());
    }

    @Test
    void getCategoriaById_mapsPoolFieldsIntoResponseDto() {
        CategoriaRepo categoriaRepo = mock(CategoriaRepo.class);
        TerminadoRepo terminadoRepo = mock(TerminadoRepo.class);
        PoolCapacidadRepo poolCapacidadRepo = mock(PoolCapacidadRepo.class);
        CategoriaService service = new CategoriaService(categoriaRepo, terminadoRepo, poolCapacidadRepo);

        Categoria categoria = buildCategoria(8, "Shampoo");
        categoria.setCategoriaDescripcion("Desc");
        categoria.setLoteSize(12);
        categoria.setTiempoDiasFabricacion(2);
        categoria.setCapacidadProductivaDiaria(60);
        categoria.setPoolCapacidad(new PoolCapacidad(4, "Pool Neutro", 150, null, true));

        when(categoriaRepo.findWithPoolCapacidadByCategoriaId(8)).thenReturn(Optional.of(categoria));

        CategoriaResponseDTO response = service.getCategoriaById(8).orElseThrow();

        assertEquals(8, response.getCategoriaId());
        assertEquals("Shampoo", response.getCategoriaNombre());
        assertEquals("Desc", response.getCategoriaDescripcion());
        assertEquals(12, response.getLoteSize());
        assertEquals(2, response.getTiempoDiasFabricacion());
        assertEquals(60, response.getCapacidadProductivaDiaria());
        assertEquals(4, response.getPoolCapacidadId());
        assertEquals("Pool Neutro", response.getPoolCapacidadNombre());
        assertEquals(150, response.getPoolCapacidadCapacidadDiaria());
    }

    private Categoria buildCategoria(int id, String nombre) {
        Categoria categoria = new Categoria();
        categoria.setCategoriaId(id);
        categoria.setCategoriaNombre(nombre);
        return categoria;
    }
}
