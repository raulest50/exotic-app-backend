package exotic.app.planta.model.organizacion.personal.dto;

import exotic.app.planta.model.organizacion.personal.IntegrantePersonal;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class IntegrantePersonalDetalleDTO {
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
    private LocalDateTime fechaRegistro;

    public static IntegrantePersonalDetalleDTO fromEntity(
            IntegrantePersonal integrante,
            LocalDateTime fechaRegistro
    ) {
        if (integrante == null) {
            return null;
        }
        return IntegrantePersonalDetalleDTO.builder()
                .id(integrante.getId())
                .nombres(integrante.getNombres())
                .apellidos(integrante.getApellidos())
                .celular(integrante.getCelular())
                .direccion(integrante.getDireccion())
                .email(integrante.getEmail())
                .nombreContactoEmergencia(integrante.getNombreContactoEmergencia())
                .celularContactoEmergencia(integrante.getCelularContactoEmergencia())
                .estadoCivil(integrante.getEstadoCivil())
                .numeroHijos(integrante.getNumeroHijos())
                .fechaIngreso(integrante.getFechaIngreso())
                .numeroCuentaBancaria(integrante.getNumeroCuentaBancaria())
                .banco(integrante.getBanco())
                .cargo(integrante.getCargo())
                .departamento(integrante.getDepartamento())
                .centroDeCosto(integrante.getCentroDeCosto())
                .centroDeProduccion(integrante.getCentroDeProduccion())
                .salario(integrante.getSalario())
                .estado(integrante.getEstado())
                .fechaRegistro(fechaRegistro)
                .build();
    }
}
