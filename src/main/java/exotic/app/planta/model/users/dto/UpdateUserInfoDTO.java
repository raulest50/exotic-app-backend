package exotic.app.planta.model.users.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class UpdateUserInfoDTO {

    private long cedula;
    private String username;
    private String nombreCompleto;
    private String email;
    private String cel;
    private String direccion;
    private LocalDate fechaNacimiento;
}
