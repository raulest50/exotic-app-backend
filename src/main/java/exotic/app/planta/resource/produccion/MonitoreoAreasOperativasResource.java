package exotic.app.planta.resource.produccion;

import exotic.app.planta.dto.ErrorResponse;
import exotic.app.planta.model.produccion.dto.AreaOperativaInactivityAlertDTO;
import exotic.app.planta.model.produccion.dto.AreaOperativaMonitoreoDTO;
import exotic.app.planta.model.users.ModuloSistema;
import exotic.app.planta.model.users.User;
import exotic.app.planta.model.users.UserAccessEvaluator;
import exotic.app.planta.repo.usuarios.UserRepository;
import exotic.app.planta.service.master.configs.MasterDirectiveService;
import exotic.app.planta.service.produccion.AreaOperativaInactivityAlertService;
import exotic.app.planta.service.produccion.MonitoreoAreasOperativasMetricasService;
import exotic.app.planta.service.produccion.MonitoreoAreasOperativasService;
import exotic.app.planta.service.produccion.MonitoreoAreasOperativasMetricasService.AreaOperativaMetricasDTO;
import exotic.app.planta.service.produccion.SeguimientoOrdenAreaService;
import exotic.app.planta.service.produccion.SeguimientoOrdenAreaService.AreaOperativaTableroDTO;
import exotic.app.planta.service.produccion.SeguimientoOrdenAreaService.SeguimientoOrdenAreaDTO;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/api/produccion/monitoreo-areas-operativas")
@RequiredArgsConstructor
@Slf4j
public class MonitoreoAreasOperativasResource {

    private final MonitoreoAreasOperativasService monitoreoAreasOperativasService;
    private final MonitoreoAreasOperativasMetricasService monitoreoAreasOperativasMetricasService;
    private final AreaOperativaInactivityAlertService areaOperativaInactivityAlertService;
    private final SeguimientoOrdenAreaService seguimientoOrdenAreaService;
    private final MasterDirectiveService masterDirectiveService;
    private final UserRepository userRepository;

    @GetMapping("/areas")
    public ResponseEntity<List<AreaOperativaMonitoreoDTO>> listarAreasMonitoreables() {
        return ResponseEntity.ok(monitoreoAreasOperativasService.listarAreasMonitoreables());
    }

    @GetMapping("/alertas-inactividad")
    public ResponseEntity<List<AreaOperativaInactivityAlertDTO>> getAlertasInactividad() {
        return ResponseEntity.ok(areaOperativaInactivityAlertService.getAlertasInactividad());
    }

    @GetMapping("/areas/{areaId}/tablero")
    public ResponseEntity<AreaOperativaTableroDTO> getTableroAreaPorFecha(
            @PathVariable int areaId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        return ResponseEntity.ok(monitoreoAreasOperativasService.getTableroAreaPorFecha(areaId, fecha));
    }

    @GetMapping("/areas/{areaId}/metricas")
    public ResponseEntity<?> getMetricasArea(
            @PathVariable int areaId,
            @RequestParam(required = false) String modo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaDesde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaHasta
    ) {
        try {
            AreaOperativaMetricasDTO dto = monitoreoAreasOperativasMetricasService.getMetricasArea(
                    areaId,
                    modo,
                    fecha,
                    fechaDesde,
                    fechaHasta
            );
            return ResponseEntity.ok(dto);
        } catch (IllegalArgumentException e) {
            log.warn("Métricas inválidas para área {}: {}", areaId, e.getMessage());
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse("Métricas inválidas", e.getMessage()));
        }
    }

    @PatchMapping("/areas/{areaId}/seguimientos/{seguimientoId}/correccion-estado")
    public ResponseEntity<?> corregirEstadoSeguimiento(
            @PathVariable int areaId,
            @PathVariable Long seguimientoId,
            @RequestBody CorregirEstadoSeguimientoRequest request,
            Authentication authentication
    ) {
        User actor = requireAdminCorrectionAccess(authentication);
        try {
            SeguimientoOrdenAreaDTO dto = seguimientoOrdenAreaService.corregirEstadoAdministrativamente(
                    areaId,
                    seguimientoId,
                    request != null ? request.getExpectedEstado() : null,
                    request != null ? request.getTargetEstado() : null,
                    actor.getId(),
                    request != null ? request.getMotivo() : null
            );
            return ResponseEntity.ok(dto);
        } catch (IllegalArgumentException e) {
            log.warn("Correccion administrativa invalida para area {} seguimiento {}: {}", areaId, seguimientoId, e.getMessage());
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse("Correccion administrativa invalida", e.getMessage()));
        }
    }

    private User requireAdminCorrectionAccess(Authentication authentication) {
        if (!masterDirectiveService.isAreaOperativaAdminCorrectionEnabled()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "La directiva de correcciones administrativas esta apagada.");
        }
        if (authentication == null || !authentication.isAuthenticated() || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No autenticado");
        }

        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario no encontrado"));

        if (isMasterLike(user.getUsername())) {
            return user;
        }

        int nivelMonitoreo = Math.max(
                UserAccessEvaluator.tabNivel(user, ModuloSistema.PRODUCCION, "MONITOREAR_AREAS_OPERATIVAS").orElse(0),
                UserAccessEvaluator.tabNivel(user, ModuloSistema.PRODUCCION, "SEGUIMIENTO_AREAS_OPERATIVAS").orElse(0)
        );
        if (nivelMonitoreo < 3) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tiene nivel suficiente para corregir estados operativos.");
        }
        return user;
    }

    private boolean isMasterLike(String username) {
        if (username == null) {
            return false;
        }
        String normalized = username.trim().toLowerCase(Locale.ROOT);
        return "master".equals(normalized) || "super_master".equals(normalized);
    }

    @Data
    public static class CorregirEstadoSeguimientoRequest {
        private Integer expectedEstado;
        private Integer targetEstado;
        private String motivo;
    }
}
