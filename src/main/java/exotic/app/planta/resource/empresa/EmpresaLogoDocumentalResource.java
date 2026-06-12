package exotic.app.planta.resource.empresa;

import exotic.app.planta.model.empresa.EmpresaLogoDocumentalVersion;
import exotic.app.planta.model.users.ModuloSistema;
import exotic.app.planta.model.users.User;
import exotic.app.planta.model.users.UserAccessEvaluator;
import exotic.app.planta.repo.usuarios.UserRepository;
import exotic.app.planta.service.empresa.EmpresaLogoDocumentalService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@RestController
@RequestMapping("/api/empresa-logo-documental")
@RequiredArgsConstructor
public class EmpresaLogoDocumentalResource {

    private static final String TAB_IDENTIDAD_LEGAL = "IDENTIDAD_LEGAL";

    private final EmpresaLogoDocumentalService service;
    private final UserRepository userRepository;

    @GetMapping("/vigente")
    public ResponseEntity<EmpresaLogoDocumentalVersion> getVigente(Authentication authentication) {
        requireAuthenticatedUser(authentication);
        return ResponseEntity.ok(service.getVigente());
    }

    @GetMapping("/vigente/imagen")
    public ResponseEntity<byte[]> getImagenVigente(Authentication authentication) {
        requireAuthenticatedUser(authentication);
        EmpresaLogoDocumentalVersion vigente = service.getVigente();
        return imageResponse(vigente);
    }

    @GetMapping("/versiones")
    public ResponseEntity<List<EmpresaLogoDocumentalVersion>> getVersiones(Authentication authentication) {
        requireTabAccess(authentication, 1);
        return ResponseEntity.ok(service.getVersiones());
    }

    @GetMapping("/versiones/{id}/imagen")
    public ResponseEntity<byte[]> getImagenVersion(
            Authentication authentication,
            @PathVariable Long id
    ) {
        requireAuthenticatedUser(authentication);
        return imageResponse(service.getVersion(id));
    }

    @PostMapping(value = "/versiones", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<EmpresaLogoDocumentalVersion> crearVersion(
            Authentication authentication,
            @RequestPart("logo") MultipartFile logo,
            @RequestPart("motivoCambio") String motivoCambio
    ) {
        User user = requireTabAccess(authentication, 2);
        EmpresaLogoDocumentalVersion created = service.crearNuevaVersion(logo, motivoCambio, user.getUsername());
        return ResponseEntity
                .created(URI.create("/api/empresa-logo-documental/versiones/" + created.getId()))
                .body(created);
    }

    private static ResponseEntity<byte[]> imageResponse(EmpresaLogoDocumentalVersion version) {
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .contentLength(version.getTamanoBytes())
                .cacheControl(CacheControl.maxAge(5, TimeUnit.MINUTES).cachePrivate())
                .body(version.getContenido());
    }

    private User requireAuthenticatedUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(UNAUTHORIZED, "No autenticado");
        }

        return userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(UNAUTHORIZED, "Usuario no encontrado"));
    }

    private User requireTabAccess(Authentication authentication, int minNivel) {
        User user = requireAuthenticatedUser(authentication);
        if (isMasterLike(user.getUsername())) {
            return user;
        }

        int nivel = UserAccessEvaluator
                .tabNivel(user, ModuloSistema.ADMINISTRACION_GLOBAL, TAB_IDENTIDAD_LEGAL)
                .orElse(0);

        if (nivel < minNivel) {
            throw new ResponseStatusException(FORBIDDEN, "No tiene permisos para administrar logo documental.");
        }

        return user;
    }

    private static boolean isMasterLike(String username) {
        if (username == null) return false;
        String normalized = username.toLowerCase();
        return "master".equals(normalized) || "super_master".equals(normalized);
    }
}
