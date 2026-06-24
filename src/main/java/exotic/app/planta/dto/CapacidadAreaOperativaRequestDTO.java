package exotic.app.planta.dto;

import exotic.app.planta.model.organizacion.PeriodoCapacidadAreaOperativa;
import exotic.app.planta.model.organizacion.TipoCapacidadAreaOperativa;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CapacidadAreaOperativaRequestDTO {
    @NotNull(message = "La unidad de medida es obligatoria")
    private Long unidadMedidaId;

    @NotNull(message = "El tipo de capacidad es obligatorio")
    private TipoCapacidadAreaOperativa tipoCapacidad;

    @NotNull(message = "La cantidad de capacidad es obligatoria")
    @DecimalMin(value = "0.000001", message = "La cantidad debe ser mayor que 0")
    private BigDecimal cantidad;

    @NotNull(message = "El periodo de capacidad es obligatorio")
    private PeriodoCapacidadAreaOperativa periodo;

    @DecimalMin(value = "0.0000", message = "La eficiencia debe ser mayor o igual a 0")
    @DecimalMax(value = "1.0000", message = "La eficiencia debe ser menor o igual a 1")
    private BigDecimal eficiencia;

    private LocalDate vigenteDesde;
    private LocalDate vigenteHasta;
    private String descripcion;
    private Boolean activo;
}
