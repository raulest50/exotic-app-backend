package exotic.app.planta.resource.empresa;

import exotic.app.planta.model.empresa.dto.EmpresaIdentidadDocumentalVigenteResponse;
import exotic.app.planta.repo.usuarios.UserRepository;
import exotic.app.planta.service.empresa.EmpresaIdentidadDocumentalService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpHeaders.IF_NONE_MATCH;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@RestController
@RequestMapping("/api/empresa-identidad-documental")
@RequiredArgsConstructor
public class EmpresaIdentidadDocumentalResource {

    private static final CacheControl CURRENT_CACHE_CONTROL = CacheControl.noCache().cachePrivate();

    private final EmpresaIdentidadDocumentalService service;
    private final UserRepository userRepository;

    @GetMapping("/vigente")
    public ResponseEntity<EmpresaIdentidadDocumentalVigenteResponse> getVigente(
            Authentication authentication,
            @RequestHeader(value = IF_NONE_MATCH, required = false) String ifNoneMatch
    ) {
        requireAuthenticatedUser(authentication);
        EmpresaIdentidadDocumentalVigenteResponse vigente = service.getVigente();
        String etag = quoteEtag(vigente.revision());

        if (etagMatches(ifNoneMatch, etag)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                    .cacheControl(CURRENT_CACHE_CONTROL)
                    .eTag(etag)
                    .build();
        }

        return ResponseEntity.ok()
                .cacheControl(CURRENT_CACHE_CONTROL)
                .eTag(etag)
                .body(vigente);
    }

    private void requireAuthenticatedUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(UNAUTHORIZED, "No autenticado");
        }

        userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(UNAUTHORIZED, "Usuario no encontrado"));
    }

    static String quoteEtag(String value) {
        return "\"" + value.replace("\"", "") + "\"";
    }

    static boolean etagMatches(String ifNoneMatch, String currentEtag) {
        if (ifNoneMatch == null || ifNoneMatch.isBlank()) {
            return false;
        }
        for (String candidate : ifNoneMatch.split(",")) {
            String normalized = candidate.trim();
            if ("*".equals(normalized)) {
                return true;
            }
            if (normalized.startsWith("W/")) {
                normalized = normalized.substring(2).trim();
            }
            if (currentEtag.equals(normalized)) {
                return true;
            }
        }
        return false;
    }
}
