package exotic.app.planta.resource.rehumanos;

import exotic.app.planta.model.organizacion.personal.IntegrantePersonal;
import exotic.app.planta.model.organizacion.personal.RegistroHoraExtra;
import exotic.app.planta.model.organizacion.personal.dto.RegistroHoraExtraDecisionDTO;
import exotic.app.planta.model.organizacion.personal.dto.RegistroHoraExtraRequestDTO;
import exotic.app.planta.model.organizacion.personal.dto.RegistroHoraExtraResponseDTO;
import exotic.app.planta.model.users.User;
import exotic.app.planta.repo.usuarios.UserRepository;
import exotic.app.planta.service.rehumanos.IntegrantePersonalService;
import exotic.app.planta.service.rehumanos.RegistroHoraExtraService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

/**
 * REST controller for managing IntegrantePersonal entities
 */
@RestController
@RequestMapping("/integrantes-personal")
@RequiredArgsConstructor
public class IntegrantePersonalResource {

    private final IntegrantePersonalService integrantePersonalService;
    private final RegistroHoraExtraService registroHoraExtraService;
    private final UserRepository userRepository;

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

    @PostMapping("/{integranteId}/horas-extra")
    public ResponseEntity<RegistroHoraExtraResponseDTO> registrarHoraExtra(
            Authentication authentication,
            @PathVariable Long integranteId,
            @RequestBody RegistroHoraExtraRequestDTO request
    ) {
        try {
            RegistroHoraExtraResponseDTO response = registroHoraExtraService.registrar(
                    integranteId,
                    request,
                    getCurrentUser(authentication)
            );
            return ResponseEntity.created(URI.create("/integrantes-personal/horas-extra/" + response.getId()))
                    .body(response);
        } catch (RuntimeException e) {
            throw toResponseStatusException(e);
        }
    }

    @GetMapping("/{integranteId}/horas-extra")
    public ResponseEntity<List<RegistroHoraExtraResponseDTO>> listarHorasExtraPorIntegrante(
            @PathVariable Long integranteId
    ) {
        try {
            return ResponseEntity.ok(registroHoraExtraService.listarPorIntegrante(integranteId));
        } catch (RuntimeException e) {
            throw toResponseStatusException(e);
        }
    }

    @GetMapping("/horas-extra")
    public ResponseEntity<Page<RegistroHoraExtraResponseDTO>> buscarHorasExtra(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) RegistroHoraExtra.Estado estado,
            @RequestParam(required = false) String q,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size
    ) {
        try {
            return ResponseEntity.ok(registroHoraExtraService.buscar(
                    desde,
                    hasta,
                    estado,
                    q,
                    PageRequest.of(page, size)
            ));
        } catch (RuntimeException e) {
            throw toResponseStatusException(e);
        }
    }

    @PutMapping("/horas-extra/{id}/aprobar")
    public ResponseEntity<RegistroHoraExtraResponseDTO> aprobarHoraExtra(
            Authentication authentication,
            @PathVariable Long id
    ) {
        try {
            return ResponseEntity.ok(registroHoraExtraService.aprobar(id, getCurrentUser(authentication)));
        } catch (RuntimeException e) {
            throw toResponseStatusException(e);
        }
    }

    @PutMapping("/horas-extra/{id}/rechazar")
    public ResponseEntity<RegistroHoraExtraResponseDTO> rechazarHoraExtra(
            Authentication authentication,
            @PathVariable Long id,
            @RequestBody RegistroHoraExtraDecisionDTO decision
    ) {
        try {
            return ResponseEntity.ok(registroHoraExtraService.rechazar(id, decision, getCurrentUser(authentication)));
        } catch (RuntimeException e) {
            throw toResponseStatusException(e);
        }
    }

    @PutMapping("/horas-extra/{id}/anular")
    public ResponseEntity<RegistroHoraExtraResponseDTO> anularHoraExtra(
            Authentication authentication,
            @PathVariable Long id,
            @RequestBody RegistroHoraExtraDecisionDTO decision
    ) {
        try {
            return ResponseEntity.ok(registroHoraExtraService.anular(id, decision, getCurrentUser(authentication)));
        } catch (RuntimeException e) {
            throw toResponseStatusException(e);
        }
    }

    private User getCurrentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(UNAUTHORIZED, "No autenticado");
        }

        return userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(UNAUTHORIZED, "Usuario no encontrado"));
    }

    private ResponseStatusException toResponseStatusException(RuntimeException error) {
        if (error instanceof ResponseStatusException responseStatusException) {
            return responseStatusException;
        }
        if (error instanceof EntityNotFoundException) {
            return new ResponseStatusException(NOT_FOUND, error.getMessage(), error);
        }
        if (error instanceof IllegalArgumentException) {
            return new ResponseStatusException(BAD_REQUEST, error.getMessage(), error);
        }
        return new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, error.getMessage(), error);
    }
}
