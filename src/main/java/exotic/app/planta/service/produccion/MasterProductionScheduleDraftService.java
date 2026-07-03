package exotic.app.planta.service.produccion;

import exotic.app.planta.model.produccion.EstadoMpsSemanal;
import exotic.app.planta.model.produccion.EstadoMpsSemanalItem;
import exotic.app.planta.model.produccion.EstadoMpsSemanalLotePlanificado;
import exotic.app.planta.model.produccion.EstadoMpsSemanalObservacion;
import exotic.app.planta.model.produccion.MasterProductionScheduleSemanal;
import exotic.app.planta.model.produccion.MpsSemanalDia;
import exotic.app.planta.model.produccion.MpsSemanalItem;
import exotic.app.planta.model.produccion.MpsSemanalLotePlanificado;
import exotic.app.planta.model.produccion.OrdenProduccion;
import exotic.app.planta.model.produccion.SemanaMPS;
import exotic.app.planta.model.produccion.dto.AprobarMpsSemanalRequestDTO;
import exotic.app.planta.model.produccion.dto.MpsSemanalDiaDTO;
import exotic.app.planta.model.produccion.dto.MpsSemanalDraftDTO;
import exotic.app.planta.model.produccion.dto.MpsSemanalItemDTO;
import exotic.app.planta.model.produccion.dto.MpsSemanalListItemDTO;
import exotic.app.planta.model.produccion.dto.MpsSemanalLotePlanificadoDTO;
import exotic.app.planta.repo.produccion.MasterProductionScheduleSemanalRepo;
import exotic.app.planta.repo.produccion.MpsSemanalDiaRepo;
import exotic.app.planta.repo.produccion.MpsSemanalItemRepo;
import exotic.app.planta.repo.produccion.MpsSemanalLotePlanificadoRepo;
import exotic.app.planta.repo.produccion.MpsSemanalObservacionRepo;
import exotic.app.planta.resource.produccion.exceptions.MpsSemanalDraftNotFoundException;
import exotic.app.planta.resource.produccion.exceptions.MpsSemanalNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(rollbackFor = Exception.class)
public class MasterProductionScheduleDraftService {

    private final MasterProductionScheduleSemanalRepo masterProductionScheduleSemanalRepo;
    private final MpsSemanalDiaRepo mpsSemanalDiaRepo;
    private final MpsSemanalItemRepo mpsSemanalItemRepo;
    private final MpsSemanalLotePlanificadoRepo mpsSemanalLotePlanificadoRepo;
    private final MpsSemanalObservacionRepo mpsSemanalObservacionRepo;
    private final MpsSemanalEditWindowService mpsSemanalEditWindowService;
    private final MpsSemanalOrdenInicioPolicyService ordenInicioPolicyService;

    @Transactional(readOnly = true)
    public MpsSemanalDraftDTO getDraftByWeekStartDate(LocalDate weekStartDate) {
        log.debug("[MPS_SEMANAL] service getDraftByWeekStartDate begin weekStartDate={}", weekStartDate);
        validateWeekStartDate(weekStartDate);

        MasterProductionScheduleSemanal entity = masterProductionScheduleSemanalRepo.findByWeekStartDate(weekStartDate)
                .orElseThrow(() -> new MpsSemanalDraftNotFoundException(
                        "No existe borrador MPS para la semana iniciando en " + weekStartDate + "."
                ));
        log.debug("[MPS_SEMANAL] service getDraftByWeekStartDate found mpsId={} estado={} revision={}", entity.getMpsId(), entity.getEstado(), entity.getRevisionNumero());

        if (entity.getEstado() != EstadoMpsSemanal.BORRADOR) {
            log.warn("[MPS_SEMANAL] service getDraftByWeekStartDate rejected weekStartDate={} mpsId={} estado={}", weekStartDate, entity.getMpsId(), entity.getEstado());
            throw new MpsSemanalDraftNotFoundException(
                    "La semana " + weekStartDate + " existe, pero no se encuentra en estado BORRADOR."
            );
        }

        MpsSemanalDraftDTO response = toDraftDTO(entity);
        log.debug(
                "[MPS_SEMANAL] service getDraftByWeekStartDate success mpsId={} weekStartDate={} totalItems={} totalLotes={} totalOdps={}",
                response.getMpsId(),
                response.getWeekStartDate(),
                response.getTotalItems(),
                response.getTotalLotesPlanificados(),
                response.getTotalOdpsGeneradas()
        );
        return response;
    }

