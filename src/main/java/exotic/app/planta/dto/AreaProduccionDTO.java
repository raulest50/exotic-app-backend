package exotic.app.planta.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO para transferir datos de Area de Produccion.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AreaProduccionDTO {

    private Integer areaId;

    @NotBlank(message = "El nombre del area no puede estar vacio")
    private String nombre;

    private String descripcion;

    @NotNull(message = "El responsable del area no puede ser nulo")
    private Long responsableId;

    private List<Integer> categoriaIds;
}
