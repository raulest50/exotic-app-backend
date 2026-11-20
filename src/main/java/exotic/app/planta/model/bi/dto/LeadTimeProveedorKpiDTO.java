package exotic.app.planta.model.bi.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
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
    private Double leadTimeCalculado;
    private Integer observaciones;
    private Integer ordenesConsideradas;
    private LocalDate fechaCorte;
    private Integer ventanaDias;
    private LocalDateTime calculadoEn;
    private String motivoEstado;
    private LocalDateTime ultimaEvaluacionEn;
    private LocalDate ultimaFechaCorteEvaluada;

    /**
     * @deprecated Usar {@link #getLeadTimeCalculado()}. Se conserva para clientes
     * que todavia leen el nombre JSON anterior.
     */
    @Deprecated
    @JsonProperty("leadTimeMedianoDias")
    public Double getLeadTimeMedianoDias() {
        return leadTimeCalculado;
    }

    /**
     * @deprecated Usar {@link #setLeadTimeCalculado(Double)}. Acepta el nombre
     * JSON anterior como alias del mismo valor calculado.
     */
    @Deprecated
    @JsonProperty("leadTimeMedianoDias")
    public void setLeadTimeMedianoDias(Double leadTimeMedianoDias) {
        this.leadTimeCalculado = leadTimeMedianoDias;
    }
}
