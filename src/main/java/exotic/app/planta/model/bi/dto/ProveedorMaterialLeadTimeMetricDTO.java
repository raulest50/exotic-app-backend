package exotic.app.planta.model.bi.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProveedorMaterialLeadTimeMetricDTO {

    private String proveedorId;
    private String proveedorNombre;
    private String materialId;
    private String materialNombre;
    private LocalDate fechaCorte;
    private Integer ventanaDias;
    private Double leadTimeMedianoDias;
    private Integer observaciones;
    private Integer ordenesConsideradas;
    private boolean calculable;
    private String reason;
    private LocalDateTime calculadoEn;
    private Integer observacionesConFechaEnvioProveedor;
    private Integer observacionesConFallbackFechaEmision;
}
