package exotic.app.planta.service.productos.procesos;

import exotic.app.planta.dto.AreaProduccionDTO;
import exotic.app.planta.dto.SearchAreaOperativaDTO;
import exotic.app.planta.dto.SearchAreaProduccionDTO;
import exotic.app.planta.model.organizacion.AreaOperativa;
import exotic.app.planta.model.users.User;
import exotic.app.planta.repo.producto.procesos.AreaProduccionRepo;
import exotic.app.planta.repo.usuarios.UserRepository;
import exotic.app.planta.service.users.UserOperationalCompatibilityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AreaProduccionService {

    private final AreaProduccionRepo areaProduccionRepo;
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
    public AreaOperativa createAreaProduccionFromDTO(AreaProduccionDTO dto) {
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

        return areaProduccionRepo.save(area);
    }

    @Transactional(readOnly = true)
    public List<AreaOperativa> searchAreasByName(SearchAreaProduccionDTO searchDTO, Pageable pageable) {
        log.info("Buscando areas de produccion por nombre: {}", searchDTO.getNombre());

        if (searchDTO.getNombre() == null || searchDTO.getNombre().trim().isEmpty()) {
            return areaProduccionRepo.findAll(pageable).getContent();
        }

        Specification<AreaOperativa> spec = (root, query, cb) ->
                cb.like(cb.lower(root.get("nombre")), "%" + searchDTO.getNombre().toLowerCase() + "%");

        return areaProduccionRepo.findAll(spec, pageable).getContent();
    }

    @Transactional
    public AreaOperativa updateAreaProduccion(Integer areaId, AreaProduccionDTO dto) {
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

        return areaProduccionRepo.save(area);
    }

    @Transactional(readOnly = true)
    public Page<AreaOperativa> searchAreas(SearchAreaOperativaDTO searchDTO, Pageable pageable) {
        log.info("Buscando areas operativas - tipo: {}", searchDTO.getSearchType());

        String searchType = searchDTO.getSearchType();
        if (searchType == null || searchType.isBlank()) {
            return areaProduccionRepo.findAll(pageable);
        }

        switch (searchType.toUpperCase()) {
            case "NOMBRE": {
                if (searchDTO.getNombre() == null || searchDTO.getNombre().trim().isEmpty()) {
                    return areaProduccionRepo.findAll(pageable);
                }
                Specification<AreaOperativa> spec = (root, query, cb) ->
                        cb.like(cb.lower(root.get("nombre")),
                                "%" + searchDTO.getNombre().toLowerCase() + "%");
                return areaProduccionRepo.findAll(spec, pageable);
            }
            case "RESPONSABLE": {
                if (searchDTO.getResponsableId() == null) {
                    return areaProduccionRepo.findAll(pageable);
                }
                Specification<AreaOperativa> spec = (root, query, cb) ->
                        cb.equal(root.get("responsableArea").get("id"), searchDTO.getResponsableId());
                return areaProduccionRepo.findAll(spec, pageable);
            }
            case "ID": {
                if (searchDTO.getAreaId() == null) {
                    return areaProduccionRepo.findAll(pageable);
                }
                Specification<AreaOperativa> spec = (root, query, cb) ->
                        cb.equal(root.get("areaId"), searchDTO.getAreaId());
                return areaProduccionRepo.findAll(spec, pageable);
            }
            default:
                return areaProduccionRepo.findAll(pageable);
        }
    }
}
