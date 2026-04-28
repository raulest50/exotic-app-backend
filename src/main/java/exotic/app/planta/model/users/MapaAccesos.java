package exotic.app.planta.model.users;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/**
 * Catalogo estatico de tabs validos por modulo.
 */
public final class MapaAccesos {

    private static final Map<ModuloSistema, Set<String>> TABS_POR_MODULO = new EnumMap<>(ModuloSistema.class);

    static {
        for (ModuloSistema modulo : ModuloSistema.values()) {
            TABS_POR_MODULO.put(modulo, Set.of("MAIN"));
        }

        TABS_POR_MODULO.put(ModuloSistema.USUARIOS, Set.of("GESTION_USUARIOS", "INFO_NIVELES", "NOTIFICACIONES"));
        TABS_POR_MODULO.put(ModuloSistema.ACTIVOS, Set.of("INCORPORACION", "CREAR_OC_AF", "REPORTES_OC_AF", "REPORTES_ACTIVOS_FIJOS"));
        TABS_POR_MODULO.put(
                ModuloSistema.BINTELLIGENCE,
                Set.of("INFORMES_DIARIOS", "SERIES_TIEMPO_PROYECCIONES", "APROVISIONAMIENTO")
        );
        TABS_POR_MODULO.put(ModuloSistema.CLIENTES, Set.of("REGISTRAR_CLIENTE", "CONSULTAR_CLIENTES"));
        TABS_POR_MODULO.put(ModuloSistema.COMPRAS, Set.of("CREAR_OCM", "REPORTES_ORDENES_COMPRA"));
        TABS_POR_MODULO.put(ModuloSistema.SEGUIMIENTO_PRODUCCION, Set.of("CREAR_AREA_PRODUCCION", "CONSULTA_AREAS_OPERATIVAS"));
        TABS_POR_MODULO.put(ModuloSistema.MASTER_DIRECTIVES, Set.of("MAIN"));
        TABS_POR_MODULO.put(ModuloSistema.OPERACIONES_CRITICAS_BD, Set.of("CARGAS_MASIVAS", "ELIMINACIONES_FORZADAS", "EXPORTACION_DATOS"));
        TABS_POR_MODULO.put(ModuloSistema.ORGANIGRAMA, Set.of("ORGANIGRAMA", "MISION_VISION"));
        TABS_POR_MODULO.put(ModuloSistema.PAGOS_PROVEEDORES, Set.of("ASENTAR_TRANSACCIONES_ALMACEN", "FACTURAS_VENCIDAS"));
        TABS_POR_MODULO.put(ModuloSistema.PERSONAL_PLANTA, Set.of("INCORPORACION", "CONSULTA"));
        TABS_POR_MODULO.put(
                ModuloSistema.PRODUCCION,
                Set.of(
                        "CREAR_ODP_MANUALMENTE",
                        "HISTORIAL",
                        "PARAMETROS_POR_CATEGORIA",
                        "PLANEACION_PRODUCCION",
                        "MONITOREAR_AREAS_OPERATIVAS",
                        "APROBACION_MPS_WEEK"
                )
        );
        TABS_POR_MODULO.put(ModuloSistema.PROVEEDORES, Set.of("CODIFICAR_PROVEEDOR", "CONSULTAR_PROVEEDORES"));
        TABS_POR_MODULO.put(ModuloSistema.STOCK, Set.of("CONSOLIDADO", "KARDEX", "HISTORIAL_TRANSACCIONES_ALMACEN"));
        TABS_POR_MODULO.put(ModuloSistema.TRANSACCIONES_ALMACEN, Set.of("INGRESO_OCM", "HACER_DISPENSACION", "HISTORIAL_DISPENSACIONES", "INGRESO_PRODUCTO_TERMINADO", "GESTION_AVERIAS", "AJUSTES_INVENTARIO"));
        TABS_POR_MODULO.put(ModuloSistema.VENTAS, Set.of("CREAR_VENTA", "HISTORIAL_VENTAS", "REPORTES", "CREAR_VENDEDOR_NUEVO"));
    }

    private MapaAccesos() {
    }

    public static Set<String> allowedTabIds(ModuloSistema modulo) {
        return TABS_POR_MODULO.getOrDefault(modulo, Set.of());
    }

    public static boolean containsTab(ModuloSistema modulo, String tabId) {
        return allowedTabIds(modulo).contains(tabId);
    }

    public static void validateAssignments(ModuloSistema modulo, Set<String> tabIds) {
        for (String tabId : tabIds) {
            if (!containsTab(modulo, tabId)) {
                throw new IllegalArgumentException("Tab no permitido para el modulo " + modulo + ": " + tabId);
            }
        }
    }
}
