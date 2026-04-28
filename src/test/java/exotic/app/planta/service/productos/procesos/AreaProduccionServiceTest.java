package exotic.app.planta.service.productos.procesos;

import exotic.app.planta.dto.AreaOperativaResponseDTO;
import exotic.app.planta.dto.AreaProduccionDTO;
import exotic.app.planta.dto.SearchAreaOperativaDTO;
import exotic.app.planta.model.organizacion.AreaOperativa;
import exotic.app.planta.model.producto.Categoria;
import exotic.app.planta.model.users.User;
import exotic.app.planta.repo.producto.CategoriaRepo;
import exotic.app.planta.repo.producto.procesos.AreaProduccionRepo;
import exotic.app.planta.repo.usuarios.UserRepository;
import exotic.app.planta.service.users.UserOperationalCompatibilityService;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AreaProduccionServiceTest {

    @Test
    void createAreaProduccionFromDto_withoutCategorias_returnsEmptyList() {
        AreaProduccionRepo areaRepo = mock(AreaProduccionRepo.class);
        CategoriaRepo categoriaRepo = mock(CategoriaRepo.class);
        UserRepository userRepository = mock(UserRepository.class);
        UserOperationalCompatibilityService compatibilityService = mock(UserOperationalCompatibilityService.class);
        AreaProduccionService service = new AreaProduccionService(areaRepo, categoriaRepo, userRepository, compatibilityService);

        User responsable = buildUser(10L, 12345L, "lider@exotic.com", "Lider Operativo");
        when(areaRepo.findByNombre("Pesaje")).thenReturn(Optional.empty());
        when(userRepository.findById(10L)).thenReturn(Optional.of(responsable));
        doNothing().when(compatibilityService).assertCanBeAreaResponsable(10L, null);
        when(areaRepo.save(any(AreaOperativa.class))).thenAnswer(invocation -> {
            AreaOperativa area = invocation.getArgument(0);
            area.setAreaId(101);
            return area;
        });

        AreaProduccionDTO dto = new AreaProduccionDTO();
        dto.setNombre("Pesaje");
        dto.setDescripcion("Area de pesaje");
        dto.setResponsableId(10L);

        AreaOperativaResponseDTO response = service.createAreaProduccionFromDTO(dto);

        assertEquals(101, response.getAreaId());
        assertEquals("Pesaje", response.getNombre());
        assertEquals(0, response.getCategoriasHabilitadas().size());
    }

    @Test
    void createAreaProduccionFromDto_withInvalidCategoria_throwsError() {
        AreaProduccionRepo areaRepo = mock(AreaProduccionRepo.class);
        CategoriaRepo categoriaRepo = mock(CategoriaRepo.class);
        UserRepository userRepository = mock(UserRepository.class);
        UserOperationalCompatibilityService compatibilityService = mock(UserOperationalCompatibilityService.class);
        AreaProduccionService service = new AreaProduccionService(areaRepo, categoriaRepo, userRepository, compatibilityService);

        User responsable = buildUser(10L, 12345L, "lider@exotic.com", "Lider Operativo");
        when(areaRepo.findByNombre("Pesaje")).thenReturn(Optional.empty());
        when(userRepository.findById(10L)).thenReturn(Optional.of(responsable));
        doNothing().when(compatibilityService).assertCanBeAreaResponsable(10L, null);
        when(categoriaRepo.findAllById(any(Iterable.class))).thenReturn(List.of(buildCategoria(1, "Capsulas")));

        AreaProduccionDTO dto = new AreaProduccionDTO();
        dto.setNombre("Pesaje");
        dto.setDescripcion("Area de pesaje");
        dto.setResponsableId(10L);
        dto.setCategoriaIds(List.of(1, 999));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> service.createAreaProduccionFromDTO(dto));

        assertEquals("No se encontraron categorias con ID: [999]", error.getMessage());
    }

    @Test
    void updateAreaProduccion_replacesCategorias() {
        AreaProduccionRepo areaRepo = mock(AreaProduccionRepo.class);
        CategoriaRepo categoriaRepo = mock(CategoriaRepo.class);
        UserRepository userRepository = mock(UserRepository.class);
        UserOperationalCompatibilityService compatibilityService = mock(UserOperationalCompatibilityService.class);
        AreaProduccionService service = new AreaProduccionService(areaRepo, categoriaRepo, userRepository, compatibilityService);

        User oldResponsable = buildUser(10L, 12345L, "old@exotic.com", "Lider Antiguo");
        User newResponsable = buildUser(20L, 67890L, "new@exotic.com", "Lider Nuevo");
        AreaOperativa area = new AreaOperativa();
        area.setAreaId(5);
        area.setNombre("Pesaje");
        area.setDescripcion("Original");
        area.setResponsableArea(oldResponsable);
        area.setCategoriasHabilitadas(new LinkedHashSet<>(List.of(buildCategoria(1, "Capsulas"))));

        when(areaRepo.findById(5)).thenReturn(Optional.of(area));
        when(userRepository.findById(20L)).thenReturn(Optional.of(newResponsable));
        doNothing().when(compatibilityService).assertCanBeAreaResponsable(20L, 5);
        when(categoriaRepo.findAllById(any(Iterable.class))).thenReturn(List.of(
                buildCategoria(2, "Liquidos"),
                buildCategoria(3, "Tabletas")
        ));
        when(areaRepo.save(any(AreaOperativa.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AreaProduccionDTO dto = new AreaProduccionDTO();
        dto.setNombre("Pesaje");
        dto.setDescripcion("Actualizada");
        dto.setResponsableId(20L);
        dto.setCategoriaIds(List.of(2, 3));

        AreaOperativaResponseDTO response = service.updateAreaProduccion(5, dto);

        assertEquals("Actualizada", response.getDescripcion());
        assertEquals(20L, response.getResponsableArea().getId());
        assertIterableEquals(
                List.of("Liquidos", "Tabletas"),
                response.getCategoriasHabilitadas().stream().map(AreaOperativaResponseDTO.CategoriaHabilitadaDTO::getCategoriaNombre).toList()
        );
    }

    @Test
    void searchAreas_mapsCategoriasIntoResponse() {
        AreaProduccionRepo areaRepo = mock(AreaProduccionRepo.class);
        CategoriaRepo categoriaRepo = mock(CategoriaRepo.class);
        UserRepository userRepository = mock(UserRepository.class);
        UserOperationalCompatibilityService compatibilityService = mock(UserOperationalCompatibilityService.class);
        AreaProduccionService service = new AreaProduccionService(areaRepo, categoriaRepo, userRepository, compatibilityService);

        AreaOperativa area = new AreaOperativa();
        area.setAreaId(7);
        area.setNombre("Mezclado");
        area.setDescripcion("Area de mezclado");
        area.setResponsableArea(buildUser(30L, 33333L, "mezclado@exotic.com", "Lider Mezclado"));
        area.setCategoriasHabilitadas(new LinkedHashSet<>(List.of(
                buildCategoria(5, "Jarabes"),
                buildCategoria(4, "Ampollas")
        )));

        when(areaRepo.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(area), PageRequest.of(0, 10), 1));

        SearchAreaOperativaDTO dto = new SearchAreaOperativaDTO();
        Page<AreaOperativaResponseDTO> page = service.searchAreas(dto, PageRequest.of(0, 10));

        assertEquals(1, page.getTotalElements());
        assertIterableEquals(
                List.of("Ampollas", "Jarabes"),
                page.getContent().getFirst().getCategoriasHabilitadas().stream()
                        .map(AreaOperativaResponseDTO.CategoriaHabilitadaDTO::getCategoriaNombre)
                        .toList()
        );
    }

    private User buildUser(Long id, Long cedula, String username, String nombreCompleto) {
        User user = new User();
        user.setId(id);
        user.setCedula(cedula);
        user.setUsername(username);
        user.setNombreCompleto(nombreCompleto);
        return user;
    }

    private Categoria buildCategoria(int id, String nombre) {
        Categoria categoria = new Categoria();
        categoria.setCategoriaId(id);
        categoria.setCategoriaNombre(nombre);
        return categoria;
    }
}
