package exotic.app.planta.model.users;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Fuente de verdad backend para módulos y tabs válidos.
 */
public final class MapaAccesos {

    public record TabDef(String tabId) {
    }

    public record ModuloDef(ModuloSistema modulo, List<TabDef> tabs, boolean especial) {
    }

    private static final Map<ModuloSistema, ModuloDef> DEFINITIONS = new EnumMap<>(ModuloSistema.class);

    static {
        for (ModuloSistema modulo : ModuloSistema.values()) {
            DEFINITIONS.put(modulo, modulo(modulo, false, "MAIN"));
        }

        DEFINITIONS.put(ModuloSistema.USUARIOS, modulo(ModuloSistema.USUARIOS, false, "GESTION_USUARIOS", "INFO_NIVELES", "NOTIFICACIONES"));
        DEFINITIONS.put(ModuloSistema.ACTIVOS, modulo(ModuloSistema.ACTIVOS, false, "INCORPORACION", "CREAR_OC_AF", "REPORTES_OC_AF", "REPORTES_ACTIVOS_FIJOS"));
        DEFINITIONS.put(ModuloSistema.BINTELLIGENCE, modulo(ModuloSistema.BINTELLIGENCE, false, "INFORMES_DIARIOS", "SERIES_TIEMPO_PROYECCIONES"));
        DEFINITIONS.put(ModuloSistema.CLIENTES, modulo(ModuloSistema.CLIENTES, false, "REGISTRAR_CLIENTE", "CONSULTAR_CLIENTES"));
        DEFINITIONS.put(ModuloSistema.COMPRAS, modulo(ModuloSistema.COMPRAS, false, "CREAR_OCM", "REPORTES_ORDENES_COMPRA"));
        DEFINITIONS.put(ModuloSistema.SEGUIMIENTO_PRODUCCION, modulo(ModuloSistema.SEGUIMIENTO_PRODUCCION, false, "CREAR_AREA_PRODUCCION", "CONSULTA_AREAS_OPERATIVAS"));
        DEFINITIONS.put(ModuloSistema.MASTER_DIRECTIVES, modulo(ModuloSistema.MASTER_DIRECTIVES, true, "MAIN"));
        DEFINITIONS.put(ModuloSistema.OPERACIONES_CRITICAS_BD, modulo(ModuloSistema.OPERACIONES_CRITICAS_BD, true, "CARGA_MASIVA_ALMACEN", "CARGA_MASIVA_MATERIALES", "CARGA_MASIVA_TERMINADOS", "ELIMINACIONES_FORZADAS", "EXPORTACION_DATOS"));
        DEFINITIONS.put(ModuloSistema.ORGANIGRAMA, modulo(ModuloSistema.ORGANIGRAMA, false, "ORGANIGRAMA", "MISION_VISION"));
        DEFINITIONS.put(ModuloSistema.PAGOS_PROVEEDORES, modulo(ModuloSistema.PAGOS_PROVEEDORES, false, "ASENTAR_TRANSACCIONES_ALMACEN", "FACTURAS_VENCIDAS"));
        DEFINITIONS.put(ModuloSistema.PERSONAL_PLANTA, modulo(ModuloSistema.PERSONAL_PLANTA, false, "INCORPORACION", "CONSULTA"));
        DEFINITIONS.put(ModuloSistema.PRODUCCION, modulo(ModuloSistema.PRODUCCION, false, "CREAR_ODP_MANUALMENTE", "HISTORIAL", "PARAMETROS_POR_CATEGORIA", "PLANEACION_PRODUCCION"));
        DEFINITIONS.put(ModuloSistema.PROVEEDORES, modulo(ModuloSistema.PROVEEDORES, false, "CODIFICAR_PROVEEDOR", "CONSULTAR_PROVEEDORES"));
        DEFINITIONS.put(ModuloSistema.STOCK, modulo(ModuloSistema.STOCK, false, "CONSOLIDADO", "KARDEX", "HISTORIAL_TRANSACCIONES_ALMACEN"));
        DEFINITIONS.put(ModuloSistema.TRANSACCIONES_ALMACEN, modulo(ModuloSistema.TRANSACCIONES_ALMACEN, false, "INGRESO_OCM", "HACER_DISPENSACION", "HISTORIAL_DISPENSACIONES", "INGRESO_PRODUCTO_TERMINADO", "GESTION_AVERIAS", "AJUSTES_INVENTARIO"));
        DEFINITIONS.put(ModuloSistema.VENTAS, modulo(ModuloSistema.VENTAS, false, "CREAR_VENTA", "HISTORIAL_VENTAS", "REPORTES", "CREAR_VENDEDOR_NUEVO"));
    }

    private MapaAccesos() {
    }

    private static ModuloDef modulo(ModuloSistema modulo, boolean especial, String... tabIds) {
        return new ModuloDef(
                modulo,
                List.of(tabIds).stream().map(TabDef::new).toList(),
                especial
        );
    }

    public static ModuloDef modulo(ModuloSistema modulo) {
        return DEFINITIONS.getOrDefault(modulo, new ModuloDef(modulo, List.of(), false));
    }

    public static List<String> allowedTabIds(ModuloSistema modulo) {
        return modulo(modulo).tabs().stream().map(TabDef::tabId).toList();
    }

    public static boolean isEspecial(ModuloSistema modulo) {
        return modulo(modulo).especial();
    }

    public static void validateAssignments(ModuloSistema modulo, Set<String> tabIds) {
        List<String> allowed = allowedTabIds(modulo);
        for (String tabId : tabIds) {
            if (!allowed.contains(tabId)) {
                throw new IllegalArgumentException("Tab no permitido para el módulo " + modulo + ": " + tabId);
            }
        }
    }

    public static Map<ModuloSistema, ModuloDef> all() {
        return Collections.unmodifiableMap(DEFINITIONS);
    }
}
