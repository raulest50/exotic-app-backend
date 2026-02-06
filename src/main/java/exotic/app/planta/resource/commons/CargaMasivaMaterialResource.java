package exotic.app.planta.resource.commons;

import exotic.app.planta.service.commons.CargaMasivaMaterialService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/carga-masiva-materiales")
@RequiredArgsConstructor
public class CargaMasivaMaterialResource {

    private final CargaMasivaMaterialService cargaMasivaMaterialService;

    @GetMapping("/template")
    public ResponseEntity<byte[]> template() {
        byte[] excel = cargaMasivaMaterialService.generateTemplateExcel();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"plantilla_carga_masiva_materiales.xlsx\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(excel);
    }
}
