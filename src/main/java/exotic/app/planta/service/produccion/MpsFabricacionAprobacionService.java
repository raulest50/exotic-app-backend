package exotic.app.planta.service.produccion;

import exotic.app.planta.model.produccion.*;
import exotic.app.planta.model.produccion.dto.MpsFabricacionDTOs.ProgramaResponse;
import exotic.app.planta.model.produccion.fabricacion.*;
import exotic.app.planta.model.users.User;
import exotic.app.planta.repo.produccion.fabricacion.MpsFabricacionObservacionRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MpsFabricacionAprobacionService {
    private final MpsFabricacionService programaService;
    private final MpsFabricacionObservacionRepo observacionRepo;
    private final Clock applicationClock;

    @Transactional(rollbackFor = Exception.class)
    public ProgramaResponse aprobar(LocalDate week, Long version, User actor) {
        MpsFabricacionSemanal programa = programaService.bloquear(week, version);
        if (programa.getEstado() != EstadoMpsSemanal.BORRADOR) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Solo se puede aprobar un MPS OF en borrador.");
        }
        if (observacionRepo.countByMps_IdAndEstadoIn(programa.getId(),
                List.of(EstadoMpsSemanalObservacion.ABIERTA, EstadoMpsSemanalObservacion.ATENDIDA)) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Hay observaciones abiertas o atendidas pendientes de aceptacion.");
        }
        List<MpsFabricacionDetalle> activas = programa.getDetalles().stream()
                .filter(d -> d.getEstado() == EstadoMpsSemanalItem.ACTIVO).toList();
        if (activas.isEmpty()) throw new IllegalArgumentException("No se puede aprobar una semana sin tandas activas.");
        if (activas.stream().anyMatch(d -> !d.getSemiTerminado().isRequiereOrdenFabricacion())) {
            throw new IllegalArgumentException("Hay semiterminados que ya no estan habilitados para OF.");
        }
        programa.setEstado(EstadoMpsSemanal.APROBADO);
        programa.setAprobadoPorUsername(actor.getUsername());
        programa.setFechaAprobacion(LocalDateTime.now(applicationClock));
        programaService.actualizar(programa, actor, false);
        return programaService.toResponse(week, programa, null);
    }
}
