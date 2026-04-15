package exotic.app.planta.model.produccion.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
public class PropuestaMpsSemanalItemDTO {
    private String productoId;
    private String productoNombre;
    private String categoriaNombre;
    private int loteSize;
    private int tiempoDiasFabricacion;
    private double stockActual;
    private double necesidadManual;
    private double necesidadNeta;
    private int lotesPropuestos;
    private double cantidadPropuesta;
    private double deltaVsNecesidad;
    private double porcentajeParticipacion;
    private double cantidadVendida;
    private double valorTotal;
    private LocalDate fechaLanzamientoSugerida;
    private LocalDate fechaFinalPlanificadaSugerida;
    private boolean desbordaSemana;
    private boolean planificable;
    private String warning;
}
