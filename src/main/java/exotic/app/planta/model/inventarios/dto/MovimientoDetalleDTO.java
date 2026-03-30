package exotic.app.planta.model.inventarios.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * DTO para representar los detalles de un movimiento de almacén.
 * Incluye información del producto, lote y movimiento.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MovimientoDetalleDTO {
    private int movimientoId;
    private String productoId;
    private String productoNombre;
    private String tipoUnidades;
    private Integer areaOperativaId;
    private String areaOperativaNombre;
    private double cantidad;
    private String batchNumber; // del lote
    private LocalDate productionDate; // del lote
    private LocalDate expirationDate; // del lote
    private String tipoMovimiento;
    private String almacen;
    private LocalDateTime fechaMovimiento;
}

