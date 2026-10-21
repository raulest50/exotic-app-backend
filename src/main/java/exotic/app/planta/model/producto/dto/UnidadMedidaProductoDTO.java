package exotic.app.planta.model.producto.dto;

import exotic.app.planta.model.producto.UnidadMedidaProducto;

import java.math.BigDecimal;

public record UnidadMedidaProductoDTO(
        String codigo,
        String simbolo,
        UnidadMedidaProducto.Magnitud magnitud,
        BigDecimal factorAReferencia
) {
    public static UnidadMedidaProductoDTO from(UnidadMedidaProducto unidad) {
        return new UnidadMedidaProductoDTO(
                unidad.getCodigo(),
                unidad.getSimbolo(),
                unidad.getMagnitud(),
                unidad.getFactorAReferencia()
        );
    }
}
