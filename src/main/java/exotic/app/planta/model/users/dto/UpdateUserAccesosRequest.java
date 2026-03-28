package exotic.app.planta.model.users.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateUserAccesosRequest {
    private List<ModuloAccesoAssignmentDTO> accesos;
}
