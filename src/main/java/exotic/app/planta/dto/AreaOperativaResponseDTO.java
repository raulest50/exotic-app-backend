package exotic.app.planta.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AreaOperativaResponseDTO {
    private Integer areaId;
    private String nombre;
    private String descripcion;
    private ResponsableAreaDTO responsableArea;

    @Builder.Default
    private List<CategoriaHabilitadaDTO> categoriasHabilitadas = new ArrayList<>();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ResponsableAreaDTO {
        private Long id;
        private Long cedula;
        private String username;
        private String nombreCompleto;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CategoriaHabilitadaDTO {
        private Integer categoriaId;
        private String categoriaNombre;
    }
}
