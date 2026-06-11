package exotic.app.planta.model.bi.dto;

import exotic.app.planta.model.compras.proveedor.metricas.EstadoLeadTimeProveedorKPI;
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
public class LeadTimeProveedorKpiDTO {

    private String proveedorId;
    private String proveedorNombre;
    private EstadoLeadTimeProveedorKPI estado;
    private Double leadTimeMedianoDias;
    private Integer observaciones;
    private Integer ordenesConsideradas;
    private LocalDate fechaCorte;
    private Integer ventanaDias;
    private LocalDateTime calculadoEn;
    private String motivoEstado;
    private LocalDateTime ultimaEvaluacionEn;
    private LocalDate ultimaFechaCorteEvaluada;
}
