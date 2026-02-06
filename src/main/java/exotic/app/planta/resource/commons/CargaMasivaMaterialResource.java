package exotic.app.planta.resource.commons;

import exotic.app.planta.model.commons.dto.ValidationResultDTO;
import exotic.app.planta.service.commons.CargaMasivaMaterialService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

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

    @PostMapping(value = "/validar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ValidationResultDTO> validar(@RequestParam("file") MultipartFile file) {
        ValidationResultDTO result = cargaMasivaMaterialService.validateExcel(file);
        return ResponseEntity.ok(result);
    }

    @PostMapping(value = "/ejecutar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ValidationResultDTO> ejecutar(@RequestParam("file") MultipartFile file) {
        ValidationResultDTO result = cargaMasivaMaterialService.processBulkInsert(file);
        if (!result.isValid() && result.getErrors() != null && !result.getErrors().isEmpty()) {
            return ResponseEntity.badRequest().body(result);
        }
        return ResponseEntity.ok(result);
    }
}
