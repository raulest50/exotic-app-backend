package exotic.app.planta.model.empresa.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class JornadaLaboralVersionRequest {

    @NotBlank
    @Size(max = 1000)
    private String motivoCambio;

    @NotEmpty
    @Valid
    private List<JornadaLaboralDiaRequest> dias = new ArrayList<>();
}
