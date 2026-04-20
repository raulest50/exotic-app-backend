package exotic.app.planta.resource.commons;

import exotic.app.planta.model.commons.dto.exportacion.BackupTotalJobResponseDTO;
import exotic.app.planta.model.users.ModuloSistema;
import exotic.app.planta.model.users.User;
import exotic.app.planta.model.users.UserAccessEvaluator;
import exotic.app.planta.repo.usuarios.UserRepository;
import exotic.app.planta.service.commons.BackupTotalExportService;
import exotic.app.planta.service.commons.ExportacionMaterialService;
import exotic.app.planta.service.commons.ExportacionProveedorService;
import exotic.app.planta.service.commons.ExportacionTerminadoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;

import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NO_CONTENT;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@RestController
@RequestMapping("/api/exportacion-datos")
@RequiredArgsConstructor
public class ExportacionDatosResource {

    private final ExportacionMaterialService exportacionMaterialService;
    private final ExportacionTerminadoService exportacionTerminadoService;
    private final ExportacionProveedorService exportacionProveedorService;
    private final BackupTotalExportService backupTotalExportService;
    private final UserRepository userRepository;

    @GetMapping("/materiales/excel")
    public ResponseEntity<byte[]> exportarMaterialesExcel() {
        byte[] excel = exportacionMaterialService.exportarMaterialesExcel();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"exportacion_materiales.xlsx\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(excel);
    }

    @GetMapping("/terminados/excel")
    public ResponseEntity<byte[]> exportarTerminadosExcel() {
        byte[] excel = exportacionTerminadoService.exportarTerminadosExcel();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"exportacion_terminados.xlsx\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(excel);
    }

    @GetMapping("/terminados/json-con-insumos")
    public ResponseEntity<byte[]> exportarTerminadosJsonConInsumos() {
        byte[] json = exportacionTerminadoService.exportarTerminadosJsonConInsumos();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"exportacion_terminados_con_insumos.json\"")
                .contentType(MediaType.APPLICATION_JSON)
                .body(json);
    }

    @GetMapping("/proveedores/json-con-contactos")
    public ResponseEntity<byte[]> exportarProveedoresJsonConContactos() {
        byte[] json = exportacionProveedorService.exportarProveedoresJsonConContactos();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"exportacion_proveedores.json\"")
                .contentType(MediaType.APPLICATION_JSON)
                .body(json);
    }

    @PostMapping("/backup-total/jobs")
    public ResponseEntity<BackupTotalJobResponseDTO> crearBackupTotal(Authentication authentication) {
        User currentUser = requireAuthorizedUser(authentication);
        BackupTotalJobResponseDTO response = backupTotalExportService.createJob(currentUser);
        return ResponseEntity.accepted().body(response);
    }

    @GetMapping("/backup-total/jobs/{jobId}")
    public ResponseEntity<BackupTotalJobResponseDTO> consultarBackupTotal(
            Authentication authentication,
            @PathVariable String jobId
    ) {
        User currentUser = requireAuthorizedUser(authentication);
        return ResponseEntity.ok(backupTotalExportService.getJob(currentUser, jobId));
    }

    @GetMapping("/backup-total/jobs/{jobId}/download")
    public ResponseEntity<?> descargarBackupTotal(
            Authentication authentication,
            @PathVariable String jobId
    ) throws IOException {
        User currentUser = requireAuthorizedUser(authentication);
        BackupTotalExportService.DownloadPayload payload = backupTotalExportService.getDownloadPayload(currentUser, jobId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + payload.filename() + "\"")
                .contentLength(payload.contentLength())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(payload.resource());
    }

    @DeleteMapping("/backup-total/jobs/{jobId}")
    public ResponseEntity<Void> eliminarBackupTotal(
            Authentication authentication,
            @PathVariable String jobId
    ) {
        User currentUser = requireAuthorizedUser(authentication);
        backupTotalExportService.deleteJob(currentUser, jobId);
        return ResponseEntity.status(NO_CONTENT).build();
    }

    private User requireAuthorizedUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(UNAUTHORIZED, "No autenticado");
        }

        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(UNAUTHORIZED, "Usuario no encontrado"));

        boolean isMasterLike = isMasterLike(user.getUsername());
        boolean hasTabAccess = UserAccessEvaluator.tabNivel(user, ModuloSistema.OPERACIONES_CRITICAS_BD, "EXPORTACION_DATOS")
                .orElse(0) >= 1;

        if (!isMasterLike && !hasTabAccess) {
            throw new ResponseStatusException(FORBIDDEN, "No tiene permisos para exportar backups totales.");
        }

        return user;
    }

    private boolean isMasterLike(String username) {
        if (username == null) return false;
        String normalized = username.toLowerCase();
        return "master".equals(normalized) || "super_master".equals(normalized);
    }
}
