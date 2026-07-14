package exotic.app.planta.resource.bi;

import exotic.app.planta.model.bi.dto.InformeGlobalAlmacenDTO;
import exotic.app.planta.model.bi.dto.InformeGlobalProduccionDTO;
import exotic.app.planta.service.bi.InformesGlobalesService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Map;

@RestController
@RequestMapping("/bi/informes-globales")
@RequiredArgsConstructor
public class InformesGlobalesResource {

    private static final int MAX_DIAS_RANGO = 31;

    private final InformesGlobalesService informesGlobalesService;

    @GetMapping("/produccion")
    public ResponseEntity<?> obtenerReporteProduccion(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaDesde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaHasta) {
        try {
            InformeFechaRange range = resolveInformeRange(fecha, fechaDesde, fechaHasta);
            InformeGlobalProduccionDTO reporte = informesGlobalesService.obtenerReporteProduccion(
                    range.fechaDesde(),
                    range.fechaHasta());
            return ResponseEntity.ok(reporte);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @GetMapping("/almacen")
    public ResponseEntity<?> obtenerReporteAlmacen(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaDesde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaHasta) {
        try {
            InformeFechaRange range = resolveInformeRange(fecha, fechaDesde, fechaHasta);
            InformeGlobalAlmacenDTO reporte = informesGlobalesService.obtenerReporteAlmacen(
                    range.fechaDesde(),
                    range.fechaHasta());
            return ResponseEntity.ok(reporte);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    private InformeFechaRange resolveInformeRange(LocalDate fecha, LocalDate fechaDesde, LocalDate fechaHasta) {
        boolean hasSingleDate = fecha != null;
        boolean hasRangeStart = fechaDesde != null;
        boolean hasRangeEnd = fechaHasta != null;

        if (hasSingleDate && (hasRangeStart || hasRangeEnd)) {
            throw new IllegalArgumentException("Use fecha o fechaDesde/fechaHasta, no ambas opciones.");
        }
        if (hasSingleDate) {
            return new InformeFechaRange(fecha, fecha);
        }
        if (!hasRangeStart || !hasRangeEnd) {
            throw new IllegalArgumentException("Debe enviar fecha o el rango completo fechaDesde/fechaHasta.");
        }
        if (fechaDesde.isAfter(fechaHasta)) {
            throw new IllegalArgumentException("fechaDesde no puede ser posterior a fechaHasta.");
        }
        long dias = ChronoUnit.DAYS.between(fechaDesde, fechaHasta) + 1;
        if (dias > MAX_DIAS_RANGO) {
            throw new IllegalArgumentException("El rango maximo permitido para este informe es de 31 dias.");
        }
        return new InformeFechaRange(fechaDesde, fechaHasta);
    }

    private record InformeFechaRange(LocalDate fechaDesde, LocalDate fechaHasta) {
    }
}
