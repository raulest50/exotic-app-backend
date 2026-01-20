package exotic.app.planta.model.inventarios.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class KardexMovimientosRequestDTO {
    private String productoId;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer page;
    private Integer size;
}

