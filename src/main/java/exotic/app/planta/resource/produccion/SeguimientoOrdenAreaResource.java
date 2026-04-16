package exotic.app.planta.resource.produccion;

import exotic.app.planta.model.users.User;
import exotic.app.planta.repo.usuarios.UserRepository;
import exotic.app.planta.service.produccion.SeguimientoOrdenAreaService;
import exotic.app.planta.service.produccion.SeguimientoOrdenAreaService.OrdenSeguimientoDetalleDTO;
import exotic.app.planta.service.produccion.SeguimientoOrdenAreaService.ReportarCompletadoRequest;
import exotic.app.planta.service.produccion.SeguimientoOrdenAreaService.SeguimientoOrdenAreaDTO;
import exotic.app.planta.service.produccion.SeguimientoOrdenAreaService.TableroOperativoDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/seguimiento-orden-area")
@RequiredArgsConstructor
public class SeguimientoOrdenAreaResource {

    private final SeguimientoOrdenAreaService seguimientoService;
    private final UserRepository userRepository;

    @GetMapping("/mis-ordenes-pendientes")
    public ResponseEntity<Page<SeguimientoOrdenAreaDTO>> getMisOrdenesPendientes(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        User user = getCurrentUser(authentication);
        Pageable pageable = PageRequest.of(page, size);
        Page<SeguimientoOrdenAreaDTO> ordenes = seguimientoService.getOrdenesPendientesPorUsuario(
                user.getId(),
                pageable
        );

        return ResponseEntity.ok(ordenes);
    }

    @GetMapping("/mis-ordenes-tablero")
    public ResponseEntity<TableroOperativoDTO> getMisOrdenesTablero(Authentication authentication) {
        User user = getCurrentUser(authentication);
        return ResponseEntity.ok(seguimientoService.getTableroOperativoUsuario(user.getId()));
    }

    @GetMapping("/area/{areaId}/pendientes")
    public ResponseEntity<Page<SeguimientoOrdenAreaDTO>> getOrdenesPendientesPorArea(
            @PathVariable int areaId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        Pageable pageable = PageRequest.of(page, size);
        Page<SeguimientoOrdenAreaDTO> ordenes = seguimientoService.getOrdenesPendientesPorArea(areaId, pageable);
        return ResponseEntity.ok(ordenes);
    }

    @GetMapping("/orden/{ordenId}/progreso")
    public ResponseEntity<List<SeguimientoOrdenAreaDTO>> getProgresoOrden(@PathVariable int ordenId) {
        return ResponseEntity.ok(seguimientoService.getProgresoOrden(ordenId));
    }

    @GetMapping("/orden/{ordenId}/detalle")
    public ResponseEntity<OrdenSeguimientoDetalleDTO> getDetalleOrden(@PathVariable int ordenId) {
        return ResponseEntity.ok(seguimientoService.getDetalleOrden(ordenId));
    }

    @PostMapping("/reportar-en-proceso")
    public ResponseEntity<SeguimientoOrdenAreaDTO> reportarEnProceso(
            Authentication authentication,
            @RequestBody ReportarCompletadoRequest request) {

        User user = getCurrentUser(authentication);
        SeguimientoOrdenAreaDTO result = seguimientoService.reportarEnProceso(
                request.getOrdenId(),
                request.getAreaId(),
                user.getId(),
                request.getObservaciones()
        );
        return ResponseEntity.ok(result);
    }

    @PostMapping("/pausar-proceso")
    public ResponseEntity<SeguimientoOrdenAreaDTO> pausarProceso(
            Authentication authentication,
            @RequestBody ReportarCompletadoRequest request) {

        User user = getCurrentUser(authentication);
        SeguimientoOrdenAreaDTO result = seguimientoService.pausarProceso(
                request.getOrdenId(),
                request.getAreaId(),
                user.getId(),
                request.getObservaciones()
        );
        return ResponseEntity.ok(result);
    }

    @PostMapping("/reportar-completado")
    public ResponseEntity<SeguimientoOrdenAreaDTO> reportarCompletado(
            Authentication authentication,
            @RequestBody ReportarCompletadoRequest request) {

        User user = getCurrentUser(authentication);
        SeguimientoOrdenAreaDTO result = seguimientoService.reportarCompletado(
                request.getOrdenId(),
                request.getAreaId(),
                user.getId(),
                request.getObservaciones()
        );

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
