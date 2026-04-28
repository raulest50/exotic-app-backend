package exotic.app.planta.service.productos.procesos;

import exotic.app.planta.dto.AreaOperativaResponseDTO;
import exotic.app.planta.dto.AreaProduccionDTO;
import exotic.app.planta.dto.SearchAreaOperativaDTO;
import exotic.app.planta.dto.SearchAreaProduccionDTO;
import exotic.app.planta.model.organizacion.AreaOperativa;
import exotic.app.planta.model.producto.Categoria;
import exotic.app.planta.model.users.User;
import exotic.app.planta.repo.producto.CategoriaRepo;
import exotic.app.planta.repo.producto.procesos.AreaProduccionRepo;
import exotic.app.planta.repo.usuarios.UserRepository;
import exotic.app.planta.service.users.UserOperationalCompatibilityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AreaProduccionService {

    private final AreaProduccionRepo areaProduccionRepo;
    private final CategoriaRepo categoriaRepo;
    private final UserRepository userRepository;
    private final UserOperationalCompatibilityService userOperationalCompatibilityService;

    @Transactional
    public AreaOperativa saveAreaProduccion(AreaOperativa areaProduccion) {
        log.info("Guardando area de produccion: {}", areaProduccion.getNombre());
        return areaProduccionRepo.save(areaProduccion);
    }

    @Transactional(readOnly = true)
    public Page<AreaOperativa> getAreasProduccionPaginadas(Pageable pageable) {
        log.info("Obteniendo areas de produccion paginadas");
        return areaProduccionRepo.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public Optional<AreaOperativa> getAreaProduccionById(Integer id) {
        log.info("Buscando area de produccion con ID: {}", id);
        return areaProduccionRepo.findById(id);
    }

    @Transactional
    public AreaOperativaResponseDTO createAreaProduccionFromDTO(AreaProduccionDTO dto) {
        log.info("Creando area de produccion desde DTO: {}", dto.getNombre());

        if (areaProduccionRepo.findByNombre(dto.getNombre()).isPresent()) {
            throw new IllegalArgumentException("Ya existe un area de produccion con el nombre: " + dto.getNombre());
        }

        User responsable = userRepository.findById(dto.getResponsableId())
                .orElseThrow(() -> new IllegalArgumentException("Usuario responsable no encontrado con ID: " + dto.getResponsableId()));
        userOperationalCompatibilityService.assertCanBeAreaResponsable(responsable.getId(), null);

        AreaOperativa area = new AreaOperativa();
        area.setNombre(dto.getNombre());
        area.setDescripcion(dto.getDescripcion());
        area.setResponsableArea(responsable);
        area.setCategoriasHabilitadas(resolveCategorias(dto.getCategoriaIds()));

        return toResponseDto(areaProduccionRepo.save(area));
    }

    @Transactional(readOnly = true)
    public List<AreaOperativaResponseDTO> searchAreasByName(SearchAreaProduccionDTO searchDTO, Pageable pageable) {
        log.info("Buscando areas de produccion por nombre: {}", searchDTO.getNombre());

        List<AreaOperativa> areas;
        if (searchDTO.getNombre() == null || searchDTO.getNombre().trim().isEmpty()) {
            areas = areaProduccionRepo.findAll(pageable).getContent();
        } else {
            Specification<AreaOperativa> spec = (root, query, cb) ->
                    cb.like(cb.lower(root.get("nombre")), "%" + searchDTO.getNombre().toLowerCase() + "%");
            areas = areaProduccionRepo.findAll(spec, pageable).getContent();
        }

        return areas.stream().map(this::toResponseDto).toList();
    }

    @Transactional
    public AreaOperativaResponseDTO updateAreaProduccion(Integer areaId, AreaProduccionDTO dto) {
        log.info("Actualizando area de produccion con ID: {}", areaId);

        AreaOperativa area = areaProduccionRepo.findById(areaId)
                .orElseThrow(() -> new IllegalArgumentException("Area no encontrada con ID: " + areaId));

        if (!area.getNombre().equals(dto.getNombre())) {
            areaProduccionRepo.findByNombre(dto.getNombre()).ifPresent(existing -> {
                throw new IllegalArgumentException("Ya existe un area con el nombre: " + dto.getNombre());
            });
        }

        User responsable = userRepository.findById(dto.getResponsableId())
                .orElseThrow(() -> new IllegalArgumentException("Usuario responsable no encontrado con ID: " + dto.getResponsableId()));
        userOperationalCompatibilityService.assertCanBeAreaResponsable(responsable.getId(), areaId);

        area.setNombre(dto.getNombre());
        area.setDescripcion(dto.getDescripcion());
        area.setResponsableArea(responsable);
        area.setCategoriasHabilitadas(resolveCategorias(dto.getCategoriaIds()));

        return toResponseDto(areaProduccionRepo.save(area));
    }

    @Transactional(readOnly = true)
    public Page<AreaOperativaResponseDTO> searchAreas(SearchAreaOperativaDTO searchDTO, Pageable pageable) {
        log.info("Buscando areas operativas - tipo: {}", searchDTO.getSearchType());

        String searchType = searchDTO.getSearchType();
        if (searchType == null || searchType.isBlank()) {
            return mapPage(areaProduccionRepo.findAll(pageable));
        }

        switch (searchType.toUpperCase()) {
            case "NOMBRE": {
                if (searchDTO.getNombre() == null || searchDTO.getNombre().trim().isEmpty()) {
                    return mapPage(areaProduccionRepo.findAll(pageable));
                }
                Specification<AreaOperativa> spec = (root, query, cb) ->
                        cb.like(cb.lower(root.get("nombre")),
                                "%" + searchDTO.getNombre().toLowerCase() + "%");
                return mapPage(areaProduccionRepo.findAll(spec, pageable));
            }
            case "RESPONSABLE": {
                if (searchDTO.getResponsableId() == null) {
                    return mapPage(areaProduccionRepo.findAll(pageable));
                }
                Specification<AreaOperativa> spec = (root, query, cb) ->
                        cb.equal(root.get("responsableArea").get("id"), searchDTO.getResponsableId());
                return mapPage(areaProduccionRepo.findAll(spec, pageable));
            }
            case "ID": {
                if (searchDTO.getAreaId() == null) {
                    return mapPage(areaProduccionRepo.findAll(pageable));
                }
                Specification<AreaOperativa> spec = (root, query, cb) ->
                        cb.equal(root.get("areaId"), searchDTO.getAreaId());
                return mapPage(areaProduccionRepo.findAll(spec, pageable));
            }
            default:
                return mapPage(areaProduccionRepo.findAll(pageable));
        }
    }

    private Page<AreaOperativaResponseDTO> mapPage(Page<AreaOperativa> areasPage) {
        return new PageImpl<>(
                areasPage.getContent().stream().map(this::toResponseDto).toList(),
                areasPage.getPageable(),
                areasPage.getTotalElements()
        );
    }

    private Set<Categoria> resolveCategorias(List<Integer> categoriaIds) {
        if (categoriaIds == null || categoriaIds.isEmpty()) {
            return new LinkedHashSet<>();
        }

        LinkedHashSet<Integer> uniqueIds = categoriaIds.stream()
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (uniqueIds.isEmpty()) {
            return new LinkedHashSet<>();
        }

        List<Categoria> categorias = categoriaRepo.findAllById(uniqueIds);
        Map<Integer, Categoria> categoriasById = categorias.stream()
                .collect(Collectors.toMap(Categoria::getCategoriaId, Function.identity()));

        List<Integer> missingIds = uniqueIds.stream()
                .filter(id -> !categoriasById.containsKey(id))
                .toList();

        if (!missingIds.isEmpty()) {
            throw new IllegalArgumentException("No se encontraron categorias con ID: " + missingIds);
        }

        return uniqueIds.stream()
                .map(categoriasById::get)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private AreaOperativaResponseDTO toResponseDto(AreaOperativa area) {
        AreaOperativaResponseDTO.ResponsableAreaDTO responsableDto = null;
        if (area.getResponsableArea() != null) {
            User responsable = area.getResponsableArea();
            responsableDto = AreaOperativaResponseDTO.ResponsableAreaDTO.builder()
                    .id(responsable.getId())
                    .cedula(responsable.getCedula())
                    .username(responsable.getUsername())
                    .nombreCompleto(responsable.getNombreCompleto())
                    .build();
        }

        List<AreaOperativaResponseDTO.CategoriaHabilitadaDTO> categoriasDto = area.getCategoriasHabilitadas().stream()
                .sorted(Comparator.comparing(Categoria::getCategoriaNombre, String.CASE_INSENSITIVE_ORDER))
                .map(categoria -> AreaOperativaResponseDTO.CategoriaHabilitadaDTO.builder()
                        .categoriaId(categoria.getCategoriaId())
                        .categoriaNombre(categoria.getCategoriaNombre())
                        .build())
                .toList();

        return AreaOperativaResponseDTO.builder()
                .areaId(area.getAreaId())
                .nombre(area.getNombre())
                .descripcion(area.getDescripcion())
                .responsableArea(responsableDto)
                .categoriasHabilitadas(categoriasDto)
                .build();
    }
}
