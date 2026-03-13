package exotic.app.planta.model.commons.dto.exportacion;

import java.time.LocalDateTime;
import java.util.List;

public record ExportacionTerminadosConInsumosDTO(
        int schemaVersion,
        LocalDateTime exportedAt,
        List<TerminadoExportDTO> terminados
) {

    public record TerminadoExportDTO(
            String productoId,
            String nombre,
            String observaciones,
            double costo,
            double ivaPercentual,
            String tipoUnidades,
            double cantidadUnidad,
            double stockMinimo,
            boolean inventareable,
            int status,
            CategoriaResumenDTO categoria,
            String fotoUrl,
            String prefijoLote,
            List<InsumoExportDTO> insumos
    ) {
    }

    public record CategoriaResumenDTO(
            Integer categoriaId,
            String categoriaNombre
    ) {
    }

    public record InsumoExportDTO(
            int insumoId,
            double cantidadRequerida,
            ProductoResumenDTO producto
    ) {
    }

    public record ProductoResumenDTO(
            String productoId,
            String nombre,
            String tipoProducto
    ) {
    }
}
