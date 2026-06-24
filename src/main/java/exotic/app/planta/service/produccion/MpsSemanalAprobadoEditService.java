package exotic.app.planta.service.produccion;

import exotic.app.planta.model.produccion.EstadoMpsSemanal;
import exotic.app.planta.model.produccion.EstadoMpsSemanalItem;
import exotic.app.planta.model.produccion.EstadoMpsSemanalLotePlanificado;
import exotic.app.planta.model.produccion.MasterProductionScheduleSemanal;
import exotic.app.planta.model.produccion.MpsSemanalDia;
import exotic.app.planta.model.produccion.MpsSemanalItem;
import exotic.app.planta.model.produccion.MpsSemanalLotePlanificado;
import exotic.app.planta.model.produccion.OrdenProduccion;
import exotic.app.planta.model.produccion.dto.MpsSemanalAprobadoItemEditRequestDTO;
import exotic.app.planta.model.produccion.dto.MpsSemanalDraftDTO;
import exotic.app.planta.repo.produccion.MpsSemanalDiaRepo;
import exotic.app.planta.repo.produccion.MpsSemanalItemRepo;
import exotic.app.planta.repo.produccion.MpsSemanalLotePlanificadoRepo;
import exotic.app.planta.repo.produccion.OrdenProduccionRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(rollbackFor = Exception.class)
public class MpsSemanalAprobadoEditService {

    private final MpsSemanalItemRepo mpsSemanalItemRepo;
    private final MpsSemanalDiaRepo mpsSemanalDiaRepo;
    private final MpsSemanalLotePlanificadoRepo mpsSemanalLotePlanificadoRepo;
    private final OrdenProduccionRepo ordenProduccionRepo;
    private final ProduccionService produccionService;
    private final MasterProductionScheduleOrderGenerationService orderGenerationService;
    private final MasterProductionScheduleDraftService draftService;
    private final MpsSemanalEditWindowService editWindowService;
    private final MpsSemanalOrdenInicioPolicyService ordenInicioPolicyService;
    private final Clock applicationClock;

    public MpsSemanalDraftDTO editarItemAprobado(
            Long itemId,
            MpsSemanalAprobadoItemEditRequestDTO request,
            String editedByUsername
    ) {
        validateRequest(itemId, request, editedByUsername);
        MpsSemanalItem item = mpsSemanalItemRepo.findByIdForAprobadoEdit(itemId)
                .orElseThrow(() -> new IllegalArgumentException("No existe item MPS semanal con id " + itemId + "."));
        MasterProductionScheduleSemanal mps = item.getMpsSemanal();
        requireEditableMpsState(mps);
        requireActiveItem(item);

        MpsSemanalDia sourceDia = item.getMpsDia();
        MpsSemanalDia targetDia = resolveTargetDia(mps, request.getDayIndex());
        requireEditableDate(sourceDia.getFecha(), "origen");
        requireEditableDate(targetDia.getFecha(), "destino");
        requireNoDuplicateTargetItem(item, targetDia);

        List<MpsSemanalLotePlanificado> activeLotes = getActiveLotes(item);
        if (hasStartedOrden(activeLotes)) {
            throw new IllegalStateException("La tarjeta MPS tiene al menos una OP iniciada y no puede editarse.");
        }

        boolean moved = !Objects.equals(sourceDia.getId(), targetDia.getId());
        if (moved) {
            item.setMpsDia(targetDia);
            item.setFechaLanzamiento(targetDia.getFecha());
            item.setFechaFinalPlanificada(targetDia.getFecha().plusDays(Math.max(item.getTiempoDiasFabricacion(), 0)));
            item.setWarning(resolveWarning(item, mps.getWeekEndDate()));
            item.setDisplayOrder(nextDisplayOrder(targetDia, item.getId()));
        }

        int desiredLotes = request.getNumeroLotes();
        int currentActiveLotes = activeLotes.size();
        if (desiredLotes < currentActiveLotes) {
            cancelExcessLotes(activeLotes, currentActiveLotes - desiredLotes);
        } else if (desiredLotes > currentActiveLotes) {
            addLotes(item, mps, desiredLotes - currentActiveLotes);
        }

        item.setNumeroLotes(desiredLotes);
        item.setCantidadTotal(desiredLotes * (double) item.getLoteSize());
        item.setObservacion(normalizeOptionalText(request.getObservacion()));
        item.setEstado(desiredLotes == 0 ? EstadoMpsSemanalItem.CANCELADO : EstadoMpsSemanalItem.ACTIVO);
        if (desiredLotes == 0) {
            item.setWarning(null);
        }

        updateActiveOrdenDates(item);
        mps.setRevisionNumero(resolveNextRevision(mps));
        mpsSemanalItemRepo.save(item);

        log.info(
                "[MPS_SEMANAL] aprobadoEdit success itemId={} mpsId={} weekStartDate={} editedByUsername={} dayIndex={} numeroLotes={}",
                item.getId(),
                mps.getMpsId(),
                mps.getWeekStartDate(),
                editedByUsername,
                request.getDayIndex(),
                desiredLotes
        );
        return draftService.getByWeekStartDate(mps.getWeekStartDate());
    }

