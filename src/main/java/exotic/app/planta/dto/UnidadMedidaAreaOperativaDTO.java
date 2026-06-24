package exotic.app.planta.dto;

import exotic.app.planta.model.organizacion.DimensionUnidadAreaOperativa;
import exotic.app.planta.model.organizacion.UnidadMedidaAreaOperativa;
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
    private String codigo;
    private String nombre;
    private String descripcion;
    private DimensionUnidadAreaOperativa dimension;
    private String unidadReferencia;
    private BigDecimal factorAReferencia;
    private boolean principal;
    private boolean discreta;
    private boolean activo;

    public static UnidadMedidaAreaOperativaDTO fromEntity(UnidadMedidaAreaOperativa entity) {
        return UnidadMedidaAreaOperativaDTO.builder()
                .id(entity.getId())
                .areaId(entity.getAreaOperativa() != null ? entity.getAreaOperativa().getAreaId() : null)
                .codigo(entity.getCodigo())
                .nombre(entity.getNombre())
                .descripcion(entity.getDescripcion())
                .dimension(entity.getDimension())
                .unidadReferencia(entity.getUnidadReferencia())
                .factorAReferencia(entity.getFactorAReferencia())
                .principal(entity.isPrincipal())
                .discreta(entity.isDiscreta())
                .activo(entity.isActivo())
                .build();
    }
}
