package exotic.app.planta.service.users;

import exotic.app.planta.model.users.ModuloSistema;
import exotic.app.planta.model.users.User;
import exotic.app.planta.model.users.UserAccessEvaluator;
import exotic.app.planta.repo.usuarios.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

/**
 * Autorización del asistente a partir del usuario vigente en la base de datos.
 * No ejecuta preguntas ni operaciones de negocio. Las futuras operaciones deben
 * volver a aplicar requireRead o requireWrite antes de acceder a datos.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AgentAssistantAccessService {

    private static final String TAB_MAIN = "MAIN";
    private static final int NIVEL_LECTURA = 1;
    private static final int NIVEL_ESCRITURA = 2;

    private final UserRepository userRepository;

    public Access getAccess(Authentication authentication) {
        User user = requireActiveUser(authentication);
        int nivel = effectiveNivel(user);
        return new Access(nivel, nivel >= NIVEL_LECTURA, nivel >= NIVEL_ESCRITURA);
    }

    public User requireRead(Authentication authentication) {
        return requireNivel(authentication, NIVEL_LECTURA);
    }

    public User requireWrite(Authentication authentication) {
        return requireNivel(authentication, NIVEL_ESCRITURA);
    }

    private User requireNivel(Authentication authentication, int minNivel) {
        User user = requireActiveUser(authentication);
        if (effectiveNivel(user) < minNivel) {
            throw new ResponseStatusException(FORBIDDEN, "No tiene permisos suficientes para el asistente.");
        }
        return user;
    }

    private User requireActiveUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new ResponseStatusException(UNAUTHORIZED, "No autenticado");
        }
        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(UNAUTHORIZED, "Usuario no encontrado"));
        // Para el asistente, master siempre se considera activo, incluso con un estado legado.
        if (!"master".equalsIgnoreCase(user.getUsername()) && user.getEstado() != 1) {
            throw new ResponseStatusException(FORBIDDEN, "El usuario no está activo.");
        }
        return user;
    }

    private int effectiveNivel(User user) {
        // Conserva la misma excepción administrativa que los módulos existentes.
        if ("master".equalsIgnoreCase(user.getUsername())
                || "super_master".equalsIgnoreCase(user.getUsername())) {
            return NIVEL_ESCRITURA;
        }
        int nivel = UserAccessEvaluator.tabNivel(user, ModuloSistema.AGENT_ASSISTANT, TAB_MAIN).orElse(0);
        // Un valor inesperado no concede capacidades nuevas por comparación >=.
        return nivel == NIVEL_LECTURA || nivel == NIVEL_ESCRITURA ? nivel : 0;
    }

    public record Access(int nivel, boolean canRead, boolean canWrite) {
    }
}