    private void validateRequest(Long itemId, MpsSemanalAprobadoItemEditRequestDTO request, String editedByUsername) {
        if (itemId == null) {
            throw new IllegalArgumentException("itemId es obligatorio.");
        }
        if (request == null) {
            throw new IllegalArgumentException("La solicitud de edicion MPS no puede ser nula.");
        }
        if (editedByUsername == null || editedByUsername.isBlank()) {
            throw new IllegalArgumentException("No se pudo determinar el usuario editor.");
        }
        if (request.getDayIndex() < 0 || request.getDayIndex() > 5) {
            throw new IllegalArgumentException("dayIndex debe estar entre 0 y 5.");
        }
        if (request.getNumeroLotes() < 0) {
            throw new IllegalArgumentException("numeroLotes debe ser mayor o igual a cero.");
        }
    }

    private void requireEditableMpsState(MasterProductionScheduleSemanal mps) {
        if (mps == null || (mps.getEstado() != EstadoMpsSemanal.APROBADO && mps.getEstado() != EstadoMpsSemanal.CERRADO)) {
            throw new IllegalStateException("La edicion avanzada solo aplica para MPS en estado APROBADO o CERRADO.");
        }
    }

    private void requireActiveItem(MpsSemanalItem item) {
        if (item.getEstado() == EstadoMpsSemanalItem.CANCELADO) {
            throw new IllegalStateException("La tarjeta MPS ya esta cancelada y no admite nuevas ediciones.");
        }
    }

    private MpsSemanalDia resolveTargetDia(MasterProductionScheduleSemanal mps, int dayIndex) {
        return mpsSemanalDiaRepo.findAllByMpsSemanal_MpsIdOrderByDayIndexAsc(mps.getMpsId()).stream()
                .filter(dia -> dia.getDayIndex() == dayIndex)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No existe dia MPS para dayIndex " + dayIndex + "."));
    }

    private void requireEditableDate(LocalDate date, String role) {
        if (!editWindowService.isEditable(date)) {
            throw new IllegalStateException("El dia de " + role + " esta bloqueado. "
                    + "La primera fecha editable es " + editWindowService.getEditableFromDate() + ".");
        }
    }

    private void requireNoDuplicateTargetItem(MpsSemanalItem item, MpsSemanalDia targetDia) {
        String productoId = item.getTerminado() != null ? item.getTerminado().getProductoId() : null;
        boolean duplicate = targetDia.getItems().stream()
                .anyMatch(existing -> !Objects.equals(existing.getId(), item.getId())
                        && existing.getTerminado() != null
                        && Objects.equals(existing.getTerminado().getProductoId(), productoId));
        if (duplicate) {
            throw new IllegalStateException("El producto ya existe en el dia destino del MPS.");
        }
    }

    private List<MpsSemanalLotePlanificado> getActiveLotes(MpsSemanalItem item) {
        return item.getLotesPlanificados().stream()
                .filter(lote -> lote.getEstado() != EstadoMpsSemanalLotePlanificado.CANCELADO)
                .sorted(Comparator.comparingInt(MpsSemanalLotePlanificado::getLoteOrdinal))
                .toList();
    }

