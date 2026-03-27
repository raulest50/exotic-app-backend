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
public class AssignModuloAccesoRequest {
    private ModuloSistema modulo;
    private List<TabAccesoAssignmentDTO> tabs;
    /** Si es true, el conjunto de tabs del módulo queda exactamente como en {@code tabs}; tabs no listados se eliminan. Si {@code tabs} está vacío, se elimina el acceso al módulo. */
    private Boolean replaceTabs;
}
