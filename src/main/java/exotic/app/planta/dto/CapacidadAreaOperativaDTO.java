package exotic.app.planta.dto;

import exotic.app.planta.model.organizacion.CapacidadAreaOperativa;
import exotic.app.planta.model.organizacion.PeriodoCapacidadAreaOperativa;
import exotic.app.planta.model.organizacion.TipoCapacidadAreaOperativa;
import exotic.app.planta.model.organizacion.UnidadRelacionAreaOperativa;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CapacidadAreaOperativaDTO {
    private Long id;
    private Integer areaId;
    private Long unidadMedidaId;
    private String unidadNombre;
    private BigDecimal unidadRelacionEstandar;
    private UnidadRelacionAreaOperativa unidadRelacion;
    private TipoCapacidadAreaOperativa tipoCapacidad;
    private BigDecimal cantidad;
    private PeriodoCapacidadAreaOperativa periodo;
    private BigDecimal eficiencia;
    private LocalDate vigenteDesde;
    private LocalDate vigenteHasta;
    private String descripcion;
    private boolean activo;

    public static CapacidadAreaOperativaDTO fromEntity(CapacidadAreaOperativa entity) {
        return CapacidadAreaOperativaDTO.builder()
                .id(entity.getId())
                .areaId(entity.getAreaOperativa() != null ? entity.getAreaOperativa().getAreaId() : null)
                .unidadMedidaId(entity.getUnidadMedida() != null ? entity.getUnidadMedida().getId() : null)
                .unidadNombre(entity.getUnidadMedida() != null ? entity.getUnidadMedida().getNombre() : null)
                .unidadRelacionEstandar(entity.getUnidadMedida() != null ? entity.getUnidadMedida().getRelacionEstandar() : null)
                .unidadRelacion(entity.getUnidadMedida() != null ? entity.getUnidadMedida().getUnidadRelacion() : null)
                .tipoCapacidad(entity.getTipoCapacidad())
                .cantidad(entity.getCantidad())
                .periodo(entity.getPeriodo())
                .eficiencia(entity.getEficiencia())
                .vigenteDesde(entity.getVigenteDesde())
                .vigenteHasta(entity.getVigenteHasta())
                .descripcion(entity.getDescripcion())
                .activo(entity.isActivo())
                .build();
    }
}
