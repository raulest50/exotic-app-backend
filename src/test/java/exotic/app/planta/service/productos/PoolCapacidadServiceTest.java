package exotic.app.planta.service.productos;

import exotic.app.planta.model.producto.PoolCapacidad;
import exotic.app.planta.model.producto.dto.PoolCapacidadDTO;
import exotic.app.planta.model.producto.dto.PoolCapacidadUpsertRequestDTO;
import exotic.app.planta.repo.producto.CategoriaRepo;
import exotic.app.planta.repo.producto.PoolCapacidadRepo;
import exotic.app.planta.resource.productos.exceptions.PoolCapacidadNotFoundException;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PoolCapacidadServiceTest {

    @Test
    void create_validPool_savesSuccessfully() {
        PoolCapacidadRepo poolRepo = mock(PoolCapacidadRepo.class);
        CategoriaRepo categoriaRepo = mock(CategoriaRepo.class);
        PoolCapacidadService service = new PoolCapacidadService(poolRepo, categoriaRepo);

        when(poolRepo.existsByNombreIgnoreCase("Neutros Compartidos")).thenReturn(false);
        when(poolRepo.save(any(PoolCapacidad.class))).thenAnswer(invocation -> {
            PoolCapacidad pool = invocation.getArgument(0);
            pool.setId(7);
            return pool;
        });

        PoolCapacidadDTO result = service.create(new PoolCapacidadUpsertRequestDTO(
                "Neutros Compartidos",
                240,
                "Pool compartido de mezclado base",
                true
        ));

        assertEquals(7, result.getId());
        assertEquals("Neutros Compartidos", result.getNombre());
        assertEquals(240, result.getCapacidadDiaria());
        assertEquals("Pool compartido de mezclado base", result.getDescripcion());
    }

    @Test
    void create_duplicateName_throwsValidationError() {
        PoolCapacidadRepo poolRepo = mock(PoolCapacidadRepo.class);
        CategoriaRepo categoriaRepo = mock(CategoriaRepo.class);
        PoolCapacidadService service = new PoolCapacidadService(poolRepo, categoriaRepo);

        when(poolRepo.existsByNombreIgnoreCase("Neutros Compartidos")).thenReturn(true);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.create(new PoolCapacidadUpsertRequestDTO("Neutros Compartidos", 120, null, true))
        );

        assertEquals("Ya existe un pool de capacidad con el nombre: Neutros Compartidos", error.getMessage());
    }

    @Test
    void update_validRequest_updatesPool() {
        PoolCapacidadRepo poolRepo = mock(PoolCapacidadRepo.class);
        CategoriaRepo categoriaRepo = mock(CategoriaRepo.class);
        PoolCapacidadService service = new PoolCapacidadService(poolRepo, categoriaRepo);

        PoolCapacidad existing = new PoolCapacidad(3, "Pool Original", 80, "Desc", true);
        when(poolRepo.findById(3)).thenReturn(Optional.of(existing));
        when(poolRepo.existsByNombreIgnoreCase("Pool Ajustado")).thenReturn(false);
        when(poolRepo.save(any(PoolCapacidad.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PoolCapacidadDTO result = service.update(3, new PoolCapacidadUpsertRequestDTO("Pool Ajustado", 95, "Nueva desc", false));

        assertEquals(3, result.getId());
        assertEquals("Pool Ajustado", result.getNombre());
        assertEquals(95, result.getCapacidadDiaria());
        assertEquals("Nueva desc", result.getDescripcion());
        assertEquals(false, result.isActivo());
    }

    @Test
    void update_poolDoesNotExist_throwsNotFound() {
        PoolCapacidadRepo poolRepo = mock(PoolCapacidadRepo.class);
        CategoriaRepo categoriaRepo = mock(CategoriaRepo.class);
        PoolCapacidadService service = new PoolCapacidadService(poolRepo, categoriaRepo);

        when(poolRepo.findById(123)).thenReturn(Optional.empty());

        PoolCapacidadNotFoundException error = assertThrows(
                PoolCapacidadNotFoundException.class,
                () -> service.update(123, new PoolCapacidadUpsertRequestDTO("Pool", 10, null, true))
        );

        assertEquals("No se encontro pool de capacidad con ID: 123", error.getMessage());
    }

    @Test
    void delete_poolInUse_throwsConflict() {
        PoolCapacidadRepo poolRepo = mock(PoolCapacidadRepo.class);
        CategoriaRepo categoriaRepo = mock(CategoriaRepo.class);
        PoolCapacidadService service = new PoolCapacidadService(poolRepo, categoriaRepo);

        PoolCapacidad existing = new PoolCapacidad(5, "Pool Activo", 120, null, true);
        when(poolRepo.findById(5)).thenReturn(Optional.of(existing));
        when(categoriaRepo.countByPoolCapacidad_Id(5)).thenReturn(2L);

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> service.delete(5));

        assertEquals(
                "No se puede eliminar el pool de capacidad porque esta asignado a 2 categoria(s).",
                error.getMessage()
        );
    }

    @Test
    void delete_poolWithoutCategorias_deletesSuccessfully() {
        PoolCapacidadRepo poolRepo = mock(PoolCapacidadRepo.class);
        CategoriaRepo categoriaRepo = mock(CategoriaRepo.class);
        PoolCapacidadService service = new PoolCapacidadService(poolRepo, categoriaRepo);

        PoolCapacidad existing = new PoolCapacidad(9, "Pool Libre", 120, null, true);
        when(poolRepo.findById(9)).thenReturn(Optional.of(existing));
        when(categoriaRepo.countByPoolCapacidad_Id(9)).thenReturn(0L);
        doNothing().when(poolRepo).delete(existing);

        service.delete(9);

        verify(poolRepo).delete(existing);
    }
}
