package exotic.app.planta.model.organigrama.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RelacionOrganigramaRequest {

    @NotBlank
    @Size(max = 128)
    private String jefeId;

    @NotBlank
    @Size(max = 128)
    private String subordinadoId;
}
