package exotic.app.planta.resource.commons;

import exotic.app.planta.model.commons.dto.BulkUpdateResponseDTO;
import exotic.app.planta.service.commons.CargaMasivaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
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
        String filename = file != null ? file.getOriginalFilename() : "null";
        String contentType = file != null ? file.getContentType() : "null";
        long sizeBytes = file != null ? file.getSize() : -1L;

        log.info("[CARGA_MASIVA][RESOURCE] Inicio request ejecutar. usuario={}, archivo={}, sizeBytes={}, contentType={}",
                username, filename, sizeBytes, contentType);

        try {
            if (file == null || file.isEmpty()) {
                log.error("[CARGA_MASIVA][RESOURCE] Archivo invalido o vacio. usuario={}, archivo={}, sizeBytes={}, contentType={}",
                        username, filename, sizeBytes, contentType);
                throw new IllegalArgumentException("El archivo de carga masiva esta vacio o no fue enviado");
            }

            BulkUpdateResponseDTO response = cargaMasivaService.processBulkUpdate(file, username);
            byte[] reportFile = response.getReportFile();
            String reportFileName = response.getReportFileName();
            int reportSizeBytes = reportFile != null ? reportFile.length : 0;

            if (reportFile != null && reportFile.length > 0) {
                log.info("[CARGA_MASIVA][RESOURCE] Request exitoso. usuario={}, archivo={}, reporte={}, reportSizeBytes={}",
                        username, filename, reportFileName, reportSizeBytes);
                return ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION,
                                "attachment; filename=\"" + reportFileName + "\"")
                        .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                        .body(reportFile);
            }

            log.warn("[CARGA_MASIVA][RESOURCE] Request completado sin reporte adjunto. usuario={}, archivo={}, reportSizeBytes={}",
                    username, filename, reportSizeBytes);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new byte[0]);
        } catch (Exception e) {
            log.error("[CARGA_MASIVA][RESOURCE] Error ejecutando carga masiva. usuario={}, archivo={}, sizeBytes={}, contentType={}, exceptionType={}, message={}",
                    username, filename, sizeBytes, contentType, e.getClass().getName(), e.getMessage(), e);
            throw e;
        }
    }
}
