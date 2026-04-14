// In lacosmetics.planta.lacmanufacture.model.produccion.dto.OrdenProduccionDTO.java

package exotic.app.planta.model.produccion.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class OrdenProduccionDTO {
    private int ordenId;
    private String productoId;
    private String productoNombre;  // producto.nombre

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String productoTipo;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Integer productoCategoriaId;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String productoCategoriaNombre;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String productoUnidad;

    private LocalDateTime fechaInicio;
    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaLanzamiento; // fecha planeada de autorizacion
    private LocalDateTime fechaFinalPlanificada; // fecha objetivo para terminar
    private int estadoOrden;        // 0: abierta, 1: en curso, 2: terminada, -1: cancelada
    private String observaciones;
    private double cantidadProducir = 1.0; // Cantidad planificada a producir (minimo 1.0)
    private String numeroPedidoComercial; // pedido comercial origen
    private String areaOperativa; // area operativa que ejecuta
    private String ultimaAreaDispensada;
    private String departamentoOperativo; // departamento responsable de coordinar
    private String loteAsignado; // numero de lote asignado a la orden
    private Long responsableId;
}
