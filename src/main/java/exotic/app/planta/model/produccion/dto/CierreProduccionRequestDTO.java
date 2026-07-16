package exotic.app.planta.model.produccion.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
public class CierreProduccionRequestDTO {
    @NotNull
    private LocalDate fechaProduccion;

    @NotNull
    private UUID idempotencyKey;

    @Valid
    @NotEmpty
    private List<ItemDTO> reportes = new ArrayList<>();

    @Data
    public static class ItemDTO {
        @NotNull
        private Long reporteId;

        @NotNull
        private Long version;

        @NotNull
        @Positive
        private BigDecimal cantidadConfirmada;

        @Size(max = 500)
        private String motivoCorreccion;
    }
}
