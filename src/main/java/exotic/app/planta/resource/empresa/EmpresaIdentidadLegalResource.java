package exotic.app.planta.resource.empresa;

import exotic.app.planta.model.empresa.EmpresaIdentidadLegalVersion;
import exotic.app.planta.model.empresa.dto.EmpresaIdentidadLegalVersionRequest;
import exotic.app.planta.model.users.ModuloSistema;
import exotic.app.planta.model.users.User;
import exotic.app.planta.model.users.UserAccessEvaluator;
import exotic.app.planta.repo.usuarios.UserRepository;
import exotic.app.planta.service.empresa.EmpresaIdentidadLegalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.List;

import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@RestController
@RequestMapping("/api/empresa-identidad-legal")
@RequiredArgsConstructor
public class EmpresaIdentidadLegalResource {

    private static final String TAB_IDENTIDAD_LEGAL = "IDENTIDAD_LEGAL";

    private final EmpresaIdentidadLegalService service;
    private final UserRepository userRepository;

    @GetMapping("/vigente")
    public ResponseEntity<EmpresaIdentidadLegalVersion> getVigente(Authentication authentication) {
        requireAuthenticatedUser(authentication);
        return ResponseEntity.ok(service.getVigente());
    }

    @GetMapping("/versiones")
    public ResponseEntity<List<EmpresaIdentidadLegalVersion>> getVersiones(Authentication authentication) {
        requireTabAccess(authentication, 1);
        return ResponseEntity.ok(service.getVersiones());
    }

    @PostMapping("/versiones")
    public ResponseEntity<EmpresaIdentidadLegalVersion> crearVersion(
            Authentication authentication,
            @Valid @RequestBody EmpresaIdentidadLegalVersionRequest request
    ) {
        User user = requireTabAccess(authentication, 2);
        EmpresaIdentidadLegalVersion created = service.crearNuevaVersion(request, user.getUsername());
        return ResponseEntity
                .created(URI.create("/api/empresa-identidad-legal/versiones/" + created.getId()))
                .body(created);
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
            throw new ResponseStatusException(FORBIDDEN, "No tiene permisos para administrar identidad legal.");
        }

        return user;
    }

    private static boolean isMasterLike(String username) {
        if (username == null) return false;
        String normalized = username.toLowerCase();
        return "master".equals(normalized) || "super_master".equals(normalized);
    }
}
