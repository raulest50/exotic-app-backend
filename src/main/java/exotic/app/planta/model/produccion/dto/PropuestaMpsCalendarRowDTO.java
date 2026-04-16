package exotic.app.planta.model.produccion.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
public class PropuestaMpsCalendarRowDTO {
    private Integer categoriaId;
    private String categoriaNombre;
    private int capacidadDiaria;
    private List<PropuestaMpsCalendarCellDTO> days = new ArrayList<>();
    private double totalAsignadoSemana;
    private int capacidadTeoricaSemana;
    private String estadoSemana;
}
