package exotic.app.planta.model.produccion.dto;

import exotic.app.planta.model.produccion.EstadoMpsSemanal;
import exotic.app.planta.model.produccion.MasterProductionScheduleSemanal;
import exotic.app.planta.model.produccion.SemanaMPS;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
public class SemanaMPSDTO {
    private Long id;
    private String codigo;
    private int anioSemana;
    private int numeroSemana;
    private LocalDate startDate;
    private LocalDate endDate;
    private String standard;
    private Integer mpsId;
    private EstadoMpsSemanal estado;
    private LocalDateTime fechaGeneracionOdps;

    public static SemanaMPSDTO fromSemana(SemanaMPS semana) {
        return fromSemanaAndMps(semana, null);
    }

    public static SemanaMPSDTO fromSemanaAndMps(SemanaMPS semana, MasterProductionScheduleSemanal mps) {
        SemanaMPSDTO dto = new SemanaMPSDTO();
        dto.setId(semana.getId());
        dto.setCodigo(semana.getCodigo());
        dto.setAnioSemana(semana.getAnioSemana());
        dto.setNumeroSemana(semana.getNumeroSemana());
        dto.setStartDate(semana.getStartDate());
        dto.setEndDate(semana.getEndDate());
        dto.setStandard(semana.getStandard());
        if (mps != null) {
            dto.setMpsId(mps.getMpsId());
            dto.setEstado(mps.getEstado());
            dto.setFechaGeneracionOdps(mps.getFechaGeneracionOdps());
        }
        return dto;
    }
}
