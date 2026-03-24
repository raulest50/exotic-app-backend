package exotic.app.planta.resource.commons.notificaciones;

import exotic.app.planta.model.commons.notificaciones.MaterialEnPuntoReordenDTO;
import exotic.app.planta.model.commons.notificaciones.ModuleNotificationDTA;
import exotic.app.planta.service.commons.notificaciones.NotificacionesModulosService;
import exotic.app.planta.service.commons.notificaciones.PuntoReordenEvaluacionService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/notificaciones")
@RequiredArgsConstructor
public class NotificacionesCardsCampanaResource {

    private static final int MAX_PAGE_SIZE = 50;

    private final NotificacionesModulosService notificacionesModulosService;
    private final PuntoReordenEvaluacionService puntoReordenEvaluacionService;

    /**
     * Endpoint para verificar notificaciones para todos los módulos a los que tiene acceso un usuario
     * @param username Nombre de usuario para el que se verifican las notificaciones
     * @return Lista de objetos con información de notificaciones por módulo
     */
    @GetMapping("/notifications4user")
    public ResponseEntity<List<ModuleNotificationDTA>> checkNotifications4User(@RequestParam String username) {
        List<ModuleNotificationDTA> notifications = notificacionesModulosService.checkAllNotifications4User(username);
        return ResponseEntity.ok(notifications);
    }

    /**
     * Materiales con stock en o bajo punto de reorden (misma regla que el correo programado).
     * Re-ejecuta la agregación en cada petición; paginación en memoria.
     */
    @GetMapping("/stock/materiales-en-punto-reorden")
    public ResponseEntity<Page<MaterialEnPuntoReordenDTO>> materialesEnPuntoReorden(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int safePage = Math.max(page, 0);
        Page<MaterialEnPuntoReordenDTO> result =
                puntoReordenEvaluacionService.pageMaterialesEnReorden(PageRequest.of(safePage, safeSize));
        return ResponseEntity.ok(result);
    }

}
