package exotic.app.planta.security;

import exotic.app.planta.model.users.ModuloSistema;
import exotic.app.planta.model.users.User;
import exotic.app.planta.model.users.UserAccessEvaluator;
import exotic.app.planta.repo.usuarios.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@Component
@RequiredArgsConstructor
public class ModuleTabAccessGuard {

    private final UserRepository userRepository;

    public User requireTabAccess(
            Authentication authentication,
            ModuloSistema modulo,
            String tabId,
            int minNivel,
            String forbiddenMessage
    ) {
        User user = requireAuthenticatedUser(authentication);
        if (isMasterLike(user.getUsername())) {
            return user;
        }

        int nivel = UserAccessEvaluator.tabNivel(user, modulo, tabId).orElse(0);
        if (nivel < minNivel) {
            throw new ResponseStatusException(FORBIDDEN, forbiddenMessage);
        }
        return user;
    }

    /**
     * Variante para decisiones reguladas que nunca heredan privilegios por
     * nombre de usuario. El nivel debe estar asignado explicitamente.
     */
    public User requireTabAccessWithoutMasterBypass(
            Authentication authentication,
            ModuloSistema modulo,
            String tabId,
            int minNivel,
            String forbiddenMessage
    ) {
        User user = requireAuthenticatedUser(authentication);
        int nivel = UserAccessEvaluator.tabNivel(user, modulo, tabId).orElse(0);
        if (nivel < minNivel) {
            throw new ResponseStatusException(FORBIDDEN, forbiddenMessage);
        }
        return user;
    }

    /** SuperMaster conserva acceso inicial; master requiere asignacion explicita. */
    public User requireTabAccessWithSuperMasterBypass(
            Authentication authentication,
            ModuloSistema modulo,
            String tabId,
            int minNivel,
            String forbiddenMessage
    ) {
        User user = requireAuthenticatedUser(authentication);
        if ("super_master".equalsIgnoreCase(user.getUsername())) {
            return user;
        }
        int nivel = UserAccessEvaluator.tabNivel(user, modulo, tabId).orElse(0);
        if (nivel < minNivel) {
            throw new ResponseStatusException(FORBIDDEN, forbiddenMessage);
        }
        return user;
    }

    /** Autoriza cuando al menos uno de los tabs alcanza su nivel; solo SuperMaster tiene bypass. */
    public User requireAnyTabAccessWithSuperMasterBypass(
            Authentication authentication,
            ModuloSistema modulo,
            Map<String, Integer> tabsYNiveles,
            String forbiddenMessage
    ) {
        User user = requireAuthenticatedUser(authentication);
        if ("super_master".equalsIgnoreCase(user.getUsername())) return user;
        boolean permitido = tabsYNiveles.entrySet().stream().anyMatch(entry ->
                UserAccessEvaluator.tabNivel(user, modulo, entry.getKey()).orElse(0) >= entry.getValue());
        if (!permitido) throw new ResponseStatusException(FORBIDDEN, forbiddenMessage);
        return user;
    }

    private User requireAuthenticatedUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(UNAUTHORIZED, "No autenticado");
        }

        return userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(UNAUTHORIZED, "Usuario no encontrado"));
    }

    private static boolean isMasterLike(String username) {
        if (username == null) return false;
        String normalized = username.toLowerCase();
        return "master".equals(normalized) || "super_master".equals(normalized);
    }
}
