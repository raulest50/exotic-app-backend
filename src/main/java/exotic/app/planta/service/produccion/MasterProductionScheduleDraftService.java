package exotic.app.planta.service.produccion;

import exotic.app.planta.model.produccion.EstadoMpsSemanal;
import exotic.app.planta.model.produccion.EstadoMpsSemanalLotePlanificado;
import exotic.app.planta.model.produccion.EstadoMpsSemanalObservacion;
import exotic.app.planta.model.produccion.MasterProductionScheduleSemanal;
import exotic.app.planta.model.produccion.MpsSemanalDia;
import exotic.app.planta.model.produccion.MpsSemanalItem;
import exotic.app.planta.model.produccion.MpsSemanalLotePlanificado;
import exotic.app.planta.model.produccion.SemanaMPS;
import exotic.app.planta.model.produccion.dto.AprobarMpsSemanalRequestDTO;
import exotic.app.planta.model.produccion.dto.GuardarMpsSemanalDraftRequestDTO;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class MasterProductionScheduleDraftService {

    private final MasterProductionScheduleSemanalRepo masterProductionScheduleSemanalRepo;
    private final MpsSemanalDiaRepo mpsSemanalDiaRepo;
    private final MpsSemanalItemRepo mpsSemanalItemRepo;
    private final MpsSemanalLotePlanificadoRepo mpsSemanalLotePlanificadoRepo;
    private final MpsSemanalObservacionRepo mpsSemanalObservacionRepo;

    public MpsSemanalDraftDTO saveDraft(GuardarMpsSemanalDraftRequestDTO ignoredRequest) {
        throw new IllegalStateException("El MPS semanal ya no se persiste desde snapshot JSON. Use la programacion semanal diaria.");
    }

    @Transactional(readOnly = true)
    public MpsSemanalDraftDTO getDraftByWeekStartDate(LocalDate weekStartDate) {
        validateWeekStartDate(weekStartDate);

        MasterProductionScheduleSemanal entity = masterProductionScheduleSemanalRepo.findByWeekStartDate(weekStartDate)
                .orElseThrow(() -> new MpsSemanalDraftNotFoundException(
                        "No existe borrador MPS para la semana iniciando en " + weekStartDate + "."
                ));

        if (entity.getEstado() != EstadoMpsSemanal.BORRADOR) {
            throw new MpsSemanalDraftNotFoundException(
                    "La semana " + weekStartDate + " existe, pero no se encuentra en estado BORRADOR."
            );
        }

        return toDraftDTO(entity);
    }

    @Transactional(readOnly = true)
    public MpsSemanalDraftDTO getByWeekStartDate(LocalDate weekStartDate) {
        validateWeekStartDate(weekStartDate);

        MasterProductionScheduleSemanal entity = masterProductionScheduleSemanalRepo.findByWeekStartDate(weekStartDate)
                .orElseThrow(() -> new MpsSemanalNotFoundException(
                        "No existe MPS semanal para la semana iniciando en " + weekStartDate + "."
                ));

        return toDraftDTO(entity);
    }

    @Transactional(readOnly = true)
    public List<MpsSemanalListItemDTO> listByEstado(EstadoMpsSemanal estado) {
        List<MasterProductionScheduleSemanal> entities = estado == null
                ? masterProductionScheduleSemanalRepo.findAllByOrderByWeekStartDateDesc()
                : masterProductionScheduleSemanalRepo.findAllByEstadoOrderByWeekStartDateDesc(estado);

        return entities.stream()
                .map(this::toListItemDTO)
                .toList();
    }

    public MpsSemanalDraftDTO approveWeek(AprobarMpsSemanalRequestDTO request, String approvedByUsername) {
        if (request == null) {
            throw new IllegalArgumentException("La solicitud de aprobacion MPS no puede ser nula.");
        }
        validateWeekStartDate(request.getWeekStartDate());
        validateApprovedByUsername(approvedByUsername);

        MasterProductionScheduleSemanal entity = masterProductionScheduleSemanalRepo.findByWeekStartDate(request.getWeekStartDate())
                .orElseThrow(() -> new MpsSemanalNotFoundException(
                        "No existe MPS semanal para la semana iniciando en " + request.getWeekStartDate() + "."
                ));

        if (entity.getEstado() != EstadoMpsSemanal.BORRADOR) {
            throw new IllegalStateException("Solo se pueden aprobar semanas en estado BORRADOR.");
        }
        if (hasPendingObservaciones(entity.getMpsId())) {
            throw new IllegalStateException("No se puede aprobar un MPS con observaciones abiertas o pendientes de aceptacion.");
        }

        long totalLotesPlanificados = mpsSemanalLotePlanificadoRepo.countByMpsItem_MpsSemanal_MpsId(entity.getMpsId());
        if (totalLotesPlanificados <= 0) {
            throw new IllegalStateException("No se puede aprobar una semana sin ODPs esperadas.");
        }

        entity.setEstado(EstadoMpsSemanal.APROBADO);
        entity.setFechaAprobacion(LocalDateTime.now());
        entity.setAprobadoPorUsername(approvedByUsername);

        MasterProductionScheduleSemanal saved = masterProductionScheduleSemanalRepo.save(entity);
        return toDraftDTO(saved);
    }

    @Transactional(readOnly = true)
    public MpsSemanalDraftDTO toDraftDTO(MasterProductionScheduleSemanal entity) {
        MpsSemanalDraftDTO dto = new MpsSemanalDraftDTO();
        fillHeader(dto, entity);
        dto.setDias(mpsSemanalDiaRepo.findAllByMpsSemanal_MpsIdOrderByDayIndexAsc(entity.getMpsId()).stream()
                .map(this::toDiaDTO)
                .toList());
        dto.setTotalItems(mpsSemanalItemRepo.countByMpsSemanal_MpsId(entity.getMpsId()));
        dto.setTotalLotesPlanificados(mpsSemanalLotePlanificadoRepo.countByMpsItem_MpsSemanal_MpsId(entity.getMpsId()));
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
        dto.setTotalItems(mpsSemanalItemRepo.countByMpsSemanal_MpsId(entity.getMpsId()));
        dto.setTotalLotesPlanificados(mpsSemanalLotePlanificadoRepo.countByMpsItem_MpsSemanal_MpsId(entity.getMpsId()));
        dto.setTotalOdpsGeneradas(mpsSemanalLotePlanificadoRepo.countByMpsItem_MpsSemanal_MpsIdAndEstado(
                entity.getMpsId(),
                EstadoMpsSemanalLotePlanificado.ODP_GENERADA
        ));
        return dto;
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
        dto.setCantidadTotal(item.getCantidadTotal());
        dto.setFechaLanzamiento(item.getFechaLanzamiento());
        dto.setFechaFinalPlanificada(item.getFechaFinalPlanificada());
        dto.setObservacion(item.getObservacion());
        dto.setWarning(item.getWarning());
        dto.setDisplayOrder(item.getDisplayOrder());
        dto.setLotesPlanificados(item.getLotesPlanificados().stream()
                .map(this::toLotePlanificadoDTO)
                .toList());
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
        }
        return dto;
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