    private boolean hasStartedOrden(List<MpsSemanalLotePlanificado> lotes) {
        return lotes.stream()
                .map(MpsSemanalLotePlanificado::getOrdenProduccion)
                .filter(Objects::nonNull)
                .anyMatch(ordenInicioPolicyService::isOrdenIniciada);
    }

    private void cancelExcessLotes(List<MpsSemanalLotePlanificado> activeLotes, int quantityToCancel) {
        List<MpsSemanalLotePlanificado> lotesToCancel = activeLotes.stream()
                .sorted(Comparator.comparingInt(MpsSemanalLotePlanificado::getLoteOrdinal).reversed())
                .limit(quantityToCancel)
                .toList();

        for (MpsSemanalLotePlanificado lote : lotesToCancel) {
            OrdenProduccion orden = lote.getOrdenProduccion();
            if (orden != null) {
                if (!ordenInicioPolicyService.isOrdenCancelable(orden)) {
                    throw new IllegalStateException("La OP " + orden.getOrdenId() + " no es cancelable.");
                }
                produccionService.cancelarOrdenProduccion(orden.getOrdenId());
            }
            lote.setEstado(EstadoMpsSemanalLotePlanificado.CANCELADO);
            mpsSemanalLotePlanificadoRepo.save(lote);
        }
    }

    private void addLotes(MpsSemanalItem item, MasterProductionScheduleSemanal mps, int quantityToAdd) {
        int nextOrdinal = item.getLotesPlanificados().stream()
                .mapToInt(MpsSemanalLotePlanificado::getLoteOrdinal)
                .max()
                .orElse(0) + 1;

        List<MpsSemanalLotePlanificado> newLotes = new ArrayList<>();
        for (int i = 0; i < quantityToAdd; i++) {
            MpsSemanalLotePlanificado lote = new MpsSemanalLotePlanificado();
            lote.setMpsItem(item);
            lote.setLoteOrdinal(nextOrdinal++);
            lote.setCantidadPlanificada(item.getLoteSize());
            lote.setEstado(EstadoMpsSemanalLotePlanificado.PENDIENTE_ODP);
            item.getLotesPlanificados().add(lote);
            newLotes.add(mpsSemanalLotePlanificadoRepo.save(lote));
        }

        if (mps.getEstado() == EstadoMpsSemanal.CERRADO || mps.getFechaGeneracionOdps() != null) {
            orderGenerationService.generarOrdenesParaLotes(mps, newLotes);
            mps.setFechaGeneracionOdps(mps.getFechaGeneracionOdps() != null
                    ? mps.getFechaGeneracionOdps()
                    : LocalDateTime.now(applicationClock));
        }
    }

    private void updateActiveOrdenDates(MpsSemanalItem item) {
        for (MpsSemanalLotePlanificado lote : getActiveLotes(item)) {
            OrdenProduccion orden = lote.getOrdenProduccion();
            if (orden == null || orden.getEstadoOrden() == -1) {
                continue;
            }
            orden.setFechaLanzamiento(item.getFechaLanzamiento().atStartOfDay());
            orden.setFechaFinalPlanificada(item.getFechaFinalPlanificada().atStartOfDay());
            ordenProduccionRepo.save(orden);
        }
    }

    private int nextDisplayOrder(MpsSemanalDia targetDia, Long movingItemId) {
        return targetDia.getItems().stream()
                .filter(existing -> !Objects.equals(existing.getId(), movingItemId))
                .mapToInt(MpsSemanalItem::getDisplayOrder)
                .max()
                .orElse(-1) + 1;
    }

    private int resolveNextRevision(MasterProductionScheduleSemanal mps) {
        return mps.getRevisionNumero() != null && mps.getRevisionNumero() > 0
                ? mps.getRevisionNumero() + 1
                : 2;
    }

    private String resolveWarning(MpsSemanalItem item, LocalDate weekEndDate) {
        if (item.getFechaFinalPlanificada() != null && weekEndDate != null && item.getFechaFinalPlanificada().isAfter(weekEndDate)) {
            return "La fecha final planificada desborda la semana lunes-sabado.";
        }
        return null;
    }

    private static String normalizeOptionalText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