    @Transactional(readOnly = true)
    public MpsSemanalDraftDTO getByWeekStartDate(LocalDate weekStartDate) {
        log.debug("[MPS_SEMANAL] service getByWeekStartDate begin weekStartDate={}", weekStartDate);
        validateWeekStartDate(weekStartDate);

        MasterProductionScheduleSemanal entity = masterProductionScheduleSemanalRepo.findByWeekStartDate(weekStartDate)
                .orElseThrow(() -> new MpsSemanalNotFoundException(
                        "No existe MPS semanal para la semana iniciando en " + weekStartDate + "."
                ));

        MpsSemanalDraftDTO response = toDraftDTO(entity);
        log.debug(
                "[MPS_SEMANAL] service getByWeekStartDate success mpsId={} weekStartDate={} estado={} revision={} totalItems={} totalLotes={} totalOdps={}",
                response.getMpsId(),
                response.getWeekStartDate(),
                response.getEstado(),
                response.getRevisionNumero(),
                response.getTotalItems(),
                response.getTotalLotesPlanificados(),
                response.getTotalOdpsGeneradas()
        );
        return response;
    }

    @Transactional(readOnly = true)
    public List<MpsSemanalListItemDTO> listByEstado(EstadoMpsSemanal estado) {
        log.debug("[MPS_SEMANAL] service listByEstado begin estado={}", estado);
        List<MasterProductionScheduleSemanal> entities = estado == null
                ? masterProductionScheduleSemanalRepo.findAllByOrderByWeekStartDateDesc()
                : masterProductionScheduleSemanalRepo.findAllByEstadoOrderByWeekStartDateDesc(estado);

        List<MpsSemanalListItemDTO> response = entities.stream()
                .map(this::toListItemDTO)
                .toList();
        log.info("[MPS_SEMANAL] service listByEstado success estado={} count={}", estado, response.size());
        return response;
    }

    public MpsSemanalDraftDTO approveWeek(AprobarMpsSemanalRequestDTO request, String approvedByUsername) {
        log.info("[MPS_SEMANAL] service approveWeek begin weekStartDate={} approvedByUsername={}", request != null ? request.getWeekStartDate() : null, approvedByUsername);
        if (request == null) {
            throw new IllegalArgumentException("La solicitud de aprobacion MPS no puede ser nula.");
        }
        validateWeekStartDate(request.getWeekStartDate());
        validateApprovedByUsername(approvedByUsername);

        MasterProductionScheduleSemanal entity = masterProductionScheduleSemanalRepo.findByWeekStartDate(request.getWeekStartDate())
                .orElseThrow(() -> new MpsSemanalNotFoundException(
                        "No existe MPS semanal para la semana iniciando en " + request.getWeekStartDate() + "."
                ));
        log.debug("[MPS_SEMANAL] service approveWeek found mpsId={} estado={} revision={}", entity.getMpsId(), entity.getEstado(), entity.getRevisionNumero());

        if (entity.getEstado() != EstadoMpsSemanal.BORRADOR) {
            log.warn("[MPS_SEMANAL] service approveWeek rejected reason=invalid_estado mpsId={} estado={}", entity.getMpsId(), entity.getEstado());
            throw new IllegalStateException("Solo se pueden aprobar semanas en estado BORRADOR.");
        }
        if (hasPendingObservaciones(entity.getMpsId())) {
            log.warn("[MPS_SEMANAL] service approveWeek rejected reason=pending_observaciones mpsId={}", entity.getMpsId());
            throw new IllegalStateException("No se puede aprobar un MPS con observaciones abiertas o pendientes de aceptacion.");
        }

        long totalLotesPlanificados = countActiveLotes(entity.getMpsId());
        if (totalLotesPlanificados <= 0) {
            log.warn("[MPS_SEMANAL] service approveWeek rejected reason=no_lotes mpsId={}", entity.getMpsId());
            throw new IllegalStateException("No se puede aprobar una semana sin ODPs esperadas.");
        }

        entity.setEstado(EstadoMpsSemanal.APROBADO);
        entity.setFechaAprobacion(LocalDateTime.now());
        entity.setAprobadoPorUsername(approvedByUsername);

        MasterProductionScheduleSemanal saved = masterProductionScheduleSemanalRepo.save(entity);
        MpsSemanalDraftDTO response = toDraftDTO(saved);
        log.info(
                "[MPS_SEMANAL] service approveWeek success mpsId={} weekStartDate={} approvedByUsername={} totalLotes={}",
                response.getMpsId(),
                response.getWeekStartDate(),
                approvedByUsername,
                totalLotesPlanificados
        );
        return response;
    }

