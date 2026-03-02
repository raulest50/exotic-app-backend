package exotic.app.planta.service.productos.procesos;

import exotic.app.planta.dto.AreaProduccionDTO;
import exotic.app.planta.dto.SearchAreaOperativaDTO;
import exotic.app.planta.dto.SearchAreaProduccionDTO;
import exotic.app.planta.model.producto.manufacturing.procesos.AreaProduccion;
import exotic.app.planta.model.users.User;
import exotic.app.planta.repo.producto.procesos.AreaProduccionRepo;
import exotic.app.planta.repo.usuarios.UserRepository;
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

    /**
     * Guarda un área de producción
     * 
     * @param areaProduccion El área de producción a guardar
     * @return El área de producción guardada
     */
    @Transactional
    public AreaProduccion saveAreaProduccion(AreaProduccion areaProduccion) {
        log.info("Guardando área de producción: {}", areaProduccion.getNombre());
        return areaProduccionRepo.save(areaProduccion);
    }

    /**
     * Obtiene áreas de producción paginadas
     * 
     * @param pageable Configuración de paginación
     * @return Página de áreas de producción
     */
    @Transactional(readOnly = true)
    public Page<AreaProduccion> getAreasProduccionPaginadas(Pageable pageable) {
        log.info("Obteniendo áreas de producción paginadas");
        return areaProduccionRepo.findAll(pageable);
    }

    /**
     * Busca un área de producción por ID
     * 
     * @param id ID del área de producción
     * @return Optional con el área de producción si existe
     */
    @Transactional(readOnly = true)
    public Optional<AreaProduccion> getAreaProduccionById(Integer id) {
        log.info("Buscando área de producción con ID: {}", id);
        return areaProduccionRepo.findById(id);
    }

    /**
     * Crea un área de producción a partir de un DTO
     * 
     * @param dto DTO con la información del área
     * @return El área de producción creada
     * @throws IllegalArgumentException si no se encuentra el usuario responsable o si ya existe un área con el mismo nombre
     */
    @Transactional
    public AreaProduccion createAreaProduccionFromDTO(AreaProduccionDTO dto) {
        log.info("Creando área de producción desde DTO: {}", dto.getNombre());

        // Verificar si ya existe un área con el mismo nombre
        if (areaProduccionRepo.findByNombre(dto.getNombre()).isPresent()) {
            throw new IllegalArgumentException("Ya existe un área de producción con el nombre: " + dto.getNombre());
        }

        // Buscar el usuario responsable
        User responsable = userRepository.findById(dto.getResponsableId())
            .orElseThrow(() -> new IllegalArgumentException("Usuario responsable no encontrado con ID: " + dto.getResponsableId()));

        // Crear y guardar el área
        AreaProduccion area = new AreaProduccion();
        area.setNombre(dto.getNombre());
        area.setDescripcion(dto.getDescripcion());
        area.setResponsableArea(responsable);

        return areaProduccionRepo.save(area);
    }

    /**
     * Busca áreas de producción por nombre (coincidencia parcial)
     * 
     * @param searchDTO DTO con el criterio de búsqueda (nombre)
     * @param pageable Configuración de paginación
     * @return Lista de áreas de producción que coinciden con el criterio
     */
    @Transactional(readOnly = true)
    public List<AreaProduccion> searchAreasByName(SearchAreaProduccionDTO searchDTO, Pageable pageable) {
        log.info("Buscando áreas de producción por nombre: {}", searchDTO.getNombre());

        // Si el nombre está vacío, devolver todas las áreas paginadas
        if (searchDTO.getNombre() == null || searchDTO.getNombre().trim().isEmpty()) {
            return areaProduccionRepo.findAll(pageable).getContent();
        }

        // Crear especificación para buscar por coincidencia parcial del nombre
        Specification<AreaProduccion> spec = (root, query, cb) -> 
            cb.like(cb.lower(root.get("nombre")), "%" + searchDTO.getNombre().toLowerCase() + "%");

        // Ejecutar la búsqueda con la especificación y paginación
        return areaProduccionRepo.findAll(spec, pageable).getContent();
    }

    @Transactional
    public AreaProduccion updateAreaProduccion(Integer areaId, AreaProduccionDTO dto) {
        log.info("Actualizando área de producción con ID: {}", areaId);

        AreaProduccion area = areaProduccionRepo.findById(areaId)
            .orElseThrow(() -> new IllegalArgumentException("Área no encontrada con ID: " + areaId));

        if (!area.getNombre().equals(dto.getNombre())) {
            areaProduccionRepo.findByNombre(dto.getNombre()).ifPresent(existing -> {
                throw new IllegalArgumentException("Ya existe un área con el nombre: " + dto.getNombre());
            });
        }

        User responsable = userRepository.findById(dto.getResponsableId())
            .orElseThrow(() -> new IllegalArgumentException("Usuario responsable no encontrado con ID: " + dto.getResponsableId()));

        area.setNombre(dto.getNombre());
        area.setDescripcion(dto.getDescripcion());
        area.setResponsableArea(responsable);

        return areaProduccionRepo.save(area);
    }

    /**
     * Busca áreas de producción con múltiples criterios (nombre, responsable, ID).
     * Retorna Page para conservar metadata de paginación.
     */
    @Transactional(readOnly = true)
    public Page<AreaProduccion> searchAreas(SearchAreaOperativaDTO searchDTO, Pageable pageable) {
        log.info("Buscando áreas operativas - tipo: {}", searchDTO.getSearchType());

        String searchType = searchDTO.getSearchType();
        if (searchType == null || searchType.isBlank()) {
            return areaProduccionRepo.findAll(pageable);
        }

        switch (searchType.toUpperCase()) {
            case "NOMBRE": {
                if (searchDTO.getNombre() == null || searchDTO.getNombre().trim().isEmpty()) {
                    return areaProduccionRepo.findAll(pageable);
                }
                Specification<AreaProduccion> spec = (root, query, cb) ->
                        cb.like(cb.lower(root.get("nombre")),
                                "%" + searchDTO.getNombre().toLowerCase() + "%");
                return areaProduccionRepo.findAll(spec, pageable);
            }
            case "RESPONSABLE": {
                if (searchDTO.getResponsableId() == null) {
                    return areaProduccionRepo.findAll(pageable);
                }
                Specification<AreaProduccion> spec = (root, query, cb) ->
                        cb.equal(root.get("responsableArea").get("id"), searchDTO.getResponsableId());
                return areaProduccionRepo.findAll(spec, pageable);
            }
            case "ID": {
                if (searchDTO.getAreaId() == null) {
                    return areaProduccionRepo.findAll(pageable);
                }
                Specification<AreaProduccion> spec = (root, query, cb) ->
                        cb.equal(root.get("areaId"), searchDTO.getAreaId());
                return areaProduccionRepo.findAll(spec, pageable);
            }
            default:
                return areaProduccionRepo.findAll(pageable);
        }
    }
}
