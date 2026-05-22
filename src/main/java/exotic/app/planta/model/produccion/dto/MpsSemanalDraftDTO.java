package exotic.app.planta.model.produccion.dto;

import exotic.app.planta.model.produccion.EstadoMpsSemanal;
import exotic.app.planta.model.produccion.MasterProductionScheduleSemanal;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
public class MpsSemanalDraftDTO {
    private Integer mpsId;
    private EstadoMpsSemanal estado;
    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaActualizacion;
    private LocalDate weekStartDate;
    private LocalDate weekEndDate;
    private PropuestaMpsSemanalSummaryDTO summary = new PropuestaMpsSemanalSummaryDTO();
    private List<PropuestaMpsSemanalItemDTO> items = new ArrayList<>();
    private PropuestaMpsSemanalCalendarDTO calendar = new PropuestaMpsSemanalCalendarDTO();

    public static MpsSemanalDraftDTO fromEntityAndSnapshot(
            MasterProductionScheduleSemanal entity,
            PropuestaMpsSemanalResponseDTO snapshot
    ) {
        MpsSemanalDraftDTO dto = new MpsSemanalDraftDTO();
        dto.setMpsId(entity.getMpsId());
        dto.setEstado(entity.getEstado());
        dto.setFechaCreacion(entity.getFechaCreacion());
        dto.setFechaActualizacion(entity.getFechaActualizacion());
        dto.setWeekStartDate(snapshot.getWeekStartDate());
        dto.setWeekEndDate(snapshot.getWeekEndDate());
        dto.setSummary(snapshot.getSummary());
        dto.setItems(snapshot.getItems());
        dto.setCalendar(snapshot.getCalendar());
        return dto;
    }
}
