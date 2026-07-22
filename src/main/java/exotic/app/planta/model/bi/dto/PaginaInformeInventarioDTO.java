package exotic.app.planta.model.bi.dto;

import java.util.List;

public record PaginaInformeInventarioDTO<T>(
        List<T> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {
}
