package exotic.app.planta.resource.users;

import exotic.app.planta.model.users.ModuloSistema;
import exotic.app.planta.model.users.User;
import exotic.app.planta.model.users.firma.FirmaVisualUsuarioVersion;
import exotic.app.planta.model.users.firma.dto.FirmaVisualUsuarioActualResponse;
import exotic.app.planta.model.users.firma.dto.FirmaVisualUsuarioVersionResponse;
import exotic.app.planta.model.users.firma.dto.RetirarFirmaVisualUsuarioRequest;
import exotic.app.planta.security.ModuleTabAccessGuard;
import exotic.app.planta.service.users.FirmaVisualUsuarioService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.time.Duration;
import java.util.List;

import static org.springframework.http.HttpHeaders.IF_NONE_MATCH;

@RestController
@RequestMapping("/usuarios/{usuarioId}/firma-visual")
@RequiredArgsConstructor
public class FirmaVisualUsuarioResource {

    private static final String TAB_GESTION_USUARIOS = "GESTION_USUARIOS";
    private static final int NIVEL_ADMINISTRACION = 2;
    private static final CacheControl METADATA_CACHE_CONTROL = CacheControl.noCache().cachePrivate();

    private final FirmaVisualUsuarioService service;
    private final ModuleTabAccessGuard accessGuard;

    @GetMapping
    public ResponseEntity<FirmaVisualUsuarioActualResponse> getActual(
            Authentication authentication,
            @PathVariable Long usuarioId
    ) {
        requireAccess(authentication);
        return ResponseEntity.ok()
                .cacheControl(METADATA_CACHE_CONTROL)
                .body(service.getActual(usuarioId));
    }

    @GetMapping("/imagen")
    public ResponseEntity<byte[]> getImagenVigente(
            Authentication authentication,
            @PathVariable Long usuarioId,
            @RequestHeader(value = IF_NONE_MATCH, required = false) String ifNoneMatch
    ) {
        requireAccess(authentication);
        return imageResponse(service.getVigente(usuarioId), ifNoneMatch, false);
    }

    @GetMapping("/versiones")
    public ResponseEntity<List<FirmaVisualUsuarioVersionResponse>> getVersiones(
            Authentication authentication,
            @PathVariable Long usuarioId
    ) {
        requireAccess(authentication);
        return ResponseEntity.ok()
                .cacheControl(METADATA_CACHE_CONTROL)
                .body(service.getVersiones(usuarioId));
    }

    @GetMapping("/versiones/{versionId}/imagen")
    public ResponseEntity<byte[]> getImagenVersion(
            Authentication authentication,
            @PathVariable Long usuarioId,
            @PathVariable Long versionId,
            @RequestHeader(value = IF_NONE_MATCH, required = false) String ifNoneMatch
    ) {
        requireAccess(authentication);
        return imageResponse(service.getVersion(usuarioId, versionId), ifNoneMatch, true);
    }

    @PostMapping(value = "/versiones", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<FirmaVisualUsuarioVersionResponse> crearVersion(
            Authentication authentication,
            @PathVariable Long usuarioId,
            @RequestPart("firma") MultipartFile firma,
            @RequestPart("motivoCambio") String motivoCambio
    ) {
        User administrador = requireAccess(authentication);
        FirmaVisualUsuarioVersion created = service.crearNuevaVersion(
                usuarioId,
                firma,
                motivoCambio,
                administrador
        );
        return ResponseEntity
                .created(URI.create(
                        "/usuarios/" + usuarioId + "/firma-visual/versiones/" + created.getId()
                ))
                .body(FirmaVisualUsuarioVersionResponse.from(created));
    }

    @PostMapping("/retirar")
    public ResponseEntity<FirmaVisualUsuarioVersionResponse> retirar(
            Authentication authentication,
            @PathVariable Long usuarioId,
            @RequestBody RetirarFirmaVisualUsuarioRequest request
    ) {
        User administrador = requireAccess(authentication);
        String motivo = request != null ? request.motivo() : null;
        return ResponseEntity.ok(FirmaVisualUsuarioVersionResponse.from(
                service.retirar(usuarioId, motivo, administrador)
        ));
    }

    private User requireAccess(Authentication authentication) {
        return accessGuard.requireTabAccess(
                authentication,
                ModuloSistema.USUARIOS,
                TAB_GESTION_USUARIOS,
                NIVEL_ADMINISTRACION,
                "No tiene permisos para administrar firmas visuales de usuarios."
        );
    }

    private static ResponseEntity<byte[]> imageResponse(
            FirmaVisualUsuarioVersion version,
            String ifNoneMatch,
            boolean immutable
    ) {
        String etag = quoteEtag(version.getSha256());
        CacheControl cacheControl = immutable
                ? CacheControl.maxAge(Duration.ofDays(365)).cachePrivate().immutable()
                : CacheControl.noCache().cachePrivate();

        if (etagMatches(ifNoneMatch, etag)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                    .cacheControl(cacheControl)
                    .eTag(etag)
                    .build();
        }

        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .contentLength(version.getTamanoBytes())
                .cacheControl(cacheControl)
                .eTag(etag)
                .body(version.getContenido());
    }

    private static String quoteEtag(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return "\"" + value.replace("\"", "") + "\"";
    }

    private static boolean etagMatches(String ifNoneMatch, String etag) {
        if (ifNoneMatch == null || etag == null) {
            return false;
        }
        String normalized = ifNoneMatch.trim();
        return "*".equals(normalized)
                || etag.equals(normalized)
                || ("W/" + etag).equals(normalized);
    }
}
