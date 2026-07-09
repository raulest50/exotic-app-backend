package exotic.app.planta.model.inventarios.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ReporteHyLRequestDTO {

    private LocalDate fechaReporte;
    private boolean costosEnCero;
    private List<IngresoHyLItemDTO> ingresos;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IngresoHyLItemDTO {
        private String productoId;
        private String productoNombre;
        private double cantidadProducida;
    }
}
