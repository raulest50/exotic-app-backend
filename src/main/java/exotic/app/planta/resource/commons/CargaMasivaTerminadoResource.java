package exotic.app.planta.resource.commons;

import exotic.app.planta.model.commons.dto.ValidationResultDTO;
import exotic.app.planta.service.commons.CargaMasivaTerminadoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/carga-masiva-terminados")
@RequiredArgsConstructor
@Slf4j
public class CargaMasivaTerminadoResource {

    private final CargaMasivaTerminadoService cargaMasivaTerminadoService;

    @GetMapping("/template")
    public ResponseEntity<byte[]> template() {
        byte[] excel = cargaMasivaTerminadoService.generateTemplateExcel();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"plantilla_carga_masiva_terminados.xlsx\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(excel);
    }

    @GetMapping("/template-sin-insumos")
    public ResponseEntity<byte[]> templateSinInsumos() {
        byte[] excel = cargaMasivaTerminadoService.generateTemplateExcelSinInsumos();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"plantilla_carga_masiva_terminados_sin_insumos.xlsx\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(excel);
    }

    @PostMapping(value = "/validar-sin-insumos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ValidationResultDTO> validarSinInsumos(
            @RequestParam("file") MultipartFile file,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        log.debug("validarSinInsumos: request received, file name={}, Authorization present={}",
                file != null ? file.getOriginalFilename() : null, authorization != null && !authorization.isBlank());
        ValidationResultDTO result = cargaMasivaTerminadoService.validateExcelSinInsumos(file);
        return ResponseEntity.ok(result);
    }

    @PostMapping(value = "/ejecutar-sin-insumos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ValidationResultDTO> ejecutarSinInsumos(@RequestParam("file") MultipartFile file) {
        log.debug("[CargaMasivaTerminados] ejecutarSinInsumos: archivo={}, size={} bytes, isEmpty={}",
                file != null ? file.getOriginalFilename() : null,
                file != null ? file.getSize() : 0,
                file != null ? file.isEmpty() : true);
        ValidationResultDTO result = cargaMasivaTerminadoService.processBulkInsertSinInsumos(file);
        log.debug("[CargaMasivaTerminados] ejecutarSinInsumos: resultado valid={}, rowCount={}, errorsCount={}",
                result.isValid(), result.getRowCount(),
                result.getErrors() != null ? result.getErrors().size() : 0);
        if (!result.isValid() && result.getErrors() != null && !result.getErrors().isEmpty()) {
            return ResponseEntity.badRequest().body(result);
        }
        return ResponseEntity.ok(result);
    }
}
