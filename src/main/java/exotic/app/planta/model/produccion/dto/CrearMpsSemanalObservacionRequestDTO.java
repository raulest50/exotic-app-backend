package exotic.app.planta.model.produccion.dto;

import exotic.app.planta.model.produccion.TipoMpsSemanalObservacion;
import lombok.Data;

import java.time.LocalDate;

@Data
public class CrearMpsSemanalObservacionRequestDTO {
    private LocalDate weekStartDate;
    private TipoMpsSemanalObservacion tipo;
    private String mensaje;
}
