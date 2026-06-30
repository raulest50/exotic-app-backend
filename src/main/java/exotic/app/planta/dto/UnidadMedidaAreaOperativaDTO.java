package exotic.app.planta.dto;

import exotic.app.planta.model.organizacion.UnidadMedidaAreaOperativa;
import exotic.app.planta.model.organizacion.UnidadRelacionAreaOperativa;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UnidadMedidaAreaOperativaDTO {
    private Long id;
    private Integer areaId;
    private String nombre;
    private BigDecimal relacionEstandar;
    private UnidadRelacionAreaOperativa unidadRelacion;

    public static UnidadMedidaAreaOperativaDTO fromEntity(UnidadMedidaAreaOperativa entity) {
        return UnidadMedidaAreaOperativaDTO.builder()
                .id(entity.getId())
                .areaId(entity.getAreaOperativa() != null ? entity.getAreaOperativa().getAreaId() : null)
                .nombre(entity.getNombre())
                .relacionEstandar(entity.getRelacionEstandar())
                .unidadRelacion(entity.getUnidadRelacion())
                .build();
    }
}
