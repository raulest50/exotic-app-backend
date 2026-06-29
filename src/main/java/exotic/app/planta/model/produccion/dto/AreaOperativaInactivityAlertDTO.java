package exotic.app.planta.model.produccion.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AreaOperativaInactivityAlertDTO {

    private Integer areaId;
    private String areaNombre;
    private EstadoAlertaInactividad estado;
    private boolean alertaActiva;
    private boolean tieneCargaActiva;
    private LocalDateTime ultimaTerminacionAt;
    private Long minutosDesdeUltimaTerminacion;
    private int thresholdMinutes;
    private int checkIntervalMinutes;
    private boolean alertsEnabled;

    public enum EstadoAlertaInactividad {
        ACTIVA,
        INACTIVA,
        SIN_TERMINACIONES
    }
}