    @Transactional(readOnly = true)
    public MpsSemanalDraftDTO toDraftDTO(MasterProductionScheduleSemanal entity) {
        MpsSemanalDraftDTO dto = new MpsSemanalDraftDTO();
        fillHeader(dto, entity);
        dto.setDias(mpsSemanalDiaRepo.findAllByMpsSemanal_MpsIdOrderByDayIndexAsc(entity.getMpsId()).stream()
                .map(this::toDiaDTO)
                .toList());
        dto.setTotalItems(countActiveItems(entity.getMpsId()));
        dto.setTotalLotesPlanificados(countActiveLotes(entity.getMpsId()));
        dto.setTotalOdpsGeneradas(mpsSemanalLotePlanificadoRepo.countByMpsItem_MpsSemanal_MpsIdAndEstado(
                entity.getMpsId(),
                EstadoMpsSemanalLotePlanificado.ODP_GENERADA
        ));
        return dto;
    }

    private MpsSemanalListItemDTO toListItemDTO(MasterProductionScheduleSemanal entity) {
        MpsSemanalListItemDTO dto = new MpsSemanalListItemDTO();
        dto.setMpsId(entity.getMpsId());
        dto.setEstado(entity.getEstado());
        dto.setFechaCreacion(entity.getFechaCreacion());
        dto.setFechaActualizacion(entity.getFechaActualizacion());
        dto.setFechaAprobacion(entity.getFechaAprobacion());
        dto.setAprobadoPorUsername(entity.getAprobadoPorUsername());
        dto.setFechaGeneracionOdps(entity.getFechaGeneracionOdps());
        dto.setGeneradoPorUsername(entity.getGeneradoPorUsername());
        dto.setRevisionNumero(entity.getRevisionNumero());
        fillSemanaFields(dto, entity);
        dto.setWeekStartDate(entity.getWeekStartDate());
        dto.setWeekEndDate(entity.getWeekEndDate());
        dto.setTotalItems(countActiveItems(entity.getMpsId()));
        dto.setTotalLotesPlanificados(countActiveLotes(entity.getMpsId()));
        dto.setTotalOdpsGeneradas(mpsSemanalLotePlanificadoRepo.countByMpsItem_MpsSemanal_MpsIdAndEstado(
                entity.getMpsId(),
                EstadoMpsSemanalLotePlanificado.ODP_GENERADA
        ));
        return dto;
    }

    private long countActiveItems(Integer mpsId) {
        return mpsSemanalItemRepo.countByMpsSemanal_MpsIdAndEstadoNot(mpsId, EstadoMpsSemanalItem.CANCELADO);
    }

    private long countActiveLotes(Integer mpsId) {
        return mpsSemanalLotePlanificadoRepo.countByMpsItem_MpsSemanal_MpsIdAndEstadoNot(
                mpsId,
                EstadoMpsSemanalLotePlanificado.CANCELADO
        );
    }

    private MpsSemanalDiaDTO toDiaDTO(MpsSemanalDia dia) {
        MpsSemanalDiaDTO dto = new MpsSemanalDiaDTO();
        dto.setId(dia.getId());
        dto.setFecha(dia.getFecha());
        dto.setDayIndex(dia.getDayIndex());
        dto.setDisplayOrder(dia.getDisplayOrder());
        dto.setItems(dia.getItems().stream()
                .map(this::toItemDTO)
                .toList());
        return dto;
    }

