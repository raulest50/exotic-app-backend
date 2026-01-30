package exotic.app.planta.model.commons.dto.eliminaciones;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LoteResumenDTO {
    private Long id;
    private String batchNumber;
    private LocalDate productionDate;
    private LocalDate expirationDate;
}
