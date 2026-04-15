package exotic.app.planta.model.produccion.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlaneacionExcelDebugResponseDTO {
    private String debugId;
    private String message;
    private int sheetCount;
    private String detectedPrimarySheet;
    private int headerMismatchCount;
}
