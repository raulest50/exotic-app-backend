package exotic.app.planta.model.users;

import java.util.OptionalInt;

/**
 * Consultas reutilizables sobre el snapshot de accesos de un usuario.
 */
public final class UserAccessEvaluator {

    private UserAccessEvaluator() {
    }

    public static OptionalInt maxNivelForModulo(User user, ModuloSistema modulo) {
        return user.getModuloAccesos().stream()
                .filter(ma -> ma.getModulo() == modulo)
                .flatMap(ma -> ma.getTabs().stream())
                .mapToInt(TabAcceso::getNivel)
                .max();
    }

    public static boolean hasModulo(User user, ModuloSistema modulo) {
        return user.getModuloAccesos().stream().anyMatch(ma -> ma.getModulo() == modulo);
    }

    public static boolean hasTab(User user, ModuloSistema modulo, String tabId) {
        return user.getModuloAccesos().stream()
                .filter(ma -> ma.getModulo() == modulo)
                .flatMap(ma -> ma.getTabs().stream())
                .anyMatch(t -> t.getTabId().equals(tabId));
    }

    public static OptionalInt tabNivel(User user, ModuloSistema modulo, String tabId) {
        return user.getModuloAccesos().stream()
                .filter(ma -> ma.getModulo() == modulo)
                .flatMap(ma -> ma.getTabs().stream())
                .filter(t -> t.getTabId().equals(tabId))
                .mapToInt(TabAcceso::getNivel)
                .max();
    }

    public static boolean hasModuloWithTabNivel(User user, ModuloSistema modulo, int nivel) {
        return user.getModuloAccesos().stream()
                .filter(ma -> ma.getModulo() == modulo)
                .flatMap(ma -> ma.getTabs().stream())
                .anyMatch(t -> t.getNivel() == nivel);
    }
}
