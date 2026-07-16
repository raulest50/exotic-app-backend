package exotic.app.planta.model.produccion.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReporteProduccionPendientesResumenDTO {
    private LocalDate fechaHoy;
    private long pendientesHoy;
    private long pendientesVencidos;
    private List<FechaPendienteDTO> fechas = new ArrayList<>();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FechaPendienteDTO {
        private LocalDate fechaProduccion;
        private long cantidadReportes;
        private BigDecimal totalUnidades;
        private boolean vencida;
    }
}
