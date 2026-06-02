package exotic.app.planta.resource.bi;

import exotic.app.planta.model.bi.dto.HorasExtraBiResumenDTO;
import exotic.app.planta.model.bi.dto.HorasExtraBiSerieDTO;
import exotic.app.planta.model.organizacion.personal.IntegrantePersonal;
import exotic.app.planta.service.bi.HorasExtraBiGranularidad;
import exotic.app.planta.service.bi.PersonalBiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@RequestMapping("/bi/personal/horas-extra")
@RequiredArgsConstructor
@Slf4j
public class PersonalBiResource {

    private final PersonalBiService personalBiService;

    @GetMapping("/resumen")
    public ResponseEntity<?> resumenHorasExtra(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaDesde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaHasta,
            @RequestParam(required = false) Long integranteId,
            @RequestParam(required = false) IntegrantePersonal.Departamento departamento,
            @RequestParam(required = false) String cargo
    ) {
        try {
            HorasExtraBiResumenDTO result = personalBiService.resumenHorasExtra(
                    fechaDesde,
                    fechaHasta,
                    integranteId,
                    departamento,
                    cargo
            );
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            log.warn("Solicitud invalida para resumen BI personal horas extra: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error consultando resumen BI personal horas extra", e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/serie")
    public ResponseEntity<?> serieHorasExtra(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaDesde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaHasta,
            @RequestParam(defaultValue = "DIA") HorasExtraBiGranularidad granularidad,
            @RequestParam(required = false) Long integranteId,
            @RequestParam(required = false) IntegrantePersonal.Departamento departamento,
            @RequestParam(required = false) String cargo
    ) {
        try {
            HorasExtraBiSerieDTO result = personalBiService.serieHorasExtra(
                    fechaDesde,
                    fechaHasta,
                    granularidad,
                    integranteId,
                    departamento,
                    cargo
            );
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            log.warn("Solicitud invalida para serie BI personal horas extra: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error consultando serie BI personal horas extra", e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/excel")
    public ResponseEntity<?> exportarHorasExtraExcel(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaDesde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaHasta,
            @RequestParam(defaultValue = "DIA") HorasExtraBiGranularidad granularidad,
            @RequestParam(required = false) Long integranteId,
            @RequestParam(required = false) IntegrantePersonal.Departamento departamento,
            @RequestParam(required = false) String cargo
    ) {
        try {
            byte[] excel = personalBiService.exportarHorasExtraExcel(
                    fechaDesde,
                    fechaHasta,
                    granularidad,
                    integranteId,
                    departamento,
                    cargo
            );
            String filename = String.format("bi_personal_horas_extra_%s_%s.xlsx", fechaDesde, fechaHasta);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.parseMediaType(
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(excel);
        } catch (IllegalArgumentException e) {
            log.warn("Solicitud invalida para Excel BI personal horas extra: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error generando Excel BI personal horas extra", e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }
}
