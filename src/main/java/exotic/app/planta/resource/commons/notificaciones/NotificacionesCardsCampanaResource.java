package exotic.app.planta.resource.commons.notificaciones;

import exotic.app.planta.model.commons.notificaciones.ModuleNotificationDTA;
import exotic.app.planta.model.commons.notificaciones.PuntoReordenEvaluacionResult;
import exotic.app.planta.service.commons.notificaciones.NotificacionesModulosService;
import exotic.app.planta.service.commons.notificaciones.PuntoReordenEvaluacionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/notificaciones")
@RequiredArgsConstructor
public class NotificacionesCardsCampanaResource {

    private final NotificacionesModulosService notificacionesModulosService;
    private final PuntoReordenEvaluacionService puntoReordenEvaluacionService;

    /**
     * Endpoint para verificar notificaciones para todos los modulos a los que tiene acceso un usuario.
     */
    @GetMapping("/notifications4user")
    public ResponseEntity<List<ModuleNotificationDTA>> checkNotifications4User(@RequestParam String username) {
        List<ModuleNotificationDTA> notifications = notificacionesModulosService.checkAllNotifications4User(username);
        return ResponseEntity.ok(notifications);
    }

    /**
     * Materiales con stock en o bajo punto de reorden, separados por estado de OCM.
     */
    @GetMapping("/stock/materiales-en-punto-reorden")
    public ResponseEntity<PuntoReordenEvaluacionResult> materialesEnPuntoReorden() {
        PuntoReordenEvaluacionResult result = puntoReordenEvaluacionService.evaluar();
        return ResponseEntity.ok(result);
    }
}
