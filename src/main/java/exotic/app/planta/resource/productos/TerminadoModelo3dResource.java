package exotic.app.planta.resource.productos;

import exotic.app.planta.model.users.ModuloSistema;
import exotic.app.planta.model.users.UserAccessEvaluator;
import exotic.app.planta.repo.usuarios.UserRepository;
import exotic.app.planta.service.productos.fotogrametria.TerminadoModelo3dService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;

@RestController
@RequestMapping("/productos/terminados/{productoId}/modelo-3d")
@RequiredArgsConstructor
@Slf4j
public class TerminadoModelo3dResource {
    private static final MediaType GLB = MediaType.parseMediaType("model/gltf-binary");
    private final TerminadoModelo3dService service;
    private final UserRepository userRepository;

    @GetMapping("/metadata")
    public ResponseEntity<TerminadoModelo3dService.ModelMetadata> metadata(
            @PathVariable String productoId, Authentication authentication) {
        requireReadAccess(authentication);
        try {
            var metadata = service.metadata(productoId);
            return ResponseEntity.ok().eTag(metadata.etag()).cacheControl(CacheControl.noStore()).body(metadata);
        } catch (IOException exception) {
            throw storageFailure(exception);
        }
    }

    @GetMapping(produces = "model/gltf-binary")
    public ResponseEntity<byte[]> download(@PathVariable String productoId, Authentication authentication) {
        requireReadAccess(authentication);
        try {
            var download = service.load(productoId);
            return ResponseEntity.ok().contentType(GLB).contentLength(download.metadata().contentLength())
                    .eTag(download.metadata().etag()).cacheControl(CacheControl.noStore())
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"modelo.glb\"")
                    .header("X-Content-Type-Options", "nosniff").body(download.bytes());
        } catch (IOException exception) {
            throw storageFailure(exception);
        }
    }

    @PutMapping(consumes = "model/gltf-binary")
    public ResponseEntity<TerminadoModelo3dService.ModelMetadata> save(
            @PathVariable String productoId, HttpServletRequest request, Authentication authentication,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {
        requireAccess(authentication, 2);
        try {
            var metadata = service.save(productoId, request.getInputStream(), request.getContentLengthLong(), ifMatch, ifNoneMatch);
            return ResponseEntity.ok().eTag(metadata.etag()).cacheControl(CacheControl.noStore()).body(metadata);
        } catch (IOException exception) {
            throw storageFailure(exception);
        }
    }

    @DeleteMapping
    public ResponseEntity<Void> delete(@PathVariable String productoId, Authentication authentication,
                                      @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        requireAccess(authentication, 2);
        try {
            service.delete(productoId, ifMatch);
            return ResponseEntity.noContent().build();
        } catch (IOException exception) {
            throw storageFailure(exception);
        }
    }

    private void requireReadAccess(Authentication authentication) {
        requireAccess(authentication, 1, true);
    }

    private void requireAccess(Authentication authentication, int minimum) {
        requireAccess(authentication, minimum, false);
    }

    private void requireAccess(Authentication authentication, int minimum, boolean allowQualityRead) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No autenticado.");
        }
        var user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario no encontrado."));
        if ("master".equalsIgnoreCase(user.getUsername()) || "super_master".equalsIgnoreCase(user.getUsername())) return;
        if (allowQualityRead && UserAccessEvaluator.tabNivel(
                user, ModuloSistema.CALIDAD, "CATALOGO_FORMULACION").orElse(0) >= 1) return;
        int level = UserAccessEvaluator.tabNivel(user, ModuloSistema.PRODUCTOS, "PROCESS_DESIGNER")
                .orElseGet(() -> UserAccessEvaluator.tabNivel(user, ModuloSistema.PRODUCTOS, "MAIN").orElse(0));
        if (level < minimum) throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "No tiene permiso para esta operacion en Process Designer.");
    }

    private ResponseStatusException storageFailure(IOException exception) {
        log.error("No fue posible completar una operacion de almacenamiento del modelo 3D.", exception);
        return new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                "No fue posible acceder al almacenamiento del modelo 3D. Puede volver a intentarlo.");
    }
}
