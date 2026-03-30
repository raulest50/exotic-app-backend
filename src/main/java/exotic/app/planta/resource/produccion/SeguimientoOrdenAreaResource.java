package exotic.app.planta.resource.produccion;

import exotic.app.planta.model.users.User;
import exotic.app.planta.repo.usuarios.UserRepository;
import exotic.app.planta.service.produccion.SeguimientoOrdenAreaService;
import exotic.app.planta.service.produccion.SeguimientoOrdenAreaService.ReportarCompletadoRequest;
import exotic.app.planta.service.produccion.SeguimientoOrdenAreaService.SeguimientoOrdenAreaDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/seguimiento-orden-area")
@RequiredArgsConstructor
public class SeguimientoOrdenAreaResource {

    private final SeguimientoOrdenAreaService seguimientoService;
    private final UserRepository userRepository;

    /**
     * Obtiene las órdenes pendientes para el usuario logueado
     * (basado en las áreas donde el usuario es responsable)
     */
    @GetMapping("/mis-ordenes-pendientes")
    public ResponseEntity<Page<SeguimientoOrdenAreaDTO>> getMisOrdenesPendientes(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        User user = getCurrentUser(authentication);
        Pageable pageable = PageRequest.of(page, size);

        Page<SeguimientoOrdenAreaDTO> ordenes = seguimientoService.getOrdenesPendientesPorUsuario(
                user.getId(), pageable);

        return ResponseEntity.ok(ordenes);
    }

    /**
     * Obtiene las órdenes pendientes para un área específica
     */
    @GetMapping("/area/{areaId}/pendientes")
    public ResponseEntity<Page<SeguimientoOrdenAreaDTO>> getOrdenesPendientesPorArea(
            @PathVariable int areaId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        Pageable pageable = PageRequest.of(page, size);
        Page<SeguimientoOrdenAreaDTO> ordenes = seguimientoService.getOrdenesPendientesPorArea(areaId, pageable);

        return ResponseEntity.ok(ordenes);
    }

    /**
     * Obtiene el progreso completo de una orden
     */
    @GetMapping("/orden/{ordenId}/progreso")
    public ResponseEntity<List<SeguimientoOrdenAreaDTO>> getProgresoOrden(@PathVariable int ordenId) {
        List<SeguimientoOrdenAreaDTO> progreso = seguimientoService.getProgresoOrden(ordenId);
        return ResponseEntity.ok(progreso);
    }

    /**
     * Reporta como completado el trabajo de un área para una orden
     */
    @PostMapping("/reportar-completado")
    public ResponseEntity<SeguimientoOrdenAreaDTO> reportarCompletado(
            Authentication authentication,
            @RequestBody ReportarCompletadoRequest request) {

        User user = getCurrentUser(authentication);

        SeguimientoOrdenAreaDTO result = seguimientoService.reportarCompletado(
                request.getOrdenId(),
                request.getAreaId(),
                user.getId(),
                request.getObservaciones());

        return ResponseEntity.ok(result);
    }

    private User getCurrentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No autenticado");
        }

        String username = authentication.getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario no encontrado"));
    }
}
