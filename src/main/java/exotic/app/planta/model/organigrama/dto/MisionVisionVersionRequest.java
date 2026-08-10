package exotic.app.planta.model.organigrama.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class MisionVisionVersionRequest {

    @NotNull
    @Min(1)
    private Integer versionBase;

    @NotBlank
    @Size(max = 25000)
    private String misionHtml;

    @NotBlank
    @Size(max = 25000)
    private String visionHtml;

    @NotEmpty
    @Size(max = 12)
    @Valid
    private List<MisionVisionValorRequest> valores = new ArrayList<>();

    @NotBlank
    @Size(max = 1000)
    private String motivoCambio;
}
