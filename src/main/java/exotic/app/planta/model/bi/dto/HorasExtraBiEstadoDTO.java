package exotic.app.planta.model.bi.dto;

import exotic.app.planta.model.organizacion.personal.RegistroHoraExtra;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class HorasExtraBiEstadoDTO {
    private RegistroHoraExtra.Estado estado;
    private long registros;
    private int minutos;
    private double horas;
}
