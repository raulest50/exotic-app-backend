package exotic.app.planta.resource.empresa;

import exotic.app.planta.dto.ErrorResponse;
import exotic.app.planta.model.empresa.dto.JornadaLaboralVersionRequest;
import exotic.app.planta.model.empresa.dto.JornadaLaboralVersionResponse;
import exotic.app.planta.model.users.ModuloSistema;
import exotic.app.planta.model.users.User;
import exotic.app.planta.model.users.UserAccessEvaluator;
import exotic.app.planta.repo.usuarios.UserRepository;
import exotic.app.planta.service.empresa.JornadaLaboralService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
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
@RequestMapping("/api/jornada-laboral")
@RequiredArgsConstructor
public class JornadaLaboralResource {

    private static final String TAB_JORNADA_LABORAL = "JORNADA_LABORAL";

    private final JornadaLaboralService service;
    private final UserRepository userRepository;

    @GetMapping("/vigente")
    public ResponseEntity<JornadaLaboralVersionResponse> getVigente(Authentication authentication) {
        requireTabAccess(authentication, 1);
        return ResponseEntity.ok(service.getVigente());
    }

    @GetMapping("/versiones")
    public ResponseEntity<List<JornadaLaboralVersionResponse>> getVersiones(Authentication authentication) {
        requireTabAccess(authentication, 1);
        return ResponseEntity.ok(service.getVersiones());
    }

    @PostMapping("/versiones")
    public ResponseEntity<JornadaLaboralVersionResponse> crearVersion(
            Authentication authentication,
            @Valid @RequestBody JornadaLaboralVersionRequest request
    ) {
        User user = requireTabAccess(authentication, 2);
        JornadaLaboralVersionResponse created = service.crearNuevaVersion(request, user.getUsername());
        return ResponseEntity
                .created(URI.create("/api/jornada-laboral/versiones/" + created.getId()))
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
                .tabNivel(user, ModuloSistema.ADMINISTRACION_GLOBAL, TAB_JORNADA_LABORAL)
                .orElse(0);

        if (nivel < minNivel) {
            throw new ResponseStatusException(FORBIDDEN, "No tiene permisos para administrar jornada laboral.");
        }

        return user;
    }

    private static boolean isMasterLike(String username) {
        if (username == null) return false;
        String normalized = username.toLowerCase();
        return "master".equals(normalized) || "super_master".equals(normalized);
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<ErrorResponse> handleBusinessError(RuntimeException error) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("Error en jornada laboral", error.getMessage()));
    }
}
