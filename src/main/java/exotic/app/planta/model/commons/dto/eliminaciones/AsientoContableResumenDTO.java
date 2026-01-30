package exotic.app.planta.model.commons.dto.eliminaciones;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AsientoContableResumenDTO {
    private Long id;
    private LocalDateTime fecha;
    private String descripcion;
    private String modulo;
    private String documentoOrigen;
    private String estado;
}
