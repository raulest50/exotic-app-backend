package exotic.app.planta.model.produccion.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Datos informativos de produccion por etapa; no representan movimientos de inventario. */
public final class ReporteProduccionEtapaDTOs {

    private ReporteProduccionEtapaDTOs() {
    }

    /** Configuracion y productos congelados al emitir la orden. */
    public record Configuracion(boolean requerido, List<Producto> productos) {
        public Configuracion {
            productos = productos == null ? List.of() : List.copyOf(productos);
        }
    }

    public record Producto(
            String productoId,
            String productoNombre,
            String unidadMedida,
            Long manufacturingVersionId,
            List<String> nodosOrigen,
            boolean productoFinalOrden
    ) {
        public Producto {
            nodosOrigen = nodosOrigen == null ? List.of() : List.copyOf(nodosOrigen);
        }
    }

    public record Solicitud(String productoId, BigDecimal cantidad, String motivoCero) {
    }

    /** Evidencia congelada junto con el evento de completado. */
    public record Reporte(
            String productoId,
            String productoNombre,
            String unidadMedida,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal cantidad,
            String motivoCero
    ) {
    }

    public record Registro(Long eventoId, LocalDateTime fechaEvento, String usuario,
                           boolean vigente, List<Reporte> reportesProduccion) {
    }
}
