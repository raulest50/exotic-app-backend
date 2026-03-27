package exotic.app.planta.model.users;

import java.util.OptionalInt;

/**
 * Consultas de permiso sobre {@link User} con el modelo ModuloAcceso / TabAcceso.
 */
public final class UserPermissionQueries {

    private UserPermissionQueries() {
    }

    /**
     * Nivel máximo entre todos los tabs del módulo indicado (vacío si no hay acceso al módulo).
     */
    public static OptionalInt maxNivelForModulo(User user, ModuloSistema modulo) {
        return user.getModuloAccesos().stream()
                .filter(ma -> ma.getModulo() == modulo)
                .flatMap(ma -> ma.getTabs().stream())
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
