package exotic.app.planta.service.calidad;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import exotic.app.planta.model.calidad.ControlProcesoEjecucion;
import exotic.app.planta.model.calidad.ResultadoControlProceso;
import exotic.app.planta.model.calidad.dto.BatchRecordQualityDTOs;
import exotic.app.planta.model.controles.dto.BloqueoControlDTO;
import exotic.app.planta.model.controles.dto.ResumenControlesBatchRecordDTO;
import exotic.app.planta.model.produccion.batchrecord.*;
import exotic.app.planta.model.users.User;
import exotic.app.planta.repo.calidad.ControlProcesoEjecucionRepo;
import exotic.app.planta.repo.produccion.batchrecord.BatchRecordDesviacionRepo;
import exotic.app.planta.repo.produccion.batchrecord.BatchRecordEtapaRepo;
import exotic.app.planta.repo.produccion.batchrecord.BatchRecordRepo;
import exotic.app.planta.repo.produccion.batchrecord.CicloRevisionBatchRecordRepo;
import exotic.app.planta.service.controles.ControlWorkflowService;
import exotic.app.planta.service.controles.ControlExecutionService;
import exotic.app.planta.model.controles.AmbitoControl;
import exotic.app.planta.service.produccion.BatchRecordService;
import exotic.app.planta.service.produccion.OrdenFabricacionOperacionService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class BatchRecordQualityService {

    private static final EnumSet<EstadoBatchRecordDesviacion> DESVIACIONES_NO_CERRADAS =
            EnumSet.of(
                    EstadoBatchRecordDesviacion.ABIERTA,
                    EstadoBatchRecordDesviacion.EN_INVESTIGACION,
                    EstadoBatchRecordDesviacion.RESUELTA);

    private static final List<EstadoBatchRecord> ESTADOS_ARCHIVO = List.of(
            EstadoBatchRecord.DEVUELTO_PRODUCCION,
            EstadoBatchRecord.EN_CORRECCION,
            EstadoBatchRecord.APROBADO,
            EstadoBatchRecord.RECHAZADO,
            EstadoBatchRecord.CERRADO);

    private final BatchRecordRepo batchRecordRepo;
    private final CicloRevisionBatchRecordRepo cicloRevisionRepo;
    private final BatchRecordEtapaRepo etapaRepo;
    private final BatchRecordDesviacionRepo desviacionRepo;
    private final ControlProcesoEjecucionRepo ejecucionRepo;
    private final BatchRecordService batchRecordService;
    private final ControlWorkflowService controlWorkflowService;
    private final ControlExecutionService controlExecutionService;
    private final OrdenFabricacionOperacionService ordenFabricacionOperacionService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public Page<BatchRecordQualityDTOs.InboxItem> bandeja(
            String scope,
            String search,
            int page,
            int size
    ) {
        String normalizado = normalizar(search);
        Integer ordenProduccionId = parseOrdenProduccionId(normalizado);
        Long ordenFabricacionId = ordenProduccionId == null
                ? null : ordenProduccionId.longValue();
        String alcance = normalizarScope(scope);
        List<EstadoBatchRecord> estados = switch (alcance) {
            case "pendientes" -> List.of(EstadoBatchRecord.PENDIENTE_REVISION);
            case "archivo" -> ESTADOS_ARCHIVO;
            default -> throw new IllegalArgumentException(
                    "El alcance debe ser 'pendientes' o 'archivo'.");
        };
        Sort sort = "pendientes".equals(alcance)
                ? Sort.by(Sort.Direction.ASC, "enviadoRevisionEn")
                : Sort.by(Sort.Direction.DESC, "enviadoRevisionEn");
        return batchRecordRepo.buscarPorEstados(
                        estados,
                        normalizado,
                        ordenProduccionId,
                        ordenFabricacionId,
                        "archivo".equals(alcance),
                        PageRequest.of(
                                Math.max(page, 0),
                                Math.min(Math.max(size, 1), 100),
                                sort))
                .map(this::evaluar);
    }

    /** Compatibilidad con consumidores anteriores: la vista por defecto es pendientes. */
    @Transactional(readOnly = true)
    public Page<BatchRecordQualityDTOs.InboxItem> bandeja(
            String search,
            int page,
            int size
    ) {
        return bandeja("pendientes", search, page, size);
    }

    @Transactional(readOnly = true)
    public BatchRecordQualityDTOs.ReviewDetail detalle(Long batchRecordId) {
        return construirDetalle(requireRecord(batchRecordId));
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

        String alcanceJson = validarYSerializarAlcance(record, request);
        if (request.getDecision() == DecisionCalidadBatchRecord.DEVOLVER_A_PRODUCCION) {
            batchRecordService.proyectarAlcanceDevolucion(
                    record,
                    request.getEtapaIds(),
                    request.getSeccionesDocumentales(),
                    actor);
            if (request.getRequisitoIds() != null && !request.getRequisitoIds().isEmpty()) {
                controlWorkflowService.marcarRepeticionRequerida(
                        record,
                        request.getRequisitoIds(),
                        record.getCicloRevisionActual());
            }
        }

        batchRecordService.registrarDecisionCalidad(
                record,
                actor,
                request.getDecision(),
                request.getMotivo(),
                alcanceJson,
                ipOrigen,
                userAgent);

        if (request.getDecision() == DecisionCalidadBatchRecord.LIBERAR
                && record.getOrdenFabricacion() != null) {
            ordenFabricacionOperacionService.cerrarTrasLiberacionCalidad(
                    record.getOrdenFabricacion().getOrdenFabricacionId(), actor);
        }
        return construirDetalle(requireRecord(batchRecordId));
    }

    public BatchRecordQualityDTOs.ReviewDetail solicitarReapertura(
            Long batchRecordId,
            User actor,
            BatchRecordQualityDTOs.ReaperturaRequest request,
            String ipOrigen,
            String userAgent
    ) {
        if (request == null) {
            throw new IllegalArgumentException("La solicitud de reapertura es obligatoria.");
        }
        batchRecordService.solicitarReaperturaRechazo(
                batchRecordId,
                actor,
                request.getMotivo(),
                request.getEvidencia(),
                request.getAlcance(),
                ipOrigen,
                userAgent);
        return construirDetalle(requireRecord(batchRecordId));
    }

    public BatchRecordQualityDTOs.ReviewDetail aprobarReapertura(
            Long batchRecordId,
            Long solicitudId,
            User actor,
            BatchRecordQualityDTOs.AprobarReaperturaRequest request,
            String ipOrigen,
            String userAgent
    ) {
        if (request == null) {
            throw new IllegalArgumentException("La aprobación de reapertura es obligatoria.");
        }
        batchRecordService.aprobarReaperturaRechazo(
                batchRecordId,
                solicitudId,
                actor,
                request.getMotivo(),
                ipOrigen,
                userAgent);
        return construirDetalle(requireRecord(batchRecordId));
    }

    private BatchRecordQualityDTOs.ReviewDetail construirDetalle(BatchRecord record) {
        return BatchRecordQualityDTOs.ReviewDetail.builder()
                .expediente(batchRecordService.detalle(record.getId()))
                .evaluacion(evaluar(record))
                .controlesProceso(
                        controlWorkflowService.controlesProcesoPorBatchRecord(record.getId()))
                .controlesCalidad(
                        controlWorkflowService.controlesCalidadPorBatchRecord(record.getId()))
                .ejecucionesCalidad(controlExecutionService.evidenciaPorBatchRecord(
                        AmbitoControl.CALIDAD, record.getId()))
                .controles(controles(record))
                .build();
    }

    private BatchRecordQualityDTOs.InboxItem evaluar(BatchRecord record) {
        List<BatchRecordQualityDTOs.EtapaControl> controles = controles(record);
        CicloRevisionBatchRecord cicloActual = record.getCicloRevisionActual() <= 0
                ? null
                : cicloRevisionRepo.findByBatchRecord_IdAndNumero(
                        record.getId(), record.getCicloRevisionActual()).orElse(null);
        ResumenControlesBatchRecordDTO resumenControles =
                controlWorkflowService.resumenPorBatchRecord(record.getId());
        int requeridos = Math.toIntExact(resumenControles.total());
        int conformes = Math.toIntExact(
                resumenControles.conformes() + resumenControles.aceptadosPorDesviacion());
        int pendientes = requeridos - conformes;
        long desviacionesBatchRecord = desviacionRepo.countByBatchRecord_IdAndEstadoIn(
                record.getId(), DESVIACIONES_NO_CERRADAS);
        long desviacionesAbiertas = desviacionesBatchRecord
                + resumenControles.desviacionesAbiertas();

        List<String> bloqueos = new ArrayList<>();
        if (record.getEstado() != EstadoBatchRecord.PENDIENTE_REVISION) {
            bloqueos.add("El expediente no está pendiente de revisión");
        } else if (cicloActual == null
                || cicloActual.getEstado() != EstadoCicloRevisionBatchRecord.EN_REVISION) {
            bloqueos.add("El ciclo de revisión vigente no es verificable");
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
        if (desviacionesAbiertas > 0) {
            bloqueos.add("Hay " + desviacionesAbiertas + " desviación(es) sin cierre");
        }
        List<BloqueoControlDTO> bloqueosControl =
                controlWorkflowService.validarBloqueosLiberacion(record);
        bloqueosControl.stream().map(BloqueoControlDTO::mensaje).forEach(bloqueos::add);

        return BatchRecordQualityDTOs.InboxItem.builder()
                .batchRecordId(record.getId())
                .codigo(record.getCodigo())
                .estado(record.getEstado())
                .ordenProduccionId(record.getOrdenProduccion() == null
                        ? null : record.getOrdenProduccion().getOrdenId())
                .ordenFabricacionId(record.getOrdenFabricacion() == null
                        ? null : record.getOrdenFabricacion().getOrdenFabricacionId())
                .loteId(record.getLoteResultado().getId())
                .lote(record.getLoteResultado().getBatchNumber())
                .estadoCalidadLote(record.getLoteResultado().getEstadoCalidad())
                .productoId(record.getProductoResultado().getProductoId())
                .productoNombre(record.getProductoResultado().getNombre())
                .cantidadObtenida(record.getCantidadObtenida())
                .unidadMedida(record.getUnidadMedida())
                .enviadoRevisionEn(record.getEnviadoRevisionEn())
                .cicloRevisionActual(record.getCicloRevisionActual())
                .estadoCicloRevision(cicloActual == null ? null : cicloActual.getEstado())
                .origenCicloRevision(cicloActual == null ? null : cicloActual.getOrigen())
                .controlesRequeridos(requeridos)
                .controlesConformes(conformes)
                .controlesPendientes(pendientes)
                .desviacionesAbiertas(desviacionesAbiertas)
                .resumenControles(resumenControles)
                .puedeLiberar(bloqueos.isEmpty())
                .bloqueos(bloqueos)
                .bloqueosControl(bloqueosControl)
                .build();
    }

    /** Los controles legados son evidencia de Proceso y siempre se muestran en solo lectura. */
    private List<BatchRecordQualityDTOs.EtapaControl> controles(BatchRecord record) {
        return etapaRepo.findByBatchRecord_IdOrderBySecuenciaAscIdAsc(record.getId())
                .stream()
                .filter(etapa -> etapa.getControlProcesoPlantilla() != null)
                .map(this::toEtapaControl)
                .toList();
    }

    private BatchRecordQualityDTOs.EtapaControl toEtapaControl(BatchRecordEtapa etapa) {
        ControlProcesoEjecucion ultima = ejecucionRepo
                .findTopByBatchRecordEtapa_IdOrderByFechaRegistroDescIdDesc(etapa.getId())
                .orElse(null);
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

    private String validarYSerializarAlcance(
            BatchRecord record,
            BatchRecordQualityDTOs.DecisionRequest request
    ) {
        Set<Long> etapas = request.getEtapaIds() == null
                ? Set.of() : new LinkedHashSet<>(request.getEtapaIds());
        Set<Long> requisitos = request.getRequisitoIds() == null
                ? Set.of() : new LinkedHashSet<>(request.getRequisitoIds());
        Set<String> secciones = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        if (request.getSeccionesDocumentales() != null) {
            request.getSeccionesDocumentales().stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(value -> !value.isBlank())
                    .forEach(secciones::add);
        }
        boolean devolucion = request.getDecision() == DecisionCalidadBatchRecord.DEVOLVER_A_PRODUCCION;
        if (!devolucion && (!etapas.isEmpty() || !requisitos.isEmpty() || !secciones.isEmpty())) {
            throw new IllegalArgumentException(
                    "El alcance selectivo solo corresponde a una devolución a Producción.");
        }
        if (!devolucion) return null;
        if (etapas.isEmpty() && requisitos.isEmpty() && secciones.isEmpty()) {
            throw new IllegalArgumentException(
                    "La devolución debe seleccionar una etapa, control o sección documental.");
        }
        if (etapas.contains(null) || requisitos.contains(null)) {
            throw new IllegalArgumentException(
                    "El alcance de la devolución contiene identificadores vacíos.");
        }
        for (Long etapaId : etapas) {
            BatchRecordEtapa etapa = etapaRepo.findById(etapaId)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "La etapa seleccionada no existe: " + etapaId));
            if (etapa.getBatchRecord() == null
                    || !Objects.equals(etapa.getBatchRecord().getId(), record.getId())) {
                throw new IllegalArgumentException(
                        "La etapa seleccionada pertenece a otro expediente: " + etapaId);
            }
        }
        Map<String, Object> alcance = new TreeMap<>();
        alcance.put("etapaIds", etapas.stream().sorted().toList());
        alcance.put("requisitoIds", requisitos.stream().sorted().toList());
        alcance.put("seccionesDocumentales", secciones.stream().sorted().toList());
        try {
            return objectMapper.writeValueAsString(alcance);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("No se pudo conservar el alcance de la devolución.", exception);
        }
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

    private String normalizarScope(String scope) {
        return scope == null || scope.isBlank()
                ? "pendientes" : scope.trim().toLowerCase(Locale.ROOT);
    }

    private Integer parseOrdenProduccionId(String search) {
        if (search == null || !search.matches("\\d{1,10}")) return null;
        try {
            return Integer.valueOf(search);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
