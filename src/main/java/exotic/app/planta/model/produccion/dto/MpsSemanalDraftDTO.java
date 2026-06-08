package exotic.app.planta.model.produccion.dto;

import exotic.app.planta.model.produccion.EstadoMpsSemanal;
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
    private LocalDateTime fechaAprobacion;
    private String aprobadoPorUsername;
    private LocalDateTime fechaGeneracionOdps;
    private String generadoPorUsername;
    private Long semanaMpsId;
    private String semanaMpsCodigo;
    private Integer anioSemana;
    private Integer numeroSemana;
    private String standard;
    private Integer revisionNumero;
    private LocalDate weekStartDate;
    private LocalDate weekEndDate;
    private List<MpsSemanalDiaDTO> dias = new ArrayList<>();
    private long totalItems;
    private long totalLotesPlanificados;
    private long totalOdpsGeneradas;
}