    private MpsSemanalItemDTO toItemDTO(MpsSemanalItem item) {
        MpsSemanalItemDTO dto = new MpsSemanalItemDTO();
        dto.setId(item.getId());
        dto.setTerminadoId(item.getTerminado() != null ? item.getTerminado().getProductoId() : null);
        dto.setTerminadoNombre(item.getTerminadoNombre());
        dto.setCategoriaId(item.getCategoriaId());
        dto.setCategoriaNombre(item.getCategoriaNombre());
        dto.setLoteSize(item.getLoteSize());
        dto.setTiempoDiasFabricacion(item.getTiempoDiasFabricacion());
        dto.setNumeroLotes(item.getNumeroLotes());
        dto.setEstadoItem(resolveItemEstado(item));
        dto.setCantidadTotal(item.getCantidadTotal());
        dto.setFechaLanzamiento(item.getFechaLanzamiento());
        dto.setFechaFinalPlanificada(item.getFechaFinalPlanificada());
        dto.setObservacion(item.getObservacion());
        dto.setWarning(item.getWarning());
        dto.setDisplayOrder(item.getDisplayOrder());
        dto.setLotesPlanificados(item.getLotesPlanificados().stream()
                .map(this::toLotePlanificadoDTO)
                .toList());
        fillItemEditMetadata(dto, item);
        return dto;
    }

    private MpsSemanalLotePlanificadoDTO toLotePlanificadoDTO(MpsSemanalLotePlanificado lote) {
        MpsSemanalLotePlanificadoDTO dto = new MpsSemanalLotePlanificadoDTO();
        dto.setId(lote.getId());
        dto.setLoteOrdinal(lote.getLoteOrdinal());
        dto.setCantidadPlanificada(lote.getCantidadPlanificada());
        dto.setEstado(lote.getEstado());
        if (lote.getOrdenProduccion() != null) {
            dto.setOrdenProduccionId(lote.getOrdenProduccion().getOrdenId());
            dto.setLoteAsignado(lote.getOrdenProduccion().getLoteAsignado());
            dto.setOrdenIniciada(ordenInicioPolicyService.isOrdenIniciada(lote.getOrdenProduccion()));
            dto.setOrdenCancelable(ordenInicioPolicyService.isOrdenCancelable(lote.getOrdenProduccion()));
        }
        return dto;
    }

    private void fillItemEditMetadata(MpsSemanalItemDTO dto, MpsSemanalItem item) {
        List<MpsSemanalLotePlanificado> activeLotes = item.getLotesPlanificados().stream()
                .filter(lote -> lote.getEstado() != EstadoMpsSemanalLotePlanificado.CANCELADO)
                .toList();
        long lotesCancelados = item.getLotesPlanificados().stream()
                .filter(lote -> lote.getEstado() == EstadoMpsSemanalLotePlanificado.CANCELADO)
                .count();
        List<OrdenProduccion> activeOrdenes = activeLotes.stream()
                .map(MpsSemanalLotePlanificado::getOrdenProduccion)
                .filter(java.util.Objects::nonNull)
                .filter(orden -> orden.getEstadoOrden() != -1)
                .toList();
        int ordenesIniciadas = (int) activeOrdenes.stream()
                .filter(ordenInicioPolicyService::isOrdenIniciada)
                .count();
        int ordenesCancelables = (int) activeOrdenes.stream()
                .filter(ordenInicioPolicyService::isOrdenCancelable)
                .count();
        int lotesCancelables = (int) activeLotes.stream()
                .filter(lote -> lote.getOrdenProduccion() == null
                        || ordenInicioPolicyService.isOrdenCancelable(lote.getOrdenProduccion()))
                .count();
        int lotesNoCancelables = activeLotes.size() - lotesCancelables;

        dto.setLotesCancelados((int) lotesCancelados);
        dto.setLotesActivos(activeLotes.size());
        dto.setOrdenesIniciadas(ordenesIniciadas);
        dto.setOrdenesCancelables(ordenesCancelables);
        dto.setLotesCancelables(lotesCancelables);
        dto.setLotesNoCancelables(lotesNoCancelables);

        String blockedReason = resolveItemBlockedReason(item, lotesNoCancelables, lotesCancelables);
        dto.setBlockedReason(blockedReason);
        dto.setEditable(blockedReason == null);
    }

