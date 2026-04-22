package exotic.app.planta.resource.commons;

import exotic.app.planta.model.commons.dto.importacion.BackupTotalImportJobResponseDTO;
import exotic.app.planta.model.users.ModuloSistema;
import exotic.app.planta.model.users.User;
import exotic.app.planta.model.users.UserAccessEvaluator;
import exotic.app.planta.repo.usuarios.UserRepository;
import exotic.app.planta.service.commons.BackupTotalImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NO_CONTENT;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@RestController
@RequestMapping("/api/importacion-datos")
@RequiredArgsConstructor
public class ImportacionDatosResource {

    private final BackupTotalImportService backupTotalImportService;
    private final UserRepository userRepository;

    @PostMapping("/backup-total/jobs")
    public ResponseEntity<BackupTotalImportJobResponseDTO> crearImportacionTotal(
            Authentication authentication,
            @RequestParam("file") MultipartFile file
    ) {
        try {
            User currentUser = requireAuthorizedUser(authentication);
            BackupTotalImportJobResponseDTO response = backupTotalImportService.createJob(currentUser, file);
            return ResponseEntity.accepted().body(response);
        } catch (UnsupportedOperationException e) {
            throw new ResponseStatusException(FORBIDDEN, e.getMessage());
        }
    }

    @GetMapping("/backup-total/jobs/{jobId}")
    public ResponseEntity<BackupTotalImportJobResponseDTO> consultarImportacionTotal(
            Authentication authentication,
            @PathVariable String jobId
    ) {
        try {
            String ownerUsername = requireAuthenticatedUsername(authentication);
            return ResponseEntity.ok(backupTotalImportService.getJob(ownerUsername, jobId));
        } catch (UnsupportedOperationException e) {
            throw new ResponseStatusException(FORBIDDEN, e.getMessage());
        }
    }

    @DeleteMapping("/backup-total/jobs/{jobId}")
    public ResponseEntity<Void> eliminarImportacionTotal(
            Authentication authentication,
            @PathVariable String jobId
    ) {
        try {
            String ownerUsername = requireAuthenticatedUsername(authentication);
            backupTotalImportService.deleteJob(ownerUsername, jobId);
            return ResponseEntity.status(NO_CONTENT).build();
        } catch (UnsupportedOperationException e) {
            throw new ResponseStatusException(FORBIDDEN, e.getMessage());
        }
    }

    private User requireAuthorizedUser(Authentication authentication) {
        String username = requireAuthenticatedUsername(authentication);
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(UNAUTHORIZED, "Usuario no encontrado"));

        boolean isMasterLike = isMasterLike(user.getUsername());
        boolean hasTabAccess = UserAccessEvaluator.tabNivel(user, ModuloSistema.OPERACIONES_CRITICAS_BD, "CARGAS_MASIVAS")
                .orElse(0) >= 1;

        if (!isMasterLike && !hasTabAccess) {
            throw new ResponseStatusException(FORBIDDEN, "No tiene permisos para importar backups totales.");
        }

        return user;
    }

    private String requireAuthenticatedUsername(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new ResponseStatusException(UNAUTHORIZED, "No autenticado");
        }
        return authentication.getName();
    }

    private boolean isMasterLike(String username) {
        if (username == null) return false;
        String normalized = username.toLowerCase();
        return "master".equals(normalized) || "super_master".equals(normalized);
    }
}
