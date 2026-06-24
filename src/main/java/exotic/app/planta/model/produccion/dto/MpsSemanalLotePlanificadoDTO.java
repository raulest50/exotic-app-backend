package exotic.app.planta.model.produccion.dto;

import exotic.app.planta.model.produccion.EstadoMpsSemanalLotePlanificado;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class MpsSemanalLotePlanificadoDTO {
    private Long id;
    private int loteOrdinal;
    private double cantidadPlanificada;
    private EstadoMpsSemanalLotePlanificado estado;
    private Integer ordenProduccionId;
    private String loteAsignado;
    private boolean ordenIniciada;
    private boolean ordenCancelable;
}
