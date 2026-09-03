package exotic.app.planta.resource.controles;

import exotic.app.planta.model.controles.dto.ControlDTOs.*;
import exotic.app.planta.model.users.ModuloSistema;
import exotic.app.planta.model.users.User;
import exotic.app.planta.model.users.UserAccessEvaluator;
import exotic.app.planta.repo.usuarios.UserRepository;
import exotic.app.planta.service.controles.ControlCatalogService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/controles/catalogos")
@RequiredArgsConstructor
public class ControlCatalogResource {
    private final ControlCatalogService service;
    private final UserRepository userRepository;

    @GetMapping("/magnitudes")
    public List<CatalogoResponse> magnitudes(
            Authentication auth, @RequestParam(defaultValue = "false") boolean incluirInactivas) {
        requirePlanAccess(auth, 1);
        return service.listarMagnitudes(incluirInactivas);
    }

    @PostMapping("/magnitudes")
    public CatalogoResponse crearMagnitud(
            Authentication auth, @Valid @RequestBody CatalogoWriteRequest request) {
        requirePlanAccess(auth, 3);
        return service.crearMagnitud(request);
    }

    @PatchMapping("/magnitudes/{id}/estado")
    public CatalogoResponse estadoMagnitud(
            Authentication auth, @PathVariable Long id, @Valid @RequestBody EstadoCatalogoRequest request) {
        requirePlanAccess(auth, 3);
        return service.cambiarEstadoMagnitud(id, request.activo());
    }

    @GetMapping("/unidades")
    public List<CatalogoResponse> unidades(
            Authentication auth, @RequestParam(defaultValue = "false") boolean incluirInactivas) {
        requirePlanAccess(auth, 1);
        return service.listarUnidades(incluirInactivas);
    }

    @PostMapping("/unidades")
    public CatalogoResponse crearUnidad(
            Authentication auth, @Valid @RequestBody CatalogoWriteRequest request) {
        requirePlanAccess(auth, 3);
        return service.crearUnidad(request);
    }

    @PatchMapping("/unidades/{id}/estado")
    public CatalogoResponse estadoUnidad(
            Authentication auth, @PathVariable Long id, @Valid @RequestBody EstadoCatalogoRequest request) {
        requirePlanAccess(auth, 3);
        return service.cambiarEstadoUnidad(id, request.activo());
    }

    private User requirePlanAccess(Authentication authentication, int nivel) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No autenticado");
        }
        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario no encontrado"));
        if ("super_master".equalsIgnoreCase(user.getUsername())) return user;
        int produccion = UserAccessEvaluator.tabNivel(
                user, ModuloSistema.PRODUCCION, "PLANES_CONTROL_PROCESO").orElse(0);
        int calidad = UserAccessEvaluator.tabNivel(
                user, ModuloSistema.CALIDAD, "PLANES_CONTROL_CALIDAD").orElse(0);
        if (Math.max(produccion, calidad) < nivel) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "No tiene nivel de administracion de catalogos de control.");
        }
        return user;
    }
}
