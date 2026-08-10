package exotic.app.planta.model.organigrama.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class GuardarOrganigramaRequest {

    @NotNull
    @Min(0)
    private Long baseRevision;

    @NotNull
    @Valid
    private List<CargoOrganigramaRequest> cargos = new ArrayList<>();

    @NotNull
    @Valid
    private List<RelacionOrganigramaRequest> relaciones = new ArrayList<>();
}
