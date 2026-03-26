package exotic.app.planta.service.rehumanos;

import org.springframework.transaction.annotation.Transactional;
import exotic.app.planta.model.organizacion.personal.DocTranDePersonal;
import exotic.app.planta.model.organizacion.personal.IntegrantePersonal;
import exotic.app.planta.repo.personal.IntegrantePersonalRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Service for managing IntegrantePersonal entities
 */
@Service
@Slf4j
@Transactional(rollbackFor = Exception.class)
@RequiredArgsConstructor
public class IntegrantePersonalService {

    private final IntegrantePersonalRepo integrantePersonalRepo;

    /**
     * Save a new IntegrantePersonal entity
     *
     * @param integrantePersonal The IntegrantePersonal entity to save
     * @param usuarioResponsable The username of the user who is creating the record
     * @return The saved IntegrantePersonal entity
     */
    @Transactional
    public IntegrantePersonal saveIntegrantePersonal(IntegrantePersonal integrantePersonal, String usuarioResponsable) {
        // Check if an integrante with the same ID already exists
        if (integrantePersonalRepo.existsById(integrantePersonal.getId())) {
            throw new IllegalArgumentException("Ya existe un Integrante de Personal con el ID " + integrantePersonal.getId());
        }

        // Set default values if not provided
        if (integrantePersonal.getEstado() == null) {
            integrantePersonal.setEstado(IntegrantePersonal.Estado.ACTIVO);
        }

        // Save the entity
        IntegrantePersonal savedIntegrante = integrantePersonalRepo.save(integrantePersonal);

        // Create and associate a document for the new integrante
        DocTranDePersonal documento = DocTranDePersonal.crearDocumentoIngreso(savedIntegrante, usuarioResponsable);

        // The document will be persisted through cascade
        /*if (savedIntegrante.getDocumentos() == null) {
            savedIntegrante.setDocumentos(List.of(documento));
        } else {
            savedIntegrante.getDocumentos().add(documento);
        }*/

        return savedIntegrante;
    }

    /**
     * Find an IntegrantePersonal by ID
     *
     * @param id The ID of the IntegrantePersonal to find
     * @return An Optional containing the IntegrantePersonal if found, or empty if not found
     */
    public Optional<IntegrantePersonal> findById(Long id) {
        return integrantePersonalRepo.findById(id);
    }

    /**
     * Find IntegrantePersonal entities by name or surname
     *
     * @param searchText The text to search for in names or surnames
     * @param page The page number
     * @param size The page size
     * @return A page of IntegrantePersonal entities matching the search criteria
     */
    public Page<IntegrantePersonal> searchIntegrantes(String searchText, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return integrantePersonalRepo.findByNombresOrApellidosContainingIgnoreCase(searchText, pageable);
    }

    /**
     * Find IntegrantePersonal entities by department
     *
     * @param departamento The department to search for
     * @return A list of IntegrantePersonal entities in the specified department
     */
    public List<IntegrantePersonal> findByDepartamento(IntegrantePersonal.Departamento departamento) {
        return integrantePersonalRepo.findByDepartamento(departamento);
    }

    /**
     * Find IntegrantePersonal entities by status
     *
     * @param estado The status to search for
     * @return A list of IntegrantePersonal entities with the specified status
     */
    public List<IntegrantePersonal> findByEstado(IntegrantePersonal.Estado estado) {
        return integrantePersonalRepo.findByEstado(estado);
    }
}
