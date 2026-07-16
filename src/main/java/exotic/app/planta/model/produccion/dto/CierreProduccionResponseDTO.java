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
public class CierreProduccionResponseDTO {
    private Long cierreId;
    private LocalDate fechaProduccion;
    private LocalDateTime cerradoEn;
    private long cantidadReportes;
    private BigDecimal totalUnidades;
    private List<ItemDTO> reportes = new ArrayList<>();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ItemDTO {
        private Long reporteId;
        private int ordenProduccionId;
        private String lote;
        private BigDecimal cantidadConfirmada;
        private int transaccionAlmacenId;
    }
}
