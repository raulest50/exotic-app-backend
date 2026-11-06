package exotic.app.planta.model.produccion.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Contrato operativo: cantidades decimales como texto y destino derivado del seguimiento. */
public final class ConsumoSemiterminadoAreaDTOs {
    private ConsumoSemiterminadoAreaDTOs() {}

    @Data
    public static class RegistroRequest {
        @NotNull private UUID idempotencyKey;
        @Valid @NotEmpty @Size(max = 200) private List<LoteRequest> lotes;
        @Size(max = 255) private String observaciones;
    }

    @Data
    public static class LoteRequest {
        @NotBlank @Size(max = 255) private String productoId;
        @NotNull private Long loteId;
        @NotBlank private String cantidad;
    }

    public record Requerimiento(String productoId, String productoNombre, String unidadMedida,
                               String cantidadRequerida, String cantidadRegistrada, String cantidadPendiente) {}
    public record ItemRegistrado(String productoId, Long loteId, String batchNumber, String cantidad) {}
    public record Registro(int transaccionId, LocalDateTime fecha, String usuario,
                           String observaciones, List<ItemRegistrado> items) {}
    public record Contexto(int ordenId, Long seguimientoId, Long rutaProcesoVersionId,
                           Long rutaProcesoNodeId, int areaOperativaId, String areaOperativaNombre,
                           boolean legacy, boolean puedeRegistrar, String motivoBloqueo,
                           List<Requerimiento> requerimientos, List<Registro> registros) {}
    public record LoteDisponible(Long loteId, String batchNumber, LocalDate fechaVencimiento,
                                 String estadoCalidad, String cantidadDisponible) {}
    public record RegistroResponse(int transaccionId, boolean reutilizado, Contexto contexto) {}
}
