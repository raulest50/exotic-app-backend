package exotic.app.planta.model.organigrama.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class MisionVisionValorRequest {

    @NotBlank
    @Size(max = 120)
    private String titulo;

    @NotBlank
    @Size(max = 12000)
    private String descripcionHtml;
}
