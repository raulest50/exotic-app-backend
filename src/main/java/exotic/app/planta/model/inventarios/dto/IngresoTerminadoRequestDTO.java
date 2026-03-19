package exotic.app.planta.model.inventarios.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * DTO con los datos digitados por el usuario para registrar el ingreso de un
 * producto terminado al almacén general y cerrar la OrdenProduccion correspondiente.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class IngresoTerminadoRequestDTO {

    /** Username del usuario que realiza el ingreso. */
    private String username;

    /** ID de la OrdenProduccion que se cierra con este ingreso. */
    private int ordenProduccionId;

    /** Cantidad real de unidades de producto terminado que ingresa al almacén. Debe ser >= 1. */
    private int cantidadIngresada;

    /** Fecha de vencimiento del lote que se registra. */
    private LocalDate fechaVencimiento;

    /** Observaciones opcionales para la transacción. */
    private String observaciones;
}
