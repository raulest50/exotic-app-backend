package exotic.app.planta.dto;

import exotic.app.planta.model.organizacion.DimensionUnidadAreaOperativa;
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
    @NotBlank(message = "El codigo de la unidad es obligatorio")
    private String codigo;

    @NotBlank(message = "El nombre de la unidad es obligatorio")
    private String nombre;

    private String descripcion;

    @NotNull(message = "La dimension de la unidad es obligatoria")
    private DimensionUnidadAreaOperativa dimension;

    @NotBlank(message = "La unidad estandar es obligatoria")
    private String unidadEstandar;

    @NotNull(message = "La cantidad en unidad estandar es obligatoria")
    @DecimalMin(value = "0.000001", message = "La cantidad en unidad estandar debe ser mayor que 0")
    private BigDecimal cantidadUnidadEstandar;

    private Boolean principal;
    private Boolean discreta;
    private Boolean activo;
}
