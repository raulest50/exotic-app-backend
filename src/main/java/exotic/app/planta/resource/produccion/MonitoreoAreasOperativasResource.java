package exotic.app.planta.resource.produccion;

import exotic.app.planta.model.produccion.dto.AreaOperativaMonitoreoDTO;
import exotic.app.planta.service.produccion.MonitoreoAreasOperativasService;
import exotic.app.planta.service.produccion.SeguimientoOrdenAreaService.AreaOperativaTableroDTO;
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
public class MonitoreoAreasOperativasResource {

    private final MonitoreoAreasOperativasService monitoreoAreasOperativasService;

    @GetMapping("/areas")
    public ResponseEntity<List<AreaOperativaMonitoreoDTO>> listarAreasMonitoreables() {
        return ResponseEntity.ok(monitoreoAreasOperativasService.listarAreasMonitoreables());
    }

    @GetMapping("/areas/{areaId}/tablero")
    public ResponseEntity<AreaOperativaTableroDTO> getTableroAreaPorFecha(
            @PathVariable int areaId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        return ResponseEntity.ok(monitoreoAreasOperativasService.getTableroAreaPorFecha(areaId, fecha));
    }
}
