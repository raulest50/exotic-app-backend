package exotic.app.planta.model.empresa.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalTime;

@Data
public class JornadaLaboralBloqueRequest {

    @NotNull
    private LocalTime horaInicio;

    @NotNull
    private LocalTime horaFin;
}
