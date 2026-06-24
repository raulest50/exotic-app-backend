package exotic.app.planta.model.producto.dto;

import exotic.app.planta.model.producto.Categoria;
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

    public static CategoriaResponseDTO fromEntity(Categoria categoria) {
        return new CategoriaResponseDTO(
                categoria.getCategoriaId(),
                categoria.getCategoriaNombre(),
                categoria.getCategoriaDescripcion(),
                categoria.getLoteSize(),
                categoria.getTiempoDiasFabricacion(),
                categoria.getCapacidadProductivaDiaria()
        );
    }
}
