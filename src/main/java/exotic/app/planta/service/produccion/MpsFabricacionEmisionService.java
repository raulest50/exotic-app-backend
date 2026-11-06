package exotic.app.planta.service.produccion;

import exotic.app.planta.model.produccion.*;
import exotic.app.planta.model.produccion.dto.MpsFabricacionDTOs.ProgramaResponse;
import exotic.app.planta.model.produccion.fabricacion.*;
import exotic.app.planta.model.users.User;
import exotic.app.planta.repo.produccion.fabricacion.MpsFabricacionSemanalRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class MpsFabricacionEmisionService {
    private final MpsFabricacionSemanalRepo programaRepo;
    private final MpsFabricacionService programaService;
    private final OrdenFabricacionService ordenService;
    private final Clock applicationClock;

    @Transactional(rollbackFor = Exception.class)
    public ProgramaResponse emitir(LocalDate week, Long expectedVersion, User actor) {
        MpsFabricacionService.validarSemana(week);
        MpsFabricacionSemanal programa = programaRepo.findByWeekForUpdate(week)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe MPS OF para esa semana."));
        // Un reintento de emision ya confirmada devuelve las mismas tandas, sin crear nada.
        if (programa.getEstado() == EstadoMpsSemanal.CERRADO) {
            return programaService.toResponse(week, programa, null);
        }
        if (!Objects.equals(expectedVersion, programa.getVersion())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El MPS OF cambio. Recargue antes de emitir.");
        }
        if (programa.getEstado() != EstadoMpsSemanal.APROBADO) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Solo se pueden emitir OF desde un MPS OF aprobado.");
        }
        var activas = programa.getDetalles().stream()
                .filter(d -> d.getEstado() == EstadoMpsSemanalItem.ACTIVO).toList();
        if (activas.isEmpty()) throw new IllegalArgumentException("No existen tandas activas para emitir.");
        activas.forEach(d -> emitirTanda(d, actor));
        programa.setEstado(EstadoMpsSemanal.CERRADO);
        programa.setFechaEmision(LocalDateTime.now(applicationClock));
        programa.setEmitidoPorUsername(actor.getUsername());
        programaService.actualizar(programa, actor, false);
        return programaService.toResponse(week, programa, null);
    }

    void emitirTanda(MpsFabricacionDetalle detalle, User actor) {
        if (detalle.getEstado() != EstadoMpsSemanalItem.ACTIVO) {
            throw new IllegalArgumentException("Una tanda cancelada no puede emitirse.");
        }
        ordenService.crearDesdeMps(detalle, actor);
    }
}
