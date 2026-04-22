package exotic.app.planta.model.commons.dto.importacion;

import java.time.LocalDateTime;

public record BackupTotalImportJobResponseDTO(
        String jobId,
        String estado,
        String filename,
        LocalDateTime requestedAt,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        LocalDateTime expiresAt,
        String errorCode,
        String message
) {
}
