package exotic.app.planta.model.producto.dto.procdesigner;

import exotic.app.planta.model.producto.Producto;
import exotic.app.planta.model.producto.SemiTerminado;
import exotic.app.planta.model.producto.Terminado;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.stream.Collectors;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TargetDTO {
    private String productoId;
    private String nombre;
    private String observaciones;
    private double costo;
    private String fechaCreacion; // sent as ISO string
    private String tipoUnidades;
    private double cantidadUnidad;
    private List<InsumoDTO> insumos;
    private String tipo_producto;

    // Helper to convert a Producto (Terminado or SemiTerminado) into TargetDTO
    public static TargetDTO fromProducto(Producto producto) {
        TargetDTO dto = new TargetDTO();
        dto.setProductoId(producto.getProductoId());
        dto.setNombre(producto.getNombre());
        dto.setObservaciones(producto.getObservaciones());
        dto.setCosto(producto.getCosto().doubleValue());
        // Format the date as needed. Here we simply use toString() (or use a formatter)
        dto.setFechaCreacion(producto.getFechaCreacion().toString());
        dto.setTipoUnidades(producto.getTipoUnidades());
        dto.setCantidadUnidad(producto.getCantidadUnidad());
        dto.setTipo_producto(producto.getTipo_producto());

        if (producto instanceof Terminado) {
            dto.setInsumos(((Terminado) producto).getInsumos()
                    .stream()
                    .map(InsumoDTO::fromInsumo)
                    .collect(Collectors.toList()));
        } else if (producto instanceof SemiTerminado) {
            dto.setInsumos(((SemiTerminado) producto).getInsumos()
                    .stream()
                    .map(InsumoDTO::fromInsumo)
                    .collect(Collectors.toList()));
        }
        return dto;
    }
}
