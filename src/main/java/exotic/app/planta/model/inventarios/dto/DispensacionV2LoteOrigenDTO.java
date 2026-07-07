package exotic.app.planta.model.inventarios.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DispensacionV2LoteOrigenDTO {
    private Long loteId;
    private String batchNumber;
    private LocalDate productionDate;
    private LocalDate expirationDate;
    private double cantidadDisponible;
    private double cantidadAsignada;
    private boolean sugerido = true;
}
