package exotic.app.planta.resource.commons;

import exotic.app.planta.service.commons.ExportacionMaterialService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/exportacion-datos")
@RequiredArgsConstructor
public class ExportacionDatosResource {

    private final ExportacionMaterialService exportacionMaterialService;

    @GetMapping("/materiales/excel")
    public ResponseEntity<byte[]> exportarMaterialesExcel() {
        byte[] excel = exportacionMaterialService.exportarMaterialesExcel();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"exportacion_materiales.xlsx\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(excel);
    }
}
