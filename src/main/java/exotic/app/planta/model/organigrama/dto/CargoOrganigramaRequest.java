package exotic.app.planta.model.organigrama.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CargoOrganigramaRequest {

    @NotBlank
    @Size(max = 128)
    private String idCargo;

    @NotBlank
    @Size(max = 255)
    private String tituloCargo;

    @NotBlank
    @Size(max = 255)
    private String descripcionCargo;

    @NotBlank
    @Size(max = 255)
    private String departamento;

    @Size(max = 120)
    private String usuario;

    private double posicionX;

    private double posicionY;

    @Min(1)
    @Max(10)
    private int nivel;
}
