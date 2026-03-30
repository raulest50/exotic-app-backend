package exotic.app.planta.service.productos.procesos;

import exotic.app.planta.dto.ProcesoProduccionDTO;
import exotic.app.planta.dto.RecursoProduccionDTO;
import exotic.app.planta.model.producto.manufacturing.procesos.ProcesoProduccion;
import exotic.app.planta.model.producto.manufacturing.procesos.ProcesoRecurso;
import exotic.app.planta.model.producto.manufacturing.procesos.RecursoProduccion;
import exotic.app.planta.model.producto.manufacturing.procesos.ProcesoProduccionCompleto;
import exotic.app.planta.model.producto.manufacturing.procesos.nodo.NodoProceso;
import exotic.app.planta.model.producto.manufacturing.procesos.nodo.ProcesoFabricacionNodo;
import exotic.app.planta.repo.producto.procesos.ProcesoProduccionCompletoRepo;
import exotic.app.planta.repo.producto.procesos.ProcesoProduccionRepo;
import exotic.app.planta.repo.producto.procesos.RecursoProduccionRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.criteria.Join;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProcesoProduccionService {

    private final ProcesoProduccionRepo procesoProduccionRepo;
    private final ProcesoProduccionCompletoRepo procesoProduccionCompletoRepo;
    private final RecursoProduccionRepo recursoProduccionRepo;
    private final ProcesoRecursoService procesoRecursoService;

    @Transactional
    public ProcesoProduccion saveProcesoProduccion(ProcesoProduccion procesoProduccion) {
        log.info("Guardando proceso de producción: {}", procesoProduccion.getNombre());
        return procesoProduccionRepo.save(procesoProduccion);
    }

    @Transactional(readOnly = true)
    public Page<ProcesoProduccion> getProcesosProduccionPaginados(Pageable pageable) {
        log.info("Obteniendo procesos de producción paginados");
        return procesoProduccionRepo.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public Optional<ProcesoProduccion> getProcesoProduccionById(Integer id) {
        log.info("Buscando proceso de producción con ID: {}", id);
        return procesoProduccionRepo.findById(id);
    }

    @Transactional
    public void deleteProcesoProduccion(Integer id) {
        log.info("Eliminando proceso de producción con ID: {}", id);

        if (!procesoProduccionRepo.existsById(id)) {
            log.warn("No se encontró proceso de producción con ID: {}", id);
            throw new IllegalArgumentException("No se encontró el proceso de producción con ID: " + id);
        }

        Specification<ProcesoProduccionCompleto> referencedInProcesoCompleto = (root, query, cb) -> {
            query.distinct(true);
            Join<ProcesoProduccionCompleto, ProcesoFabricacionNodo> nodeJoin = root.join("nodes");
            Join<ProcesoProduccionCompleto, NodoProceso> procesoNodeJoin = cb.treat(nodeJoin, NodoProceso.class);
            return cb.equal(procesoNodeJoin.get("procesoProduccion").get("procesoId"), id);
        };

        long references = procesoProduccionCompletoRepo.count(referencedInProcesoCompleto);
        if (references > 0) {
            log.warn("No se puede eliminar el proceso de producción con ID: {} porque está referenciado en {} procesos completos", id, references);
            throw new IllegalStateException("El proceso de producción está siendo utilizado en uno o más procesos completos");
        }

        procesoProduccionRepo.deleteById(id);
    }

    /**
     * Verifica si un proceso de producción puede ser eliminado
     * 
     * @param id El ID del proceso de producción a verificar
     * @return Un objeto con información sobre si el proceso es eliminable y la razón si no lo es
     */
    @Transactional(readOnly = true)
    public Map<String, Object> isProcesoProduccionDeletable(Integer id) {
        log.info("Verificando si el proceso de producción con ID: {} es eliminable", id);
        Map<String, Object> result = new HashMap<>();

        // Verificar que el proceso existe
        if (!procesoProduccionRepo.existsById(id)) {
            result.put("deletable", false);
            result.put("reason", "No se encontró el proceso de producción con ID: " + id);
            return result;
        }

        // Verificar si está referenciado en algún proceso completo
        Specification<ProcesoProduccionCompleto> referencedInProcesoCompleto = (root, query, cb) -> {
            query.distinct(true);
            Join<ProcesoProduccionCompleto, ProcesoFabricacionNodo> nodeJoin = root.join("nodes");
            Join<ProcesoProduccionCompleto, NodoProceso> procesoNodeJoin = cb.treat(nodeJoin, NodoProceso.class);
            return cb.equal(procesoNodeJoin.get("procesoProduccion").get("procesoId"), id);
        };

        long references = procesoProduccionCompletoRepo.count(referencedInProcesoCompleto);
        if (references > 0) {
            result.put("deletable", false);
            result.put("reason", "El proceso de producción está siendo utilizado en " + references + " proceso(s) completo(s)");
            result.put("referencesCount", references);
            return result;
        }

        // Si no hay problemas, el proceso es eliminable
        result.put("deletable", true);
        return result;
    }

    /**
     * Crea un proceso de producción a partir de un DTO, incluyendo la asignación de recursos con cantidades
     * 
     * @param dto El DTO con la información del proceso y sus recursos
     * @return El proceso de producción creado
     * @throws RuntimeException si no se encuentra algún recurso
     */
    @Transactional
    public ProcesoProduccion createProcesoProduccionFromDTO(ProcesoProduccionDTO dto) {
        log.info("Creando proceso de producción desde DTO: {}", dto.getNombre());

        // Crear y guardar el proceso primero para obtener su ID
        ProcesoProduccion proceso = new ProcesoProduccion();
        proceso.setNombre(dto.getNombre());
        proceso.setSetUpTime(dto.getSetUpTime());
        proceso.setNivelAcceso(dto.getNivelAcceso() != null ? dto.getNivelAcceso() : 1);

        // Configurar el modelo de tiempo y sus parámetros
        proceso.setModel(dto.getModel());

        // Configurar los parámetros específicos según el modelo de tiempo
        switch (dto.getModel()) {
            case CONSTANT:
                if (dto.getConstantSeconds() == null) {
                    throw new IllegalArgumentException("El tiempo constante no puede ser nulo para el modelo CONSTANT");
                }
                proceso.setConstantSeconds(dto.getConstantSeconds());
                break;
            case THROUGHPUT_RATE:
                if (dto.getThroughputUnitsPerSec() == null) {
                    throw new IllegalArgumentException("La tasa de rendimiento no puede ser nula para el modelo THROUGHPUT_RATE");
                }
                proceso.setThroughputUnitsPerSec(dto.getThroughputUnitsPerSec());
                break;
            case PER_UNIT:
                if (dto.getSecondsPerUnit() == null) {
                    throw new IllegalArgumentException("El tiempo por unidad no puede ser nulo para el modelo PER_UNIT");
                }
                proceso.setSecondsPerUnit(dto.getSecondsPerUnit());
                break;
            case PER_BATCH:
                if (dto.getSecondsPerBatch() == null || dto.getBatchSize() == null) {
                    throw new IllegalArgumentException("El tiempo por lote y el tamaño del lote no pueden ser nulos para el modelo PER_BATCH");
                }
                proceso.setSecondsPerBatch(dto.getSecondsPerBatch());
                proceso.setBatchSize(dto.getBatchSize());
                break;
            default:
                throw new IllegalArgumentException("Modelo de tiempo no soportado: " + dto.getModel());
        }

        // Guardar el proceso para obtener su ID
        proceso = procesoProduccionRepo.save(proceso);

        // Crear las relaciones ProcesoRecurso con las cantidades
        if (dto.getRecursosRequeridos() != null && !dto.getRecursosRequeridos().isEmpty()) {
            List<ProcesoRecurso> recursosRequeridos = new ArrayList<>();

            for (RecursoProduccionDTO recursoDTO : dto.getRecursosRequeridos()) {
                if (recursoDTO.getId() == null) {
                    throw new IllegalArgumentException("El ID del recurso no puede ser nulo");
                }

                // Buscar el recurso por ID
                RecursoProduccion recurso = recursoProduccionRepo.findById(recursoDTO.getId())
                    .orElseThrow(() -> new IllegalArgumentException("Recurso no encontrado con ID: " + recursoDTO.getId()));

                // Crear la relación proceso-recurso
                ProcesoRecurso procesoRecurso = new ProcesoRecurso();
                procesoRecurso.setProceso(proceso);
                procesoRecurso.setRecurso(recurso);

                // Asignar la cantidad (nunca menor a 1)
                Integer cantidad = recursoDTO.getCantidad();
                if (cantidad == null || cantidad < 1) {
                    log.warn("Cantidad inválida para el recurso ID {}: {}. Se establecerá a 1.", 
                            recursoDTO.getId(), cantidad);
                    cantidad = 1;
                }
                procesoRecurso.setCantidad(cantidad);

                // Guardar la relación
                procesoRecurso = procesoRecursoService.saveProcesoRecurso(procesoRecurso);
                recursosRequeridos.add(procesoRecurso);
            }

            // Asignar las relaciones al proceso
            proceso.setRecursosRequeridos(recursosRequeridos);
        } else {
            log.warn("No se especificaron recursos para el proceso: {}", dto.getNombre());
        }

        return proceso;
    }

    /**
     * Actualiza un proceso de producción existente a partir de un DTO, incluyendo la actualización de recursos con cantidades
     * 
     * @param id El ID del proceso a actualizar
     * @param dto El DTO con la información actualizada del proceso y sus recursos
     * @return El proceso de producción actualizado
     * @throws RuntimeException si no se encuentra el proceso o algún recurso
     */
    @Transactional
    public ProcesoProduccion updateProcesoProduccionFromDTO(Integer id, ProcesoProduccionDTO dto) {
        log.info("Actualizando proceso de producción con ID {} desde DTO", id);

        if (id == null) {
            throw new IllegalArgumentException("El ID del proceso no puede ser nulo");
        }

        // Verificar que el proceso existe
        ProcesoProduccion proceso = procesoProduccionRepo.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Proceso de producción no encontrado con ID: " + id));

        // Actualizar los datos básicos del proceso
        proceso.setNombre(dto.getNombre());
        proceso.setSetUpTime(dto.getSetUpTime());
        proceso.setNivelAcceso(dto.getNivelAcceso() != null ? dto.getNivelAcceso() : 1);

        // Configurar el modelo de tiempo y sus parámetros
        proceso.setModel(dto.getModel());

        // Configurar los parámetros específicos según el modelo de tiempo
        switch (dto.getModel()) {
            case CONSTANT:
                if (dto.getConstantSeconds() == null) {
                    throw new IllegalArgumentException("El tiempo constante no puede ser nulo para el modelo CONSTANT");
                }
                proceso.setConstantSeconds(dto.getConstantSeconds());
                break;
            case THROUGHPUT_RATE:
                if (dto.getThroughputUnitsPerSec() == null) {
                    throw new IllegalArgumentException("La tasa de rendimiento no puede ser nula para el modelo THROUGHPUT_RATE");
                }
                proceso.setThroughputUnitsPerSec(dto.getThroughputUnitsPerSec());
                break;
            case PER_UNIT:
                if (dto.getSecondsPerUnit() == null) {
                    throw new IllegalArgumentException("El tiempo por unidad no puede ser nulo para el modelo PER_UNIT");
                }
                proceso.setSecondsPerUnit(dto.getSecondsPerUnit());
                break;
            case PER_BATCH:
                if (dto.getSecondsPerBatch() == null || dto.getBatchSize() == null) {
                    throw new IllegalArgumentException("El tiempo por lote y el tamaño del lote no pueden ser nulos para el modelo PER_BATCH");
                }
                proceso.setSecondsPerBatch(dto.getSecondsPerBatch());
                proceso.setBatchSize(dto.getBatchSize());
                break;
            default:
                throw new IllegalArgumentException("Modelo de tiempo no soportado: " + dto.getModel());
        }

        // Guardar los cambios básicos
        proceso = procesoProduccionRepo.save(proceso);

        // Eliminar las relaciones existentes para recrearlas
        if (proceso.getRecursosRequeridos() != null) {
            log.info("Eliminando {} relaciones de recursos existentes para el proceso ID: {}", 
                    proceso.getRecursosRequeridos().size(), id);
            proceso.getRecursosRequeridos().clear();
        }

        // Crear las nuevas relaciones ProcesoRecurso con las cantidades
        if (dto.getRecursosRequeridos() != null && !dto.getRecursosRequeridos().isEmpty()) {
            List<ProcesoRecurso> recursosRequeridos = new ArrayList<>();

            for (RecursoProduccionDTO recursoDTO : dto.getRecursosRequeridos()) {
                if (recursoDTO.getId() == null) {
                    throw new IllegalArgumentException("El ID del recurso no puede ser nulo");
                }

                // Buscar el recurso por ID
                RecursoProduccion recurso = recursoProduccionRepo.findById(recursoDTO.getId())
                    .orElseThrow(() -> new IllegalArgumentException("Recurso no encontrado con ID: " + recursoDTO.getId()));

                // Crear la relación proceso-recurso
                ProcesoRecurso procesoRecurso = new ProcesoRecurso();
                procesoRecurso.setProceso(proceso);
                procesoRecurso.setRecurso(recurso);

                // Asignar la cantidad (nunca menor a 1)
                Integer cantidad = recursoDTO.getCantidad();
                if (cantidad == null || cantidad < 1) {
                    log.warn("Cantidad inválida para el recurso ID {}: {}. Se establecerá a 1.", 
                            recursoDTO.getId(), cantidad);
                    cantidad = 1;
                }
                procesoRecurso.setCantidad(cantidad);

                // Guardar la relación
                procesoRecurso = procesoRecursoService.saveProcesoRecurso(procesoRecurso);
                recursosRequeridos.add(procesoRecurso);
            }

            // Asignar las relaciones al proceso
            proceso.setRecursosRequeridos(recursosRequeridos);
            log.info("Se asignaron {} recursos al proceso ID: {}", recursosRequeridos.size(), id);
        } else {
            log.warn("No se especificaron recursos para el proceso ID: {}", id);
        }

        return proceso;
    }
}
