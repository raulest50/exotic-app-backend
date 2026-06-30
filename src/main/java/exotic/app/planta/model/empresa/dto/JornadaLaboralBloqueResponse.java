package exotic.app.planta.model.empresa.dto;

import exotic.app.planta.model.empresa.JornadaLaboralBloque;
import lombok.Builder;
import lombok.Data;

import java.time.LocalTime;

@Data
@Builder
public class JornadaLaboralBloqueResponse {

    private Long id;
    private Integer diaSemana;
    private Integer orden;
    private LocalTime horaInicio;
    private LocalTime horaFin;

    public static JornadaLaboralBloqueResponse fromEntity(JornadaLaboralBloque bloque) {
        return JornadaLaboralBloqueResponse.builder()
                .id(bloque.getId())
                .diaSemana(bloque.getDiaSemana())
                .orden(bloque.getOrden())
                .horaInicio(bloque.getHoraInicio())
                .horaFin(bloque.getHoraFin())
                .build();
    }
}
