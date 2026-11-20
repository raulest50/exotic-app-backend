package exotic.app.planta.model.producto.dto.manufacturing;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
public class NodoProcesoDTO extends ProcesoFabricacionNodoDTO {
    /** Tipos previstos para esta operacion, no equipos fisicos ni reservas de capacidad. */
    private java.util.List<Long> recursosProduccionIds;
    private Integer procesoId;
    private String procesoNombre;
    private Integer areaOperativaId;
    private String areaOperativaNombre;
    private Double setUpTime;
    private String model;
    private Double constantSeconds;
    private Double throughputUnitsPerSec;
    private Double secondsPerUnit;
    private Double secondsPerBatch;
    private Double batchSize;

    public NodoProcesoDTO() {
        setNodeType("PROCESO");
    }
}