    private String resolveItemBlockedReason(MpsSemanalItem item, int lotesNoCancelables, int lotesCancelables) {
        if (resolveItemEstado(item) == EstadoMpsSemanalItem.CANCELADO) {
            return "Tarjeta MPS cancelada.";
        }
        if (item.getMpsDia() == null || item.getMpsDia().getFecha() == null) {
            return "La tarjeta no tiene fecha MPS valida.";
        }
        EstadoMpsSemanal estadoMps = item.getMpsSemanal() != null ? item.getMpsSemanal().getEstado() : null;
        if (estadoMps == EstadoMpsSemanal.BORRADOR
                && !mpsSemanalEditWindowService.isEditable(item.getMpsDia().getFecha())) {
            return "Dia bloqueado. La primera fecha editable es " + mpsSemanalEditWindowService.getEditableFromDate() + ".";
        }
        if (lotesNoCancelables > 0) {
            if (lotesCancelables > 0) {
                return "Tiene " + lotesNoCancelables
                        + " lote(s) con OP iniciada o movimientos reales. Puede cancelar "
                        + lotesCancelables + " lote(s) sin ejecucion.";
            }
            return "Tiene " + lotesNoCancelables + " lote(s) con OP iniciada o movimientos reales.";
        }
        return null;
    }

    private void fillHeader(MpsSemanalDraftDTO dto, MasterProductionScheduleSemanal entity) {
        dto.setMpsId(entity.getMpsId());
        dto.setEstado(entity.getEstado());
        dto.setFechaCreacion(entity.getFechaCreacion());
        dto.setFechaActualizacion(entity.getFechaActualizacion());
        dto.setFechaAprobacion(entity.getFechaAprobacion());
        dto.setAprobadoPorUsername(entity.getAprobadoPorUsername());
        dto.setFechaGeneracionOdps(entity.getFechaGeneracionOdps());
        dto.setGeneradoPorUsername(entity.getGeneradoPorUsername());
        dto.setRevisionNumero(entity.getRevisionNumero());
        fillSemanaFields(dto, entity);
        dto.setWeekStartDate(entity.getWeekStartDate());
        dto.setWeekEndDate(entity.getWeekEndDate());
    }

    private EstadoMpsSemanalItem resolveItemEstado(MpsSemanalItem item) {
        return item.getEstado() != null ? item.getEstado() : EstadoMpsSemanalItem.ACTIVO;
    }

    private void fillSemanaFields(MpsSemanalDraftDTO dto, MasterProductionScheduleSemanal entity) {
        SemanaMPS semanaMps = entity.getSemanaMps();
        if (semanaMps == null) {
            return;
        }
        dto.setSemanaMpsId(semanaMps.getId());
        dto.setSemanaMpsCodigo(semanaMps.getCodigo());
        dto.setAnioSemana(semanaMps.getAnioSemana());
        dto.setNumeroSemana(semanaMps.getNumeroSemana());
        dto.setStandard(semanaMps.getStandard());
    }

    private void fillSemanaFields(MpsSemanalListItemDTO dto, MasterProductionScheduleSemanal entity) {
        SemanaMPS semanaMps = entity.getSemanaMps();
        if (semanaMps == null) {
            return;
        }
        dto.setSemanaMpsId(semanaMps.getId());
        dto.setSemanaMpsCodigo(semanaMps.getCodigo());
        dto.setAnioSemana(semanaMps.getAnioSemana());
        dto.setNumeroSemana(semanaMps.getNumeroSemana());
        dto.setStandard(semanaMps.getStandard());
    }

    private void validateWeekStartDate(LocalDate weekStartDate) {
        if (weekStartDate == null) {
            throw new IllegalArgumentException("weekStartDate es obligatorio.");
        }
        if (weekStartDate.getDayOfWeek() != DayOfWeek.MONDAY) {
            throw new IllegalArgumentException("weekStartDate debe corresponder a un lunes.");
        }
    }

    private void validateApprovedByUsername(String approvedByUsername) {
        if (approvedByUsername == null || approvedByUsername.isBlank()) {
            throw new IllegalArgumentException("No se pudo determinar el usuario aprobador.");
        }
    }

    private boolean hasPendingObservaciones(Integer mpsId) {
        return mpsSemanalObservacionRepo.existsByMpsSemanal_MpsIdAndEstado(
                mpsId,
                EstadoMpsSemanalObservacion.ABIERTA
        ) || mpsSemanalObservacionRepo.existsByMpsSemanal_MpsIdAndEstado(
                mpsId,
                EstadoMpsSemanalObservacion.ATENDIDA
        );
    }
}
