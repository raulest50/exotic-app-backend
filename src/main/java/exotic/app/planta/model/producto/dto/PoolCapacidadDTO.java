package exotic.app.planta.model.producto.dto;

import exotic.app.planta.model.producto.PoolCapacidad;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PoolCapacidadDTO {
    private Integer id;
    private String nombre;
    private Integer capacidadDiaria;
    private String descripcion;
    private boolean activo;

    public static PoolCapacidadDTO fromEntity(PoolCapacidad entity) {
        return new PoolCapacidadDTO(
                entity.getId(),
                entity.getNombre(),
                entity.getCapacidadDiaria(),
                entity.getDescripcion(),
                entity.isActivo()
        );
    }
}
