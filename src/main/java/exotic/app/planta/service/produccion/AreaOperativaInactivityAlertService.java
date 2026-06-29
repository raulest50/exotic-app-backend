package exotic.app.planta.service.produccion;

import exotic.app.planta.config.initializers.AreaOperativaInitializer;
import exotic.app.planta.model.organizacion.AreaOperativa;
import exotic.app.planta.model.produccion.ActorTipoEventoSeguimiento;
import exotic.app.planta.model.produccion.EstadoSeguimientoOrdenArea;
import exotic.app.planta.model.produccion.dto.AreaOperativaInactivityAlertDTO;
import exotic.app.planta.model.produccion.dto.AreaOperativaInactivityAlertDTO.EstadoAlertaInactividad;
import exotic.app.planta.repo.produccion.SeguimientoOrdenAreaEventoRepo;
import exotic.app.planta.repo.produccion.SeguimientoOrdenAreaRepo;
import exotic.app.planta.repo.producto.procesos.AreaProduccionRepo;
import exotic.app.planta.service.master.configs.MasterDirectiveService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AreaOperativaInactivityAlertService {

    private final AreaProduccionRepo areaProduccionRepo;
    private final SeguimientoOrdenAreaEventoRepo seguimientoOrdenAreaEventoRepo;
    private final SeguimientoOrdenAreaRepo seguimientoOrdenAreaRepo;
    private final MasterDirectiveService masterDirectiveService;
    private final Clock applicationClock;

    @Transactional(readOnly = true)
    public List<AreaOperativaInactivityAlertDTO> getAlertasInactividad() {
        List<AreaOperativa> areas = areaProduccionRepo
                .findAllByResponsableAreaIsNotNullAndAreaIdNotOrderByNombreAsc(
                        AreaOperativaInitializer.ALMACEN_GENERAL_ID);

        if (areas.isEmpty()) {
            return List.of();
        }

        List<Integer> areaIds = areas.stream()
                .map(AreaOperativa::getAreaId)
                .toList();

        Map<Integer, LocalDateTime> ultimaTerminacionByAreaId = seguimientoOrdenAreaEventoRepo
                .findUltimasTerminacionesByAreaIds(
                        areaIds,
                        ActorTipoEventoSeguimiento.USER,
                        EstadoSeguimientoOrdenArea.COMPLETADO.getCode()
                )
                .stream()
                .collect(Collectors.toMap(
                        SeguimientoOrdenAreaEventoRepo.UltimaTerminacionAreaProjection::getAreaId,
                        SeguimientoOrdenAreaEventoRepo.UltimaTerminacionAreaProjection::getUltimaTerminacionAt
                ));

        Set<Integer> areasConCargaActiva = seguimientoOrdenAreaRepo
                .countCargaActivaByAreaIds(
                        areaIds,
                        List.of(
                                EstadoSeguimientoOrdenArea.ESPERA.getCode(),
                                EstadoSeguimientoOrdenArea.EN_PROCESO.getCode()
                        )
                )
                .stream()
                .filter(projection -> projection.getTotal() != null && projection.getTotal() > 0)
                .map(SeguimientoOrdenAreaRepo.CargaActivaAreaProjection::getAreaId)
                .collect(Collectors.toSet());

        boolean alertsEnabled = masterDirectiveService.isAreaOperativaInactivityAlertEnabled();
        int thresholdMinutes = masterDirectiveService.getAreaOperativaInactivityThresholdMinutes();
        int checkIntervalMinutes = masterDirectiveService.getAreaOperativaInactivityCheckIntervalMinutes();
        LocalDateTime now = LocalDateTime.now(applicationClock);

        return areas.stream()
                .map(toAlertDto(
                        ultimaTerminacionByAreaId,
                        areasConCargaActiva,
                        alertsEnabled,
                        thresholdMinutes,
                        checkIntervalMinutes,
                        now
                ))
                .toList();
    }

    private Function<AreaOperativa, AreaOperativaInactivityAlertDTO> toAlertDto(
            Map<Integer, LocalDateTime> ultimaTerminacionByAreaId,
            Set<Integer> areasConCargaActiva,
            boolean alertsEnabled,
            int thresholdMinutes,
            int checkIntervalMinutes,
            LocalDateTime now
    ) {
        return area -> {
            LocalDateTime ultimaTerminacionAt = ultimaTerminacionByAreaId.get(area.getAreaId());
            Long minutosDesdeUltimaTerminacion = resolveMinutosDesdeUltimaTerminacion(ultimaTerminacionAt, now);
            boolean tieneCargaActiva = areasConCargaActiva.contains(area.getAreaId());
            boolean alertaActiva = alertsEnabled
                    && tieneCargaActiva
                    && minutosDesdeUltimaTerminacion != null
                    && minutosDesdeUltimaTerminacion >= thresholdMinutes;
            EstadoAlertaInactividad estado = resolveEstado(ultimaTerminacionAt, alertaActiva);

            return AreaOperativaInactivityAlertDTO.builder()
                    .areaId(area.getAreaId())
                    .areaNombre(area.getNombre())
                    .estado(estado)
                    .alertaActiva(alertaActiva)
                    .tieneCargaActiva(tieneCargaActiva)
                    .ultimaTerminacionAt(ultimaTerminacionAt)
                    .minutosDesdeUltimaTerminacion(minutosDesdeUltimaTerminacion)
                    .thresholdMinutes(thresholdMinutes)
                    .checkIntervalMinutes(checkIntervalMinutes)
                    .alertsEnabled(alertsEnabled)
                    .build();
        };
    }

    private Long resolveMinutosDesdeUltimaTerminacion(LocalDateTime ultimaTerminacionAt, LocalDateTime now) {
        if (ultimaTerminacionAt == null) {
            return null;
        }
        return Math.max(0, Duration.between(ultimaTerminacionAt, now).toMinutes());
    }

    private EstadoAlertaInactividad resolveEstado(LocalDateTime ultimaTerminacionAt, boolean alertaActiva) {
        if (ultimaTerminacionAt == null) {
            return EstadoAlertaInactividad.SIN_TERMINACIONES;
        }
        return alertaActiva ? EstadoAlertaInactividad.INACTIVA : EstadoAlertaInactividad.ACTIVA;
    }
}
