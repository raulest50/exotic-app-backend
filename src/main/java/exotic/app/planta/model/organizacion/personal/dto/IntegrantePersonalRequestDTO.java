package exotic.app.planta.model.organizacion.personal.dto;

import exotic.app.planta.model.organizacion.personal.IntegrantePersonal;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class IntegrantePersonalRequestDTO {
    private Long id;
    private String nombres;
    private String apellidos;
    private String celular;
    private String direccion;
    private String email;
    private String nombreContactoEmergencia;
    private String celularContactoEmergencia;
    private IntegrantePersonal.EstadoCivil estadoCivil;
    private Integer numeroHijos;
    private LocalDate fechaIngreso;
    private String numeroCuentaBancaria;
    private String banco;
    private String cargo;
    private IntegrantePersonal.Departamento departamento;
    private String centroDeCosto;
    private String centroDeProduccion;
    private Integer salario;
    private IntegrantePersonal.Estado estado;
}
