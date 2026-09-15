package exotic.app.planta.model.bi.dto;

import java.util.List;

public record PaginaDesviacionesProduccionDTO(
        List<DesviacionDTO> items,
        CountsDTO counts,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {
    public enum TipoDesviacion {
        SIN_PRODUCCION,
        DEFICIT,
        NO_PLANEADA,
        SOBREPRODUCCION
    }

    public record DesviacionDTO(
            InformeGlobalProduccionDTO.DetalleReferenciaDTO reference,
            TipoDesviacion kind,
            double difference,
            Double variationPct
    ) {
    }

    public record CountsDTO(
            int sinProduccion,
            int deficit,
            int noPlaneada,
            int sobreproduccion
    ) {
    }
}
