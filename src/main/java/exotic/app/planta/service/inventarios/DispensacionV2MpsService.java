package exotic.app.planta.service.inventarios;

import exotic.app.planta.config.initializers.AreaOperativaInitializer;
import exotic.app.planta.model.organizacion.AreaOperativa;
import exotic.app.planta.model.produccion.EstadoMpsSemanal;
import exotic.app.planta.model.produccion.EstadoMpsSemanalItem;
import exotic.app.planta.model.produccion.EstadoMpsSemanalLotePlanificado;
import exotic.app.planta.model.produccion.dto.MpsSemanalDiaDTO;
import exotic.app.planta.model.produccion.dto.MpsSemanalDraftDTO;
import exotic.app.planta.model.produccion.dto.MpsSemanalItemDTO;
import exotic.app.planta.model.produccion.dto.MpsSemanalLotePlanificadoDTO;
import exotic.app.planta.repo.produccion.SeguimientoOrdenAreaRepo;
import exotic.app.planta.repo.producto.procesos.AreaProduccionRepo;
import exotic.app.planta.resource.produccion.exceptions.MpsSemanalNotFoundException;
import exotic.app.planta.service.produccion.MasterProductionScheduleDraftService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DispensacionV2MpsService {

    private final MasterProductionScheduleDraftService masterProductionScheduleDraftService;
    private final SeguimientoOrdenAreaRepo seguimientoOrdenAreaRepo;
    private final AreaProduccionRepo areaProduccionRepo;

    @Transactional(readOnly = true)
    public MpsSemanalDraftDTO getMpsSemanalFiltradoPorArea(LocalDate weekStartDate, int areaId) {
        validateArea(areaId);

        MpsSemanalDraftDTO mps;
        try {
            mps = masterProductionScheduleDraftService.getByWeekStartDate(weekStartDate);
        } catch (MpsSemanalNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }

        if (mps.getEstado() != EstadoMpsSemanal.APROBADO && mps.getEstado() != EstadoMpsSemanal.CERRADO) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "El MPS de la semana solicitada aun no esta aprobado para consulta operativa."
            );
        }

        if (mps.getTotalOdpsGeneradas() <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "El MPS de la semana solicitada aun no tiene ODPs generadas."
            );
        }

        List<SeguimientoOrdenAreaRepo.MpsIntervencionAreaProjection> intervenciones =
                seguimientoOrdenAreaRepo.findMpsIntervencionesByMpsIdAndAreaId(
                        mps.getMpsId(),
                        areaId,
                        EstadoMpsSemanalItem.CANCELADO,
                        EstadoMpsSemanalLotePlanificado.CANCELADO
                );

        Set<Long> loteIds = intervenciones.stream()
                .map(SeguimientoOrdenAreaRepo.MpsIntervencionAreaProjection::getMpsLotePlanificadoId)
                .collect(Collectors.toSet());

        return filterMpsByLotes(mps, loteIds);
    }

    private void validateArea(int areaId) {
        if (areaId == AreaOperativaInitializer.ALMACEN_GENERAL_ID) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Almacen General no es un area destino valida para Dispensacion v2.");
        }

        AreaOperativa area = areaProduccionRepo.findById(areaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Area operativa no encontrada."));

        if (area.getAreaId() == AreaOperativaInitializer.ALMACEN_GENERAL_ID) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Almacen General no es un area destino valida para Dispensacion v2.");
        }
    }

    private MpsSemanalDraftDTO filterMpsByLotes(MpsSemanalDraftDTO source, Set<Long> loteIds) {
        MpsSemanalDraftDTO filtered = copyMpsHeader(source);
        List<MpsSemanalDiaDTO> filteredDias = source.getDias().stream()
                .map(dia -> filterDiaByLotes(dia, loteIds))
                .toList();

        long totalItems = filteredDias.stream()
                .mapToLong(dia -> dia.getItems().size())
                .sum();
        long totalLotes = filteredDias.stream()
                .flatMap(dia -> dia.getItems().stream())
                .mapToLong(item -> item.getLotesPlanificados().size())
                .sum();
        long totalOdps = filteredDias.stream()
                .flatMap(dia -> dia.getItems().stream())
                .flatMap(item -> item.getLotesPlanificados().stream())
                .filter(lote -> lote.getEstado() == EstadoMpsSemanalLotePlanificado.ODP_GENERADA)
                .count();

        filtered.setDias(filteredDias);
        filtered.setTotalItems(totalItems);
        filtered.setTotalLotesPlanificados(totalLotes);
        filtered.setTotalOdpsGeneradas(totalOdps);
        return filtered;
    }

    private MpsSemanalDiaDTO filterDiaByLotes(MpsSemanalDiaDTO source, Set<Long> loteIds) {
        MpsSemanalDiaDTO filtered = new MpsSemanalDiaDTO();
        filtered.setId(source.getId());
        filtered.setFecha(source.getFecha());
        filtered.setDayIndex(source.getDayIndex());
        filtered.setDisplayOrder(source.getDisplayOrder());
        filtered.setItems(source.getItems().stream()
                .map(item -> filterItemByLotes(item, loteIds))
                .filter(item -> !item.getLotesPlanificados().isEmpty())
                .toList());
        return filtered;
    }

    private MpsSemanalItemDTO filterItemByLotes(MpsSemanalItemDTO source, Set<Long> loteIds) {
        List<MpsSemanalLotePlanificadoDTO> lotes = source.getLotesPlanificados().stream()
                .filter(lote -> lote.getId() != null && loteIds.contains(lote.getId()))
                .map(this::copyLote)
                .toList();

        MpsSemanalItemDTO filtered = copyItemBase(source);
        filtered.setLotesPlanificados(lotes);
        filtered.setNumeroLotes(lotes.size());
        filtered.setCantidadTotal(lotes.stream()
                .mapToDouble(MpsSemanalLotePlanificadoDTO::getCantidadPlanificada)
                .sum());

        int lotesCancelados = (int) lotes.stream()
                .filter(lote -> lote.getEstado() == EstadoMpsSemanalLotePlanificado.CANCELADO)
                .count();
        int lotesActivos = lotes.size() - lotesCancelados;
        int ordenesIniciadas = (int) lotes.stream()
                .filter(MpsSemanalLotePlanificadoDTO::isOrdenIniciada)
                .count();
        int ordenesCancelables = (int) lotes.stream()
                .filter(MpsSemanalLotePlanificadoDTO::isOrdenCancelable)
                .count();

        filtered.setLotesCancelados(lotesCancelados);
        filtered.setLotesActivos(lotesActivos);
        filtered.setOrdenesIniciadas(ordenesIniciadas);
        filtered.setOrdenesCancelables(ordenesCancelables);
        filtered.setLotesCancelables(ordenesCancelables);
        filtered.setLotesNoCancelables(lotesActivos - ordenesCancelables);
        return filtered;
    }

    private MpsSemanalDraftDTO copyMpsHeader(MpsSemanalDraftDTO source) {
        MpsSemanalDraftDTO target = new MpsSemanalDraftDTO();
        target.setMpsId(source.getMpsId());
        target.setEstado(source.getEstado());
        target.setFechaCreacion(source.getFechaCreacion());
        target.setFechaActualizacion(source.getFechaActualizacion());
        target.setFechaAprobacion(source.getFechaAprobacion());
        target.setAprobadoPorUsername(source.getAprobadoPorUsername());
        target.setFechaGeneracionOdps(source.getFechaGeneracionOdps());
        target.setGeneradoPorUsername(source.getGeneradoPorUsername());
        target.setSemanaMpsId(source.getSemanaMpsId());
        target.setSemanaMpsCodigo(source.getSemanaMpsCodigo());
        target.setAnioSemana(source.getAnioSemana());
        target.setNumeroSemana(source.getNumeroSemana());
        target.setStandard(source.getStandard());
        target.setRevisionNumero(source.getRevisionNumero());
        target.setWeekStartDate(source.getWeekStartDate());
        target.setWeekEndDate(source.getWeekEndDate());
        return target;
    }

    private MpsSemanalItemDTO copyItemBase(MpsSemanalItemDTO source) {
        MpsSemanalItemDTO target = new MpsSemanalItemDTO();
        target.setId(source.getId());
        target.setTerminadoId(source.getTerminadoId());
        target.setTerminadoNombre(source.getTerminadoNombre());
        target.setCategoriaId(source.getCategoriaId());
        target.setCategoriaNombre(source.getCategoriaNombre());
        target.setLoteSize(source.getLoteSize());
        target.setTiempoDiasFabricacion(source.getTiempoDiasFabricacion());
        target.setEstadoItem(source.getEstadoItem());
        target.setFechaLanzamiento(source.getFechaLanzamiento());
        target.setFechaFinalPlanificada(source.getFechaFinalPlanificada());
        target.setObservacion(source.getObservacion());
        target.setWarning(source.getWarning());
        target.setDisplayOrder(source.getDisplayOrder());
        target.setEditable(source.isEditable());
        target.setBlockedReason(source.getBlockedReason());
        return target;
    }

    private MpsSemanalLotePlanificadoDTO copyLote(MpsSemanalLotePlanificadoDTO source) {
        MpsSemanalLotePlanificadoDTO target = new MpsSemanalLotePlanificadoDTO();
        target.setId(source.getId());
        target.setLoteOrdinal(source.getLoteOrdinal());
        target.setCantidadPlanificada(source.getCantidadPlanificada());
        target.setEstado(source.getEstado());
        target.setOrdenProduccionId(source.getOrdenProduccionId());
        target.setLoteAsignado(source.getLoteAsignado());
        target.setOrdenIniciada(source.isOrdenIniciada());
        target.setOrdenCancelable(source.isOrdenCancelable());
        return target;
    }
}
