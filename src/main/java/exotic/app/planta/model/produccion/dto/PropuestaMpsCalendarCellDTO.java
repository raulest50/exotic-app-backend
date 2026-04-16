package exotic.app.planta.model.produccion.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
public class PropuestaMpsCalendarCellDTO {
    private LocalDate date;
    private int dayIndex;
    private double totalAsignado;
    private int capacidadDiaria;
    private String estado;
    private List<PropuestaMpsCalendarBlockDTO> blocks = new ArrayList<>();
}
