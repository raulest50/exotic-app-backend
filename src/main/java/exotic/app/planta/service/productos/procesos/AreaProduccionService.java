package exotic.app.planta.service.productos.procesos;

import exotic.app.planta.dto.AreaOperativaResponseDTO;
import exotic.app.planta.dto.AreaOperativaAsociacionesDTO;
import exotic.app.planta.dto.AreaProduccionDTO;
import exotic.app.planta.dto.SearchAreaOperativaDTO;
import exotic.app.planta.dto.SearchAreaProduccionDTO;
import exotic.app.planta.model.organizacion.AreaOperativa;
import exotic.app.planta.model.organizacion.AreaOperativaCategoriaUnidadMedida;
import exotic.app.planta.model.organizacion.UnidadMedidaAreaOperativa;
import exotic.app.planta.model.producto.Categoria;
import exotic.app.planta.model.producto.SemiTerminado;
import exotic.app.planta.model.producto.manufacturing.procesos.RecursoProduccion;
import exotic.app.planta.model.users.User;
import exotic.app.planta.repo.producto.CategoriaRepo;
import exotic.app.planta.repo.producto.SemiTerminadoRepo;
import exotic.app.planta.repo.producto.procesos.RecursoProduccionRepo;
import exotic.app.planta.repo.producto.procesos.AreaOperativaCategoriaUnidadMedidaRepo;
import exotic.app.planta.repo.producto.procesos.AreaProduccionRepo;
import exotic.app.planta.repo.producto.procesos.UnidadMedidaAreaOperativaRepo;
import exotic.app.planta.repo.usuarios.UserRepository;
import exotic.app.planta.service.users.UserOperationalCompatibilityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
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
    private final UnidadMedidaAreaOperativaRepo unidadMedidaAreaOperativaRepo;
    private final AreaOperativaCategoriaUnidadMedidaRepo areaCategoriaUnidadRepo;
    private final UserRepository userRepository;
    private final UserOperationalCompatibilityService userOperationalCompatibilityService;
    private final SemiTerminadoRepo semiTerminadoRepo;
    private final RecursoProduccionRepo recursoProduccionRepo;

    @Transactional(readOnly = true)
    public List<AreaOperativaAsociacionesDTO.Item> listarSemiterminadosManufacturados(Integer areaId) {
        return semiterminadosItems(requireAreaAsociaciones(areaId, false));
    }

    @Transactional
    public List<AreaOperativaAsociacionesDTO.Item> guardarSemiterminadosManufacturados(
            Integer areaId, AreaOperativaAsociacionesDTO.SemiterminadosRequest request) {
        AreaOperativa area = requireAreaAsociaciones(areaId, true);
        List<String> ids = request.productoIds();
        if (ids == null || ids.stream().anyMatch(id -> id == null || id.isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Los codigos de semiterminados son obligatorios.");
        }
        Set<String> uniqueIds = new LinkedHashSet<>(ids);
        List<SemiTerminado> productos = semiTerminadoRepo.findAllById(uniqueIds);
        if (productos.size() != uniqueIds.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Algun semiterminado ya no existe o no es de tipo semiterminado. Recargue el catalogo.");
        }
        area.getSemiterminadosManufacturados().clear();
        area.getSemiterminadosManufacturados().addAll(productos);
        return semiterminadosItems(area);
    }

    @Transactional(readOnly = true)
    public List<AreaOperativaAsociacionesDTO.Item> listarRecursosProduccion(Integer areaId) {
        return recursosItems(requireAreaAsociaciones(areaId, false));
    }

    @Transactional
    public List<AreaOperativaAsociacionesDTO.Item> guardarRecursosProduccion(
            Integer areaId, AreaOperativaAsociacionesDTO.RecursosRequest request) {
        AreaOperativa area = requireAreaAsociaciones(areaId, true);
        List<Long> ids = request.recursoIds();
        if (ids == null || ids.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Los IDs de recursos deben ser positivos.");
        }
        Set<Long> uniqueIds = new LinkedHashSet<>(ids);
        List<RecursoProduccion> recursos = recursoProduccionRepo.findAllById(uniqueIds);
        if (recursos.size() != uniqueIds.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Algun recurso ya no existe. Recargue el catalogo.");
        }
        area.getRecursosProduccion().clear();
        area.getRecursosProduccion().addAll(recursos);
        return recursosItems(area);
    }

    @Transactional(readOnly = true)
    public Page<AreaOperativaAsociacionesDTO.Item> buscarSemiterminadosParaArea(Integer areaId, String search, int page) {
        requireAreaAsociaciones(areaId, false);
        return semiTerminadoRepo.buscarCatalogoArea(catalogoSearch(search), catalogoPage(page, "productoId"))
                .map(item -> new AreaOperativaAsociacionesDTO.Item(item.getProductoId(), item.getNombre(), item.getTipoUnidades()));
    }

    @Transactional(readOnly = true)
    public Page<AreaOperativaAsociacionesDTO.Item> buscarRecursosParaArea(Integer areaId, String search, int page) {
        requireAreaAsociaciones(areaId, false);
        return recursoProduccionRepo.buscarCatalogoArea(catalogoSearch(search), catalogoPage(page, "id"))
                .map(item -> new AreaOperativaAsociacionesDTO.Item(item.getId().toString(), item.getNombre(), item.getDescripcion()));
    }

    private String catalogoSearch(String search) {
        if (search.length() > 200) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La busqueda no puede superar 200 caracteres.");
        return search.trim();
    }

    private Pageable catalogoPage(int page, String idProperty) {
        if (page < 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La pagina no puede ser negativa.");
        return PageRequest.of(page, 20, Sort.by("nombre").ascending().and(Sort.by(idProperty)));
    }

    private AreaOperativa requireAreaAsociaciones(Integer areaId, boolean escritura) {
        if (escritura && areaId == -1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Almacen General es un area de solo lectura.");
        }
        return (escritura ? areaProduccionRepo.findByIdForUpdate(areaId) : areaProduccionRepo.findById(areaId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Area operativa no encontrada."));
    }

    private List<AreaOperativaAsociacionesDTO.Item> semiterminadosItems(AreaOperativa area) {
        return ordenarAsociaciones(area.getSemiterminadosManufacturados().stream()
                .map(item -> new AreaOperativaAsociacionesDTO.Item(item.getProductoId(), item.getNombre(), item.getTipoUnidades())).toList());
    }

    private List<AreaOperativaAsociacionesDTO.Item> recursosItems(AreaOperativa area) {
        return ordenarAsociaciones(area.getRecursosProduccion().stream()
                .map(item -> new AreaOperativaAsociacionesDTO.Item(item.getId().toString(), item.getNombre(), item.getDescripcion())).toList());
    }

    private List<AreaOperativaAsociacionesDTO.Item> ordenarAsociaciones(List<AreaOperativaAsociacionesDTO.Item> items) {
        return items.stream().sorted(Comparator.comparing(AreaOperativaAsociacionesDTO.Item::nombre,
                Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)).thenComparing(AreaOperativaAsociacionesDTO.Item::id)).toList();
    }

    @Transactional
    public AreaOperativa saveAreaProduccion(AreaOperativa areaProduccion) {
        log.info("Guardando area de produccion: {}", areaProduccion.getNombre());
        validarAlcanceEjecucion(areaProduccion);
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
        aplicarConfiguracionArea(area, dto);
        area.setResponsableArea(responsable);
        area.setCategoriasHabilitadas(resolveCategorias(resolveCategoriaIds(dto)));

        AreaOperativa savedArea = areaProduccionRepo.saveAndFlush(area);
        syncCategoriaUnidadAssociations(savedArea, dto);
        return toResponseDto(savedArea);
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
        aplicarConfiguracionArea(area, dto);
        area.setResponsableArea(responsable);
        area.setCategoriasHabilitadas(resolveCategorias(resolveCategoriaIds(dto)));

        AreaOperativa savedArea = areaProduccionRepo.saveAndFlush(area);
        syncCategoriaUnidadAssociations(savedArea, dto);
        return toResponseDto(savedArea);
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

    private List<Integer> resolveCategoriaIds(AreaProduccionDTO dto) {
        if (dto.getCategoriasHabilitadas() != null) {
            List<Integer> ids = new ArrayList<>();
            for (AreaProduccionDTO.CategoriaHabilitadaRequestDTO categoriaRequest : dto.getCategoriasHabilitadas()) {
                if (categoriaRequest == null || categoriaRequest.getCategoriaId() == null) {
                    throw new IllegalArgumentException("La categoria habilitada es obligatoria");
                }
                ids.add(categoriaRequest.getCategoriaId());
            }
            return ids;
        }
        return dto.getCategoriaIds();
    }

    private void syncCategoriaUnidadAssociations(AreaOperativa area, AreaProduccionDTO dto) {
        if (dto.getCategoriasHabilitadas() == null) {
            removeAssociationsForDisabledCategorias(area);
            return;
        }

        areaCategoriaUnidadRepo.deleteAllByAreaOperativa_AreaId(area.getAreaId());
        areaCategoriaUnidadRepo.flush();

        Map<Integer, Categoria> categoriasById = area.getCategoriasHabilitadas().stream()
                .collect(Collectors.toMap(Categoria::getCategoriaId, Function.identity()));

        LinkedHashSet<Long> unidadIds = dto.getCategoriasHabilitadas().stream()
                .map(AreaProduccionDTO.CategoriaHabilitadaRequestDTO::getUnidadMedidaId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Map<Long, UnidadMedidaAreaOperativa> unidadesById = unidadMedidaAreaOperativaRepo.findAllById(unidadIds).stream()
                .collect(Collectors.toMap(UnidadMedidaAreaOperativa::getId, Function.identity()));

        List<Long> missingUnidadIds = unidadIds.stream()
                .filter(id -> !unidadesById.containsKey(id))
                .toList();
        if (!missingUnidadIds.isEmpty()) {
            throw new IllegalArgumentException("No se encontraron unidades de medida con ID: " + missingUnidadIds);
        }

        List<AreaOperativaCategoriaUnidadMedida> nextAssociations = new ArrayList<>();
        Set<Integer> seenCategoriaIds = new HashSet<>();

        for (AreaProduccionDTO.CategoriaHabilitadaRequestDTO categoriaRequest : dto.getCategoriasHabilitadas()) {
            Categoria categoria = categoriasById.get(categoriaRequest.getCategoriaId());
            if (categoria == null) {
                throw new IllegalArgumentException(
                        "La categoria " + categoriaRequest.getCategoriaId() + " no esta habilitada para esta area"
                );
            }
            if (!seenCategoriaIds.add(categoria.getCategoriaId())) {
                throw new IllegalArgumentException("La categoria " + categoria.getCategoriaId() + " esta duplicada en la solicitud");
            }

            Long unidadId = categoriaRequest.getUnidadMedidaId();
            if (unidadId == null) {
                if (categoriaRequest.getFactorLote() != null) {
                    throw new IllegalArgumentException("No se puede configurar factor lote sin unidad de medida");
                }
                continue;
            }

            UnidadMedidaAreaOperativa unidad = unidadesById.get(unidadId);
            if (area.getAreaId() != unidad.getAreaOperativa().getAreaId()) {
                throw new IllegalArgumentException(
                        "La unidad " + unidadId + " no pertenece al area operativa " + area.getAreaId()
                );
            }

            AreaOperativaCategoriaUnidadMedida association = new AreaOperativaCategoriaUnidadMedida();
            association.setAreaOperativa(area);
            association.setCategoria(categoria);
            association.setUnidadMedida(unidad);
            association.setFactorLote(requirePositiveFactorLote(categoriaRequest.getFactorLote()));
            nextAssociations.add(association);
        }

        if (!nextAssociations.isEmpty()) {
            areaCategoriaUnidadRepo.saveAll(nextAssociations);
        }
    }

    private void removeAssociationsForDisabledCategorias(AreaOperativa area) {
        Set<Integer> enabledCategoriaIds = area.getCategoriasHabilitadas().stream()
                .map(Categoria::getCategoriaId)
                .collect(Collectors.toSet());

        List<AreaOperativaCategoriaUnidadMedida> toRemove = areaCategoriaUnidadRepo
                .findAllByAreaOperativa_AreaId(area.getAreaId())
                .stream()
                .filter(association -> !enabledCategoriaIds.contains(association.getCategoria().getCategoriaId()))
                .toList();

        if (!toRemove.isEmpty()) {
            areaCategoriaUnidadRepo.deleteAll(toRemove);
        }
    }

    private void aplicarConfiguracionArea(AreaOperativa area, AreaProduccionDTO dto) {
        // Clientes anteriores no deben restablecer una configuracion que no conocen.
        if (dto.getVisibilidadMps() != null) area.setVisibilidadMps(dto.getVisibilidadMps());
        if (dto.getAlcanceMps() != null) area.setAlcanceMps(dto.getAlcanceMps());
        if (dto.getAlcanceEjecucion() != null) area.setAlcanceEjecucion(dto.getAlcanceEjecucion());
        if (dto.getRequiereEquipoLoteOf() != null) area.setRequiereEquipoLoteOf(dto.getRequiereEquipoLoteOf());
        if (dto.getRequiereReporteProduccionEtapa() != null)
            area.setRequiereReporteProduccionEtapa(dto.getRequiereReporteProduccionEtapa());
        if (area.getAreaId() == -1 && area.isRequiereReporteProduccionEtapa())
            throw new IllegalArgumentException("Almacen General no reporta produccion por etapa.");
        validarAlcanceEjecucion(area);
    }

    private void validarAlcanceEjecucion(AreaOperativa area) {
        if (area.isRequiereEquipoLoteOf() && (area.getAreaId() == -1
                || area.getAlcanceEjecucion() == AreaOperativa.AlcanceEjecucion.SOLO_OP)) {
            throw new IllegalArgumentException("El equipo de lote solo se exige en areas que ejecutan OF.");
        }
        if (area.getAlcanceEjecucion() == null) {
            throw new IllegalArgumentException("El alcance de ejecucion del area es obligatorio.");
        }
        if (area.getAreaId() == -1 && area.getAlcanceEjecucion() != AreaOperativa.AlcanceEjecucion.AMBOS) {
            throw new IllegalArgumentException("Almacen General debe conservar el alcance de ejecucion AMBOS.");
        }
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

        Map<Integer, AreaOperativaCategoriaUnidadMedida> unidadByCategoria = buildUnidadByCategoria(area.getAreaId());

        List<AreaOperativaResponseDTO.CategoriaHabilitadaDTO> categoriasDto = area.getCategoriasHabilitadas().stream()
                .sorted(Comparator.comparing(Categoria::getCategoriaNombre, String.CASE_INSENSITIVE_ORDER))
                .map(categoria -> {
                    AreaOperativaCategoriaUnidadMedida association = unidadByCategoria.get(categoria.getCategoriaId());
                    return AreaOperativaResponseDTO.CategoriaHabilitadaDTO.builder()
                            .categoriaId(categoria.getCategoriaId())
                            .categoriaNombre(categoria.getCategoriaNombre())
                            .unidadMedidaId(association != null ? association.getUnidadMedida().getId() : null)
                            .factorLote(association != null ? association.getFactorLote() : null)
                            .build();
                })
                .toList();

        return AreaOperativaResponseDTO.builder()
                .areaId(area.getAreaId())
                .nombre(area.getNombre())
                .descripcion(area.getDescripcion())
                .visibilidadMps(area.getVisibilidadMps())
                .alcanceMps(area.getAlcanceMps())
                .alcanceEjecucion(area.getAlcanceEjecucion())
                .requiereEquipoLoteOf(area.isRequiereEquipoLoteOf())
                .requiereReporteProduccionEtapa(area.isRequiereReporteProduccionEtapa())
                .responsableArea(responsableDto)
                .categoriasHabilitadas(categoriasDto)
                .build();
    }

    private Map<Integer, AreaOperativaCategoriaUnidadMedida> buildUnidadByCategoria(Integer areaId) {
        Map<Integer, AreaOperativaCategoriaUnidadMedida> unidadByCategoria = new HashMap<>();
        if (areaId == null) {
            return unidadByCategoria;
        }

        for (AreaOperativaCategoriaUnidadMedida association : areaCategoriaUnidadRepo.findAllByAreaOperativa_AreaId(areaId)) {
            Integer categoriaId = association.getCategoria().getCategoriaId();
            unidadByCategoria.putIfAbsent(categoriaId, association);
        }

        return unidadByCategoria;
    }

    private BigDecimal requirePositiveFactorLote(BigDecimal factorLote) {
        if (factorLote == null || factorLote.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("El factor lote debe ser mayor que 0");
        }
        return factorLote;
    }
}
