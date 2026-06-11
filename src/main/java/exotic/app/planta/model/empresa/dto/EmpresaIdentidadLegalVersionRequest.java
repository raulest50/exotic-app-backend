package exotic.app.planta.model.empresa.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class EmpresaIdentidadLegalVersionRequest {

    @NotBlank
    @Size(max = 255)
    private String razonSocial;

    @NotBlank
    @Size(max = 160)
    private String nombreComercial;

    @NotBlank
    @Size(max = 30)
    private String tipoIdentificacion;

    @NotBlank
    @Size(max = 40)
    private String numeroIdentificacion;

    @NotBlank
    @Size(max = 10)
    private String digitoVerificacion;

    @NotBlank
    @Size(max = 80)
    private String telefonoPrincipal;

    @NotBlank
    @Email
    @Size(max = 255)
    private String emailPrincipal;

    @NotBlank
    @Size(max = 1000)
    private String motivoCambio;
}
