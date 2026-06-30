package exotic.app.planta.dto;

import exotic.app.planta.model.organizacion.UnidadRelacionAreaOperativa;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UnidadMedidaAreaOperativaRequestDTO {
    @NotBlank(message = "El nombre de la unidad es obligatorio")
    private String nombre;

    @NotNull(message = "La relacion estandar es obligatoria")
    @DecimalMin(value = "0.000001", message = "La relacion estandar debe ser mayor que 0")
    private BigDecimal relacionEstandar;

    @NotNull(message = "La unidad de relacion es obligatoria")
    private UnidadRelacionAreaOperativa unidadRelacion;
}
