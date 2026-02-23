package exotic.app.planta.model.inventarios.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * DTO para filtrar transacciones de almacen por tipo de entidad causante y rango de fechas.
 * Usado en el historial general de transacciones de almacen.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FiltroHistorialTransaccionAlmacenDTO {

    /**
     * Tipo de entidad causante (requerido).
     * Valores posibles: OCM, OP, OTA, OAA, OD, CM
     */
    private String tipoEntidadCausante;

    /**
     * Fecha inicial del rango (opcional).
     */
    private LocalDate fechaInicio;

    /**
     * Fecha final del rango (opcional).
     */
    private LocalDate fechaFin;

    private int page = 0;

    private int size = 10;
}
