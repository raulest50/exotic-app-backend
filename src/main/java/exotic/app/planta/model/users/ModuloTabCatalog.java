package exotic.app.planta.model.users;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Tabs permitidas por módulo (ids estables compartidos con el frontend).
 */
public final class ModuloTabCatalog {

    private static final Map<ModuloSistema, List<String>> ALLOWED = new EnumMap<>(ModuloSistema.class);

    static {
        for (ModuloSistema m : ModuloSistema.values()) {
            ALLOWED.put(m, List.of("MAIN"));
        }
        ALLOWED.put(ModuloSistema.USUARIOS, List.of(
                "GESTION_USUARIOS",
                "INFO_NIVELES",
                "NOTIFICACIONES"
        ));
    }

    private ModuloTabCatalog() {
    }

    public static List<String> allowedTabIds(ModuloSistema modulo) {
        return ALLOWED.getOrDefault(modulo, List.of());
    }

    public static void validateAssignments(ModuloSistema modulo, Set<String> tabIds) {
        List<String> allowed = allowedTabIds(modulo);
        for (String tabId : tabIds) {
            if (!allowed.contains(tabId)) {
                throw new IllegalArgumentException("Tab no permitido para el módulo " + modulo + ": " + tabId);
            }
        }
    }

    public static Map<ModuloSistema, List<String>> allDefinitions() {
        return Collections.unmodifiableMap(ALLOWED);
    }
}
