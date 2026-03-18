package exotic.app.planta.model.produccion.dto;

import java.time.LocalDateTime;
import java.util.List;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class OrdenProduccionBatchDTO {
    private String productoId;
    private String observaciones;
    private double cantidadProducir = 1.0;

    private LocalDateTime fechaLanzamiento;
    private LocalDateTime fechaFinalPlanificada;

    private String numeroPedidoComercial;
    private String areaOperativa;
    private String departamentoOperativo;

    private Long vendedorResponsableId;

    private List<String> loteBatchNumbers;
}
