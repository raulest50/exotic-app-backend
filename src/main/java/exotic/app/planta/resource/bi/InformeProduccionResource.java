package exotic.app.planta.resource.bi;

import exotic.app.planta.service.bi.InformeGlobalFechaResolver;
import exotic.app.planta.service.bi.InformeProduccionService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/bi/informes-globales/produccion")
@RequiredArgsConstructor
public class InformeProduccionResource {
    private final InformeProduccionService service;

    @GetMapping
    public ResponseEntity<?> reporte(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaDesde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaHasta) {
        try {
            var rango = InformeGlobalFechaResolver.resolve(fecha, fechaDesde, fechaHasta);
            return ResponseEntity.ok(service.obtenerReporte(rango.fechaDesde(), rango.fechaHasta()));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }
}
