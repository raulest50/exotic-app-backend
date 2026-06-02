package exotic.app.planta.model.bi.dto;

import exotic.app.planta.model.organizacion.personal.IntegrantePersonal;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
public class HorasExtraBiResumenDTO {
    private LocalDate fechaDesde;
    private LocalDate fechaHasta;
    private Long integranteId;
    private IntegrantePersonal.Departamento departamento;
    private String cargo;
    private long totalRegistros;
    private int totalMinutos;
    private double totalHoras;
    private List<HorasExtraBiEstadoDTO> estados;
}
