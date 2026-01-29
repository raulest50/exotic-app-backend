package exotic.app.planta.resource.commons;

import exotic.app.planta.model.commons.dto.BulkUpdateResponseDTO;
import exotic.app.planta.service.commons.CargaMasivaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/carga-masiva")
@RequiredArgsConstructor
public class CargaMasivaResource {

    private final CargaMasivaService cargaMasivaService;

    @GetMapping("/template-inventario")
    public ResponseEntity<byte[]> templateInventario() {
        byte[] excel = cargaMasivaService.generateTemplateExcel();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"plantilla_carga_masiva_inventario.xlsx\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(excel);
    }

    @PostMapping(value = "/ejecutar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> ejecutar(
            @RequestParam("file") MultipartFile file,
            Authentication authentication
    ) {
        String username = authentication != null ? authentication.getName() : "system";
        BulkUpdateResponseDTO response = cargaMasivaService.processBulkUpdate(file, username);
        
        if (response.getReportFile() != null && response.getReportFile().length > 0) {
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, 
                            "attachment; filename=\"" + response.getReportFileName() + "\"")
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(response.getReportFile());
        } else {
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new byte[0]);
        }
    }
}
