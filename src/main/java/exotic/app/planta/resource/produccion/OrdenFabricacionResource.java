package exotic.app.planta.resource.produccion;

import exotic.app.planta.dto.ErrorResponse;
import exotic.app.planta.model.produccion.dto.OrdenFabricacionDTOs;
import exotic.app.planta.model.users.ModuloSistema;
import exotic.app.planta.model.users.User;
import exotic.app.planta.model.users.UserAccessEvaluator;
import exotic.app.planta.repo.usuarios.UserRepository;
import exotic.app.planta.service.produccion.OrdenFabricacionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/produccion/ordenes-fabricacion")
@RequiredArgsConstructor
public class OrdenFabricacionResource {

    private static final String TAB = "CREAR_ORDEN_FABRICACION";

    private final OrdenFabricacionService service;
    private final UserRepository userRepository;

    @PostMapping
    public ResponseEntity<OrdenFabricacionDTOs.Response> crear(
            Authentication authentication,
            @Valid @RequestBody OrdenFabricacionDTOs.CreateRequest request
    ) {
        User actor = requireAccess(authentication, 2);
        return ResponseEntity.status(HttpStatus.CREATED).body(service.crear(request, actor));
    }

    @GetMapping
    public Page<OrdenFabricacionDTOs.Response> buscar(
            Authentication authentication,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        requireAccess(authentication, 1);
        return service.buscar(search, page, size);
    }

    @GetMapping("/semiterminados-elegibles")
    public Page<OrdenFabricacionDTOs.SemiterminadoOption> buscarSemiterminadosElegibles(
            Authentication authentication,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        requireAccess(authentication, 1);
        return service.buscarSemiterminadosElegibles(search, page, size);
    }

    @GetMapping("/{id}")
    public OrdenFabricacionDTOs.Response detalle(
            Authentication authentication,
            @PathVariable Long id
    ) {
        requireAccess(authentication, 1);
        return service.detalle(id);
    }

    @PutMapping("/{id}/cancelar")
    public OrdenFabricacionDTOs.Response cancelar(
            Authentication authentication,
            @PathVariable Long id
    ) {
        return service.cancelar(id, requireAccess(authentication, 2));
    }

    private User requireAccess(Authentication authentication, int minNivel) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No autenticado");
        }
        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Usuario no encontrado"));
        if (isMasterLike(user.getUsername())) return user;
        int nivel = UserAccessEvaluator.tabNivel(user, ModuloSistema.PRODUCCION, TAB).orElse(0);
        if (nivel < minNivel) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "No tiene permisos para gestionar órdenes de fabricación.");
        }
        return user;
    }

    private boolean isMasterLike(String username) {
        if (username == null) return false;
        String normalized = username.trim().toLowerCase(Locale.ROOT);
        return "master".equals(normalized) || "super_master".equals(normalized);
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<ErrorResponse> handleBusiness(RuntimeException error) {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("No fue posible procesar la orden", error.getMessage()));
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NoSuchElementException error) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("No encontrado", error.getMessage()));
    }
}
