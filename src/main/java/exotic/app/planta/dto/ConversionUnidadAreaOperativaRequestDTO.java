package exotic.app.planta.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConversionUnidadAreaOperativaRequestDTO {
    @NotNull(message = "La unidad origen es obligatoria")
    private Long unidadOrigenId;

    @NotNull(message = "La cantidad origen es obligatoria")
    @DecimalMin(value = "0.000001", message = "La cantidad origen debe ser mayor que 0")
    private BigDecimal cantidadOrigen;

    @NotNull(message = "La unidad destino es obligatoria")
    private Long unidadDestinoId;
}
