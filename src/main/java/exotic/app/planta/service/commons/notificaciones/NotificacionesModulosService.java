package exotic.app.planta.service.commons.notificaciones;

import exotic.app.planta.model.commons.notificaciones.ModuleNotificationDTA;
import exotic.app.planta.model.inventarios.TransaccionAlmacen;
import exotic.app.planta.model.users.ModuloAcceso;
import exotic.app.planta.model.users.ModuloSistema;
import exotic.app.planta.model.users.User;
import exotic.app.planta.repo.compras.OrdenCompraRepo;
import exotic.app.planta.repo.inventarios.TransaccionAlmacenHeaderRepo;
import exotic.app.planta.repo.usuarios.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class NotificacionesModulosService {

    private final OrdenCompraRepo ordenCompraRepo;
    private final UserRepository userRepository;
    private final TransaccionAlmacenHeaderRepo transaccionAlmacenHeaderRepo;
    private final PuntoReordenEvaluacionService puntoReordenEvaluacionService;

    /**
     * Verifica las notificaciones para todos los m?dulos a los que tiene acceso un usuario
     * @param username Nombre de usuario para el que se verifican las notificaciones
     * @return Lista de objetos con informaci?n de notificaciones por m?dulo
     */
    public List<ModuleNotificationDTA> checkAllNotifications4User(String username) {
        List<ModuleNotificationDTA> notifications = new ArrayList<>();

        // Buscar el usuario por su nombre de usuario
        Optional<User> userOpt = userRepository.findByUsername(username);

        if (userOpt.isPresent()) {
            User user = userOpt.get();

            for (ModuloAcceso moduloAcceso : user.getModuloAccesos()) {
                ModuloSistema modulo = moduloAcceso.getModulo();

                ModuleNotificationDTA notification = null;

                switch (modulo) {
                    case USUARIOS:
                        notification = checkNotificacionesUsuarios(user);
                        break;
                    case PRODUCTOS:
                        notification = checkNotificacionesProductos(user);
                        break;
                    case PRODUCCION:
                        notification = checkNotificacionesProduccion(user);
                        break;
                    case STOCK:
                        notification = checkNotificacionesStock(user);
                        break;
                    case PROVEEDORES:
                        notification = checkNotificacionesProveedores(user);
                        break;
                    case COMPRAS:
                        notification = checkNotificacionesCompras(user);
                        break;
                    case SEGUIMIENTO_PRODUCCION:
                        notification = checkNotificacionesSeguimientoProduccion(user);
                        break;
                    case CLIENTES:
                        notification = checkNotificacionesClientes(user);
                        break;
                    case VENTAS:
                        notification = checkNotificacionesVentas(user);
                        break;
                    case TRANSACCIONES_ALMACEN:
                        notification = checkNotificacionesTransaccionesAlmacen(user);
                        break;
                    case ACTIVOS:
                        notification = checkNotificacionesActivos(user);
                        break;
                    case CONTABILIDAD:
                        notification = checkNotificacionesContabilidad(user);
                        break;
                    case PERSONAL_PLANTA:
                        notification = checkNotificacionesPersonalPlanta(user);
                        break;
                    case BINTELLIGENCE:
                        notification = checkNotificacionesBIntelligence(user);
                        break;
                    case CARGA_MASIVA:
                        notification = checkNotificacionesCargaMasiva(user);
                        break;
                    case ADMINISTRACION_ALERTAS:
                        notification = checkNotificacionesAdministracionAlertas(user);
                        break;
                    case MASTER_DIRECTIVES:
                        notification = checkNotificacionesMasterDirectives(user);
                        break;
                    case CRONOGRAMA:
                        notification = checkNotificacionesCronograma(user);
                        break;
                    case ORGANIGRAMA:
                        notification = checkNotificacionesOrganigrama(user);
                        break;
                    case PAGOS_PROVEEDORES:
                        notification = checkNotificacionesPagosProveedores(user);
                        break;
                    case VENDEDORES:
                    default:
                        break;
                }

                if (notification != null) {
                    notifications.add(notification);
                }
            }
        }

        return notifications;
    }


    /**
     * Verifica si hay notificaciones para el m?dulo USUARIOS
     * @param user Usuario para el que se verifican las notificaciones
     * @return Objeto con informaci?n de notificaci?n
     */
    public ModuleNotificationDTA checkNotificacionesUsuarios(User user) {
        ModuleNotificationDTA notification = new ModuleNotificationDTA();
        notification.setModulo(ModuloSistema.USUARIOS);
        notification.setRequireAtention(false);
        notification.setMessage("");
        return notification;
    }

    /**
     * Verifica si hay notificaciones para el m?dulo PRODUCTOS
     * @param user Usuario para el que se verifican las notificaciones
     * @return Objeto con informaci?n de notificaci?n
     */
    public ModuleNotificationDTA checkNotificacionesProductos(User user) {
        ModuleNotificationDTA notification = new ModuleNotificationDTA();
        notification.setModulo(ModuloSistema.PRODUCTOS);
        notification.setRequireAtention(false);
        notification.setMessage("");
        return notification;
    }

    /**
     * Verifica si hay notificaciones para el m?dulo PRODUCCION
     * @param user Usuario para el que se verifican las notificaciones
     * @return Objeto con informaci?n de notificaci?n
     */
    public ModuleNotificationDTA checkNotificacionesProduccion(User user) {
        ModuleNotificationDTA notification = new ModuleNotificationDTA();
        notification.setModulo(ModuloSistema.PRODUCCION);
        notification.setRequireAtention(false);
        notification.setMessage("");
        return notification;
    }

    /**
     * Verifica si hay notificaciones para el m?dulo STOCK
     * @param user Usuario para el que se verifican las notificaciones
     * @return Objeto con informaci?n de notificaci?n
     */
    public ModuleNotificationDTA checkNotificacionesStock(User user) {
        ModuleNotificationDTA notification = new ModuleNotificationDTA();
        notification.setModulo(ModuloSistema.STOCK);

        long count = puntoReordenEvaluacionService.evaluar().enReorden().size();
        notification.setMaterialesEnPuntoReorden(count);

        if (count > 0) {
            notification.setRequireAtention(true);
            notification.setMessage(count == 1
                    ? "Hay 1 material en o bajo punto de reorden"
                    : "Hay " + count + " materiales en o bajo punto de reorden");
        } else {
            notification.setRequireAtention(false);
            notification.setMessage("");
        }

        return notification;
    }

    /**
     * Verifica si hay notificaciones para el m?dulo PROVEEDORES
     * @param user Usuario para el que se verifican las notificaciones
     * @return Objeto con informaci?n de notificaci?n
     */
    public ModuleNotificationDTA checkNotificacionesProveedores(User user) {
        ModuleNotificationDTA notification = new ModuleNotificationDTA();
        notification.setModulo(ModuloSistema.PROVEEDORES);
        notification.setRequireAtention(false);
        notification.setMessage("");
        return notification;
    }

    /**
     * Verifica si hay notificaciones para el m?dulo COMPRAS
     * @param user Usuario para el que se verifican las notificaciones
     * @return Objeto con informaci?n de notificaci?n
     */
    public ModuleNotificationDTA checkNotificacionesCompras(User user) {
        ModuleNotificationDTA notification = new ModuleNotificationDTA();
        notification.setModulo(ModuloSistema.COMPRAS);

        long countLiberar = ordenCompraRepo.countByEstado(0);
        long countEnviar = ordenCompraRepo.countByEstado(1);
        notification.setOrdenesPendientesLiberar(countLiberar);
        notification.setOrdenesPendientesEnviar(countEnviar);

        boolean existsOrdenPendienteLiberar = countLiberar > 0;
        boolean existsOrdenPendienteEnviar = countEnviar > 0;

        if (existsOrdenPendienteLiberar || existsOrdenPendienteEnviar) {
            notification.setRequireAtention(true);

            if (existsOrdenPendienteLiberar && existsOrdenPendienteEnviar) {
                notification.setMessage("Hay ?rdenes de compra pendientes por liberar y por enviar al proveedor");
            } else if (existsOrdenPendienteLiberar) {
                notification.setMessage("Hay ?rdenes de compra pendientes por liberar");
            } else {
                notification.setMessage("Hay ?rdenes de compra pendientes por enviar al proveedor");
            }
        } else {
            notification.setRequireAtention(false);
            notification.setMessage("");
        }

        return notification;
    }

    /**
     * Verifica si hay notificaciones para el m?dulo SEGUIMIENTO_PRODUCCION
     * @param user Usuario para el que se verifican las notificaciones
     * @return Objeto con informaci?n de notificaci?n
     */
    public ModuleNotificationDTA checkNotificacionesSeguimientoProduccion(User user) {
        ModuleNotificationDTA notification = new ModuleNotificationDTA();
        notification.setModulo(ModuloSistema.SEGUIMIENTO_PRODUCCION);
        notification.setRequireAtention(false);
        notification.setMessage("");
        return notification;
    }

    /**
     * Verifica si hay notificaciones para el m?dulo CLIENTES
     * @param user Usuario para el que se verifican las notificaciones
     * @return Objeto con informaci?n de notificaci?n
     */
    public ModuleNotificationDTA checkNotificacionesClientes(User user) {
        ModuleNotificationDTA notification = new ModuleNotificationDTA();
        notification.setModulo(ModuloSistema.CLIENTES);
        notification.setRequireAtention(false);
        notification.setMessage("");
        return notification;
    }

    /**
     * Verifica si hay notificaciones para el m?dulo VENTAS
     * @param user Usuario para el que se verifican las notificaciones
     * @return Objeto con informaci?n de notificaci?n
     */
    public ModuleNotificationDTA checkNotificacionesVentas(User user) {
        ModuleNotificationDTA notification = new ModuleNotificationDTA();
        notification.setModulo(ModuloSistema.VENTAS);
        notification.setRequireAtention(false);
        notification.setMessage("");
        return notification;
    }

    /**
     * Verifica si hay notificaciones para el m?dulo TRANSACCIONES_ALMACEN
     * @param user Usuario para el que se verifican las notificaciones
     * @return Objeto con informaci?n de notificaci?n
     */
    public ModuleNotificationDTA checkNotificacionesTransaccionesAlmacen(User user) {
        ModuleNotificationDTA notification = new ModuleNotificationDTA();
        notification.setModulo(ModuloSistema.TRANSACCIONES_ALMACEN);
        notification.setRequireAtention(false);
        notification.setMessage("");
        return notification;
    }

    /**
     * Verifica si hay notificaciones para el m?dulo ACTIVOS
     * @param user Usuario para el que se verifican las notificaciones
     * @return Objeto con informaci?n de notificaci?n
     */
    public ModuleNotificationDTA checkNotificacionesActivos(User user) {
        ModuleNotificationDTA notification = new ModuleNotificationDTA();
        notification.setModulo(ModuloSistema.ACTIVOS);
        notification.setRequireAtention(false);
        notification.setMessage("");
        return notification;
    }

    /**
     * Verifica si hay notificaciones para el m?dulo CONTABILIDAD
     * @param user Usuario para el que se verifican las notificaciones
     * @return Objeto con informaci?n de notificaci?n
     */
    public ModuleNotificationDTA checkNotificacionesContabilidad(User user) {
        ModuleNotificationDTA notification = new ModuleNotificationDTA();
        notification.setModulo(ModuloSistema.CONTABILIDAD);
        notification.setRequireAtention(false);
        notification.setMessage("");
        return notification;
    }

    /**
     * Verifica si hay notificaciones para el m?dulo PERSONAL_PLANTA
     * @param user Usuario para el que se verifican las notificaciones
     * @return Objeto con informaci?n de notificaci?n
     */
    public ModuleNotificationDTA checkNotificacionesPersonalPlanta(User user) {
        ModuleNotificationDTA notification = new ModuleNotificationDTA();
        notification.setModulo(ModuloSistema.PERSONAL_PLANTA);
        notification.setRequireAtention(false);
        notification.setMessage("");
        return notification;
    }

    /**
     * Verifica si hay notificaciones para el m?dulo BINTELLIGENCE
     * @param user Usuario para el que se verifican las notificaciones
     * @return Objeto con informaci?n de notificaci?n
     */
    public ModuleNotificationDTA checkNotificacionesBIntelligence(User user) {
        ModuleNotificationDTA notification = new ModuleNotificationDTA();
        notification.setModulo(ModuloSistema.BINTELLIGENCE);
        notification.setRequireAtention(false);
        notification.setMessage("");
        return notification;
    }

    /**
     * Verifica si hay notificaciones para el m?dulo CARGA_MASIVA
     * @param user Usuario para el que se verifican las notificaciones
     * @return Objeto con informaci?n de notificaci?n
     */
    public ModuleNotificationDTA checkNotificacionesCargaMasiva(User user) {
        ModuleNotificationDTA notification = new ModuleNotificationDTA();
        notification.setModulo(ModuloSistema.CARGA_MASIVA);
        notification.setRequireAtention(false);
        notification.setMessage("");
        return notification;
    }

    /**
     * Verifica si hay notificaciones para el m?dulo ADMINISTRACION_ALERTAS
     * @param user Usuario para el que se verifican las notificaciones
     * @return Objeto con informaci?n de notificaci?n
     */
    public ModuleNotificationDTA checkNotificacionesAdministracionAlertas(User user) {
        ModuleNotificationDTA notification = new ModuleNotificationDTA();
        notification.setModulo(ModuloSistema.ADMINISTRACION_ALERTAS);
        notification.setRequireAtention(false);
        notification.setMessage("");
        return notification;
    }

    /**
     * Verifica si hay notificaciones para el m?dulo MASTER_DIRECTIVES
     * @param user Usuario para el que se verifican las notificaciones
     * @return Objeto con informaci?n de notificaci?n
     */
    public ModuleNotificationDTA checkNotificacionesMasterDirectives(User user) {
        ModuleNotificationDTA notification = new ModuleNotificationDTA();
        notification.setModulo(ModuloSistema.MASTER_DIRECTIVES);
        notification.setRequireAtention(false);
        notification.setMessage("");
        return notification;
    }

    /**
     * Verifica si hay notificaciones para el m?dulo CRONOGRAMA
     * @param user Usuario para el que se verifican las notificaciones
     * @return Objeto con informaci?n de notificaci?n
     */
    public ModuleNotificationDTA checkNotificacionesCronograma(User user) {
        ModuleNotificationDTA notification = new ModuleNotificationDTA();
        notification.setModulo(ModuloSistema.CRONOGRAMA);
        notification.setRequireAtention(false);
        notification.setMessage("");
        return notification;
    }

    /**
     * Verifica si hay notificaciones para el m?dulo ORGANIGRAMA
     * @param user Usuario para el que se verifican las notificaciones
     * @return Objeto con informaci?n de notificaci?n
     */
    public ModuleNotificationDTA checkNotificacionesOrganigrama(User user) {
        ModuleNotificationDTA notification = new ModuleNotificationDTA();
        notification.setModulo(ModuloSistema.ORGANIGRAMA);
        notification.setRequireAtention(false);
        notification.setMessage("");
        return notification;
    }

    /**
     * Verifica si hay notificaciones para el m?dulo PAGOS_PROVEEDORES
     * @param user Usuario para el que se verifican las notificaciones
     * @return Objeto con informaci?n de notificaci?n
     */
    public ModuleNotificationDTA checkNotificacionesPagosProveedores(User user) {
        ModuleNotificationDTA notification = new ModuleNotificationDTA();
        notification.setModulo(ModuloSistema.PAGOS_PROVEEDORES);

        // Verificar si hay transacciones de almac?n pendientes por asentar contablemente
        // causadas por ?rdenes de compra de materiales
        long countPendientes = transaccionAlmacenHeaderRepo.countByEstadoContableAndTipoEntidadCausante(
            TransaccionAlmacen.EstadoContable.PENDIENTE,
            TransaccionAlmacen.TipoEntidadCausante.OCM
        );

        if (countPendientes > 0) {
            notification.setRequireAtention(true);
            notification.setMessage("Hay " + countPendientes + " transacciones de almac?n pendientes por asentar contablemente");
        } else {
            notification.setRequireAtention(false);
            notification.setMessage("");
        }

        return notification;
    }

}
