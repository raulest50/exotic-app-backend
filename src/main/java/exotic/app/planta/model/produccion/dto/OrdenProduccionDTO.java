// In lacosmetics.planta.lacmanufacture.model.produccion.dto.OrdenProduccionDTO.java

package exotic.app.planta.model.produccion.dto;

import java.time.LocalDateTime;
import java.util.List;

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
    private LocalDateTime fechaInicio;
    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaLanzamiento; // fecha planeada de autorización
    private LocalDateTime fechaFinalPlanificada; // fecha objetivo para terminar
    private int estadoOrden;        // 0: abierta, 1: en curso, 2: terminada, -1: cancelada
    private String observaciones;
    private double cantidadProducir = 1.0; // Cantidad planificada a producir (mínimo 1.0)
    private String numeroPedidoComercial; // pedido comercial origen
    private String areaOperativa; // área operativa que ejecuta
    private String departamentoOperativo; // departamento responsable de coordinar
    private String loteAsignado; // número de lote asignado a la orden
    private Long responsableId;
    private List<OrdenSeguimientoDTO> ordenesSeguimiento;
}
