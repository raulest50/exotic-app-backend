package exotic.app.planta.resource.bi;

import exotic.app.planta.model.bi.dto.InformeDiarioIngresoTerminadosReporteDTO;
import exotic.app.planta.service.bi.BiExcelExportOptions;
import exotic.app.planta.service.bi.ExcelDecimalSeparator;
import exotic.app.planta.service.bi.InformesDiariosService;
import exotic.app.planta.service.bi.SentidoAjusteInforme;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/bi/informes-diarios")
@RequiredArgsConstructor
public class InformesDiariosResource {

    private final InformesDiariosService informesDiariosService;

    @GetMapping("/ping")
    public ResponseEntity<Map<String, String>> ping() {
        return ResponseEntity.ok(informesDiariosService.ping());
    }

    @GetMapping("/almacen/ingreso-materiales/excel")
    public ResponseEntity<?> exportarIngresoMaterialesExcel(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaDesde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaHasta,
            @RequestParam(required = false) ExcelDecimalSeparator decimalSeparator) {
        try {
            InformeFechaRange range = resolveInformeRange(fecha, fechaDesde, fechaHasta);
            byte[] excel = informesDiariosService.exportarIngresoMaterialesExcel(
                    range.fechaDesde(),
                    range.fechaHasta(),
                    BiExcelExportOptions.of(decimalSeparator));
            return excelResponse(excel, "informe_ingreso_materiales_" + range.filenameSuffix() + ".xlsx");
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @GetMapping("/almacen/dispensacion-materiales/excel")
    public ResponseEntity<?> exportarDispensacionMaterialesExcel(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaDesde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaHasta,
            @RequestParam(required = false) ExcelDecimalSeparator decimalSeparator) {
        try {
            InformeFechaRange range = resolveInformeRange(fecha, fechaDesde, fechaHasta);
            byte[] excel = informesDiariosService.exportarDispensacionMaterialesExcel(
                    range.fechaDesde(),
                    range.fechaHasta(),
                    BiExcelExportOptions.of(decimalSeparator));
            return excelResponse(excel, "informe_dispensacion_materiales_" + range.filenameSuffix() + ".xlsx");
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @GetMapping("/almacen/ingreso-terminados/excel")
    public ResponseEntity<?> exportarIngresoTerminadosExcel(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaDesde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaHasta,
            @RequestParam(required = false) ExcelDecimalSeparator decimalSeparator) {
        try {
            InformeFechaRange range = resolveInformeRange(fecha, fechaDesde, fechaHasta);
            byte[] excel = informesDiariosService.exportarIngresoTerminadosExcel(
                    range.fechaDesde(),
                    range.fechaHasta(),
                    BiExcelExportOptions.of(decimalSeparator));
            return excelResponse(excel, "informe_ingreso_terminados_" + range.filenameSuffix() + ".xlsx");
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @GetMapping("/almacen/ingreso-terminados/reporte")
    public ResponseEntity<InformeDiarioIngresoTerminadosReporteDTO> obtenerReporteIngresoTerminados(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        return ResponseEntity.ok(informesDiariosService.obtenerReporteIngresoTerminados(fecha));
    }

    @GetMapping("/almacen/ingreso-terminados/reporte-excel")
    public ResponseEntity<byte[]> exportarReporteIngresoTerminadosExcel(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            @RequestParam(required = false) ExcelDecimalSeparator decimalSeparator) {
        byte[] excel = informesDiariosService.exportarReporteIngresoTerminadosExcel(
                fecha,
                BiExcelExportOptions.of(decimalSeparator));
        String filename = "reporte_produccion_terminados_" + fecha + ".xlsx";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(excel);
    }

    @GetMapping("/compras/excel")
    public ResponseEntity<?> exportarComprasExcel(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaDesde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaHasta,
            @RequestParam(required = false) ExcelDecimalSeparator decimalSeparator) {
        try {
            InformeFechaRange range = resolveInformeRange(fecha, fechaDesde, fechaHasta);
            byte[] excel = informesDiariosService.exportarComprasExcel(
                    range.fechaDesde(),
                    range.fechaHasta(),
                    BiExcelExportOptions.of(decimalSeparator));
            return excelResponse(excel, "informe_compras_ocm_" + range.filenameSuffix() + ".xlsx");
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @GetMapping("/almacen/ajustes/excel")
    public ResponseEntity<byte[]> exportarAjustesAlmacenExcel(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaDesde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaHasta,
            @RequestParam SentidoAjusteInforme sentido,
            @RequestParam(required = false) ExcelDecimalSeparator decimalSeparator) {
        try {
            byte[] excel = informesDiariosService.exportarAjustesAlmacenExcel(
                    fechaDesde,
                    fechaHasta,
                    sentido,
                    BiExcelExportOptions.of(decimalSeparator));
            String filename = String.format(
                    "informe_ajustes_almacen_%s_%s_%s.xlsx", sentido.name(), fechaDesde, fechaHasta);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.parseMediaType(
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(excel);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().build();
        }
    }

    private ResponseEntity<byte[]> excelResponse(byte[] excel, String filename) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(excel);
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
        return new InformeFechaRange(fechaDesde, fechaHasta);
    }

    private record InformeFechaRange(LocalDate fechaDesde, LocalDate fechaHasta) {
        private String filenameSuffix() {
            return fechaDesde.equals(fechaHasta)
                    ? fechaDesde.toString()
                    : fechaDesde + "_a_" + fechaHasta;
        }
    }
}
