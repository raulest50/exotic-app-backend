package exotic.app.planta.model.produccion.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AreaOperativaMonitoreoDTO {
    private Integer areaId;
    private String nombre;
    private String descripcion;
    private ResponsableAreaResumenDTO responsableArea;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ResponsableAreaResumenDTO {
        private Long id;
        private String username;
        private String nombreCompleto;
    }
}
