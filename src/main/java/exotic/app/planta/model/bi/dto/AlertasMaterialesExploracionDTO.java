package exotic.app.planta.model.bi.dto;

import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

@Builder
public record AlertasMaterialesExploracionDTO(
        LocalDateTime fechaHoraCorteStock,
        ResumenAlertasDTO resumen,
        List<InformeInventarioDTO.AlertaStockDTO> prioritarios,
        FacetasAlertasDTO facetas,
        PaginaInformeInventarioDTO<InformeInventarioDTO.AlertaStockDTO> pagina
) {
    @Builder
    public record ResumenAlertasDTO(
            int total,
            int negativas,
            int agotadas,
            int bajoUmbral,
            int sinCosto
    ) {
    }

    @Builder
    public record FacetasAlertasDTO(
            List<String> gruposDisponibles,
            List<String> unidadesDisponibles
    ) {
    }
}
