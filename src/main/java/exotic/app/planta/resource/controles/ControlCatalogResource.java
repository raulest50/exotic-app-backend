package exotic.app.planta.resource.controles;

import exotic.app.planta.model.controles.dto.ControlDTOs.*;
import exotic.app.planta.model.users.ModuloSistema;
import exotic.app.planta.model.users.User;
import exotic.app.planta.security.ModuleTabAccessGuard;
import exotic.app.planta.service.controles.ControlCatalogService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/controles/catalogos")
@RequiredArgsConstructor
public class ControlCatalogResource {
    private final ControlCatalogService service;
    private final ModuleTabAccessGuard accessGuard;

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
        return accessGuard.requireAnyTabAccess(
                authentication,
                Map.of(
                        ModuloSistema.PRODUCCION, Map.of("PLANES_CONTROL_PROCESO", nivel),
                        ModuloSistema.CALIDAD, Map.of("PLANES_CONTROL_CALIDAD", nivel)),
                "No tiene nivel de administracion de catalogos de control.");
    }
}
