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
public class PuntoReordenEstimadoDTO {

    private String materialId;
    private String materialNombre;
    private LocalDate fechaCorte;
    private Integer ventanaDias;
    private String metodoUsado;
    private String reason;
    private Double puntoReordenEstimado;
    private Double demandaDiariaPromedio;
    private Double desviacionEstandarDemandaDiaria;
    private Double leadTimeRepresentativoDias;
    private Double leadTimePromedioDias;
    private Double desviacionEstandarLeadTimeDias;
    private Double demandaTotalVentana;
    private Integer diasVentana;
    private Integer observacionesLeadTime;
    private Integer proveedoresObservados;
    private Integer confianzaGlobal;

}
