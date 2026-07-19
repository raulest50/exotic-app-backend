package exotic.app.planta.model.bi.dto;

import lombok.Builder;

import java.util.List;

@Builder
public record BusquedaStockMaterialDTO(
        String buscar,
        List<ResultadoStockMaterialDTO> resultados
) {
    @Builder
    public record ResultadoStockMaterialDTO(
            String productoId,
            String nombre,
            String unidadMedida,
            double stockGeneral,
            double costoUnitario,
            boolean costoDisponible,
            double valorEstimado
    ) {
    }
}
