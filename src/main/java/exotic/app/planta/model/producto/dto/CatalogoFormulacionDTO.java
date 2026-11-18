package exotic.app.planta.model.producto.dto;

import java.math.BigDecimal;
import java.util.List;

/** Proyecciones de consulta: no contienen costos, existencias ni documentos operativos. */
public final class CatalogoFormulacionDTO {
    private CatalogoFormulacionDTO() { }

    public record ProductoResumen(
            String productoId, String nombre, String tipoProducto, String tipoUnidades,
            String fotoUrl, String categoriaNombre, Integer status) { }

    public record Formulacion(
            ProductoResumen producto, List<InsumoNodo> insumos, List<String> advertencias,
            boolean recetaHistorica, BigDecimal cantidadSalidaReceta) { }

    /** cantidadPorUnidad se refiere a una unidad de inventario del padre inmediato. */
    public record InsumoNodo(
            Integer insumoId, String productoId, String nombre, String tipoProducto,
            String tipoUnidades, BigDecimal cantidadPorUnidad, boolean requiereOrdenFabricacion,
            List<InsumoNodo> subInsumos, String observacion) { }
}
