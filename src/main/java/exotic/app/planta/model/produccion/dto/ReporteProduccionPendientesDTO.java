package exotic.app.planta.model.produccion.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReporteProduccionPendientesDTO {
    private LocalDate fechaProduccion;
    private List<ItemDTO> reportes = new ArrayList<>();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ItemDTO {
        private Long reporteId;
        private long version;
        private int ordenProduccionId;
        private String lote;
        private String productoId;
        private String productoNombre;
        private String tipoUnidades;
        private BigDecimal cantidadPlaneada;
        private BigDecimal cantidadReportada;
        private LocalDateTime reportadoEn;
        private String reportadoPor;
    }
}
