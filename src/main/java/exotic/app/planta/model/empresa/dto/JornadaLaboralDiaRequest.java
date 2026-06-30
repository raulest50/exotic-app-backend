package exotic.app.planta.model.empresa.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class JornadaLaboralDiaRequest {

    @NotNull
    @Min(1)
    @Max(7)
    private Integer diaSemana;

    @NotNull
    private Boolean laborable;

    @Valid
    private List<JornadaLaboralBloqueRequest> bloques = new ArrayList<>();
}
