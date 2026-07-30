package exotic.app.planta.resource.commons;

import exotic.app.planta.model.commons.dto.CargaPuntosReordenDTOs;
import exotic.app.planta.model.users.ModuloSistema;
import exotic.app.planta.model.users.User;
import exotic.app.planta.model.users.UserAccessEvaluator;
import exotic.app.planta.repo.usuarios.UserRepository;
import exotic.app.planta.service.commons.CargaMasivaPuntosReordenService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/carga-masiva-puntos-reorden")
@RequiredArgsConstructor
public class CargaMasivaPuntosReordenResource {

    private static final String TAB_ID = "CARGAS_MASIVAS";

    private final CargaMasivaPuntosReordenService service;
    private final UserRepository userRepository;

    @GetMapping("/template")
    public ResponseEntity<byte[]> template(Authentication authentication) {
        requireAccess(authentication);
        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"plantilla_actualizacion_puntos_reorden.xlsx\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(service.generateTemplateExcel());
    }

    @PostMapping(value = "/validar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CargaPuntosReordenDTOs.ValidationResponse> validate(
            @RequestParam("file") MultipartFile file,
            Authentication authentication
    ) {
        requireAccess(authentication);
        return ResponseEntity.ok(service.validateExcel(file));
    }

    @PostMapping(value = "/ejecutar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CargaPuntosReordenDTOs.ExecutionResponse> execute(
            @RequestParam("file") MultipartFile file,
            Authentication authentication
    ) {
        requireAccess(authentication);
        return ResponseEntity.ok(service.execute(file));
    }

    private User requireAccess(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication.getName() == null
                || authentication.getName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No autenticado");
        }
        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Usuario no encontrado"));
        boolean allowed = UserAccessEvaluator.tabNivel(
                        user,
                        ModuloSistema.OPERACIONES_CRITICAS_BD,
                        TAB_ID)
                .orElse(0) >= 1;
        if (!allowed) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "No tiene permiso para cargas masivas");
        }
        return user;
    }
}
