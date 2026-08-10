package exotic.app.planta.model.organigrama.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ManualFuncionesUrlRequest {

    @NotBlank
    @Size(max = 255)
    private String url;
}
