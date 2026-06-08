package exotic.app.planta.model.produccion.dto;

import exotic.app.planta.model.produccion.MpsSemanalLotePlanificado;
import exotic.app.planta.model.produccion.OrdenProduccion;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
public class MpsSemanalOrdenProduccionListItemDTO {
    private int ordenId;
    private String productoId;
    private String productoNombre;
    private String loteAsignado;
    private double cantidadProducir;
    private LocalDateTime fechaLanzamiento;
    private LocalDateTime fechaFinalPlanificada;
    private int estadoOrden;
    private Long mpsLotePlanificadoId;
    private Long mpsItemId;
    private Integer mpsLoteOrdinal;

    public static MpsSemanalOrdenProduccionListItemDTO fromEntity(OrdenProduccion orden) {
        MpsSemanalOrdenProduccionListItemDTO dto = new MpsSemanalOrdenProduccionListItemDTO();
        dto.setOrdenId(orden.getOrdenId());
        dto.setProductoId(orden.getProducto() != null ? orden.getProducto().getProductoId() : null);
        dto.setProductoNombre(orden.getProducto() != null ? orden.getProducto().getNombre() : null);
        dto.setLoteAsignado(orden.getLoteAsignado());
        dto.setCantidadProducir(orden.getCantidadProducir());
        dto.setFechaLanzamiento(orden.getFechaLanzamiento());
        dto.setFechaFinalPlanificada(orden.getFechaFinalPlanificada());
        dto.setEstadoOrden(orden.getEstadoOrden());

        MpsSemanalLotePlanificado lotePlanificado = orden.getMpsLotePlanificado();
        if (lotePlanificado != null) {
            dto.setMpsLotePlanificadoId(lotePlanificado.getId());
            dto.setMpsLoteOrdinal(lotePlanificado.getLoteOrdinal());
            if (lotePlanificado.getMpsItem() != null) {
                dto.setMpsItemId(lotePlanificado.getMpsItem().getId());
            }
        }
        return dto;
    }
}
