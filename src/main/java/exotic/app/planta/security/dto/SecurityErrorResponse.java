package exotic.app.planta.security.dto;

import java.time.LocalDateTime;

public record SecurityErrorResponse(
        String code,
        String message,
        LocalDateTime timestamp,
        String path
) {
}
