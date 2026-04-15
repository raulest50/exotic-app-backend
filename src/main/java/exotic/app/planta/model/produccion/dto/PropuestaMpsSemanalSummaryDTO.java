package exotic.app.planta.model.produccion.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class PropuestaMpsSemanalSummaryDTO {
    private int totalTerminadosEvaluados;
    private int totalPlanificables;
    private int totalNoPlanificablesPorFaltaLoteSize;
    private int totalLotesPropuestos;
    private double totalUnidadesPropuestas;
}
