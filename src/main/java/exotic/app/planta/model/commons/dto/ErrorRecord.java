package exotic.app.planta.model.commons.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ErrorRecord {
    private int rowNumber;
    private String productoId;
    private String message;
    private String columnName;

    public ErrorRecord(int rowNumber, String productoId, String message) {
        this(rowNumber, productoId, message, null);
    }

    public ErrorRecord(int rowNumber, String productoId, String message, String columnName) {
        this.rowNumber = rowNumber;
        this.productoId = productoId != null ? productoId : "";
        this.message = message != null ? message : "";
        this.columnName = columnName;
    }
}
