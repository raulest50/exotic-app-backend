package exotic.app.planta.model.organigrama.dto;

import java.time.LocalDateTime;
import java.util.List;

public record OrganigramaSnapshotResponse(
        long revision,
        LocalDateTime actualizadoEn,
        String actualizadoPor,
        List<CargoOrganigramaResponse> cargos,
        List<RelacionOrganigramaResponse> relaciones
) {
}
