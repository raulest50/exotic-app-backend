package exotic.app.planta.model.commons.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class CargaCostosDTOs {
    private CargaCostosDTOs() {}

    public record ErrorFila(int fila, String codigo, String campo, String mensaje) {}

    public record ItemPreview(
            int fila,
            String productoId,
            String nombreProducto,
            String descripcionExcel,
            boolean descripcionCoincide,
            BigDecimal costoActual,
            BigDecimal costoNuevo,
            BigDecimal diferencia,
            BigDecimal porcentajeCambio,
            boolean cambia
    ) {}

    public record PreparacionResponse(
            UUID loteId,
            String estado,
            String nombreArchivo,
            String motivo,
            OffsetDateTime expiraEn,
            int totalFilas,
            int totalCandidatas,
            int totalActualizadas,
            int totalSinCambio,
            int totalOmitidas,
            List<String> advertencias
    ) {}

    public record ItemsPageResponse(
            List<ItemPreview> items,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {}

    public record ErrorResponse(
            String codigo,
            String mensaje,
            List<ErrorFila> errores,
            List<String> advertencias,
            Integer intentosRestantes
    ) {
        public static ErrorResponse simple(String codigo, String mensaje) {
            return new ErrorResponse(codigo, mensaje, List.of(), List.of(), null);
        }
    }

    public record TokenResponse(
            String token,
            OffsetDateTime expiraEn,
            int generacionesRestantes,
            int intentosPermitidos
    ) {}

    public record ConfirmacionRequest(
            @NotBlank(message = "El token es obligatorio")
            @Pattern(regexp = "\\d{4}", message = "El token debe contener cuatro digitos")
            String token
    ) {}

    public record ConfirmacionResponse(
            UUID loteId,
            boolean ejecutado,
            String estado,
            String mensaje,
            OffsetDateTime ejecutadoEn,
            int totalActualizadas,
            int totalSinCambio
    ) {}
}
