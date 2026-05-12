package exotic.app.planta.model.producto.dto;

import exotic.app.planta.model.producto.Categoria;
import exotic.app.planta.model.producto.PoolCapacidad;
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
    private Integer poolCapacidadId;
    private String poolCapacidadNombre;
    private Integer poolCapacidadCapacidadDiaria;

    public static CategoriaResponseDTO fromEntity(Categoria categoria) {
        PoolCapacidad poolCapacidad = categoria.getPoolCapacidad();
        return new CategoriaResponseDTO(
                categoria.getCategoriaId(),
                categoria.getCategoriaNombre(),
                categoria.getCategoriaDescripcion(),
                categoria.getLoteSize(),
                categoria.getTiempoDiasFabricacion(),
                categoria.getCapacidadProductivaDiaria(),
                poolCapacidad != null ? poolCapacidad.getId() : null,
                poolCapacidad != null ? poolCapacidad.getNombre() : null,
                poolCapacidad != null ? poolCapacidad.getCapacidadDiaria() : null
        );
    }
}
