package exotic.app.planta.model.bi.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LeadTimeProveedorMaterialPageRowDTO {

    private String proveedorId;
    private String proveedorNombre;
    private String materialId;
    private String materialNombre;
    private Double representativeFirstReceiptLeadTimeDays;
    private Double representativeCompleteReceiptLeadTimeDays;
    private Integer firstReceiptConfidenceScore;
    private Integer completeReceiptConfidenceScore;
    private Integer firstReceiptValidObservations;
    private Integer completeReceiptValidObservations;
    private Integer totalOrdersConsidered;
    private Double adjustedLeadTimeDays;

}
