package exotic.app.planta.model.inventarios.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * DTO de respuesta para el registro masivo de ingresos de producto terminado.
 * Contiene un resumen del proceso y los resultados individuales de cada ingreso.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class IngresoMasivoResponseDTO {

    /** Indica si todos los ingresos fueron exitosos. */
    private boolean success;

    /** Total de ingresos que se intentaron procesar. */
    private int totalProcesados;

    /** Cantidad de ingresos procesados exitosamente. */
    private int exitosos;

    /** Cantidad de ingresos que fallaron. */
    private int fallidos;

    /** Lista con el resultado individual de cada ingreso. */
    private List<IngresoResultadoDTO> resultados;

    /**
     * DTO interno que representa el resultado de un ingreso individual.
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IngresoResultadoDTO {

        /** ID de la OrdenProduccion procesada. */
        private int ordenProduccionId;

        /** Lote asignado de la OP (para identificación visual). */
        private String loteAsignado;

        /** Indica si el ingreso fue exitoso. */
        private boolean exito;

        /** Mensaje de error si falló; null si fue exitoso. */
        private String mensaje;

        /** ID de la transacción creada; null si falló. */
        private Integer transaccionId;
    }
}
