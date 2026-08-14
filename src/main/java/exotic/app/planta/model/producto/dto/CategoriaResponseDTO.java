package exotic.app.planta.model.producto.dto;

import exotic.app.planta.model.producto.Categoria;
import exotic.app.planta.model.producto.UnidadTiempoVencimiento;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CategoriaResponseDTO {
    private int categoriaId;
    private String categoriaNombre;
    private String categoriaDescripcion;
    private Integer loteSize;
    private Integer tiempoDiasFabricacion;
    private Integer capacidadProductivaDiaria;
    private Integer vidaUtilCantidad;
    private UnidadTiempoVencimiento vidaUtilUnidad;

    public static CategoriaResponseDTO fromEntity(Categoria categoria) {
        return new CategoriaResponseDTO(
                categoria.getCategoriaId(),
                categoria.getCategoriaNombre(),
                categoria.getCategoriaDescripcion(),
                categoria.getLoteSize(),
                categoria.getTiempoDiasFabricacion(),
                categoria.getCapacidadProductivaDiaria(),
                categoria.getVidaUtilCantidad(),
                categoria.getVidaUtilUnidad()
        );
    }
}
