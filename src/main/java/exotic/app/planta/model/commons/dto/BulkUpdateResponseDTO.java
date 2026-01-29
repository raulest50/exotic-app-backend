package exotic.app.planta.model.commons.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BulkUpdateResponseDTO {
    private int successCount;
    private int failureCount;
    private List<ErrorRecord> errors;
    private byte[] reportFile;
    private String reportFileName;
}
