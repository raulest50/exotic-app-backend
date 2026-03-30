package exotic.app.planta.model.inventarios.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

/**
 * DTO para la solicitud de registro masivo de ingresos de producto terminado.
 * Contiene el username del usuario que realiza el ingreso y una lista de items a procesar.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class IngresoMasivoRequestDTO {

    /** Username del usuario que realiza los ingresos. */
    private String username;

    /** Lista de ingresos a procesar. */
    private List<IngresoItemDTO> ingresos;

    /**
     * DTO interno que representa un item individual de ingreso masivo.
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IngresoItemDTO {

        /** ID de la OrdenProduccion que se cierra con este ingreso. */
        private int ordenProduccionId;

        /** Cantidad real de unidades de producto terminado que ingresa al almacén. */
        private int cantidadIngresada;

        /** Fecha de vencimiento del lote que se registra. */
        private LocalDate fechaVencimiento;
    }
}
