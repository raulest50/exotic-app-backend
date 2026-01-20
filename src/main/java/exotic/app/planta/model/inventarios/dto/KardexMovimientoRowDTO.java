package exotic.app.planta.model.inventarios.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class KardexMovimientoRowDTO {
    private int movimientoId;
    private String productoId;
    private String productoNombre;
    private String tipoUnidades;

    /** Cantidad firmada: positivo=entrada, negativo=salida */
    private double cantidad;

    /** Convenience fields (opcionales) */
    private double entrada;
    private double salida;

    private String batchNumber;
    private LocalDate productionDate;
    private LocalDate expirationDate;

    private String tipoMovimiento;
    private String almacen;
    private LocalDateTime fechaMovimiento;

    /** Saldo acumulado después de aplicar este movimiento */
    private double saldo;
}

