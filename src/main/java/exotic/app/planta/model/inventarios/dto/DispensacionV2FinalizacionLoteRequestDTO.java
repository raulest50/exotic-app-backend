package exotic.app.planta.model.inventarios.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class DispensacionV2FinalizacionLoteRequestDTO {
    private Long loteId;
    private Double cantidadAsignada;
}
