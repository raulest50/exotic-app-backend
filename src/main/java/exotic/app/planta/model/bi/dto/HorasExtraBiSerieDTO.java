package exotic.app.planta.model.bi.dto;

import exotic.app.planta.model.organizacion.personal.IntegrantePersonal;
import exotic.app.planta.service.bi.HorasExtraBiGranularidad;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
public class HorasExtraBiSerieDTO {
    private LocalDate fechaDesde;
    private LocalDate fechaHasta;
    private HorasExtraBiGranularidad granularidad;
    private Long integranteId;
    private IntegrantePersonal.Departamento departamento;
    private String cargo;
    private List<HorasExtraBiSeriePuntoDTO> puntos;
}
