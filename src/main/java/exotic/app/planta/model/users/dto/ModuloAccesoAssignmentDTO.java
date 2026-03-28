package exotic.app.planta.model.users.dto;

import exotic.app.planta.model.users.ModuloSistema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ModuloAccesoAssignmentDTO {
    private ModuloSistema modulo;
    private List<TabAccesoAssignmentDTO> tabs;
}
