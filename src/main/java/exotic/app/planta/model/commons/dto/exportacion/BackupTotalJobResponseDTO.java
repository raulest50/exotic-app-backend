package exotic.app.planta.model.commons.dto.exportacion;

import java.time.LocalDateTime;

public record BackupTotalJobResponseDTO(
        String jobId,
        String estado,
        String filename,
        LocalDateTime requestedAt,
        LocalDateTime readyAt,
        LocalDateTime expiresAt,
        String errorCode,
        String message
) {
}
