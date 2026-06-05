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
public class MpsSemanalListItemDTO {
    private Integer mpsId;
    private EstadoMpsSemanal estado;
    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaActualizacion;
    private LocalDateTime fechaAprobacion;
    private String aprobadoPorUsername;
    private LocalDateTime fechaGeneracionOdps;
    private String generadoPorUsername;
    private Long semanaMpsId;
    private String semanaMpsCodigo;
    private Integer anioSemana;
    private Integer numeroSemana;
    private String standard;
    private LocalDate weekStartDate;
    private LocalDate weekEndDate;
    private PropuestaMpsSemanalSummaryDTO summary = new PropuestaMpsSemanalSummaryDTO();
    private int totalOrdenesEsperadas;
    private long totalOrdenesGeneradas;
    private boolean odpsGeneradasCompletas;
    private int totalBloquesNoProgramados;
    private int totalLotesNoProgramados;
    private double totalUnidadesNoProgramadas;

    public static MpsSemanalListItemDTO fromEntityAndSnapshot(
            MasterProductionScheduleSemanal entity,
            PropuestaMpsSemanalResponseDTO snapshot,
            int totalOrdenesEsperadas,
            long totalOrdenesGeneradas,
            int totalBloquesNoProgramados,
            int totalLotesNoProgramados,
            double totalUnidadesNoProgramadas
    ) {
        MpsSemanalListItemDTO dto = new MpsSemanalListItemDTO();
        dto.setMpsId(entity.getMpsId());
        dto.setEstado(entity.getEstado());
        dto.setFechaCreacion(entity.getFechaCreacion());
        dto.setFechaActualizacion(entity.getFechaActualizacion());
        dto.setFechaAprobacion(entity.getFechaAprobacion());
        dto.setAprobadoPorUsername(entity.getAprobadoPorUsername());
        dto.setFechaGeneracionOdps(entity.getFechaGeneracionOdps());
        dto.setGeneradoPorUsername(entity.getGeneradoPorUsername());
        SemanaMPS semanaMps = entity.getSemanaMps();
        if (semanaMps != null) {
            dto.setSemanaMpsId(semanaMps.getId());
            dto.setSemanaMpsCodigo(semanaMps.getCodigo());
            dto.setAnioSemana(semanaMps.getAnioSemana());
            dto.setNumeroSemana(semanaMps.getNumeroSemana());
            dto.setStandard(semanaMps.getStandard());
        }
        dto.setWeekStartDate(snapshot.getWeekStartDate());
        dto.setWeekEndDate(snapshot.getWeekEndDate());
        dto.setSummary(snapshot.getSummary());
        dto.setTotalOrdenesEsperadas(totalOrdenesEsperadas);
        dto.setTotalOrdenesGeneradas(totalOrdenesGeneradas);
        dto.setOdpsGeneradasCompletas(totalOrdenesEsperadas == totalOrdenesGeneradas);
        dto.setTotalBloquesNoProgramados(totalBloquesNoProgramados);
        dto.setTotalLotesNoProgramados(totalLotesNoProgramados);
        dto.setTotalUnidadesNoProgramadas(totalUnidadesNoProgramadas);
        return dto;
    }
}
