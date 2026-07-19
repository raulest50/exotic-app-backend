package exotic.app.planta.model.producto.dto.procdesigner;

import exotic.app.planta.model.producto.manufacturing.receta.Insumo;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class InsumoDTO {
    private String cantidadRequerida;
    private ProductoDTO producto;

    // Maps a given Insumo into an InsumoDTO.
    public static InsumoDTO fromInsumo(Insumo insumo) {
        InsumoDTO dto = new InsumoDTO();
        dto.setCantidadRequerida(String.valueOf(insumo.getCantidadRequerida()));

        ProductoDTO prodDto = new ProductoDTO();
        prodDto.setProductoId(insumo.getProducto().getProductoId());
        prodDto.setNombre(insumo.getProducto().getNombre());
        prodDto.setTipo_producto(insumo.getProducto().getTipo_producto());
        prodDto.setObservaciones(insumo.getProducto().getObservaciones());
        prodDto.setCosto(insumo.getProducto().getCosto().doubleValue());
        prodDto.setTipoUnidades(insumo.getProducto().getTipoUnidades());
        prodDto.setCantidadUnidad(insumo.getProducto().getCantidadUnidad());
        prodDto.setFechaCreacion(insumo.getProducto().getFechaCreacion().toString());

        dto.setProducto(prodDto);
        return dto;
    }
}
