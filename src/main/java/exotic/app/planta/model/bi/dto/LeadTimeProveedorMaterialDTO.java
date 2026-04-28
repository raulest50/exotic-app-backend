package exotic.app.planta.model.bi.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LeadTimeProveedorMaterialDTO {

    private String proveedorId;
    private String proveedorNombre;
    private String materialId;
    private String materialNombre;
    private LocalDate fechaCorte;
    private Integer ventanaDias;
    private Integer totalOrdersConsidered;
    private LeadTimeStatsDTO firstReceipt;
    private LeadTimeStatsDTO completeReceipt;

}
