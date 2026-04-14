package exotic.app.planta.dto;

import exotic.app.planta.config.AppTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO for standardized error responses
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ErrorResponse {
    
    private String title;
    private String message;
    private LocalDateTime timestamp = AppTime.now();
    
    public ErrorResponse(String title, String message) {
        this.title = title;
        this.message = message;
    }
}
