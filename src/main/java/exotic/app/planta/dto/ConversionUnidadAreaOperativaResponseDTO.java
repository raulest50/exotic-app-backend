package exotic.app.planta.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConversionUnidadAreaOperativaResponseDTO {
    private UnidadMedidaAreaOperativaDTO unidadOrigen;
    private UnidadMedidaAreaOperativaDTO unidadDestino;
    private BigDecimal cantidadOrigen;
    private BigDecimal cantidadEstandar;
    private String unidadEstandar;
    private BigDecimal cantidadDestino;
}
