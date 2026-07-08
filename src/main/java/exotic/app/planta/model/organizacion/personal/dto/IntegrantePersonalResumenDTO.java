package exotic.app.planta.model.organizacion.personal.dto;

import exotic.app.planta.model.organizacion.personal.IntegrantePersonal;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

@Data
@Builder
public class IntegrantePersonalResumenDTO {
    private Long id;
    private String nombres;
    private String apellidos;
    private String cargo;
    private IntegrantePersonal.Departamento departamento;
    private String centroDeCosto;
    private String centroDeProduccion;
    private Integer salario;
    private IntegrantePersonal.Estado estado;
    private LocalDate fechaIngreso;

    public static IntegrantePersonalResumenDTO fromEntity(IntegrantePersonal integrante) {
        if (integrante == null) {
            return null;
        }
        return IntegrantePersonalResumenDTO.builder()
                .id(integrante.getId())
                .nombres(integrante.getNombres())
                .apellidos(integrante.getApellidos())
                .cargo(integrante.getCargo())
                .departamento(integrante.getDepartamento())
                .centroDeCosto(integrante.getCentroDeCosto())
                .centroDeProduccion(integrante.getCentroDeProduccion())
                .salario(integrante.getSalario())
                .estado(integrante.getEstado())
                .fechaIngreso(integrante.getFechaIngreso())
                .build();
    }
}
