package exotic.app.planta.resource.rehumanos;

import exotic.app.planta.model.organizacion.personal.IntegrantePersonal;
import exotic.app.planta.service.rehumanos.IntegrantePersonalService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.Optional;

/**
 * REST controller for managing IntegrantePersonal entities
 */
@RestController
@RequestMapping("/integrantes-personal")
@RequiredArgsConstructor
public class IntegrantePersonalResource {

    private final IntegrantePersonalService integrantePersonalService;

    /**
     * POST endpoint to save a new IntegrantePersonal entity
     *
     * @param integrantePersonal The IntegrantePersonal entity to save
     * @param usuarioResponsable The username of the user who is creating the record
     * @return The saved IntegrantePersonal entity
     */
    @PostMapping("/save")
    public ResponseEntity<IntegrantePersonal> saveIntegrantePersonal(
            @RequestBody IntegrantePersonal integrantePersonal,
            @RequestParam(value = "usuarioResponsable", defaultValue = "sistema") String usuarioResponsable
    ) {
        try {
            IntegrantePersonal saved = integrantePersonalService.saveIntegrantePersonal(integrantePersonal, usuarioResponsable);
            return ResponseEntity.created(URI.create("/integrantes-personal/" + saved.getId())).body(saved);
        } catch (IllegalArgumentException e) {
            // Return a 400 Bad Request for validation errors
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            // Log error and return 500 Internal Server Error for other exceptions
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * GET endpoint to find an IntegrantePersonal by ID
     *
     * @param id The ID of the IntegrantePersonal to find
     * @return The IntegrantePersonal if found, or 404 Not Found
     */
    @GetMapping("/{id}")
    public ResponseEntity<IntegrantePersonal> findById(@PathVariable Long id) {
        Optional<IntegrantePersonal> integranteOpt = integrantePersonalService.findById(id);
        return integranteOpt.map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * GET endpoint to search for IntegrantePersonal entities by name or surname
     *
     * @param searchText The text to search for in names or surnames
     * @param page The page number (default 0)
     * @param size The page size (default 10)
     * @return A page of IntegrantePersonal entities matching the search criteria
     */
    @GetMapping("/search")
    public ResponseEntity<Page<IntegrantePersonal>> searchIntegrantes(
            @RequestParam("q") String searchText,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size
    ) {
        Page<IntegrantePersonal> integrantes = integrantePersonalService.searchIntegrantes(searchText, page, size);
        return ResponseEntity.ok(integrantes);
    }

    /**
     * GET endpoint to find IntegrantePersonal entities by department
     *
     * @param departamento The department to search for
     * @return A list of IntegrantePersonal entities in the specified department
     */
    @GetMapping("/by-departamento/{departamento}")
    public ResponseEntity<List<IntegrantePersonal>> findByDepartamento(
            @PathVariable IntegrantePersonal.Departamento departamento
    ) {
        List<IntegrantePersonal> integrantes = integrantePersonalService.findByDepartamento(departamento);
        return ResponseEntity.ok(integrantes);
    }

    /**
     * GET endpoint to find IntegrantePersonal entities by status
     *
     * @param estado The status to search for
     * @return A list of IntegrantePersonal entities with the specified status
     */
    @GetMapping("/by-estado/{estado}")
    public ResponseEntity<List<IntegrantePersonal>> findByEstado(
            @PathVariable IntegrantePersonal.Estado estado
    ) {
        List<IntegrantePersonal> integrantes = integrantePersonalService.findByEstado(estado);
        return ResponseEntity.ok(integrantes);
    }
}
