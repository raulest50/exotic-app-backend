package exotic.app.planta.model.commons.dto;

import java.util.List;

public final class CargaPuntosReordenDTOs {

    private CargaPuntosReordenDTOs() {
    }

    public record ErrorFila(
            int rowNumber,
            String productoId,
            String columnName,
            String message
    ) {
    }

    public record CambioPreview(
            int rowNumber,
            String productoId,
            String nombre,
            double currentValue,
            double newValue
    ) {
    }

    public record ValidationResponse(
            boolean valid,
            int totalRows,
            int ignoredRows,
            int unchangedRows,
            int updateRows,
            int errorRows,
            List<CambioPreview> changes,
            List<ErrorFila> errors
    ) {
    }

    public record ExecutionResponse(
            boolean success,
            int totalRows,
            int ignoredRows,
            int unchangedRows,
            int updatedRows
    ) {
    }
}
