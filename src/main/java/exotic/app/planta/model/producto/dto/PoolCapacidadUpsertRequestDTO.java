package exotic.app.planta.model.producto.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PoolCapacidadUpsertRequestDTO {
    private String nombre;
    private Integer capacidadDiaria;
    private String descripcion;
    private Boolean activo;
}
