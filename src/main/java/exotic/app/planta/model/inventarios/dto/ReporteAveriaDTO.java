package exotic.app.planta.model.inventarios.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ReporteAveriaDTO {

    private int ordenProduccionId;
    private int areaProduccionId;
    private String observaciones;
    private String username;
    private List<ReporteAveriaItemDTO> items;
}
