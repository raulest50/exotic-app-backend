package exotic.app.planta.resource.bi;

import exotic.app.planta.service.bi.InformesDiariosService;
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
    public ResponseEntity<byte[]> exportarIngresoMaterialesExcel(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        byte[] excel = informesDiariosService.exportarIngresoMaterialesExcel(fecha);
        String filename = "informe_ingreso_materiales_" + fecha + ".xlsx";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(excel);
    }
}
