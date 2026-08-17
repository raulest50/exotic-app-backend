package exotic.app.planta.service.calidad;

import exotic.app.planta.model.calidad.ControlProcesoEjecucion;
import exotic.app.planta.model.calidad.ResultadoControlProceso;
import exotic.app.planta.model.calidad.dto.BatchRecordQualityDTOs;
import exotic.app.planta.model.produccion.batchrecord.BatchRecord;
import exotic.app.planta.model.produccion.batchrecord.BatchRecordEtapa;
import exotic.app.planta.model.produccion.batchrecord.DecisionCalidadBatchRecord;
import exotic.app.planta.model.produccion.batchrecord.EstadoBatchRecord;
import exotic.app.planta.model.produccion.batchrecord.EstadoBatchRecordDesviacion;
import exotic.app.planta.model.users.User;
import exotic.app.planta.repo.calidad.ControlProcesoEjecucionRepo;
import exotic.app.planta.repo.produccion.batchrecord.BatchRecordDesviacionRepo;
import exotic.app.planta.repo.produccion.batchrecord.BatchRecordEtapaRepo;
import exotic.app.planta.repo.produccion.batchrecord.BatchRecordRepo;
import exotic.app.planta.service.produccion.BatchRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class BatchRecordQualityService {

    private static final EnumSet<EstadoBatchRecordDesviacion> DESVIACIONES_NO_CERRADAS =
            EnumSet.of(
                    EstadoBatchRecordDesviacion.ABIERTA,
                    EstadoBatchRecordDesviacion.EN_INVESTIGACION,
                    EstadoBatchRecordDesviacion.RESUELTA);

    private final BatchRecordRepo batchRecordRepo;
    private final BatchRecordEtapaRepo etapaRepo;
    private final BatchRecordDesviacionRepo desviacionRepo;
    private final ControlProcesoEjecucionRepo ejecucionRepo;
    private final BatchRecordService batchRecordService;

    @Transactional(readOnly = true)
    public Page<BatchRecordQualityDTOs.InboxItem> bandeja(
            String search,
            int page,
            int size
    ) {
        String normalizado = normalizar(search);
        Integer ordenId = parseOrdenId(normalizado);
        return batchRecordRepo.buscarPorEstados(
                        List.of(EstadoBatchRecord.PENDIENTE_REVISION),
                        normalizado,
                        ordenId,
                        PageRequest.of(
                                Math.max(page, 0),
                                Math.min(Math.max(size, 1), 100),
                                Sort.by(Sort.Direction.ASC, "enviadoRevisionEn")))
                .map(this::evaluar);
    }

    @Transactional(readOnly = true)
    public BatchRecordQualityDTOs.ReviewDetail detalle(Long batchRecordId) {
        BatchRecord record = requireRecord(batchRecordId);
        return construirDetalle(record);
    }

    public BatchRecordQualityDTOs.ReviewDetail decidir(
            Long batchRecordId,
            User actor,
            BatchRecordQualityDTOs.DecisionRequest request,
            String ipOrigen,
            String userAgent
    ) {
        if (request == null || request.getDecision() == null) {
            throw new IllegalArgumentException("La decisión de Calidad es obligatoria.");
        }
        BatchRecord record = batchRecordRepo.findByIdForUpdate(batchRecordId)
                .orElseThrow(() -> new NoSuchElementException("Expediente digital no encontrado."));
        BatchRecordQualityDTOs.InboxItem evaluacion = evaluar(record);
        if (request.getDecision() == DecisionCalidadBatchRecord.LIBERAR
                && !evaluacion.isPuedeLiberar()) {
            throw new IllegalStateException(
                    "El lote todavía no puede liberarse: "
                            + String.join("; ", evaluacion.getBloqueos()));
        }

        batchRecordService.registrarDecisionCalidad(
                record,
                actor,
                request.getDecision(),
                request.getMotivo(),
                ipOrigen,
                userAgent);
        return construirDetalle(record);
    }

    private BatchRecordQualityDTOs.ReviewDetail construirDetalle(BatchRecord record) {
        return BatchRecordQualityDTOs.ReviewDetail.builder()
                .expediente(batchRecordService.detalle(record.getId()))
                .evaluacion(evaluar(record))
                .controles(controles(record))
                .build();
    }

    private BatchRecordQualityDTOs.InboxItem evaluar(BatchRecord record) {
        List<BatchRecordQualityDTOs.EtapaControl> controles = controles(record);
        int requeridos = controles.size();
        int conformes = (int) controles.stream()
                .filter(control -> control.getUltimoResultado() == ResultadoControlProceso.CONFORME)
                .count();
        int pendientes = requeridos - conformes;
        long desviacionesAbiertas = desviacionRepo.countByBatchRecord_IdAndEstadoIn(
                record.getId(), DESVIACIONES_NO_CERRADAS);

        List<String> bloqueos = new ArrayList<>();
        if (record.getEstado() != EstadoBatchRecord.PENDIENTE_REVISION) {
            bloqueos.add("El expediente no está pendiente de revisión");
        }
        if (record.getCantidadObtenida() == null) {
            bloqueos.add("Falta la cantidad obtenida");
        }
        if (record.getLoteResultado().getProductionDate() == null) {
            bloqueos.add("Falta la fecha de fabricación del lote");
        }
        if (record.getLoteResultado().getExpirationDate() == null) {
            bloqueos.add("Falta la fecha de vencimiento del lote");
        } else if (record.getLoteResultado().getProductionDate() != null
                && !record.getLoteResultado().getExpirationDate().isAfter(
                record.getLoteResultado().getProductionDate())) {
            bloqueos.add("La fecha de vencimiento no es posterior a la fabricación");
        }
        if (pendientes > 0) {
            bloqueos.add("Hay " + pendientes + " control(es) pendientes o no conformes");
        }
        if (desviacionesAbiertas > 0) {
            bloqueos.add("Hay " + desviacionesAbiertas + " desviación(es) sin cierre");
        }

        return BatchRecordQualityDTOs.InboxItem.builder()
                .batchRecordId(record.getId())
                .codigo(record.getCodigo())
                .estado(record.getEstado())
                .ordenProduccionId(record.getOrdenProduccion() == null
                        ? null : record.getOrdenProduccion().getOrdenId())
                .loteId(record.getLoteResultado().getId())
                .lote(record.getLoteResultado().getBatchNumber())
                .estadoCalidadLote(record.getLoteResultado().getEstadoCalidad())
                .productoId(record.getProductoResultado().getProductoId())
                .productoNombre(record.getProductoResultado().getNombre())
                .cantidadObtenida(record.getCantidadObtenida())
                .unidadMedida(record.getUnidadMedida())
                .enviadoRevisionEn(record.getEnviadoRevisionEn())
                .controlesRequeridos(requeridos)
                .controlesConformes(conformes)
                .controlesPendientes(pendientes)
                .desviacionesAbiertas(desviacionesAbiertas)
                .puedeLiberar(bloqueos.isEmpty())
                .bloqueos(bloqueos)
                .build();
    }

    private List<BatchRecordQualityDTOs.EtapaControl> controles(BatchRecord record) {
        return etapaRepo.findByBatchRecord_IdOrderBySecuenciaAscIdAsc(record.getId())
                .stream()
                .filter(etapa -> etapa.getControlProcesoPlantilla() != null)
                .map(etapa -> toEtapaControl(etapa, record.getEnviadoRevisionEn()))
                .toList();
    }

    private BatchRecordQualityDTOs.EtapaControl toEtapaControl(
            BatchRecordEtapa etapa,
            java.time.LocalDateTime enviadoRevisionEn
    ) {
        ControlProcesoEjecucion ultima = ejecucionRepo
                .findTopByBatchRecordEtapa_IdOrderByFechaRegistroDescIdDesc(etapa.getId())
                .orElse(null);
        if (ultima != null && enviadoRevisionEn != null
                && ultima.getFechaRegistro().isBefore(enviadoRevisionEn)) {
            ultima = null;
        }
        return BatchRecordQualityDTOs.EtapaControl.builder()
                .etapaId(etapa.getId())
                .secuencia(etapa.getSecuencia())
                .areaOperativaId(etapa.getAreaOperativa().getAreaId())
                .areaOperativaNombre(etapa.getAreaOperativa().getNombre())
                .etapaNombre(etapa.getNombre())
                .plantillaId(etapa.getControlProcesoPlantilla().getId())
                .plantillaVersion(etapa.getControlProcesoPlantilla().getVersion())
                .ultimaEjecucionId(ultima == null ? null : ultima.getId())
                .ultimoResultado(ultima == null ? null : ultima.getResultado())
                .ultimaEjecucionEn(ultima == null ? null : ultima.getFechaRegistro())
                .pendiente(ultima == null
                        || ultima.getResultado() != ResultadoControlProceso.CONFORME)
                .build();
    }

    private BatchRecord requireRecord(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("El identificador del expediente es obligatorio.");
        }
        return batchRecordRepo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Expediente digital no encontrado."));
    }

    private String normalizar(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }

    private Integer parseOrdenId(String search) {
        if (search == null || !search.matches("\\d{1,10}")) return null;
        try {
            return Integer.valueOf(search);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
