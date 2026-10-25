package exotic.app.planta.model.producto.dto.manufacturing;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProcesoProduccionCompletoDTO {
    private Integer procesoCompletoId;
    private Double rendimientoTeorico;
    private List<ProcesoFabricacionNodoDTO> nodes = new ArrayList<>();
    private List<ProcesoFabricacionEdgeDTO> edges = new ArrayList<>();
    /** Salida de la receta en tipoUnidades del producto que contiene este DTO. */
    @com.fasterxml.jackson.annotation.JsonFormat(shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
    private java.math.BigDecimal cantidadSalidaReceta;

    public ProcesoProduccionCompletoDTO(Integer id, Double rendimiento,
            List<ProcesoFabricacionNodoDTO> nodes, List<ProcesoFabricacionEdgeDTO> edges) {
        this(id, rendimiento, nodes, edges, null);
    }
}
