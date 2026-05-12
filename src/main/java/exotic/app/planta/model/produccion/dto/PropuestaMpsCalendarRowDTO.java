package exotic.app.planta.model.produccion.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
public class PropuestaMpsCalendarRowDTO {
    private String rowKey;
    private Integer categoriaId;
    private String categoriaNombre;
    private Integer poolCapacidadId;
    private String poolCapacidadNombre;
    private int capacidadDiaria;
    private List<PropuestaMpsCalendarCellDTO> days = new ArrayList<>();
    private double totalAsignadoSemana;
    private int capacidadTeoricaSemana;
    private String estadoSemana;
}
