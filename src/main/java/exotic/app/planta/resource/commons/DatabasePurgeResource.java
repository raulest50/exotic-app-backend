package exotic.app.planta.resource.commons;

import exotic.app.planta.config.runtime.ApplicationRuntimeEnvironmentResolver;
import exotic.app.planta.model.commons.dto.eliminaciones.PurgaBaseDatosResultDTO;
import exotic.app.planta.model.users.User;
import exotic.app.planta.repo.usuarios.UserRepository;
import exotic.app.planta.service.commons.DangerousOperationGuard;
import exotic.app.planta.service.commons.DatabasePurgeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/eliminaciones-forzadas")
@RequiredArgsConstructor
@Slf4j
public class DatabasePurgeResource {

    private static final String OPERATION_NAME = "La purga total de base de datos";

    private final DatabasePurgeService databasePurgeService;
    private final DangerousOperationGuard dangerousOperationGuard;
    private final ApplicationRuntimeEnvironmentResolver applicationRuntimeEnvironmentResolver;
    private final UserRepository userRepository;

    @DeleteMapping("/base-datos")
    public ResponseEntity<PurgaBaseDatosResultDTO> purgeDatabase(Authentication authentication) {
        try {
            dangerousOperationGuard.assertLocalOrStagingOnly(OPERATION_NAME);

            User currentUser = requireCurrentUser(authentication);
            if (!isMasterLike(currentUser.getUsername())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(buildForbiddenResult("Solo master o super_master pueden ejecutar la purga total."));
            }

            PurgaBaseDatosResultDTO result = databasePurgeService.purgeDatabaseKeepingMasterLikeAccess();
            return ResponseEntity.ok(result);
        } catch (UnsupportedOperationException e) {
            log.warn("[PURGA_TOTAL_BD] Purga no soportada en el entorno actual. message={}", e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(buildBlockedResult(e.getMessage()));
        } catch (IllegalStateException e) {
            log.warn("[PURGA_TOTAL_BD] Purga bloqueada. message={}", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(buildBlockedResult(e.getMessage()));
        }
    }

    private User requireCurrentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalStateException("No autenticado");
        }

        return userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new IllegalStateException("Usuario autenticado no encontrado"));
    }

    private PurgaBaseDatosResultDTO buildForbiddenResult(String message) {
        PurgaBaseDatosResultDTO result = baseResult(message);
        result.setPermitted(false);
        result.setExecuted(false);
        return result;
    }

    private PurgaBaseDatosResultDTO buildBlockedResult(String message) {
        PurgaBaseDatosResultDTO result = baseResult(message);
        result.setPermitted(false);
        result.setExecuted(false);
        return result;
    }

    private PurgaBaseDatosResultDTO baseResult(String message) {
        PurgaBaseDatosResultDTO result = new PurgaBaseDatosResultDTO();
        result.setMessage(message);
        result.setEnvironment(applicationRuntimeEnvironmentResolver.getCurrentEnvironment().value());
        return result;
    }

    private boolean isMasterLike(String username) {
        if (username == null) {
            return false;
        }
        String normalized = username.trim().toLowerCase();
        return "master".equals(normalized) || "super_master".equals(normalized);
    }
}
