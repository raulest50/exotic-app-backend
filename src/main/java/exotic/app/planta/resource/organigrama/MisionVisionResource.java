package exotic.app.planta.resource.organigrama;

import exotic.app.planta.model.organigrama.dto.MisionVisionRestoreRequest;
import exotic.app.planta.model.organigrama.dto.MisionVisionVersionRequest;
import exotic.app.planta.model.organigrama.dto.MisionVisionVersionResponse;
import exotic.app.planta.model.organigrama.dto.MisionVisionVersionSummaryResponse;
import exotic.app.planta.model.users.ModuloSistema;
import exotic.app.planta.model.users.User;
import exotic.app.planta.security.ModuleTabAccessGuard;
import exotic.app.planta.service.organigrama.MisionVisionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/organigrama/mision-vision")
@RequiredArgsConstructor
public class MisionVisionResource {

    private static final String TAB_MISION_VISION = "MISION_VISION";
    private static final CacheControl CACHE_CONTROL = CacheControl.noCache().cachePrivate();

    private final MisionVisionService service;
    private final ModuleTabAccessGuard accessGuard;

    @GetMapping("/vigente")
    public ResponseEntity<MisionVisionVersionResponse> getVigente(Authentication authentication) {
        requireAccess(authentication, 1);
        return ResponseEntity.ok()
                .cacheControl(CACHE_CONTROL)
                .body(service.getVigente());
    }

    @GetMapping("/versiones")
    public ResponseEntity<List<MisionVisionVersionSummaryResponse>> getVersiones(
            Authentication authentication
    ) {
        requireAccess(authentication, 1);
        return ResponseEntity.ok()
                .cacheControl(CACHE_CONTROL)
                .body(service.getVersiones());
    }

    @GetMapping("/versiones/{id}")
    public ResponseEntity<MisionVisionVersionResponse> getVersion(
            Authentication authentication,
            @PathVariable Long id
    ) {
        requireAccess(authentication, 1);
        return ResponseEntity.ok()
                .cacheControl(CACHE_CONTROL)
                .body(service.getVersion(id));
    }

    @PostMapping("/versiones")
    public ResponseEntity<MisionVisionVersionResponse> crearVersion(
            Authentication authentication,
            @Valid @RequestBody MisionVisionVersionRequest request
    ) {
        User user = requireAccess(authentication, 2);
        MisionVisionVersionResponse created = service.crearNuevaVersion(request, user.getUsername());
        return ResponseEntity
                .created(URI.create("/api/organigrama/mision-vision/versiones/" + created.id()))
                .body(created);
    }

    @PostMapping("/versiones/{id}/restaurar")
    public ResponseEntity<MisionVisionVersionResponse> restaurarVersion(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @RequestBody MisionVisionRestoreRequest request
    ) {
        User user = requireAccess(authentication, 2);
        MisionVisionVersionResponse created = service.restaurarVersion(id, request, user.getUsername());
        return ResponseEntity
                .created(URI.create("/api/organigrama/mision-vision/versiones/" + created.id()))
                .body(created);
    }

    private User requireAccess(Authentication authentication, int minNivel) {
        return accessGuard.requireTabAccess(
                authentication,
                ModuloSistema.ORGANIGRAMA,
                TAB_MISION_VISION,
                minNivel,
                "No tiene permisos suficientes para administrar mision, vision y valores."
        );
    }
}
