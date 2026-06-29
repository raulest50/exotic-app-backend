package exotic.app.planta.resource.produccion;

import exotic.app.planta.dto.ErrorResponse;
import exotic.app.planta.model.produccion.dto.AreaOperativaInactivityAlertDTO;
import exotic.app.planta.model.produccion.dto.AreaOperativaMonitoreoDTO;
import exotic.app.planta.service.produccion.AreaOperativaInactivityAlertService;
import exotic.app.planta.service.produccion.MonitoreoAreasOperativasMetricasService;
import exotic.app.planta.service.produccion.MonitoreoAreasOperativasService;
import exotic.app.planta.service.produccion.MonitoreoAreasOperativasMetricasService.AreaOperativaMetricasDTO;
import exotic.app.planta.service.produccion.SeguimientoOrdenAreaService.AreaOperativaTableroDTO;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/produccion/monitoreo-areas-operativas")
@RequiredArgsConstructor
@Slf4j
public class MonitoreoAreasOperativasResource {

    private final MonitoreoAreasOperativasService monitoreoAreasOperativasService;
    private final MonitoreoAreasOperativasMetricasService monitoreoAreasOperativasMetricasService;
    private final AreaOperativaInactivityAlertService areaOperativaInactivityAlertService;

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
}
