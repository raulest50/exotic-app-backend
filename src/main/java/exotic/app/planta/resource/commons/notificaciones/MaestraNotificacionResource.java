package exotic.app.planta.resource.commons.notificaciones;

import exotic.app.planta.model.notificaciones.MaestraNotificacion;
import exotic.app.planta.service.commons.notificaciones.MaestraNotificacionService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/maestra-notificaciones")
@RequiredArgsConstructor
public class MaestraNotificacionResource {

    private final MaestraNotificacionService maestraNotificacionService;

    @GetMapping
    public ResponseEntity<List<MaestraNotificacion>> getAll() {
        List<MaestraNotificacion> notificaciones = maestraNotificacionService.getAll();
        notificaciones.forEach(n -> n.getUsersGroup().forEach(u -> u.setPassword("")));
        return ResponseEntity.ok(notificaciones);
    }

    @PostMapping("/{notificacionId}/users/{userId}")
    public ResponseEntity<?> addUser(
            @PathVariable int notificacionId,
            @PathVariable Long userId) {
        try {
            MaestraNotificacion updated = maestraNotificacionService.addUserToNotificacion(notificacionId, userId);
            updated.getUsersGroup().forEach(u -> u.setPassword(""));
            return ResponseEntity.ok(updated);
        } catch (EntityNotFoundException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{notificacionId}/users/{userId}")
    public ResponseEntity<?> removeUser(
            @PathVariable int notificacionId,
            @PathVariable Long userId) {
        try {
            MaestraNotificacion updated = maestraNotificacionService.removeUserFromNotificacion(notificacionId, userId);
            updated.getUsersGroup().forEach(u -> u.setPassword(""));
            return ResponseEntity.ok(updated);
        } catch (EntityNotFoundException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
