package exotic.app.planta.model.produccion.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
public class MpsSemanalDiaDTO {
    private Long id;
    private LocalDate fecha;
    private int dayIndex;
    private int displayOrder;
    private List<MpsSemanalItemDTO> items = new ArrayList<>();
}
