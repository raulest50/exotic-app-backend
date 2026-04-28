package exotic.app.planta.model.bi.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LeadTimeStatsDTO {

    private boolean calculable;
    private String reason;
    private Double representativeLeadTimeDays;
    private Double averageLeadTimeDays;
    private Double medianLeadTimeDays;
    private Double minLeadTimeDays;
    private Double maxLeadTimeDays;
    private Double standardDeviationLeadTimeDays;
    private Integer validObservations;
    private Integer totalOrdersConsidered;
    private Integer confidenceScore;
    private LocalDateTime lastReceiptObservedAt;

}
