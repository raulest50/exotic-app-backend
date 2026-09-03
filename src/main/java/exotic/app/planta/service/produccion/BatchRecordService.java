package exotic.app.planta.service.produccion;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import exotic.app.planta.model.calidad.*;
import exotic.app.planta.model.controles.PuntoExigenciaControl;
import exotic.app.planta.model.controles.ControlRequerido;
import exotic.app.planta.model.controles.dto.BloqueoControlDTO;
import exotic.app.planta.model.inventarios.EstadoCalidadLote;
import exotic.app.planta.model.inventarios.Lote;
import exotic.app.planta.model.inventarios.Movimiento;
import exotic.app.planta.model.inventarios.TransaccionAlmacen;
import exotic.app.planta.model.produccion.*;
import exotic.app.planta.model.produccion.batchrecord.*;
import exotic.app.planta.model.produccion.dto.BatchRecordDTOs;
import exotic.app.planta.model.produccion.fabricacion.OrdenFabricacion;
import exotic.app.planta.model.produccion.fabricacion.OrdenFabricacionOperacion;
import exotic.app.planta.model.produccion.fabricacion.OrdenFabricacionOperacionEvento;
import exotic.app.planta.model.producto.manufacturing.procesos.ProcesoProduccionDocumentoVersion;
import exotic.app.planta.model.users.User;
import exotic.app.planta.model.users.firma.FirmaVisualUsuarioVersion;
import exotic.app.planta.repo.calidad.ControlProcesoEjecucionRepo;
import exotic.app.planta.repo.calidad.ControlProcesoPlantillaRepo;
import exotic.app.planta.repo.inventarios.LoteRepo;
import exotic.app.planta.repo.inventarios.TransaccionAlmacenHeaderRepo;
import exotic.app.planta.repo.produccion.SeguimientoOrdenAreaRepo;
import exotic.app.planta.repo.produccion.batchrecord.*;
import exotic.app.planta.repo.usuarios.FirmaVisualUsuarioVersionRepo;
import exotic.app.planta.service.controles.ControlWorkflowService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class BatchRecordService {

    public static final String ESQUEMA_VERSION = "batch-record-v4";
    public static final String PLANTILLA_PDF_VERSION = "batch-record-pdf-v4";
    private static final int ALMACEN_GENERAL_AREA_ID = -1;
    private static final EnumSet<TransaccionAlmacen.TipoEntidadCausante> TIPOS_DISPENSACION_DOCUMENTAL =
            EnumSet.of(
                    TransaccionAlmacen.TipoEntidadCausante.OD,
                    TransaccionAlmacen.TipoEntidadCausante.OD_RA,
                    TransaccionAlmacen.TipoEntidadCausante.OD_OF);

    private final BatchRecordRepo batchRecordRepo;
    private final BatchRecordEtapaRepo etapaRepo;
    private final BatchRecordRevisionRepo revisionRepo;
    private final BatchRecordFirmaRepo firmaRepo;
    private final BatchRecordCorreccionRepo correccionRepo;
    private final BatchRecordDecisionCalidadRepo decisionRepo;
    private final CicloRevisionBatchRecordRepo cicloRevisionRepo;
    private final SolicitudReaperturaRechazoRepo solicitudReaperturaRepo;
    private final BatchRecordSeccionCorreccionRepo seccionCorreccionRepo;
    private final BatchRecordConsumoRepo consumoRepo;
    private final BatchRecordDesviacionRepo desviacionRepo;
    private final SeguimientoOrdenAreaRepo seguimientoRepo;
    private final ControlProcesoPlantillaRepo plantillaRepo;
    private final ControlProcesoEjecucionRepo ejecucionRepo;
    private final FirmaVisualUsuarioVersionRepo firmaVisualRepo;
    private final TransaccionAlmacenHeaderRepo transaccionRepo;
    private final LoteRepo loteRepo;
    private final ObjectMapper objectMapper;
    private final Clock applicationClock;
    private final MaterialRequirementSnapshotService materialRequirementSnapshotService;
    private final ControlWorkflowService controlWorkflowService;

    public BatchRecord crearParaOrdenProduccion(
            OrdenProduccion orden,
            Lote lote,
            User creador
    ) {
        validarCreacionBase(orden != null ? orden.getProducto() : null,
                orden != null ? orden.getManufacturingVersion() : null, lote, creador);
        if (batchRecordRepo.findByOrdenProduccion_OrdenId(orden.getOrdenId()).isPresent()) {
            throw new IllegalStateException("La orden de producción ya tiene expediente digital.");
        }

        BatchRecord record = new BatchRecord();
        record.setCodigo("BR-OP-" + orden.getOrdenId());
        record.setOrdenProduccion(orden);
        record.setLoteResultado(lote);
        record.setProductoResultado(orden.getProducto());
        record.setManufacturingVersion(orden.getManufacturingVersion());
        record.setCantidadPlanificada(BigDecimal.valueOf(orden.getCantidadProducir()));
        record.setUnidadMedida(unidadObligatoria(orden.getProducto().getTipoUnidades()));
        record.setCreadoPor(creador);
        record.setEstado(EstadoBatchRecord.BORRADOR);
        String requerimientosJson = materialRequirementSnapshotService.construirJson(
                orden.getProducto(), orden.getManufacturingVersion(),
                BigDecimal.valueOf(orden.getCantidadProducir()));
        record.setRequerimientosMaterialesJson(requerimientosJson);
        if (!materialRequirementSnapshotService.requiereRegistroDispensacion(
                requerimientosJson)) {
            orden.setEstadoDispensacionMateriales(
                    EstadoDispensacionMateriales.LIBERADA_SIN_DISPENSACION);
        }
        batchRecordRepo.saveAndFlush(record);

        crearEtapasDesdeSeguimiento(record, orden);
        controlWorkflowService.materializarRequisitos(record);
        return record;
    }

    public BatchRecord crearParaOrdenFabricacion(
            OrdenFabricacion orden,
            Lote lote,
            User creador
    ) {
        validarCreacionBase(orden != null ? orden.getSemiTerminado() : null,
                orden != null ? orden.getManufacturingVersion() : null, lote, creador);
        if (batchRecordRepo.findByOrdenFabricacion_OrdenFabricacionId(
                orden.getOrdenFabricacionId()).isPresent()) {
            throw new IllegalStateException("La orden de fabricación ya tiene expediente digital.");
        }

        BatchRecord record = new BatchRecord();
        record.setCodigo("BR-OF-" + orden.getOrdenFabricacionId());
        record.setOrdenFabricacion(orden);
        record.setLoteResultado(lote);
        record.setProductoResultado(orden.getSemiTerminado());
        record.setManufacturingVersion(orden.getManufacturingVersion());
        record.setCantidadPlanificada(orden.getCantidadPlanificada());
        record.setUnidadMedida(unidadObligatoria(orden.getUnidadMedida()));
        record.setCreadoPor(creador);
        record.setEstado(EstadoBatchRecord.BORRADOR);
        String requerimientosJson = materialRequirementSnapshotService.construirJson(
                orden.getSemiTerminado(), orden.getManufacturingVersion(),
                orden.getCantidadPlanificada());
        record.setRequerimientosMaterialesJson(requerimientosJson);
        if (!materialRequirementSnapshotService.requiereRegistroDispensacion(
                requerimientosJson)) {
            orden.setEstadoDispensacionMateriales(
                    EstadoDispensacionMateriales.LIBERADA_SIN_DISPENSACION);
        }
        batchRecordRepo.saveAndFlush(record);
        return record;
    }

    /** Materializa requisitos una vez que las etapas de una OF ya fueron creadas. */
    public void materializarRequisitos(OrdenFabricacion orden) {
        if (orden == null || orden.getOrdenFabricacionId() == null) {
            throw new IllegalArgumentException("La orden de fabricación es obligatoria.");
        }
        BatchRecord record = batchRecordRepo.findByOrdenFabricacion_OrdenFabricacionId(
                        orden.getOrdenFabricacionId())
                .orElseThrow(() -> new IllegalStateException(
                        "La orden de fabricación no tiene expediente digital."));
        controlWorkflowService.materializarRequisitos(record);
    }

    /** Sincroniza la evidencia operativa propia de una OF. */
    public void sincronizarEventoFabricacion(OrdenFabricacionOperacionEvento evento) {
        if (evento == null || evento.getId() == null || evento.getOperacion() == null
                || evento.getOperacion().getId() == null) return;
        BatchRecordEtapa etapa = etapaRepo
                .findByOrdenFabricacionOperacion_Id(evento.getOperacion().getId())
                .orElse(null);
        if (etapa == null) return;

        BatchRecord record = etapa.getBatchRecord();
        EstadoSeguimientoOrdenArea destino = EstadoSeguimientoOrdenArea.fromCode(
                evento.getEstadoDestino());
        if (destino == EstadoSeguimientoOrdenArea.COMPLETADO) {
            validarGateControl(record, etapa, PuntoExigenciaControl.CIERRE_ETAPA);
        }
        aplicarEstadoEtapaFabricacion(etapa, record, evento, destino);
        etapaRepo.saveAndFlush(etapa);
        batchRecordRepo.save(record);

        if (evento.getTipoEvento() == TipoEventoSeguimiento.CORRECCION_ADMINISTRATIVA) {
            registrarCorreccionFabricacion(record, etapa, evento);
        } else if (destino == EstadoSeguimientoOrdenArea.COMPLETADO
                && evento.getActorTipo() == ActorTipoEventoSeguimiento.USER
                && evento.getUsuario() != null) {
            registrarFirmaEtapaFabricacion(record, etapa, evento);
        }
    }

    public void prepararRevisionCalidad(
            OrdenFabricacion orden,
            BigDecimal cantidadObtenida
    ) {
        if (orden == null || orden.getOrdenFabricacionId() == null
                || cantidadObtenida == null || cantidadObtenida.signum() <= 0) {
            throw new IllegalArgumentException("La orden de fabricacion es obligatoria.");
        }
        BatchRecord encontrado = batchRecordRepo.findByOrdenFabricacion_OrdenFabricacionId(
                        orden.getOrdenFabricacionId())
                .orElseThrow(() -> new IllegalStateException(
                        "La orden de fabricacion no tiene expediente digital."));
        BatchRecord record = batchRecordRepo.findByIdForUpdate(encontrado.getId())
                .orElseThrow(() -> new NoSuchElementException("Expediente digital no encontrado."));
        if (record.getEstado() != EstadoBatchRecord.EN_EJECUCION
                && record.getEstado() != EstadoBatchRecord.LISTO_PARA_REVISION
                && record.getEstado() != EstadoBatchRecord.DEVUELTO_PRODUCCION
                && record.getEstado() != EstadoBatchRecord.EN_CORRECCION) {
            throw new IllegalStateException(
                    "El expediente de fabricación no admite preparar un nuevo envío.");
        }
        validarEtapasTerminadas(record);
        sincronizarConsumos(record);
        record.setCantidadObtenida(cantidadObtenida);
        record.setEstado(record.getCicloRevisionActual() == 0
                ? EstadoBatchRecord.LISTO_PARA_REVISION
                : EstadoBatchRecord.EN_CORRECCION);
        if (record.getIniciadoEn() == null) {
            record.setIniciadoEn(orden.getFechaInicio() != null
                    ? orden.getFechaInicio()
                    : LocalDateTime.now(applicationClock));
        }
        batchRecordRepo.saveAndFlush(record);
    }

    public void sincronizarConsumosOrdenProduccion(Integer ordenProduccionId) {
        if (ordenProduccionId == null) return;
        batchRecordRepo.findByOrdenProduccion_OrdenId(ordenProduccionId)
                .ifPresent(this::sincronizarConsumos);
    }

    public void sincronizarConsumosOrdenFabricacion(Long ordenFabricacionId) {
        if (ordenFabricacionId == null) return;
        batchRecordRepo.findByOrdenFabricacion_OrdenFabricacionId(ordenFabricacionId)
                .ifPresent(this::sincronizarConsumos);
    }

    @Transactional(readOnly = true)
    public boolean estaDevueltoAProduccion(OrdenFabricacion orden) {
        if (orden == null || orden.getOrdenFabricacionId() == null) return false;
        return batchRecordRepo.findByOrdenFabricacion_OrdenFabricacionId(
                        orden.getOrdenFabricacionId())
                .map(record -> record.getEstado() == EstadoBatchRecord.DEVUELTO_PRODUCCION
                        || record.getEstado() == EstadoBatchRecord.EN_CORRECCION)
                .orElse(false);
    }

    public void validarCorreccionPermitida(SeguimientoOrdenArea seguimiento) {
        if (seguimiento == null || seguimiento.getOrdenProduccion() == null) {
            return;
        }
        batchRecordRepo.findByOrdenProduccion_OrdenId(
                        seguimiento.getOrdenProduccion().getOrdenId())
                .ifPresent(record -> validarCorreccionPermitida(
                        record,
                        etapaRepo.findBySeguimientoOrdenArea_Id(seguimiento.getId()).orElse(null)));
    }

    /** Impide que una devolución selectiva habilite operaciones de OF no elegidas. */
    public void validarCorreccionPermitida(OrdenFabricacionOperacion operacion) {
        if (operacion == null || operacion.getOrdenFabricacion() == null
                || operacion.getOrdenFabricacion().getOrdenFabricacionId() == null) {
            return;
        }
        batchRecordRepo.findByOrdenFabricacion_OrdenFabricacionId(
                        operacion.getOrdenFabricacion().getOrdenFabricacionId())
                .ifPresent(record -> validarCorreccionPermitida(
                        record,
                        etapaRepo.findByOrdenFabricacionOperacion_Id(operacion.getId()).orElse(null)));
    }

    /**
     * Proyecta el alcance selectivo decidido por Calidad sobre el expediente.
     * La decisión y esta proyección participan en la misma transacción externa.
     */
    public void proyectarAlcanceDevolucion(
            BatchRecord referencia,
            Collection<Long> etapaIds,
            Collection<String> seccionesDocumentales,
            User actor
    ) {
        validarIdentidadAuditable(actor);
        if (referencia == null || referencia.getId() == null) {
            throw new IllegalArgumentException("El expediente de la devolución es obligatorio.");
        }
        BatchRecord record = batchRecordRepo.findByIdForUpdate(referencia.getId())
                .orElseThrow(() -> new NoSuchElementException("Expediente digital no encontrado."));
        if (record.getEstado() != EstadoBatchRecord.PENDIENTE_REVISION
                || record.getCicloRevisionActual() <= 0) {
            throw new IllegalStateException(
                    "Solo el ciclo vigente en revisión admite proyectar una devolución.");
        }
        CicloRevisionBatchRecord ciclo = cicloRevisionRepo
                .findByBatchRecord_IdAndNumero(record.getId(), record.getCicloRevisionActual())
                .orElseThrow(() -> new IllegalStateException(
                        "El expediente no tiene un ciclo de revisión vigente."));
        if (ciclo.getEstado() != EstadoCicloRevisionBatchRecord.EN_REVISION) {
            throw new IllegalStateException("El ciclo de revisión ya recibió una decisión.");
        }

        Set<Long> etapasUnicas = new LinkedHashSet<>(
                etapaIds == null ? List.of() : etapaIds);
        for (Long etapaId : etapasUnicas) {
            if (etapaId == null) {
                throw new IllegalArgumentException("El alcance contiene una etapa sin identificador.");
            }
            BatchRecordEtapa etapa = etapaRepo.findByIdAndBatchRecord_Id(etapaId, record.getId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "La etapa seleccionada no pertenece al expediente: " + etapaId));
            etapa.setCicloCorreccionHabilitado(record.getCicloRevisionActual());
            etapa.setEstado(EstadoBatchRecordEtapa.EN_CORRECCION);
            etapaRepo.save(etapa);
        }

        Map<String, String> seccionesUnicas = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        if (seccionesDocumentales != null) {
            for (String seccion : seccionesDocumentales) {
                String normalizada = normalizarTexto(seccion);
                if (normalizada != null) seccionesUnicas.putIfAbsent(normalizada, normalizada);
            }
        }
        LocalDateTime ahora = LocalDateTime.now(applicationClock);
        for (String seccion : seccionesUnicas.values()) {
            BatchRecordSeccionCorreccion pendiente = new BatchRecordSeccionCorreccion();
            pendiente.setBatchRecord(record);
            pendiente.setCicloRevisionNumero(record.getCicloRevisionActual());
            pendiente.setSeccion(seccion);
            pendiente.setEstado(EstadoSeccionCorreccionBatchRecord.PENDIENTE);
            pendiente.setSolicitadaEn(ahora);
            pendiente.setSolicitadaPor(actor);
            seccionCorreccionRepo.save(pendiente);
        }
        etapaRepo.flush();
        seccionCorreccionRepo.flush();
    }

    public BatchRecordSeccionCorreccion atenderSeccionCorreccion(
            Long batchRecordId,
            Long seccionId,
            User actor,
            String justificacion
    ) {
        validarIdentidadAuditable(actor);
        BatchRecord record = batchRecordRepo.findByIdForUpdate(batchRecordId)
                .orElseThrow(() -> new NoSuchElementException("Expediente digital no encontrado."));
        if (record.getEstado() != EstadoBatchRecord.DEVUELTO_PRODUCCION
                && record.getEstado() != EstadoBatchRecord.EN_CORRECCION) {
            throw new IllegalStateException(
                    "La sección solo puede atenderse durante la corrección de una devolución.");
        }
        BatchRecordSeccionCorreccion seccion = seccionCorreccionRepo.findByIdForUpdate(seccionId)
                .orElseThrow(() -> new NoSuchElementException(
                        "Sección documental devuelta no encontrada."));
        if (seccion.getBatchRecord() == null
                || !Objects.equals(seccion.getBatchRecord().getId(), record.getId())
                || seccion.getCicloRevisionNumero() != record.getCicloRevisionActual()) {
            throw new IllegalArgumentException(
                    "La sección no pertenece a la devolución vigente del expediente.");
        }
        if (seccion.getEstado() != EstadoSeccionCorreccionBatchRecord.PENDIENTE) {
            throw new IllegalStateException("La sección documental ya fue atendida.");
        }
        seccion.setEstado(EstadoSeccionCorreccionBatchRecord.ATENDIDA);
        seccion.setAtendidaEn(LocalDateTime.now(applicationClock));
        seccion.setAtendidaPor(actor);
        seccion.setJustificacion(textoObligatorio(
                justificacion, "La justificación de la subsanación es obligatoria."));
        return seccionCorreccionRepo.saveAndFlush(seccion);
    }

    /** Sincroniza la proyección actual y conserva cada evento/corrección como evidencia. */
    public void sincronizarEventoSeguimiento(SeguimientoOrdenAreaEvento evento) {
        if (evento == null || evento.getId() == null
                || evento.getSeguimientoOrdenArea() == null
                || evento.getSeguimientoOrdenArea().getId() == null) {
            return;
        }
        BatchRecordEtapa etapa = etapaRepo.findBySeguimientoOrdenArea_Id(
                        evento.getSeguimientoOrdenArea().getId())
                .orElse(null);
        if (etapa == null) {
            return;
        }

        BatchRecord record = etapa.getBatchRecord();
        EstadoSeguimientoOrdenArea destino = EstadoSeguimientoOrdenArea.fromCode(
                evento.getEstadoDestino());
        if (destino == EstadoSeguimientoOrdenArea.COMPLETADO) {
            validarGateControl(record, etapa, PuntoExigenciaControl.CIERRE_ETAPA);
        }
        aplicarEstadoEtapa(etapa, record, evento, destino);
        etapaRepo.saveAndFlush(etapa);
        batchRecordRepo.save(record);

        if (evento.getTipoEvento() == TipoEventoSeguimiento.CORRECCION_ADMINISTRATIVA) {
            registrarCorreccion(record, etapa, evento);
        } else if (destino == EstadoSeguimientoOrdenArea.COMPLETADO
                && evento.getActorTipo() == ActorTipoEventoSeguimiento.USER
                && evento.getUsuario() != null) {
            registrarFirmaEtapa(record, etapa, evento);
        }
    }

    /**
     * Deja el expediente terminado a disposición de la revisión productiva.
     * No lo envía ni firma: esa acción es deliberadamente explícita.
     */
    public void prepararRevisionCalidad(OrdenProduccion orden, BigDecimal cantidadObtenida) {
        if (orden == null || cantidadObtenida == null || cantidadObtenida.signum() <= 0) {
            throw new IllegalArgumentException("La orden y la cantidad obtenida son obligatorias.");
        }
        BatchRecord encontrado = batchRecordRepo.findByOrdenProduccion_OrdenId(orden.getOrdenId())
                .orElseThrow(() -> new IllegalStateException(
                        "La orden no tiene expediente digital para preparar la revisión."));
        BatchRecord record = batchRecordRepo.findByIdForUpdate(encontrado.getId())
                .orElseThrow(() -> new NoSuchElementException("Expediente digital no encontrado."));
        if (record.getEstado() != EstadoBatchRecord.EN_EJECUCION
                && record.getEstado() != EstadoBatchRecord.LISTO_PARA_REVISION
                && record.getEstado() != EstadoBatchRecord.DEVUELTO_PRODUCCION
                && record.getEstado() != EstadoBatchRecord.EN_CORRECCION) {
            throw new IllegalStateException(
                    "El expediente no admite preparar un nuevo envío desde su estado actual.");
        }
        validarEtapasTerminadas(record);
        sincronizarConsumos(record);
        record.setCantidadObtenida(cantidadObtenida);
        record.setEstado(record.getCicloRevisionActual() == 0
                ? EstadoBatchRecord.LISTO_PARA_REVISION
                : EstadoBatchRecord.EN_CORRECCION);
        if (record.getIniciadoEn() == null) {
            record.setIniciadoEn(orden.getFechaInicio() != null
                    ? orden.getFechaInicio()
                    : record.getCreadoEn());
        }
        batchRecordRepo.saveAndFlush(record);
    }

    public void revertirPreparacionRevision(OrdenProduccion orden) {
        if (orden == null) return;
        batchRecordRepo.findByOrdenProduccion_OrdenId(orden.getOrdenId()).ifPresent(encontrado -> {
            BatchRecord record = batchRecordRepo.findByIdForUpdate(encontrado.getId())
                    .orElseThrow(() -> new NoSuchElementException("Expediente digital no encontrado."));
            if (record.getEstado() == EstadoBatchRecord.LISTO_PARA_REVISION) {
                record.setEstado(EstadoBatchRecord.EN_EJECUCION);
                record.setCantidadObtenida(null);
                batchRecordRepo.save(record);
            } else if (record.getEstado() == EstadoBatchRecord.EN_CORRECCION) {
                record.setCantidadObtenida(null);
                batchRecordRepo.save(record);
            }
        });
    }

    public BatchRecordRevision enviarARevisionCalidad(
            Long batchRecordId,
            User actor,
            String motivo,
            String ipOrigen,
            String userAgent
    ) {
        return someterARevisionCalidad(
                batchRecordId, actor, motivo, ipOrigen, userAgent, false);
    }

    public BatchRecordRevision reenviarARevisionCalidad(
            Long batchRecordId,
            User actor,
            String motivo,
            String ipOrigen,
            String userAgent
    ) {
        return someterARevisionCalidad(
                batchRecordId, actor, motivo, ipOrigen, userAgent, true);
    }

    private BatchRecordRevision someterARevisionCalidad(
            Long batchRecordId,
            User actor,
            String motivo,
            String ipOrigen,
            String userAgent,
            boolean reenvio
    ) {
        validarIdentidadAuditable(actor);
        String motivoNormalizado = textoObligatorio(
                motivo, "El motivo del envío a Calidad es obligatorio.");
        BatchRecord record = batchRecordRepo.findByIdForUpdate(batchRecordId)
                .orElseThrow(() -> new NoSuchElementException("Expediente digital no encontrado."));
        if (!reenvio && (record.getEstado() != EstadoBatchRecord.LISTO_PARA_REVISION
                || record.getCicloRevisionActual() != 0)) {
            throw new IllegalStateException(
                    "Solo un expediente listo y nunca enviado admite el envío inicial.");
        }
        if (reenvio && record.getEstado() != EstadoBatchRecord.DEVUELTO_PRODUCCION
                && record.getEstado() != EstadoBatchRecord.EN_CORRECCION) {
            throw new IllegalStateException(
                    "Solo un expediente devuelto o en corrección admite reenvío.");
        }
        if (reenvio) validarSeccionesAtendidas(record);
        validarEtapasTerminadas(record);
        if (record.getCantidadObtenida() == null) {
            throw new IllegalStateException("Falta registrar la cantidad obtenida del lote.");
        }
        if (reenvio) {
            lanzarBloqueosControl(
                    PuntoExigenciaControl.ENVIO_CALIDAD,
                    controlWorkflowService.validarBloqueosReenvio(record));
        }
        validarGateControl(record, PuntoExigenciaControl.ENVIO_CALIDAD);
        sincronizarConsumos(record);

        LocalDateTime ahora = LocalDateTime.now(applicationClock);
        long numeroCiclo = record.getCicloRevisionActual() + 1;
        OrigenCicloRevisionBatchRecord origen = reenvio
                ? origenReenvio(record)
                : OrigenCicloRevisionBatchRecord.ENVIO_INICIAL;
        record.setCicloRevisionActual(numeroCiclo);
        record.setEstado(EstadoBatchRecord.PENDIENTE_REVISION);
        record.setEnviadoRevisionEn(ahora);
        batchRecordRepo.saveAndFlush(record);

        CicloRevisionBatchRecord ciclo = new CicloRevisionBatchRecord();
        ciclo.setBatchRecord(record);
        ciclo.setNumero(numeroCiclo);
        ciclo.setOrigen(origen);
        ciclo.setEstado(EstadoCicloRevisionBatchRecord.EN_REVISION);
        ciclo.setEnviadoEn(ahora);
        ciclo.setEnviadoPor(actor);
        ciclo.setMotivoEnvio(motivoNormalizado);
        cicloRevisionRepo.save(ciclo);

        if (reenvio) {
            controlWorkflowService.prepararRevalidacionCalidad(record, numeroCiclo);
        }
        BatchRecordRevision revision = crearRevision(
                record,
                reenvio ? TipoRevisionBatchRecord.REENVIO_CALIDAD
                        : TipoRevisionBatchRecord.ENVIO_CALIDAD,
                actor,
                motivoNormalizado);
        ciclo.setRevisionEnvio(revision);
        cicloRevisionRepo.save(ciclo);
        registrarFirmaRevision(
                record,
                revision,
                actor,
                AlcanceFirmaBatchRecord.REVISION_PRODUCCION,
                DecisionFirmaBatchRecord.CONFIRMA,
                reenvio
                        ? "Confirmo las correcciones y el reenvío del expediente a Calidad."
                        : "Confirmo la revisión productiva y el envío del expediente a Calidad.",
                motivoNormalizado,
                "Responsable de revisión de Producción",
                ipOrigen,
                userAgent);
        return revision;
    }

    public BatchRecordDecisionCalidad registrarDecisionCalidad(
            BatchRecord record,
            User actor,
            DecisionCalidadBatchRecord decision,
            String motivo,
            String alcanceDevolucionJson,
            String ipOrigen,
            String userAgent
    ) {
        if (record == null || actor == null || decision == null) {
            throw new IllegalArgumentException("La decisión de Calidad está incompleta.");
        }
        if (record.getId() == null) {
            throw new IllegalArgumentException("El expediente de la decisión no está persistido.");
        }
        record = batchRecordRepo.findByIdForUpdate(record.getId())
                .orElseThrow(() -> new NoSuchElementException("Expediente digital no encontrado."));
        if (record.getEstado() != EstadoBatchRecord.PENDIENTE_REVISION) {
            throw new IllegalStateException(
                    "Solo un expediente pendiente de revisión admite una decisión de Calidad.");
        }
        if (decision == DecisionCalidadBatchRecord.DEVOLVER_A_PRODUCCION
                && (alcanceDevolucionJson == null || alcanceDevolucionJson.isBlank())) {
            throw new IllegalArgumentException(
                    "La devolución debe conservar el alcance selectivo indicado por Calidad.");
        }
        if (decision != DecisionCalidadBatchRecord.DEVOLVER_A_PRODUCCION
                && alcanceDevolucionJson != null) {
            throw new IllegalArgumentException(
                    "Solo una devolución puede registrar alcance de corrección.");
        }
        if (decision == DecisionCalidadBatchRecord.LIBERAR) {
            lanzarBloqueosControl(
                    PuntoExigenciaControl.LIBERACION,
                    controlWorkflowService.validarBloqueosLiberacionParaDecision(record));
            controlWorkflowService.validarSinDesviacionesAbiertasParaDecision(record);
            long desviacionesAbiertas = desviacionRepo.countByBatchRecord_IdAndEstadoIn(
                    record.getId(), EnumSet.of(
                            EstadoBatchRecordDesviacion.ABIERTA,
                            EstadoBatchRecordDesviacion.EN_INVESTIGACION,
                            EstadoBatchRecordDesviacion.RESUELTA));
            if (desviacionesAbiertas > 0) {
                throw new IllegalStateException(
                        "El lote conserva desviaciones sin cierre y no puede liberarse.");
            }
        }

        CicloRevisionBatchRecord ciclo = cicloRevisionRepo
                .findByBatchRecord_IdAndNumero(record.getId(), record.getCicloRevisionActual())
                .orElseThrow(() -> new IllegalStateException(
                        "El expediente pendiente no tiene un ciclo de revisión vigente."));
        if (ciclo.getEstado() != EstadoCicloRevisionBatchRecord.EN_REVISION) {
            throw new IllegalStateException("El ciclo de revisión ya recibió una decisión.");
        }

        switch (decision) {
            case LIBERAR -> {
                record.setEstado(EstadoBatchRecord.APROBADO);
                record.getLoteResultado().setEstadoCalidad(EstadoCalidadLote.LIBERADO);
            }
            case RECHAZAR -> {
                record.setEstado(EstadoBatchRecord.RECHAZADO);
                record.getLoteResultado().setEstadoCalidad(EstadoCalidadLote.RECHAZADO);
            }
            case DEVOLVER_A_PRODUCCION -> {
                record.setEstado(EstadoBatchRecord.DEVUELTO_PRODUCCION);
                record.getLoteResultado().setEstadoCalidad(EstadoCalidadLote.CUARENTENA);
                // El próximo ciclo es el que deberá confirmar o repetir estos
                // ensayos. La marca nace con la devolución, no tardíamente al
                // reenviar, para que el expediente refleje de inmediato el
                // trabajo regulatorio pendiente.
                controlWorkflowService.prepararRevalidacionCalidad(
                        record, record.getCicloRevisionActual() + 1);
            }
        }
        loteRepo.save(record.getLoteResultado());
        batchRecordRepo.saveAndFlush(record);

        ciclo.setEstado(switch (decision) {
            case LIBERAR -> EstadoCicloRevisionBatchRecord.LIBERADO;
            case RECHAZAR -> EstadoCicloRevisionBatchRecord.RECHAZADO;
            case DEVOLVER_A_PRODUCCION -> EstadoCicloRevisionBatchRecord.DEVUELTO_PRODUCCION;
        });
        ciclo.setCerradoEn(LocalDateTime.now(applicationClock));
        ciclo.setCerradoPor(actor);
        cicloRevisionRepo.save(ciclo);

        BatchRecordDecisionCalidad evidencia = new BatchRecordDecisionCalidad();
        evidencia.setBatchRecord(record);
        evidencia.setDecision(decision);
        evidencia.setMotivo(textoObligatorio(motivo, "El motivo de la decisión es obligatorio."));
        evidencia.setDecididaEn(LocalDateTime.now(applicationClock));
        evidencia.setDecididaPor(actor);
        evidencia.setCicloRevision(ciclo);
        evidencia.setAlcanceDevolucionJson(alcanceDevolucionJson);
        decisionRepo.saveAndFlush(evidencia);

        BatchRecordRevision revision = crearRevision(
                record,
                TipoRevisionBatchRecord.DECISION_CALIDAD,
                actor,
                "Decisión de Calidad: " + decision.name());
        BatchRecordFirma firma = registrarFirmaRevision(
                record,
                revision,
                actor,
                decision == DecisionCalidadBatchRecord.LIBERAR
                        ? AlcanceFirmaBatchRecord.LIBERACION_LOTE
                        : AlcanceFirmaBatchRecord.REVISION_CALIDAD,
                switch (decision) {
                    case LIBERAR -> DecisionFirmaBatchRecord.APRUEBA;
                    case RECHAZAR -> DecisionFirmaBatchRecord.RECHAZA;
                    case DEVOLVER_A_PRODUCCION -> DecisionFirmaBatchRecord.DEVUELVE;
                },
                "Declaro que revisé el expediente y emito la decisión de Calidad indicada.",
                motivo,
                "Responsable de Calidad",
                ipOrigen,
                userAgent
        );
        evidencia.setRevision(revision);
        evidencia.setFirma(firma);
        return decisionRepo.save(evidencia);
    }

    /** Emite la revisión y firma vinculables a una adición excepcional ya persistida. */
    public BatchRecordFirma registrarAdicionExcepcionalControl(
            ControlRequerido requisito,
            User actor,
            String motivo,
            String ipOrigen,
            String userAgent
    ) {
        validarIdentidadAuditable(actor);
        if (requisito == null || requisito.getId() == null
                || requisito.getBatchRecord() == null
                || requisito.getBatchRecord().getId() == null
                || !requisito.isAgregadoExcepcionalmente()) {
            throw new IllegalArgumentException(
                    "Se requiere un control excepcional persistido y vinculado a un expediente.");
        }
        if (!mismoUsuario(actor, requisito.getAgregadoPor())) {
            throw new IllegalStateException(
                    "La firma debe corresponder al usuario que agregó el control excepcional.");
        }
        String motivoNormalizado = textoObligatorio(
                motivo, "El motivo de la adición excepcional es obligatorio.");
        BatchRecord record = batchRecordRepo.findByIdForUpdate(
                        requisito.getBatchRecord().getId())
                .orElseThrow(() -> new NoSuchElementException(
                        "Expediente digital no encontrado."));
        BatchRecordRevision revision = crearRevision(
                record,
                TipoRevisionBatchRecord.ADICION_CONTROL_REQUERIDO,
                actor,
                motivoNormalizado);
        return registrarFirmaRevision(
                record,
                revision,
                actor,
                AlcanceFirmaBatchRecord.ADICION_CONTROL_REQUERIDO,
                DecisionFirmaBatchRecord.CONFIRMA,
                "Confirmo la adición excepcional del control requerido "
                        + requisito.getId() + " al expediente.",
                motivoNormalizado,
                "Administrador de planes de control",
                ipOrigen,
                userAgent);
    }

    public SolicitudReaperturaRechazo solicitarReaperturaRechazo(
            Long batchRecordId,
            User actor,
            String motivo,
            String evidencia,
            String alcance,
            String ipOrigen,
            String userAgent
    ) {
        validarIdentidadAuditable(actor);
        BatchRecord record = batchRecordRepo.findByIdForUpdate(batchRecordId)
                .orElseThrow(() -> new NoSuchElementException("Expediente digital no encontrado."));
        if (record.getEstado() != EstadoBatchRecord.RECHAZADO) {
            throw new IllegalStateException(
                    "Solo un expediente rechazado admite una solicitud de reapertura.");
        }
        if (record.getCicloRevisionActual() <= 0) {
            throw new IllegalStateException(
                    "El rechazo histórico no tiene un ciclo de revisión inferible y requiere depuración administrativa.");
        }
        CicloRevisionBatchRecord cicloRechazado = cicloRevisionRepo
                .findByBatchRecord_IdAndNumero(record.getId(), record.getCicloRevisionActual())
                .orElseThrow(() -> new IllegalStateException(
                        "El rechazo histórico no tiene un ciclo de revisión conservado."));
        if (cicloRechazado.getEstado() != EstadoCicloRevisionBatchRecord.RECHAZADO) {
            throw new IllegalStateException(
                    "El cierre rechazado del ciclo no es inferible y requiere depuración administrativa.");
        }
        if (solicitudReaperturaRepo.existsByBatchRecord_IdAndEstado(
                record.getId(), EstadoSolicitudReaperturaRechazo.PENDIENTE)) {
            throw new IllegalStateException("Ya existe una solicitud de reapertura pendiente.");
        }

        SolicitudReaperturaRechazo solicitud = new SolicitudReaperturaRechazo();
        solicitud.setBatchRecord(record);
        solicitud.setCicloRevisionNumero(record.getCicloRevisionActual());
        solicitud.setEstado(EstadoSolicitudReaperturaRechazo.PENDIENTE);
        solicitud.setSolicitadaEn(LocalDateTime.now(applicationClock));
        solicitud.setSolicitadaPor(actor);
        solicitud.setMotivo(textoObligatorio(motivo, "El motivo de reapertura es obligatorio."));
        solicitud.setEvidencia(textoObligatorio(
                evidencia, "La evidencia que sustenta la reapertura es obligatoria."));
        solicitud.setAlcance(textoObligatorio(
                alcance, "El alcance de la reapertura es obligatorio."));
        solicitudReaperturaRepo.save(solicitud);

        BatchRecordRevision revision = crearRevision(
                record,
                TipoRevisionBatchRecord.SOLICITUD_REAPERTURA_RECHAZO,
                actor,
                solicitud.getMotivo());
        BatchRecordFirma firma = registrarFirmaRevision(
                record,
                revision,
                actor,
                AlcanceFirmaBatchRecord.SOLICITUD_REAPERTURA_RECHAZO,
                DecisionFirmaBatchRecord.SOLICITA,
                "Solicito la reapertura excepcional del rechazo y confirmo su justificación.",
                solicitud.getMotivo(),
                "Solicitante de Calidad",
                ipOrigen,
                userAgent);
        solicitud.setRevisionSolicitud(revision);
        solicitud.setFirmaSolicitud(firma);
        return solicitudReaperturaRepo.save(solicitud);
    }

    public SolicitudReaperturaRechazo aprobarReaperturaRechazo(
            Long batchRecordId,
            Long solicitudId,
            User actor,
            String motivo,
            String ipOrigen,
            String userAgent
    ) {
        validarIdentidadAuditable(actor);
        SolicitudReaperturaRechazo referencia = solicitudReaperturaRepo.findById(solicitudId)
                .orElseThrow(() -> new NoSuchElementException(
                        "Solicitud de reapertura no encontrada."));
        if (referencia.getBatchRecord() == null
                || !Objects.equals(referencia.getBatchRecord().getId(), batchRecordId)) {
            throw new NoSuchElementException("La solicitud no pertenece al expediente indicado.");
        }
        BatchRecord record = batchRecordRepo.findByIdForUpdate(batchRecordId)
                .orElseThrow(() -> new NoSuchElementException("Expediente digital no encontrado."));
        SolicitudReaperturaRechazo solicitud = solicitudReaperturaRepo
                .findByIdForUpdate(solicitudId)
                .orElseThrow(() -> new NoSuchElementException(
                        "Solicitud de reapertura no encontrada."));
        if (record.getEstado() != EstadoBatchRecord.RECHAZADO) {
            throw new IllegalStateException("El expediente ya no se encuentra rechazado.");
        }
        CicloRevisionBatchRecord cicloRechazado = cicloRevisionRepo
                .findByBatchRecord_IdAndNumero(record.getId(), record.getCicloRevisionActual())
                .orElseThrow(() -> new IllegalStateException(
                        "El rechazo vigente no tiene un ciclo de revisión conservado."));
        if (cicloRechazado.getEstado() != EstadoCicloRevisionBatchRecord.RECHAZADO) {
            throw new IllegalStateException(
                    "El ciclo vigente no conserva una decisión de rechazo verificable.");
        }
        if (solicitud.getEstado() != EstadoSolicitudReaperturaRechazo.PENDIENTE) {
            throw new IllegalStateException("La solicitud ya fue resuelta.");
        }
        if (solicitud.getCicloRevisionNumero() != record.getCicloRevisionActual()) {
            throw new IllegalStateException(
                    "La solicitud no corresponde al rechazo vigente del expediente.");
        }
        if (mismoUsuario(actor, solicitud.getSolicitadaPor())) {
            throw new IllegalStateException(
                    "El aprobador de la reapertura debe ser distinto del solicitante.");
        }

        LocalDateTime ahora = LocalDateTime.now(applicationClock);
        String motivoNormalizado = textoObligatorio(
                motivo, "El motivo de aprobación de la reapertura es obligatorio.");
        record.setEstado(EstadoBatchRecord.DEVUELTO_PRODUCCION);
        record.getLoteResultado().setEstadoCalidad(EstadoCalidadLote.CUARENTENA);
        controlWorkflowService.prepararRevalidacionCalidad(
                record, record.getCicloRevisionActual() + 1);
        loteRepo.save(record.getLoteResultado());
        batchRecordRepo.saveAndFlush(record);

        solicitud.setEstado(EstadoSolicitudReaperturaRechazo.APROBADA);
        solicitud.setAprobadaEn(ahora);
        solicitud.setAprobadaPor(actor);
        solicitud.setMotivoAprobacion(motivoNormalizado);
        solicitudReaperturaRepo.save(solicitud);

        BatchRecordRevision revision = crearRevision(
                record,
                TipoRevisionBatchRecord.REAPERTURA_RECHAZO,
                actor,
                motivoNormalizado);
        BatchRecordFirma firma = registrarFirmaRevision(
                record,
                revision,
                actor,
                AlcanceFirmaBatchRecord.APROBACION_REAPERTURA_RECHAZO,
                DecisionFirmaBatchRecord.REABRE,
                "Apruebo la reapertura excepcional; el rechazo previo permanece en el historial.",
                motivoNormalizado,
                "Aprobador de Calidad",
                ipOrigen,
                userAgent);
        solicitud.setRevisionAprobacion(revision);
        solicitud.setFirmaAprobacion(firma);
        return solicitudReaperturaRepo.save(solicitud);
    }

    public void anularPorCancelacion(OrdenProduccion orden, User actor) {
        if (orden == null) return;
        batchRecordRepo.findByOrdenProduccion_OrdenId(orden.getOrdenId()).ifPresent(record -> {
            if (record.getEstado() == EstadoBatchRecord.CERRADO
                    || record.getEstado() == EstadoBatchRecord.APROBADO) {
                throw new IllegalStateException("No se puede cancelar una orden con lote liberado o cerrado.");
            }
            record.setEstado(EstadoBatchRecord.ANULADO);
            record.getLoteResultado().setEstadoCalidad(EstadoCalidadLote.BLOQUEADO);
            loteRepo.save(record.getLoteResultado());
            record.setObservaciones(appendObservacion(
                    record.getObservaciones(),
                    "Anulado por cancelación de la OP. Usuario: " + nombreUsuario(actor)));
            batchRecordRepo.save(record);
        });
    }

    public void anularPorCancelacion(OrdenFabricacion orden, User actor) {
        if (orden == null || orden.getOrdenFabricacionId() == null) return;
        batchRecordRepo.findByOrdenFabricacion_OrdenFabricacionId(
                orden.getOrdenFabricacionId()).ifPresent(record -> {
            if (record.getEstado() == EstadoBatchRecord.CERRADO) {
                throw new IllegalStateException(
                        "No se puede cancelar una orden de fabricación con expediente cerrado.");
            }
            record.setEstado(EstadoBatchRecord.ANULADO);
            record.getLoteResultado().setEstadoCalidad(EstadoCalidadLote.BLOQUEADO);
            loteRepo.save(record.getLoteResultado());
            record.setObservaciones(appendObservacion(
                    record.getObservaciones(),
                    "Anulado por cancelación de la OF. Usuario: " + nombreUsuario(actor)));
            batchRecordRepo.save(record);
        });
    }

    public void cerrarPorIngresoAlmacen(OrdenProduccion orden, User actor) {
        if (orden == null) return;
        BatchRecord record = batchRecordRepo.findByOrdenProduccion_OrdenId(orden.getOrdenId())
                .orElse(null);
        if (record == null) {
            return; // Compatibilidad con órdenes históricas anteriores al expediente digital.
        }
        if (record.getEstado() != EstadoBatchRecord.APROBADO
                || record.getLoteResultado().getEstadoCalidad() != EstadoCalidadLote.LIBERADO) {
            throw new IllegalStateException(
                    "El lote debe estar liberado por Calidad antes de ingresar a almacén.");
        }
        record.setEstado(EstadoBatchRecord.CERRADO);
        record.setCerradoEn(LocalDateTime.now(applicationClock));
        batchRecordRepo.saveAndFlush(record);
        crearRevision(record, TipoRevisionBatchRecord.CIERRE, actor,
                "Ingreso confirmado en almacén y cierre del expediente");
    }

    public void cerrarPorIngresoAlmacen(OrdenFabricacion orden, User actor) {
        if (orden == null || orden.getOrdenFabricacionId() == null) return;
        BatchRecord record = batchRecordRepo.findByOrdenFabricacion_OrdenFabricacionId(
                        orden.getOrdenFabricacionId())
                .orElseThrow(() -> new IllegalStateException(
                        "La orden de fabricación no tiene expediente digital."));
        if (record.getEstado() != EstadoBatchRecord.APROBADO
                || record.getLoteResultado().getEstadoCalidad() != EstadoCalidadLote.LIBERADO) {
            throw new IllegalStateException(
                    "El lote debe estar liberado por Calidad antes de ingresar a almacén.");
        }
        record.setEstado(EstadoBatchRecord.CERRADO);
        record.setCerradoEn(LocalDateTime.now(applicationClock));
        batchRecordRepo.saveAndFlush(record);
        crearRevision(record, TipoRevisionBatchRecord.CIERRE, actor,
                "Ingreso de lote intermedio confirmado y cierre del expediente");
    }

    @Transactional(readOnly = true)
    public Page<BatchRecordDTOs.ListItem> buscar(
            Integer ordenProduccionId,
            String lote,
            int page,
            int size
    ) {
        return batchRecordRepo.buscar(
                        ordenProduccionId,
                        normalizarTexto(lote),
                        PageRequest.of(
                                Math.max(page, 0),
                                Math.min(Math.max(size, 1), 100),
                                Sort.by(Sort.Direction.DESC, "creadoEn")
                        ))
                .map(this::toListItem);
    }

    @Transactional(readOnly = true)
    public BatchRecordDTOs.Detail detalle(Long id) {
        BatchRecord record = requireRecord(id);
        return toDetail(record);
    }

    @Transactional(readOnly = true)
    public BatchRecordDTOs.PrevalidacionEnvio prevalidarEnvio(
            Long id,
            boolean reenvio
    ) {
        BatchRecord record = requireRecord(id);
        List<String> generales = new ArrayList<>();
        if (!reenvio && (record.getEstado() != EstadoBatchRecord.LISTO_PARA_REVISION
                || record.getCicloRevisionActual() != 0)) {
            generales.add("El expediente no está listo para su envío inicial");
        }
        if (reenvio && record.getEstado() != EstadoBatchRecord.DEVUELTO_PRODUCCION
                && record.getEstado() != EstadoBatchRecord.EN_CORRECCION) {
            generales.add("El expediente no está devuelto ni en corrección");
        }
        boolean etapasPendientes = etapaRepo
                .findByBatchRecord_IdOrderBySecuenciaAscIdAsc(record.getId())
                .stream()
                .anyMatch(etapa -> etapa.getEstado() != EstadoBatchRecordEtapa.COMPLETADA
                        && etapa.getEstado() != EstadoBatchRecordEtapa.OMITIDA);
        if (etapasPendientes) generales.add("Existen etapas operativas pendientes");
        if (record.getCantidadObtenida() == null) generales.add("Falta la cantidad obtenida");
        if (reenvio) {
            long seccionesPendientes = seccionCorreccionRepo
                    .countByBatchRecord_IdAndCicloRevisionNumeroAndEstado(
                            record.getId(),
                            record.getCicloRevisionActual(),
                            EstadoSeccionCorreccionBatchRecord.PENDIENTE);
            if (seccionesPendientes > 0) {
                generales.add("Existen " + seccionesPendientes
                        + " sección(es) documentales pendientes de subsanación");
            }
        }
        Map<Long, BloqueoControlDTO> controlesUnicos = new LinkedHashMap<>();
        controlWorkflowService.validarBloqueos(record, PuntoExigenciaControl.ENVIO_CALIDAD)
                .forEach(bloqueo -> controlesUnicos.put(
                        bloqueo.controlRequeridoId(), bloqueo));
        if (reenvio) {
            controlWorkflowService.validarBloqueosReenvio(record)
                    .forEach(bloqueo -> controlesUnicos.putIfAbsent(
                            bloqueo.controlRequeridoId(), bloqueo));
        }
        List<BloqueoControlDTO> controles = List.copyOf(controlesUnicos.values());
        return BatchRecordDTOs.PrevalidacionEnvio.builder()
                .batchRecordId(record.getId())
                .estado(record.getEstado())
                .cicloRevisionActual(record.getCicloRevisionActual())
                .reenvio(reenvio)
                .permitido(generales.isEmpty() && controles.isEmpty())
                .bloqueosGenerales(generales)
                .bloqueosControl(controles)
                .build();
    }

    @Transactional(readOnly = true)
    public List<BatchRecordDTOs.Revision> revisiones(Long id) {
        requireRecord(id);
        return revisionRepo.findByBatchRecord_IdOrderByNumeroAsc(id).stream()
                .map(this::toRevisionDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public BatchRecordRevision requireRevision(Long recordId, Integer numero) {
        if (numero == null) {
            return revisionRepo.findTopByBatchRecord_IdOrderByNumeroDesc(recordId)
                    .orElseThrow(() -> new NoSuchElementException(
                            "El expediente aún no tiene una revisión emitida."));
        }
        return revisionRepo.findByBatchRecord_IdAndNumero(recordId, numero)
                .orElseThrow(() -> new NoSuchElementException(
                        "No existe la revisión " + numero + " para el expediente."));
    }

    @Transactional(readOnly = true)
    public String construirBorradorCanonico(Long recordId) {
        BatchRecord record = requireRecord(recordId);
        return serializarCanonico(record, LocalDateTime.now(applicationClock));
    }

    public BatchRecordRevision crearRevision(
            BatchRecord record,
            TipoRevisionBatchRecord tipo,
            User actor,
            String motivo
    ) {
        validarIdentidadAuditable(actor);
        if (record == null || record.getId() == null) {
            throw new IllegalArgumentException("El expediente persistido es obligatorio para crear una revisión.");
        }
        record = batchRecordRepo.findByIdForUpdate(record.getId())
                .orElseThrow(() -> new NoSuchElementException(
                        "Expediente digital no encontrado al crear la revisión."));
        int numero = revisionRepo.findTopByBatchRecord_IdOrderByNumeroDesc(record.getId())
                .map(ultima -> ultima.getNumero() + 1)
                .orElse(1);
        LocalDateTime ahora = LocalDateTime.now(applicationClock);
        record.setRevisionDocumental(numero);
        batchRecordRepo.saveAndFlush(record);

        String contenido = serializarCanonico(record, ahora);
        String hash = sha256(contenido);

        BatchRecordRevision revision = new BatchRecordRevision();
        revision.setBatchRecord(record);
        revision.setNumero(numero);
        revision.setTipo(tipo);
        revision.setContenidoCanonico(contenido);
        revision.setContenidoSha256(hash);
        revision.setEsquemaVersion(ESQUEMA_VERSION);
        revision.setPlantillaPdfVersion(PLANTILLA_PDF_VERSION);
        revision.setCreadaEn(ahora);
        revision.setCreadaPor(actor);
        revision.setCreadaPorUsername(actor.getUsername());
        revision.setCreadaPorNombre(nombreUsuario(actor));
        revision.setCreadaPorCedula(Long.toString(actor.getCedula()));
        revision.setMotivo(normalizarTexto(motivo));
        revisionRepo.saveAndFlush(revision);

        record.setContenidoSha256(hash);
        batchRecordRepo.save(record);
        return revision;
    }

    public BatchRecord requireRecord(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("El identificador del expediente es obligatorio.");
        }
        return batchRecordRepo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Expediente digital no encontrado."));
    }

    private void crearEtapasDesdeSeguimiento(BatchRecord record, OrdenProduccion orden) {
        List<SeguimientoOrdenArea> seguimientos = seguimientoRepo
                .findByOrdenProduccion_OrdenIdOrderByPosicionSecuenciaAsc(orden.getOrdenId())
                .stream()
                .filter(seguimiento -> seguimiento.getAreaOperativa() != null)
                .filter(seguimiento -> seguimiento.getAreaOperativa().getAreaId()
                        != ALMACEN_GENERAL_AREA_ID)
                .toList();
        int secuencia = 0;
        for (SeguimientoOrdenArea seguimiento : seguimientos) {
            BatchRecordEtapa etapa = new BatchRecordEtapa();
            etapa.setBatchRecord(record);
            etapa.setAreaOperativa(seguimiento.getAreaOperativa());
            etapa.setSeguimientoOrdenArea(seguimiento);
            etapa.setNombre(nombreEtapa(seguimiento));
            etapa.setSecuencia(secuencia++);
            etapa.setEstado(mapEstado(seguimiento.getEstadoEnum()));
            etapa.setIniciadaEn(seguimiento.getEstadoEnum() == EstadoSeguimientoOrdenArea.EN_PROCESO
                    ? seguimiento.getFechaEstadoActual()
                    : null);
            etapa.setControlProcesoPlantilla(plantillaVigente(
                    seguimiento.getAreaOperativa().getAreaId()));
            etapaRepo.save(etapa);
            record.getEtapas().add(etapa);
        }
    }

    private void aplicarEstadoEtapa(
            BatchRecordEtapa etapa,
            BatchRecord record,
            SeguimientoOrdenAreaEvento evento,
            EstadoSeguimientoOrdenArea destino
    ) {
        switch (destino) {
            case COLA, ESPERA -> {
                etapa.setEstado(EstadoBatchRecordEtapa.PENDIENTE);
                if (evento.getTipoEvento() == TipoEventoSeguimiento.CORRECCION_ADMINISTRATIVA) {
                    limpiarCierreActual(etapa);
                }
            }
            case EN_PROCESO -> {
                boolean correccion = record.getEstado() == EstadoBatchRecord.DEVUELTO_PRODUCCION
                        || record.getEstado() == EstadoBatchRecord.EN_CORRECCION;
                etapa.setEstado(correccion
                        ? EstadoBatchRecordEtapa.EN_CORRECCION
                        : EstadoBatchRecordEtapa.EN_EJECUCION);
                if (etapa.getIniciadaEn() == null) etapa.setIniciadaEn(evento.getFechaEvento());
                if (evento.getTipoEvento() == TipoEventoSeguimiento.CORRECCION_ADMINISTRATIVA) {
                    limpiarCierreActual(etapa);
                }
                if (record.getIniciadoEn() == null) record.setIniciadoEn(evento.getFechaEvento());
                record.setEstado(correccion
                        ? EstadoBatchRecord.EN_CORRECCION
                        : EstadoBatchRecord.EN_EJECUCION);
            }
            case COMPLETADO -> {
                if (evento.getActorTipo() == ActorTipoEventoSeguimiento.USER
                        && evento.getUsuario() != null) {
                    etapa.setEstado(EstadoBatchRecordEtapa.COMPLETADA);
                    if (etapa.getIniciadaEn() == null) etapa.setIniciadaEn(evento.getFechaEvento());
                    etapa.setCompletadaEn(evento.getFechaEvento());
                    etapa.setReportadaPor(evento.getUsuario());
                    etapa.setSeguimientoEventoOrigen(evento);
                    etapa.setObservaciones(normalizarTexto(evento.getNota()));
                    etapa.setContenidoSha256(hashEtapa(etapa, evento));
                } else {
                    etapa.setEstado(EstadoBatchRecordEtapa.OMITIDA);
                }
                if (record.getIniciadoEn() == null) record.setIniciadoEn(evento.getFechaEvento());
                if (record.getEstado() == EstadoBatchRecord.BORRADOR) {
                    record.setEstado(EstadoBatchRecord.EN_EJECUCION);
                }
            }
            case OMITIDO -> etapa.setEstado(EstadoBatchRecordEtapa.OMITIDA);
        }
    }

    private void aplicarEstadoEtapaFabricacion(
            BatchRecordEtapa etapa,
            BatchRecord record,
            OrdenFabricacionOperacionEvento evento,
            EstadoSeguimientoOrdenArea destino
    ) {
        switch (destino) {
            case COLA, ESPERA -> {
                etapa.setEstado(EstadoBatchRecordEtapa.PENDIENTE);
                if (evento.getTipoEvento() == TipoEventoSeguimiento.CORRECCION_ADMINISTRATIVA) {
                    limpiarCierreActualFabricacion(etapa);
                }
            }
            case EN_PROCESO -> {
                boolean correccion = record.getEstado() == EstadoBatchRecord.DEVUELTO_PRODUCCION
                        || record.getEstado() == EstadoBatchRecord.EN_CORRECCION;
                etapa.setEstado(correccion
                        ? EstadoBatchRecordEtapa.EN_CORRECCION
                        : EstadoBatchRecordEtapa.EN_EJECUCION);
                if (etapa.getIniciadaEn() == null) etapa.setIniciadaEn(evento.getFechaEvento());
                if (evento.getTipoEvento() == TipoEventoSeguimiento.CORRECCION_ADMINISTRATIVA) {
                    limpiarCierreActualFabricacion(etapa);
                }
                if (record.getIniciadoEn() == null) record.setIniciadoEn(evento.getFechaEvento());
                record.setEstado(correccion
                        ? EstadoBatchRecord.EN_CORRECCION
                        : EstadoBatchRecord.EN_EJECUCION);
            }
            case COMPLETADO -> {
                if (evento.getActorTipo() == ActorTipoEventoSeguimiento.USER
                        && evento.getUsuario() != null) {
                    etapa.setEstado(EstadoBatchRecordEtapa.COMPLETADA);
                    if (etapa.getIniciadaEn() == null) etapa.setIniciadaEn(evento.getFechaEvento());
                    etapa.setCompletadaEn(evento.getFechaEvento());
                    etapa.setReportadaPor(evento.getUsuario());
                    etapa.setOrdenFabricacionEventoOrigen(evento);
                    etapa.setObservaciones(normalizarTexto(evento.getNota()));
                    etapa.setContenidoSha256(hashEtapaFabricacion(etapa, evento));
                } else {
                    etapa.setEstado(EstadoBatchRecordEtapa.OMITIDA);
                }
                if (record.getIniciadoEn() == null) record.setIniciadoEn(evento.getFechaEvento());
                if (record.getEstado() == EstadoBatchRecord.BORRADOR) {
                    record.setEstado(EstadoBatchRecord.EN_EJECUCION);
                }
            }
            case OMITIDO -> etapa.setEstado(EstadoBatchRecordEtapa.OMITIDA);
        }
    }

    private void limpiarCierreActual(BatchRecordEtapa etapa) {
        etapa.setCompletadaEn(null);
        etapa.setReportadaPor(null);
        etapa.setSeguimientoEventoOrigen(null);
        etapa.setContenidoSha256(null);
        etapa.setObservaciones(null);
    }

    private void limpiarCierreActualFabricacion(BatchRecordEtapa etapa) {
        etapa.setCompletadaEn(null);
        etapa.setReportadaPor(null);
        etapa.setOrdenFabricacionEventoOrigen(null);
        etapa.setContenidoSha256(null);
        etapa.setObservaciones(null);
    }

    private void registrarCorreccion(
            BatchRecord record,
            BatchRecordEtapa etapa,
            SeguimientoOrdenAreaEvento evento
    ) {
        if (evento.getUsuario() == null
                || correccionRepo.existsByEventoCorreccion_Id(evento.getId())) {
            return;
        }
        if (record.getEstado() != EstadoBatchRecord.BORRADOR) {
            record.setEstado(record.getCicloRevisionActual() > 0
                    ? EstadoBatchRecord.EN_CORRECCION
                    : EstadoBatchRecord.EN_EJECUCION);
        }
        record.setCantidadObtenida(null);
        record.setContenidoSha256(null);
        record.getLoteResultado().setEstadoCalidad(EstadoCalidadLote.CUARENTENA);
        loteRepo.save(record.getLoteResultado());
        batchRecordRepo.saveAndFlush(record);

        BatchRecordCorreccion correccion = new BatchRecordCorreccion();
        correccion.setBatchRecord(record);
        correccion.setEtapa(etapa);
        correccion.setEventoCorreccion(evento);
        correccion.setEventoRevertido(evento.getEventoRevertido());
        correccion.setValorAnterior(nombreEstadoSeguimiento(evento.getEstadoOrigen()));
        correccion.setValorNuevo(nombreEstadoSeguimiento(evento.getEstadoDestino()));
        correccion.setMotivo(textoObligatorio(
                evento.getNota(), "La corrección administrativa requiere motivo."));
        correccion.setCorregidaEn(evento.getFechaEvento());
        correccion.setCorregidaPor(evento.getUsuario());
        correccionRepo.saveAndFlush(correccion);

        BatchRecordRevision revision = crearRevision(
                record,
                TipoRevisionBatchRecord.CORRECCION,
                evento.getUsuario(),
                evento.getNota());
        BatchRecordFirma firma = registrarFirmaRevision(
                record,
                revision,
                evento.getUsuario(),
                AlcanceFirmaBatchRecord.CORRECCION_EXPEDIENTE,
                DecisionFirmaBatchRecord.CONFIRMA,
                "Declaro que la corrección conserva el dato original y corresponde al motivo registrado.",
                evento.getNota(),
                "Jefatura de Producción",
                null,
                null
        );
        correccion.setRevision(revision);
        correccion.setFirma(firma);
        correccionRepo.save(correccion);
    }

    private void registrarCorreccionFabricacion(
            BatchRecord record,
            BatchRecordEtapa etapa,
            OrdenFabricacionOperacionEvento evento
    ) {
        if (evento.getUsuario() == null
                || correccionRepo.existsByOrdenFabricacionEventoCorreccion_Id(evento.getId())) {
            return;
        }
        record.setEstado(record.getCicloRevisionActual() > 0
                ? EstadoBatchRecord.EN_CORRECCION
                : EstadoBatchRecord.EN_EJECUCION);
        record.setCantidadObtenida(null);
        record.setContenidoSha256(null);
        record.getLoteResultado().setEstadoCalidad(EstadoCalidadLote.CUARENTENA);
        loteRepo.save(record.getLoteResultado());
        batchRecordRepo.saveAndFlush(record);

        BatchRecordCorreccion correccion = new BatchRecordCorreccion();
        correccion.setBatchRecord(record);
        correccion.setEtapa(etapa);
        correccion.setOrdenFabricacionEventoCorreccion(evento);
        correccion.setOrdenFabricacionEventoRevertido(evento.getEventoRevertido());
        correccion.setValorAnterior(nombreEstadoSeguimiento(evento.getEstadoOrigen()));
        correccion.setValorNuevo(nombreEstadoSeguimiento(evento.getEstadoDestino()));
        correccion.setMotivo(textoObligatorio(
                evento.getNota(), "La correccion administrativa requiere motivo."));
        correccion.setCorregidaEn(evento.getFechaEvento());
        correccion.setCorregidaPor(evento.getUsuario());
        correccionRepo.saveAndFlush(correccion);

        BatchRecordRevision revision = crearRevision(
                record, TipoRevisionBatchRecord.CORRECCION,
                evento.getUsuario(), evento.getNota());
        BatchRecordFirma firma = registrarFirmaRevision(
                record, revision, evento.getUsuario(),
                AlcanceFirmaBatchRecord.CORRECCION_EXPEDIENTE,
                DecisionFirmaBatchRecord.CONFIRMA,
                "Declaro que la correccion conserva el dato original y corresponde al motivo registrado.",
                evento.getNota(), "Jefatura de Produccion", null, null);
        correccion.setRevision(revision);
        correccion.setFirma(firma);
        correccionRepo.save(correccion);
    }

    private void registrarFirmaEtapa(
            BatchRecord record,
            BatchRecordEtapa etapa,
            SeguimientoOrdenAreaEvento evento
    ) {
        if (firmaRepo.existsBySeguimientoEvento_Id(evento.getId())) return;
        User actor = evento.getUsuario();
        LocalDateTime firmadoEn = evento.getFechaEvento();
        BatchRecordFirma firma = firmaBase(
                record,
                actor,
                firmadoEn,
                "Responsable del área " + etapa.getAreaOperativa().getNombre());
        firma.setEtapa(etapa);
        firma.setSeguimientoEvento(evento);
        firma.setAlcance(AlcanceFirmaBatchRecord.CIERRE_ETAPA_AREA);
        firma.setDecision(DecisionFirmaBatchRecord.CONFIRMA);
        firma.setHashContenidoFirmado(etapa.getContenidoSha256());
        firma.setManifestacion(
                "Confirmo que la etapa fue ejecutada y que la información reportada es veraz.");
        firma.setComentario(recortar(normalizarTexto(evento.getNota()), 500));
        firmaRepo.save(firma);
    }

    private void registrarFirmaEtapaFabricacion(
            BatchRecord record,
            BatchRecordEtapa etapa,
            OrdenFabricacionOperacionEvento evento
    ) {
        if (firmaRepo.existsByOrdenFabricacionEvento_Id(evento.getId())) return;
        BatchRecordFirma firma = firmaBase(
                record, evento.getUsuario(), evento.getFechaEvento(),
                "Responsable del area " + etapa.getAreaOperativa().getNombre());
        firma.setEtapa(etapa);
        firma.setOrdenFabricacionEvento(evento);
        firma.setAlcance(AlcanceFirmaBatchRecord.CIERRE_ETAPA_AREA);
        firma.setDecision(DecisionFirmaBatchRecord.CONFIRMA);
        firma.setHashContenidoFirmado(etapa.getContenidoSha256());
        firma.setManifestacion(
                "Confirmo que la etapa fue ejecutada y que la informacion reportada es veraz.");
        firma.setComentario(recortar(normalizarTexto(evento.getNota()), 500));
        firmaRepo.save(firma);
    }

    private BatchRecordFirma registrarFirmaRevision(
            BatchRecord record,
            BatchRecordRevision revision,
            User actor,
            AlcanceFirmaBatchRecord alcance,
            DecisionFirmaBatchRecord decision,
            String manifestacion,
            String comentario,
            String rol,
            String ipOrigen,
            String userAgent
    ) {
        BatchRecordFirma firma = firmaBase(
                record, actor, LocalDateTime.now(applicationClock), rol);
        firma.setRevision(revision);
        firma.setAlcance(alcance);
        firma.setDecision(decision);
        firma.setHashContenidoFirmado(revision.getContenidoSha256());
        firma.setManifestacion(manifestacion);
        firma.setComentario(recortar(normalizarTexto(comentario), 500));
        firma.setIpOrigen(recortar(normalizarTexto(ipOrigen), 64));
        firma.setUserAgent(recortar(normalizarTexto(userAgent), 500));
        return firmaRepo.save(firma);
    }

    private BatchRecordFirma firmaBase(
            BatchRecord record,
            User actor,
            LocalDateTime firmadoEn,
            String rol
    ) {
        validarIdentidadAuditable(actor);
        BatchRecordFirma firma = new BatchRecordFirma();
        firma.setBatchRecord(record);
        firma.setFirmante(actor);
        firma.setMetodo(MetodoFirmaElectronica.SESION_AUTENTICADA);
        firma.setFirmadoEn(firmadoEn);
        firma.setAutenticadoEn(firmadoEn);
        firma.setUsernameFirmante(actor.getUsername());
        firma.setNombreFirmante(nombreUsuario(actor));
        firma.setCedulaFirmante(Long.toString(actor.getCedula()));
        firma.setRolFirmante(recortar(
                textoObligatorio(rol, "El rol de la firma es obligatorio."), 120));
        firmaVisualRepo.findFirstByTitularIdAndEstadoOrderByVersionDesc(
                        actor.getId(), FirmaVisualUsuarioVersion.Estado.VIGENTE)
                .ifPresent(firma::setFirmaVisualVersion);
        return firma;
    }

    private void sincronizarConsumos(BatchRecord record) {
        int ordenId;
        List<TransaccionAlmacen.TipoEntidadCausante> tipos;
        Map<String, String> unidadesCongeladas = materialRequirementSnapshotService
                .leer(record.getRequerimientosMaterialesJson()).stream()
                .collect(java.util.stream.Collectors.toMap(
                        MaterialRequirementSnapshotService.RequirementView::productoId,
                        MaterialRequirementSnapshotService.RequirementView::unidadMedida,
                        (primera, ignored) -> primera));
        if (record.getOrdenProduccion() != null) {
            ordenId = record.getOrdenProduccion().getOrdenId();
            tipos = List.of(
                    TransaccionAlmacen.TipoEntidadCausante.OD,
                    TransaccionAlmacen.TipoEntidadCausante.OD_RA,
                    TransaccionAlmacen.TipoEntidadCausante.RA);
        } else if (record.getOrdenFabricacion() != null) {
            ordenId = Math.toIntExact(record.getOrdenFabricacion().getOrdenFabricacionId());
            tipos = List.of(TransaccionAlmacen.TipoEntidadCausante.OD_OF);
        } else {
            return;
        }
        for (TransaccionAlmacen transaccion : transaccionRepo
                .findByTipoEntidadCausanteInAndIdEntidadCausanteWithMovimientos(tipos, ordenId)) {
            if (transaccion.getMovimientosTransaccion() == null) continue;
            for (Movimiento movimiento : transaccion.getMovimientosTransaccion()) {
                if (movimiento == null || movimiento.getMovimientoId() == 0
                        || movimiento.getProducto() == null
                        || movimiento.getCantidad() == 0
                        || consumoRepo.existsByMovimiento_MovimientoId(
                        movimiento.getMovimientoId())) {
                    continue;
                }
                TipoRegistroConsumoBatchRecord tipo = switch (transaccion.getTipoEntidadCausante()) {
                    case OD, OD_OF -> TipoRegistroConsumoBatchRecord.DISPENSACION;
                    case OD_RA -> TipoRegistroConsumoBatchRecord.REPOSICION_AVERIA;
                    case RA -> TipoRegistroConsumoBatchRecord.EXCLUSION_AVERIA;
                    default -> null;
                };
                if (tipo == null) continue;

                BigDecimal cantidad = BigDecimal.valueOf(Math.abs(movimiento.getCantidad()))
                        .setScale(4, java.math.RoundingMode.HALF_UP);
                if (cantidad.signum() == 0) {
                    throw new IllegalStateException(
                            "El movimiento " + movimiento.getMovimientoId()
                                    + " es menor que la precision auditable del expediente (0.0001).");
                }
                if (tipo == TipoRegistroConsumoBatchRecord.EXCLUSION_AVERIA) {
                    cantidad = cantidad.negate();
                }
                User registradoPor = transaccion.getUsuarioAprobador() != null
                        ? transaccion.getUsuarioAprobador()
                        : record.getCreadoPor();
                LocalDateTime registradoEn = movimiento.getFechaMovimiento() != null
                        ? movimiento.getFechaMovimiento()
                        : transaccion.getFechaTransaccion() != null
                        ? transaccion.getFechaTransaccion()
                        : LocalDateTime.now(applicationClock);

                BatchRecordConsumo consumo = new BatchRecordConsumo();
                consumo.setBatchRecord(record);
                consumo.setProducto(movimiento.getProducto());
                consumo.setLoteOrigen(movimiento.getLote());
                consumo.setMovimiento(movimiento);
                consumo.setTipo(tipo);
                consumo.setCantidad(cantidad);
                consumo.setUnidadMedida(unidadObligatoria(
                        unidadesCongeladas.getOrDefault(
                                movimiento.getProducto().getProductoId(),
                                movimiento.getProducto().getTipoUnidades())));
                consumo.setRegistradoEn(registradoEn);
                consumo.setRegistradoPor(registradoPor);
                consumo.setObservaciones("Movimiento " + movimiento.getMovimientoId()
                        + " / " + transaccion.getTipoEntidadCausante().name());
                consumoRepo.save(consumo);
            }
        }
    }

    private String serializarCanonico(BatchRecord record, LocalDateTime registradoEn) {
        Map<String, Object> root = new TreeMap<>();
        root.put("esquemaVersion", ESQUEMA_VERSION);
        root.put("registradoEn", registradoEn);
        root.put("codigo", record.getCodigo());
        root.put("estado", record.getEstado().name());
        root.put("revisionDocumental", record.getRevisionDocumental());

        Map<String, Object> orden = new TreeMap<>();
        if (record.getOrdenProduccion() != null) {
            OrdenProduccion ordenProduccion = record.getOrdenProduccion();
            orden.put("tipo", "ORDEN_PRODUCCION");
            orden.put("id", ordenProduccion.getOrdenId());
            orden.put("estado", ordenProduccion.getEstadoOrden());
            orden.put("estadoDispensacion", ordenProduccion.getEstadoDispensacionMateriales());
            orden.put("politicaDispensacion", ordenProduccion.getPoliticaDispensacionInicio());
            orden.put("fechaCreacion", ordenProduccion.getFechaCreacion());
            orden.put("fechaLanzamiento", ordenProduccion.getFechaLanzamiento());
            orden.put("fechaFinalPlanificada", ordenProduccion.getFechaFinalPlanificada());
            orden.put("fechaInicio", ordenProduccion.getFechaInicio());
            orden.put("fechaFinal", ordenProduccion.getFechaFinal());
            orden.put("pedidoComercial", ordenProduccion.getNumeroPedidoComercial());
            orden.put("areaOperativa", ordenProduccion.getAreaOperativa());
            orden.put("departamentoOperativo", ordenProduccion.getDepartamentoOperativo());
            orden.put("observaciones", ordenProduccion.getObservaciones());
            if (ordenProduccion.getVendedorResponsable() != null) {
                Map<String, Object> responsable = new TreeMap<>();
                responsable.put("cedula", ordenProduccion.getVendedorResponsable().getCedula());
                responsable.put("nombre", String.join(" ",
                        Objects.toString(ordenProduccion.getVendedorResponsable().getNombres(), ""),
                        Objects.toString(ordenProduccion.getVendedorResponsable().getApellidos(), "")).trim());
                orden.put("responsable", responsable);
            }
        } else {
            OrdenFabricacion ordenFabricacion = record.getOrdenFabricacion();
            orden.put("tipo", "ORDEN_FABRICACION");
            orden.put("id", ordenFabricacion.getOrdenFabricacionId());
            orden.put("estado", ordenFabricacion.getEstado());
            orden.put("estadoDispensacion", ordenFabricacion.getEstadoDispensacionMateriales());
            orden.put("politicaDispensacion", ordenFabricacion.getPoliticaDispensacionInicio());
            orden.put("fechaCreacion", ordenFabricacion.getFechaCreacion());
            orden.put("fechaLanzamiento", ordenFabricacion.getFechaLanzamiento());
            orden.put("fechaFinalPlanificada", ordenFabricacion.getFechaFinalPlanificada());
            orden.put("fechaInicio", ordenFabricacion.getFechaInicio());
            orden.put("fechaFinal", ordenFabricacion.getFechaFinal());
            orden.put("ordenProduccionOrigenId", ordenFabricacion.getOrdenProduccionOrigen() == null
                    ? null : ordenFabricacion.getOrdenProduccionOrigen().getOrdenId());
            orden.put("creadaPor", identidadUsuario(ordenFabricacion.getCreadaPor()));
            orden.put("responsable", identidadUsuario(ordenFabricacion.getResponsable()));
            orden.put("observaciones", ordenFabricacion.getObservaciones());
        }
        root.put("orden", orden);
        root.put("lote", Map.of(
                "id", record.getLoteResultado().getId(),
                "numero", record.getLoteResultado().getBatchNumber(),
                "estadoCalidad", record.getLoteResultado().getEstadoCalidad().name(),
                "fechaProduccion", Objects.toString(record.getLoteResultado().getProductionDate(), ""),
                "fechaVencimiento", Objects.toString(record.getLoteResultado().getExpirationDate(), "")
        ));
        root.put("producto", Map.of(
                "id", record.getProductoResultado().getProductoId(),
                "nombre", record.getProductoResultado().getNombre(),
                "tipo", record.getProductoResultado().getTipo_producto(),
                "unidad", record.getUnidadMedida()
        ));
        root.put("manufactura", Map.of(
                "versionId", record.getManufacturingVersion().getId(),
                "versionNumero", record.getManufacturingVersion().getVersionNumber(),
                "insumosJson", Objects.toString(record.getManufacturingVersion().getInsumosJson(), ""),
                "procesoJson", Objects.toString(record.getManufacturingVersion().getProcesoProduccionJson(), ""),
                "casePackJson", Objects.toString(record.getManufacturingVersion().getCasePackJson(), "")
        ));
        root.put("requerimientosMaterialesJson",
                Objects.toString(record.getRequerimientosMaterialesJson(), "[]"));
        Map<String, Object> cantidades = new TreeMap<>();
        cantidades.put("planificada", record.getCantidadPlanificada());
        cantidades.put("obtenida", record.getCantidadObtenida());
        cantidades.put("unidad", record.getUnidadMedida());
        root.put("cantidades", cantidades);
        root.put("creadoEn", record.getCreadoEn());
        root.put("creadoPor", identidadUsuario(record.getCreadoPor()));
        root.put("iniciadoEn", record.getIniciadoEn());
        root.put("enviadoRevisionEn", record.getEnviadoRevisionEn());
        root.put("cicloRevisionActual", record.getCicloRevisionActual());
        root.put("cerradoEn", record.getCerradoEn());
        root.put("observaciones", record.getObservaciones());

        List<BatchRecordEtapa> etapas =
                etapaRepo.findByBatchRecord_IdOrderBySecuenciaAscIdAsc(record.getId());
        List<BatchRecordConsumo> consumos =
                consumoRepo.findByBatchRecord_IdOrderByRegistradoEnAscIdAsc(record.getId());
        root.put("etapas", etapas.stream().map(this::mapEtapaCanonica).toList());
        root.put("consumos", consumos.stream().map(this::mapConsumoCanonico).toList());
        root.put("dispensaciones", mapDispensacionesCanonicas(consumos));
        root.put("controles", ejecucionRepo.findByBatchRecord_IdOrderByFechaRegistroAscIdAsc(record.getId())
                .stream().map(this::mapControlCanonico).toList());
        root.put("controlesUnificados",
                controlWorkflowService.documentoCanonicoPorBatchRecord(record.getId()));
        root.put("desviaciones", desviacionRepo.findByBatchRecord_IdOrderByDetectadaEnAscIdAsc(record.getId())
                .stream().map(this::mapDesviacionCanonica).toList());
        root.put("correcciones", correccionRepo.findByBatchRecord_IdOrderByCorregidaEnAscIdAsc(record.getId())
                .stream().map(this::mapCorreccionCanonica).toList());
        root.put("decisionesCalidad", decisionRepo.findByBatchRecord_IdOrderByDecididaEnAscIdAsc(record.getId())
                .stream().map(this::mapDecisionCanonica).toList());
        root.put("ciclosRevision", cicloRevisionRepo.findByBatchRecord_IdOrderByNumeroAsc(record.getId())
                .stream().map(this::mapCicloRevisionCanonico).toList());
        root.put("solicitudesReapertura", solicitudReaperturaRepo
                .findByBatchRecord_IdOrderBySolicitadaEnAscIdAsc(record.getId())
                .stream().map(this::mapSolicitudReaperturaCanonica).toList());
        root.put("seccionesCorreccion", seccionCorreccionRepo
                .findByBatchRecord_IdOrderByCicloRevisionNumeroAscIdAsc(record.getId())
                .stream().map(this::mapSeccionCorreccionCanonica).toList());
        root.put("firmas", firmaRepo.findByBatchRecord_IdOrderByFirmadoEnAscIdAsc(record.getId())
                .stream().map(this::mapFirmaCanonica).toList());

        try {
            ObjectMapper canonicalMapper = objectMapper.copy()
                    .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
            return canonicalMapper.writeValueAsString(root);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("No se pudo construir la revisión canónica del expediente.", exception);
        }
    }

    private Map<String, Object> mapEtapaCanonica(BatchRecordEtapa etapa) {
        Map<String, Object> data = new TreeMap<>();
        data.put("id", etapa.getId());
        data.put("secuencia", etapa.getSecuencia());
        data.put("nombre", etapa.getNombre());
        data.put("areaId", etapa.getAreaOperativa().getAreaId());
        data.put("areaNombre", etapa.getAreaOperativa().getNombre());
        data.put("estado", etapa.getEstado().name());
        data.put("cicloCorreccionHabilitado", etapa.getCicloCorreccionHabilitado());
        data.put("iniciadaEn", etapa.getIniciadaEn());
        data.put("completadaEn", etapa.getCompletadaEn());
        data.put("reportadaPor", etapa.getReportadaPor() == null
                ? null : identidadUsuario(etapa.getReportadaPor()));
        data.put("eventoOrigenId", etapa.getSeguimientoEventoOrigen() == null
                ? null : etapa.getSeguimientoEventoOrigen().getId());
        data.put("operacionFabricacionId", etapa.getOrdenFabricacionOperacion() == null
                ? null : etapa.getOrdenFabricacionOperacion().getId());
        data.put("eventoFabricacionOrigenId", etapa.getOrdenFabricacionEventoOrigen() == null
                ? null : etapa.getOrdenFabricacionEventoOrigen().getId());
        data.put("plantillaControlId", etapa.getControlProcesoPlantilla() == null
                ? null : etapa.getControlProcesoPlantilla().getId());
        data.put("plantillaControlVersion", etapa.getControlProcesoPlantilla() == null
                ? null : etapa.getControlProcesoPlantilla().getVersion());
        data.put("poe", mapPoeCanonica(etapa));
        data.put("observaciones", etapa.getObservaciones());
        data.put("contenidoSha256", etapa.getContenidoSha256());
        return data;
    }

    private Map<String, Object> mapConsumoCanonico(BatchRecordConsumo consumo) {
        Map<String, Object> data = new TreeMap<>();
        data.put("id", consumo.getId());
        data.put("tipo", consumo.getTipo().name());
        data.put("productoId", consumo.getProducto().getProductoId());
        data.put("productoNombre", consumo.getProducto().getNombre());
        data.put("loteOrigenId", consumo.getLoteOrigen() == null ? null : consumo.getLoteOrigen().getId());
        data.put("loteOrigen", consumo.getLoteOrigen() == null ? null : consumo.getLoteOrigen().getBatchNumber());
        data.put("movimientoId", consumo.getMovimiento() == null ? null : consumo.getMovimiento().getMovimientoId());
        data.put("cantidad", consumo.getCantidad());
        data.put("unidad", consumo.getUnidadMedida());
        data.put("registradoEn", consumo.getRegistradoEn());
        data.put("registradoPor", identidadUsuario(consumo.getRegistradoPor()));
        return data;
    }

    private Map<String, Object> mapControlCanonico(ControlProcesoEjecucion ejecucion) {
        Map<String, Object> data = new TreeMap<>();
        data.put("id", ejecucion.getId());
        data.put("etapaId", ejecucion.getBatchRecordEtapa() == null
                ? null : ejecucion.getBatchRecordEtapa().getId());
        data.put("plantillaId", ejecucion.getPlantilla().getId());
        data.put("plantillaVersion", ejecucion.getPlantilla().getVersion());
        data.put("areaId", ejecucion.getPlantilla().getAreaOperativa().getAreaId());
        data.put("areaNombre", ejecucion.getPlantilla().getAreaOperativa().getNombre());
        data.put("resultado", ejecucion.getResultado() == null ? null : ejecucion.getResultado().name());
        data.put("fechaRegistro", ejecucion.getFechaRegistro());
        data.put("registradoPor", identidadUsuario(ejecucion.getUsuario()));
        data.put("observaciones", ejecucion.getObservaciones());
        data.put("muestras", ejecucion.getMuestras().stream()
                .sorted(Comparator
                        .comparing((ControlProcesoMuestra muestra) -> muestra.getCaracteristica().getOrden())
                        .thenComparing(ControlProcesoMuestra::getNumeroMuestra))
                .map(muestra -> {
                    Map<String, Object> item = new TreeMap<>();
                    ControlProcesoCaracteristica caracteristica = muestra.getCaracteristica();
                    item.put("caracteristicaId", caracteristica.getId());
                    item.put("caracteristica", caracteristica.getNombre());
                    item.put("tipo", caracteristica.getTipo().name());
                    item.put("unidad", caracteristica.getUnidad());
                    item.put("limiteInferior", caracteristica.getLimiteInferior());
                    item.put("limiteSuperior", caracteristica.getLimiteSuperior());
                    item.put("numeroMuestra", muestra.getNumeroMuestra());
                    item.put("lecturas", muestra.getLecturas().stream()
                            .sorted(Comparator.comparing(ControlProcesoLectura::getIndiceUnidad))
                            .map(lectura -> {
                                Map<String, Object> valor = new TreeMap<>();
                                valor.put("indiceUnidad", lectura.getIndiceUnidad());
                                valor.put("valorNumerico", lectura.getValorNumerico());
                                valor.put("valorBooleano", lectura.getValorBooleano());
                                return valor;
                            }).toList());
                    return item;
                }).toList());
        return data;
    }

    private Map<String, Object> mapDesviacionCanonica(BatchRecordDesviacion desviacion) {
        Map<String, Object> data = new TreeMap<>();
        data.put("id", desviacion.getId());
        data.put("etapaId", desviacion.getEtapa() == null ? null : desviacion.getEtapa().getId());
        data.put("codigo", desviacion.getCodigo());
        data.put("descripcion", desviacion.getDescripcion());
        data.put("estado", desviacion.getEstado().name());
        data.put("ocurridaEn", desviacion.getOcurridaEn());
        data.put("detectadaEn", desviacion.getDetectadaEn());
        data.put("detectadaPor", identidadUsuario(desviacion.getDetectadaPor()));
        data.put("origen", desviacion.getOrigen() == null ? null : desviacion.getOrigen().name());
        data.put("accionInmediata", desviacion.getAccionInmediata());
        data.put("evaluacionImpacto", desviacion.getEvaluacionImpacto());
        data.put("causaRaiz", desviacion.getCausaRaiz());
        data.put("accionesCorrectivasPreventivas", desviacion.getAccionesCorrectivasPreventivas());
        data.put("resolucion", desviacion.getResolucion());
        data.put("resueltaEn", desviacion.getResueltaEn());
        data.put("resueltaPor", desviacion.getResueltaPor() == null
                ? null : identidadUsuario(desviacion.getResueltaPor()));
        return data;
    }

    private Map<String, Object> mapCorreccionCanonica(BatchRecordCorreccion correccion) {
        Map<String, Object> data = new TreeMap<>();
        data.put("id", correccion.getId());
        data.put("etapaId", correccion.getEtapa() == null ? null : correccion.getEtapa().getId());
        data.put("eventoCorreccionId", correccion.getEventoCorreccion() == null
                ? null : correccion.getEventoCorreccion().getId());
        data.put("eventoRevertidoId", correccion.getEventoRevertido() == null
                ? null : correccion.getEventoRevertido().getId());
        data.put("eventoFabricacionCorreccionId",
                correccion.getOrdenFabricacionEventoCorreccion() == null
                        ? null : correccion.getOrdenFabricacionEventoCorreccion().getId());
        data.put("eventoFabricacionRevertidoId",
                correccion.getOrdenFabricacionEventoRevertido() == null
                        ? null : correccion.getOrdenFabricacionEventoRevertido().getId());
        data.put("valorAnterior", correccion.getValorAnterior());
        data.put("valorNuevo", correccion.getValorNuevo());
        data.put("motivo", correccion.getMotivo());
        data.put("corregidaEn", correccion.getCorregidaEn());
        data.put("corregidaPor", identidadUsuario(correccion.getCorregidaPor()));
        return data;
    }

    private Map<String, Object> mapDecisionCanonica(BatchRecordDecisionCalidad decision) {
        Map<String, Object> data = new TreeMap<>();
        data.put("id", decision.getId());
        data.put("decision", decision.getDecision().name());
        data.put("motivo", decision.getMotivo());
        data.put("decididaEn", decision.getDecididaEn());
        data.put("decididaPor", identidadUsuario(decision.getDecididaPor()));
        data.put("cicloRevision", decision.getCicloRevision() == null
                ? null : decision.getCicloRevision().getNumero());
        data.put("alcanceDevolucionJson", decision.getAlcanceDevolucionJson());
        return data;
    }

    private List<Map<String, Object>> mapDispensacionesCanonicas(
            List<BatchRecordConsumo> consumos
    ) {
        Map<Integer, List<BatchRecordConsumo>> porTransaccion = new TreeMap<>();
        for (BatchRecordConsumo consumo : consumos) {
            Movimiento movimiento = consumo.getMovimiento();
            TransaccionAlmacen transaccion = movimiento == null
                    ? null : movimiento.getTransaccionAlmacen();
            if (transaccion == null
                    || !TIPOS_DISPENSACION_DOCUMENTAL.contains(
                    transaccion.getTipoEntidadCausante())) {
                continue;
            }
            porTransaccion.computeIfAbsent(transaccion.getTransaccionId(), ignored -> new ArrayList<>())
                    .add(consumo);
        }

        return porTransaccion.values().stream()
                .map(this::mapDispensacionCanonica)
                .sorted(Comparator
                        .comparing((Map<String, Object> value) ->
                                Objects.toString(value.get("fechaTransaccion"), ""))
                        .thenComparing(value -> ((Number) value.get("transaccionId")).intValue()))
                .toList();
    }

    private Map<String, Object> mapDispensacionCanonica(
            List<BatchRecordConsumo> consumos
    ) {
        TransaccionAlmacen transaccion = consumos.get(0).getMovimiento().getTransaccionAlmacen();
        Map<String, Object> data = new TreeMap<>();
        data.put("transaccionId", transaccion.getTransaccionId());
        data.put("tipo", transaccion.getTipoEntidadCausante().name());
        data.put("fechaTransaccion", transaccion.getFechaTransaccion());
        data.put("estadoContable", transaccion.getEstadoContable() == null
                ? null : transaccion.getEstadoContable().name());
        data.put("observaciones", transaccion.getObservaciones());
        data.put("usuariosRealizadores", transaccion.getUsuariosResponsables() == null
                ? List.of()
                : transaccion.getUsuariosResponsables().stream()
                .sorted(Comparator.comparing(User::getId, Comparator.nullsLast(Long::compareTo)))
                .map(this::identidadUsuario)
                .toList());
        data.put("usuarioAprobador", identidadUsuario(transaccion.getUsuarioAprobador()));
        data.put("movimientos", consumos.stream()
                .sorted(Comparator.comparingInt(value -> value.getMovimiento().getMovimientoId()))
                .map(this::mapMovimientoDispensacionCanonica)
                .toList());
        return data;
    }

    private Map<String, Object> mapMovimientoDispensacionCanonica(
            BatchRecordConsumo consumo
    ) {
        Movimiento movimiento = consumo.getMovimiento();
        Map<String, Object> data = new TreeMap<>();
        data.put("movimientoId", movimiento.getMovimientoId());
        data.put("tipoConsumo", consumo.getTipo().name());
        data.put("productoId", consumo.getProducto().getProductoId());
        data.put("productoNombre", consumo.getProducto().getNombre());
        data.put("loteOrigen", consumo.getLoteOrigen() == null
                ? null : consumo.getLoteOrigen().getBatchNumber());
        data.put("cantidad", consumo.getCantidad());
        data.put("cantidadMovimientoInventario", movimiento.getCantidad());
        data.put("unidad", consumo.getUnidadMedida());
        data.put("almacen", movimiento.getAlmacen() == null
                ? null : movimiento.getAlmacen().name());
        data.put("areaOperativa", movimiento.getAreaOperativa() == null
                ? null : movimiento.getAreaOperativa().getNombre());
        data.put("fechaMovimiento", movimiento.getFechaMovimiento());
        data.put("registradoPor", identidadUsuario(consumo.getRegistradoPor()));
        return data;
    }

    private Map<String, Object> mapCicloRevisionCanonico(CicloRevisionBatchRecord ciclo) {
        Map<String, Object> data = new TreeMap<>();
        data.put("id", ciclo.getId());
        data.put("numero", ciclo.getNumero());
        data.put("origen", ciclo.getOrigen().name());
        data.put("estado", ciclo.getEstado().name());
        data.put("enviadoEn", ciclo.getEnviadoEn());
        data.put("enviadoPor", identidadUsuario(ciclo.getEnviadoPor()));
        data.put("motivoEnvio", ciclo.getMotivoEnvio());
        data.put("revisionEnvio", ciclo.getRevisionEnvio() == null
                ? null : ciclo.getRevisionEnvio().getNumero());
        data.put("cerradoEn", ciclo.getCerradoEn());
        data.put("cerradoPor", ciclo.getCerradoPor() == null
                ? null : identidadUsuario(ciclo.getCerradoPor()));
        return data;
    }

    private Map<String, Object> mapSolicitudReaperturaCanonica(
            SolicitudReaperturaRechazo solicitud) {
        Map<String, Object> data = new TreeMap<>();
        data.put("id", solicitud.getId());
        data.put("cicloRevisionNumero", solicitud.getCicloRevisionNumero());
        data.put("estado", solicitud.getEstado().name());
        data.put("solicitadaEn", solicitud.getSolicitadaEn());
        data.put("solicitadaPor", identidadUsuario(solicitud.getSolicitadaPor()));
        data.put("motivo", solicitud.getMotivo());
        data.put("evidencia", solicitud.getEvidencia());
        data.put("alcance", solicitud.getAlcance());
        data.put("revisionSolicitud", solicitud.getRevisionSolicitud() == null
                ? null : solicitud.getRevisionSolicitud().getNumero());
        data.put("aprobadaEn", solicitud.getAprobadaEn());
        data.put("aprobadaPor", solicitud.getAprobadaPor() == null
                ? null : identidadUsuario(solicitud.getAprobadaPor()));
        data.put("motivoAprobacion", solicitud.getMotivoAprobacion());
        data.put("revisionAprobacion", solicitud.getRevisionAprobacion() == null
                ? null : solicitud.getRevisionAprobacion().getNumero());
        return data;
    }

    private Map<String, Object> mapSeccionCorreccionCanonica(
            BatchRecordSeccionCorreccion seccion) {
        Map<String, Object> data = new TreeMap<>();
        data.put("id", seccion.getId());
        data.put("cicloRevisionNumero", seccion.getCicloRevisionNumero());
        data.put("seccion", seccion.getSeccion());
        data.put("estado", seccion.getEstado().name());
        data.put("solicitadaEn", seccion.getSolicitadaEn());
        data.put("solicitadaPor", identidadUsuario(seccion.getSolicitadaPor()));
        data.put("atendidaEn", seccion.getAtendidaEn());
        data.put("atendidaPor", seccion.getAtendidaPor() == null
                ? null : identidadUsuario(seccion.getAtendidaPor()));
        data.put("justificacion", seccion.getJustificacion());
        return data;
    }

    private Map<String, Object> mapFirmaCanonica(BatchRecordFirma firma) {
        Map<String, Object> data = new TreeMap<>();
        data.put("id", firma.getId());
        data.put("etapaId", firma.getEtapa() == null ? null : firma.getEtapa().getId());
        data.put("eventoId", firma.getSeguimientoEvento() == null
                ? null : firma.getSeguimientoEvento().getId());
        data.put("eventoFabricacionId", firma.getOrdenFabricacionEvento() == null
                ? null : firma.getOrdenFabricacionEvento().getId());
        data.put("revision", firma.getRevision() == null ? null : firma.getRevision().getNumero());
        data.put("alcance", firma.getAlcance().name());
        data.put("decision", firma.getDecision().name());
        data.put("metodo", firma.getMetodo().name());
        data.put("firmadoEn", firma.getFirmadoEn());
        data.put("autenticadoEn", firma.getAutenticadoEn());
        data.put("username", firma.getUsernameFirmante());
        data.put("nombre", firma.getNombreFirmante());
        data.put("cedula", firma.getCedulaFirmante());
        data.put("rol", firma.getRolFirmante());
        data.put("manifestacion", firma.getManifestacion());
        data.put("comentario", firma.getComentario());
        data.put("hash", firma.getHashContenidoFirmado());
        data.put("algoritmoHash", firma.getAlgoritmoHash());
        data.put("ipOrigen", firma.getIpOrigen());
        data.put("userAgent", firma.getUserAgent());
        data.put("firmaVisualVersionId", firma.getFirmaVisualVersion() == null
                ? null : firma.getFirmaVisualVersion().getId());
        return data;
    }

    private BatchRecordDTOs.Detail toDetail(BatchRecord record) {
        List<BatchRecordEtapa> etapas = etapaRepo
                .findByBatchRecord_IdOrderBySecuenciaAscIdAsc(record.getId());
        List<ControlProcesoEjecucion> controles = ejecucionRepo
                .findByBatchRecord_IdOrderByFechaRegistroAscIdAsc(record.getId());
        List<BatchRecordConsumo> consumos = consumoRepo
                .findByBatchRecord_IdOrderByRegistradoEnAscIdAsc(record.getId());
        return BatchRecordDTOs.Detail.builder()
                .resumen(toListItem(record))
                .manufacturingVersionId(record.getManufacturingVersion().getId())
                .manufacturingVersionNumber(record.getManufacturingVersion().getVersionNumber())
                .creadoPor(nombreUsuario(record.getCreadoPor()))
                .iniciadoEn(record.getIniciadoEn())
                .cerradoEn(record.getCerradoEn())
                .observaciones(record.getObservaciones())
                .etapas(etapas.stream().map(this::toEtapaDTO).toList())
                .consumos(consumos.stream().map(this::toConsumoDTO).toList())
                .controles(controles.stream().map(this::toControlDTO).toList())
                .desviaciones(desviacionRepo.findByBatchRecord_IdOrderByDetectadaEnAscIdAsc(record.getId())
                        .stream().map(this::toDesviacionDTO).toList())
                .correcciones(correccionRepo.findByBatchRecord_IdOrderByCorregidaEnAscIdAsc(record.getId())
                        .stream().map(this::toCorreccionDTO).toList())
                .firmas(firmaRepo.findByBatchRecord_IdOrderByFirmadoEnAscIdAsc(record.getId())
                        .stream().map(this::toFirmaDTO).toList())
                .revisiones(revisionRepo.findByBatchRecord_IdOrderByNumeroAsc(record.getId())
                        .stream().map(this::toRevisionDTO).toList())
                .decisionesCalidad(decisionRepo.findByBatchRecord_IdOrderByDecididaEnAscIdAsc(record.getId())
                        .stream().map(this::toDecisionDTO).toList())
                .ciclosRevision(cicloRevisionRepo.findByBatchRecord_IdOrderByNumeroAsc(record.getId())
                        .stream().map(this::toCicloRevisionDTO).toList())
                .solicitudesReapertura(solicitudReaperturaRepo
                        .findByBatchRecord_IdOrderBySolicitadaEnAscIdAsc(record.getId())
                        .stream().map(this::toSolicitudReaperturaDTO).toList())
                .seccionesCorreccion(seccionCorreccionRepo
                        .findByBatchRecord_IdOrderByCicloRevisionNumeroAscIdAsc(record.getId())
                        .stream().map(this::toSeccionCorreccionDTO).toList())
                .lotesOrigen(consumos.stream()
                        .filter(consumo -> consumo.getLoteOrigen() != null)
                        .map(this::toVinculoOrigenDTO)
                        .toList())
                .lotesDestino(consumoRepo.findByLoteOrigen_IdOrderByRegistradoEnAscIdAsc(
                                record.getLoteResultado().getId())
                        .stream().map(this::toVinculoDestinoDTO).toList())
                .build();
    }

    private BatchRecordDTOs.VinculoGenealogia toVinculoOrigenDTO(
            BatchRecordConsumo consumo) {
        BatchRecord productor = batchRecordRepo.findByLoteResultado_Id(
                consumo.getLoteOrigen().getId()).orElse(null);
        return BatchRecordDTOs.VinculoGenealogia.builder()
                .batchRecordId(productor == null ? null : productor.getId())
                .batchRecordCodigo(productor == null ? null : productor.getCodigo())
                .ordenProduccionId(productor == null || productor.getOrdenProduccion() == null
                        ? null : productor.getOrdenProduccion().getOrdenId())
                .ordenFabricacionId(productor == null || productor.getOrdenFabricacion() == null
                        ? null : productor.getOrdenFabricacion().getOrdenFabricacionId())
                .loteId(consumo.getLoteOrigen().getId())
                .lote(consumo.getLoteOrigen().getBatchNumber())
                .productoId(consumo.getProducto().getProductoId())
                .productoNombre(consumo.getProducto().getNombre())
                .cantidad(consumo.getCantidad())
                .unidadMedida(consumo.getUnidadMedida())
                .build();
    }

    private BatchRecordDTOs.VinculoGenealogia toVinculoDestinoDTO(
            BatchRecordConsumo consumo) {
        BatchRecord destino = consumo.getBatchRecord();
        return BatchRecordDTOs.VinculoGenealogia.builder()
                .batchRecordId(destino.getId())
                .batchRecordCodigo(destino.getCodigo())
                .ordenProduccionId(destino.getOrdenProduccion() == null
                        ? null : destino.getOrdenProduccion().getOrdenId())
                .ordenFabricacionId(destino.getOrdenFabricacion() == null
                        ? null : destino.getOrdenFabricacion().getOrdenFabricacionId())
                .loteId(destino.getLoteResultado().getId())
                .lote(destino.getLoteResultado().getBatchNumber())
                .productoId(destino.getProductoResultado().getProductoId())
                .productoNombre(destino.getProductoResultado().getNombre())
                .cantidad(consumo.getCantidad())
                .unidadMedida(consumo.getUnidadMedida())
                .build();
    }

    private BatchRecordDTOs.ListItem toListItem(BatchRecord record) {
        return BatchRecordDTOs.ListItem.builder()
                .id(record.getId())
                .codigo(record.getCodigo())
                .estado(record.getEstado())
                .revisionDocumental(record.getRevisionDocumental())
                .ordenProduccionId(record.getOrdenProduccion() == null
                        ? null : record.getOrdenProduccion().getOrdenId())
                .ordenFabricacionId(record.getOrdenFabricacion() == null
                        ? null : record.getOrdenFabricacion().getOrdenFabricacionId())
                .lote(record.getLoteResultado().getBatchNumber())
                .estadoCalidadLote(record.getLoteResultado().getEstadoCalidad())
                .productoId(record.getProductoResultado().getProductoId())
                .productoNombre(record.getProductoResultado().getNombre())
                .tipoProducto(record.getProductoResultado().getTipo_producto())
                .cantidadPlanificada(record.getCantidadPlanificada())
                .cantidadObtenida(record.getCantidadObtenida())
                .unidadMedida(record.getUnidadMedida())
                .creadoEn(record.getCreadoEn())
                .enviadoRevisionEn(record.getEnviadoRevisionEn())
                .cicloRevisionActual(record.getCicloRevisionActual())
                .build();
    }

    private BatchRecordDTOs.Etapa toEtapaDTO(BatchRecordEtapa etapa) {
        return BatchRecordDTOs.Etapa.builder()
                .id(etapa.getId())
                .secuencia(etapa.getSecuencia())
                .nombre(etapa.getNombre())
                .areaOperativaId(etapa.getAreaOperativa().getAreaId())
                .areaOperativaNombre(etapa.getAreaOperativa().getNombre())
                .estado(etapa.getEstado())
                .cicloCorreccionHabilitado(etapa.getCicloCorreccionHabilitado())
                .iniciadaEn(etapa.getIniciadaEn())
                .completadaEn(etapa.getCompletadaEn())
                .reportadaPor(etapa.getReportadaPor() == null
                        ? null : nombreUsuario(etapa.getReportadaPor()))
                .observaciones(etapa.getObservaciones())
                .plantillaControlId(etapa.getControlProcesoPlantilla() == null
                        ? null : etapa.getControlProcesoPlantilla().getId())
                .plantillaControlVersion(etapa.getControlProcesoPlantilla() == null
                        ? null : etapa.getControlProcesoPlantilla().getVersion())
                .seguimientoEventoOrigenId(etapa.getSeguimientoEventoOrigen() == null
                        ? null : etapa.getSeguimientoEventoOrigen().getId())
                .ordenFabricacionOperacionId(etapa.getOrdenFabricacionOperacion() == null
                        ? null : etapa.getOrdenFabricacionOperacion().getId())
                .ordenFabricacionEventoOrigenId(etapa.getOrdenFabricacionEventoOrigen() == null
                        ? null : etapa.getOrdenFabricacionEventoOrigen().getId())
                .poe(toPoeReferenciaDTO(etapa))
                .build();
    }

    private BatchRecordDTOs.PoeReferencia toPoeReferenciaDTO(BatchRecordEtapa etapa) {
        ProcesoProduccionDocumentoVersion documento = poeDocumento(etapa);
        if (documento == null) {
            return null;
        }
        return BatchRecordDTOs.PoeReferencia.builder()
                .procesoProduccionId(documento.getProceso().getProcesoId())
                .procesoProduccionNombre(documento.getProceso().getNombre())
                .documentoVersionId(documento.getId())
                .version(documento.getVersion())
                .nombreArchivo(documento.getNombreArchivoOriginal())
                .sha256(documento.getSha256())
                .build();
    }

    private Map<String, Object> mapPoeCanonica(BatchRecordEtapa etapa) {
        ProcesoProduccionDocumentoVersion documento = poeDocumento(etapa);
        if (documento == null) {
            return null;
        }
        Map<String, Object> data = new TreeMap<>();
        data.put("procesoProduccionId", documento.getProceso().getProcesoId());
        data.put("procesoProduccionNombre", documento.getProceso().getNombre());
        data.put("documentoVersionId", documento.getId());
        data.put("version", documento.getVersion());
        data.put("nombreArchivo", documento.getNombreArchivoOriginal());
        data.put("sha256", documento.getSha256());
        return data;
    }

    private ProcesoProduccionDocumentoVersion poeDocumento(BatchRecordEtapa etapa) {
        if (etapa.getSeguimientoOrdenArea() != null) {
            return etapa.getSeguimientoOrdenArea().getPoeDocumentoVersion();
        }
        return etapa.getOrdenFabricacionOperacion() == null
                ? null : etapa.getOrdenFabricacionOperacion().getPoeDocumentoVersion();
    }

    private BatchRecordDTOs.Consumo toConsumoDTO(BatchRecordConsumo consumo) {
        return BatchRecordDTOs.Consumo.builder()
                .id(consumo.getId())
                .productoId(consumo.getProducto().getProductoId())
                .productoNombre(consumo.getProducto().getNombre())
                .loteOrigenId(consumo.getLoteOrigen() == null ? null : consumo.getLoteOrigen().getId())
                .loteOrigen(consumo.getLoteOrigen() == null ? null : consumo.getLoteOrigen().getBatchNumber())
                .movimientoId(consumo.getMovimiento() == null ? null : consumo.getMovimiento().getMovimientoId())
                .tipo(consumo.getTipo())
                .cantidad(consumo.getCantidad())
                .unidadMedida(consumo.getUnidadMedida())
                .registradoEn(consumo.getRegistradoEn())
                .registradoPor(nombreUsuario(consumo.getRegistradoPor()))
                .build();
    }

    private BatchRecordDTOs.Control toControlDTO(ControlProcesoEjecucion ejecucion) {
        return BatchRecordDTOs.Control.builder()
                .id(ejecucion.getId())
                .etapaId(ejecucion.getBatchRecordEtapa() == null
                        ? null : ejecucion.getBatchRecordEtapa().getId())
                .plantillaId(ejecucion.getPlantilla().getId())
                .plantillaVersion(ejecucion.getPlantilla().getVersion())
                .areaOperativaId(ejecucion.getPlantilla().getAreaOperativa().getAreaId())
                .areaOperativaNombre(ejecucion.getPlantilla().getAreaOperativa().getNombre())
                .resultado(ejecucion.getResultado())
                .fechaRegistro(ejecucion.getFechaRegistro())
                .registradoPor(nombreUsuario(ejecucion.getUsuario()))
                .observaciones(ejecucion.getObservaciones())
                .build();
    }

    private BatchRecordDTOs.Desviacion toDesviacionDTO(BatchRecordDesviacion desviacion) {
        return BatchRecordDTOs.Desviacion.builder()
                .id(desviacion.getId())
                .etapaId(desviacion.getEtapa() == null ? null : desviacion.getEtapa().getId())
                .codigo(desviacion.getCodigo())
                .descripcion(desviacion.getDescripcion())
                .estado(desviacion.getEstado())
                .ocurridaEn(desviacion.getOcurridaEn())
                .detectadaEn(desviacion.getDetectadaEn())
                .detectadaPor(nombreUsuario(desviacion.getDetectadaPor()))
                .origen(desviacion.getOrigen())
                .accionInmediata(desviacion.getAccionInmediata())
                .evaluacionImpacto(desviacion.getEvaluacionImpacto())
                .causaRaiz(desviacion.getCausaRaiz())
                .accionesCorrectivasPreventivas(desviacion.getAccionesCorrectivasPreventivas())
                .resolucion(desviacion.getResolucion())
                .resueltaEn(desviacion.getResueltaEn())
                .resueltaPor(desviacion.getResueltaPor() == null
                        ? null : nombreUsuario(desviacion.getResueltaPor()))
                .build();
    }

    private BatchRecordDTOs.Correccion toCorreccionDTO(BatchRecordCorreccion correccion) {
        return BatchRecordDTOs.Correccion.builder()
                .id(correccion.getId())
                .etapaId(correccion.getEtapa() == null ? null : correccion.getEtapa().getId())
                .eventoCorreccionId(correccion.getEventoCorreccion() == null
                        ? null : correccion.getEventoCorreccion().getId())
                .eventoRevertidoId(correccion.getEventoRevertido() == null
                        ? null : correccion.getEventoRevertido().getId())
                .ordenFabricacionEventoCorreccionId(
                        correccion.getOrdenFabricacionEventoCorreccion() == null
                                ? null : correccion.getOrdenFabricacionEventoCorreccion().getId())
                .ordenFabricacionEventoRevertidoId(
                        correccion.getOrdenFabricacionEventoRevertido() == null
                                ? null : correccion.getOrdenFabricacionEventoRevertido().getId())
                .valorAnterior(correccion.getValorAnterior())
                .valorNuevo(correccion.getValorNuevo())
                .motivo(correccion.getMotivo())
                .corregidaEn(correccion.getCorregidaEn())
                .corregidaPor(nombreUsuario(correccion.getCorregidaPor()))
                .revision(correccion.getRevision() == null ? null : correccion.getRevision().getNumero())
                .build();
    }

    private BatchRecordDTOs.Firma toFirmaDTO(BatchRecordFirma firma) {
        return BatchRecordDTOs.Firma.builder()
                .id(firma.getId())
                .etapaId(firma.getEtapa() == null ? null : firma.getEtapa().getId())
                .seguimientoEventoId(firma.getSeguimientoEvento() == null
                        ? null : firma.getSeguimientoEvento().getId())
                .ordenFabricacionEventoId(firma.getOrdenFabricacionEvento() == null
                        ? null : firma.getOrdenFabricacionEvento().getId())
                .revision(firma.getRevision() == null ? null : firma.getRevision().getNumero())
                .alcance(firma.getAlcance())
                .decision(firma.getDecision())
                .firmadoEn(firma.getFirmadoEn())
                .usernameFirmante(firma.getUsernameFirmante())
                .nombreFirmante(firma.getNombreFirmante())
                .cedulaFirmante(firma.getCedulaFirmante())
                .rolFirmante(firma.getRolFirmante())
                .manifestacion(firma.getManifestacion())
                .hashContenidoFirmado(firma.getHashContenidoFirmado())
                .firmaVisualVersionId(firma.getFirmaVisualVersion() == null
                        ? null : firma.getFirmaVisualVersion().getId())
                .build();
    }

    private BatchRecordDTOs.Revision toRevisionDTO(BatchRecordRevision revision) {
        return BatchRecordDTOs.Revision.builder()
                .id(revision.getId())
                .numero(revision.getNumero())
                .tipo(revision.getTipo())
                .contenidoSha256(revision.getContenidoSha256())
                .esquemaVersion(revision.getEsquemaVersion())
                .plantillaPdfVersion(revision.getPlantillaPdfVersion())
                .creadaEn(revision.getCreadaEn())
                .creadaPor(revision.getCreadaPorNombre())
                .motivo(revision.getMotivo())
                .build();
    }

    private BatchRecordDTOs.DecisionCalidad toDecisionDTO(BatchRecordDecisionCalidad decision) {
        return BatchRecordDTOs.DecisionCalidad.builder()
                .id(decision.getId())
                .decision(decision.getDecision())
                .motivo(decision.getMotivo())
                .decididaEn(decision.getDecididaEn())
                .decididaPor(nombreUsuario(decision.getDecididaPor()))
                .revision(decision.getRevision() == null ? null : decision.getRevision().getNumero())
                .firmaId(decision.getFirma() == null ? null : decision.getFirma().getId())
                .cicloRevision(decision.getCicloRevision() == null
                        ? null : decision.getCicloRevision().getNumero())
                .alcanceDevolucionJson(decision.getAlcanceDevolucionJson())
                .build();
    }

    private BatchRecordDTOs.CicloRevision toCicloRevisionDTO(CicloRevisionBatchRecord ciclo) {
        return BatchRecordDTOs.CicloRevision.builder()
                .id(ciclo.getId())
                .numero(ciclo.getNumero())
                .origen(ciclo.getOrigen())
                .estado(ciclo.getEstado())
                .enviadoEn(ciclo.getEnviadoEn())
                .enviadoPor(nombreUsuario(ciclo.getEnviadoPor()))
                .motivoEnvio(ciclo.getMotivoEnvio())
                .revisionEnvio(ciclo.getRevisionEnvio() == null
                        ? null : ciclo.getRevisionEnvio().getNumero())
                .cerradoEn(ciclo.getCerradoEn())
                .cerradoPor(ciclo.getCerradoPor() == null
                        ? null : nombreUsuario(ciclo.getCerradoPor()))
                .build();
    }

    private BatchRecordDTOs.SolicitudReapertura toSolicitudReaperturaDTO(
            SolicitudReaperturaRechazo solicitud) {
        return BatchRecordDTOs.SolicitudReapertura.builder()
                .id(solicitud.getId())
                .cicloRevisionNumero(solicitud.getCicloRevisionNumero())
                .estado(solicitud.getEstado())
                .solicitadaEn(solicitud.getSolicitadaEn())
                .solicitadaPor(nombreUsuario(solicitud.getSolicitadaPor()))
                .motivo(solicitud.getMotivo())
                .evidencia(solicitud.getEvidencia())
                .alcance(solicitud.getAlcance())
                .revisionSolicitud(solicitud.getRevisionSolicitud() == null
                        ? null : solicitud.getRevisionSolicitud().getNumero())
                .firmaSolicitudId(solicitud.getFirmaSolicitud() == null
                        ? null : solicitud.getFirmaSolicitud().getId())
                .aprobadaEn(solicitud.getAprobadaEn())
                .aprobadaPor(solicitud.getAprobadaPor() == null
                        ? null : nombreUsuario(solicitud.getAprobadaPor()))
                .motivoAprobacion(solicitud.getMotivoAprobacion())
                .revisionAprobacion(solicitud.getRevisionAprobacion() == null
                        ? null : solicitud.getRevisionAprobacion().getNumero())
                .firmaAprobacionId(solicitud.getFirmaAprobacion() == null
                        ? null : solicitud.getFirmaAprobacion().getId())
                .build();
    }

    private BatchRecordDTOs.SeccionCorreccion toSeccionCorreccionDTO(
            BatchRecordSeccionCorreccion seccion) {
        return BatchRecordDTOs.SeccionCorreccion.builder()
                .id(seccion.getId())
                .cicloRevisionNumero(seccion.getCicloRevisionNumero())
                .seccion(seccion.getSeccion())
                .estado(seccion.getEstado())
                .solicitadaEn(seccion.getSolicitadaEn())
                .solicitadaPor(nombreUsuario(seccion.getSolicitadaPor()))
                .atendidaEn(seccion.getAtendidaEn())
                .atendidaPor(seccion.getAtendidaPor() == null
                        ? null : nombreUsuario(seccion.getAtendidaPor()))
                .justificacion(seccion.getJustificacion())
                .build();
    }

    private ControlProcesoPlantilla plantillaVigente(int areaId) {
        return plantillaRepo.findFirstByAreaOperativa_AreaIdAndEstado(
                        areaId, EstadoControlProcesoPlantilla.VIGENTE)
                .orElse(null);
    }

    private EstadoBatchRecordEtapa mapEstado(EstadoSeguimientoOrdenArea estado) {
        return switch (estado) {
            case COLA, ESPERA -> EstadoBatchRecordEtapa.PENDIENTE;
            case EN_PROCESO -> EstadoBatchRecordEtapa.EN_EJECUCION;
            case COMPLETADO -> EstadoBatchRecordEtapa.COMPLETADA;
            case OMITIDO -> EstadoBatchRecordEtapa.OMITIDA;
        };
    }

    private String nombreEtapa(SeguimientoOrdenArea seguimiento) {
        return primerTexto(
                seguimiento.getRutaProcesoNode() != null
                        ? seguimiento.getRutaProcesoNode().getLabel() : null,
                seguimiento.getAreaOperativa().getNombre(),
                "Etapa " + seguimiento.getPosicionSecuencia());
    }

    private String hashEtapa(BatchRecordEtapa etapa, SeguimientoOrdenAreaEvento evento) {
        Map<String, Object> contenido = new TreeMap<>();
        contenido.put("batchRecordId", etapa.getBatchRecord().getId());
        contenido.put("etapaId", etapa.getId());
        contenido.put("areaId", etapa.getAreaOperativa().getAreaId());
        contenido.put("nombre", etapa.getNombre());
        contenido.put("eventoId", evento.getId());
        contenido.put("completadaEn", evento.getFechaEvento());
        contenido.put("usuarioId", evento.getUsuario().getId());
        contenido.put("observaciones", evento.getNota());
        ProcesoProduccionDocumentoVersion documento = poeDocumento(etapa);
        contenido.put("poeDocumentoVersionId", documento == null ? null : documento.getId());
        contenido.put("poeProcesoProduccionId", documento == null
                ? null : documento.getProceso().getProcesoId());
        contenido.put("poeVersion", documento == null ? null : documento.getVersion());
        contenido.put("poeSha256", documento == null ? null : documento.getSha256());
        try {
            return sha256(objectMapper.copy()
                    .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
                    .writeValueAsString(contenido));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("No se pudo proteger el contenido de la etapa.", exception);
        }
    }

    private String hashEtapaFabricacion(
            BatchRecordEtapa etapa,
            OrdenFabricacionOperacionEvento evento
    ) {
        Map<String, Object> contenido = new TreeMap<>();
        contenido.put("batchRecordId", etapa.getBatchRecord().getId());
        contenido.put("etapaId", etapa.getId());
        contenido.put("operacionFabricacionId", evento.getOperacion().getId());
        contenido.put("areaId", etapa.getAreaOperativa().getAreaId());
        contenido.put("nombre", etapa.getNombre());
        contenido.put("eventoId", evento.getId());
        contenido.put("completadaEn", evento.getFechaEvento());
        contenido.put("usuarioId", evento.getUsuario().getId());
        contenido.put("observaciones", evento.getNota());
        ProcesoProduccionDocumentoVersion documento = poeDocumento(etapa);
        contenido.put("poeDocumentoVersionId", documento == null ? null : documento.getId());
        contenido.put("poeProcesoProduccionId", documento == null
                ? null : documento.getProceso().getProcesoId());
        contenido.put("poeVersion", documento == null ? null : documento.getVersion());
        contenido.put("poeSha256", documento == null ? null : documento.getSha256());
        try {
            return sha256(objectMapper.copy()
                    .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
                    .writeValueAsString(contenido));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "No se pudo proteger el contenido de la etapa de fabricacion.", exception);
        }
    }

    private Map<String, Object> identidadUsuario(User user) {
        if (user == null) return Map.of();
        return Map.of(
                "id", user.getId(),
                "username", user.getUsername(),
                "nombre", nombreUsuario(user),
                "cedula", Long.toString(user.getCedula())
        );
    }

    private String sha256(String contenido) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(contenido.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 no está disponible.", exception);
        }
    }

    private void validarCreacionBase(
            Object producto,
            Object version,
            Lote lote,
            User creador
    ) {
        if (producto == null || version == null || lote == null || creador == null) {
            throw new IllegalStateException(
                    "Producto, versión de manufactura, lote y creador son obligatorios para el expediente.");
        }
    }

    private void validarEtapasTerminadas(BatchRecord record) {
        boolean pendientes = etapaRepo.findByBatchRecord_IdOrderBySecuenciaAscIdAsc(record.getId())
                .stream()
                .anyMatch(etapa -> etapa.getEstado() != EstadoBatchRecordEtapa.COMPLETADA
                        && etapa.getEstado() != EstadoBatchRecordEtapa.OMITIDA);
        if (pendientes) {
            throw new IllegalStateException(
                    "No se puede enviar a Calidad mientras existan etapas operativas pendientes.");
        }
    }

    private void validarCorreccionPermitida(
            BatchRecord record,
            BatchRecordEtapa etapa
    ) {
        if (record.getEstado() == EstadoBatchRecord.APROBADO
                || record.getEstado() == EstadoBatchRecord.CERRADO
                || record.getEstado() == EstadoBatchRecord.PENDIENTE_REVISION
                || record.getEstado() == EstadoBatchRecord.RECHAZADO
                || record.getEstado() == EstadoBatchRecord.ANULADO) {
            throw new IllegalStateException(
                    record.getEstado() == EstadoBatchRecord.ANULADO
                            ? "Un expediente anulado no admite reapertura."
                            : record.getEstado() == EstadoBatchRecord.RECHAZADO
                            ? "Un rechazo es terminal y solo admite la reapertura excepcional de Calidad."
                            : record.getEstado() == EstadoBatchRecord.PENDIENTE_REVISION
                            ? "Un expediente en revisión solo puede corregirse después de una devolución de Calidad."
                            : "Un lote liberado por Calidad no admite reapertura ordinaria. "
                            + "Debe bloquearse y evaluarse mediante el procedimiento excepcional de Calidad.");
        }
        if (record.getEstado() != EstadoBatchRecord.DEVUELTO_PRODUCCION
                && record.getEstado() != EstadoBatchRecord.EN_CORRECCION) {
            return;
        }
        boolean reaperturaExcepcional = solicitudReaperturaRepo
                .existsByBatchRecord_IdAndCicloRevisionNumeroAndEstado(
                        record.getId(),
                        record.getCicloRevisionActual(),
                        EstadoSolicitudReaperturaRechazo.APROBADA);
        boolean etapaSeleccionada = etapa != null
                && Objects.equals(
                etapa.getCicloCorreccionHabilitado(), record.getCicloRevisionActual());
        if (!reaperturaExcepcional && !etapaSeleccionada) {
            throw new IllegalStateException(
                    "La etapa no fue incluida por Calidad en el alcance de la devolución.");
        }
    }

    private void validarSeccionesAtendidas(BatchRecord record) {
        long pendientes = seccionCorreccionRepo
                .countByBatchRecord_IdAndCicloRevisionNumeroAndEstado(
                        record.getId(),
                        record.getCicloRevisionActual(),
                        EstadoSeccionCorreccionBatchRecord.PENDIENTE);
        if (pendientes > 0) {
            throw new IllegalStateException(
                    "Existen " + pendientes
                            + " sección(es) documentales devueltas pendientes de subsanación.");
        }
    }

    private void validarGateControl(BatchRecord record, PuntoExigenciaControl punto) {
        List<BloqueoControlDTO> bloqueos = controlWorkflowService.validarBloqueos(record, punto);
        lanzarBloqueosControl(punto, bloqueos);
    }

    private void validarGateControl(
            BatchRecord record,
            BatchRecordEtapa etapa,
            PuntoExigenciaControl punto
    ) {
        List<BloqueoControlDTO> bloqueos =
                controlWorkflowService.validarBloqueos(record, etapa, punto);
        lanzarBloqueosControl(punto, bloqueos);
    }

    private void lanzarBloqueosControl(
            PuntoExigenciaControl punto,
            List<BloqueoControlDTO> bloqueos
    ) {
        if (bloqueos == null || bloqueos.isEmpty()) return;
        String detalle = bloqueos.stream()
                .map(BloqueoControlDTO::mensaje)
                .collect(java.util.stream.Collectors.joining("; "));
        throw new IllegalStateException(
                "Los controles requeridos bloquean " + punto.name() + ": " + detalle);
    }

    private OrigenCicloRevisionBatchRecord origenReenvio(BatchRecord record) {
        return cicloRevisionRepo.findTopByBatchRecord_IdOrderByNumeroDesc(record.getId())
                .filter(ciclo -> ciclo.getEstado() == EstadoCicloRevisionBatchRecord.RECHAZADO)
                .map(ignored -> OrigenCicloRevisionBatchRecord.REENVIO_TRAS_REAPERTURA)
                .orElse(OrigenCicloRevisionBatchRecord.REENVIO);
    }

    private boolean mismoUsuario(User primero, User segundo) {
        return primero == segundo || (primero != null && segundo != null
                && primero.getId() != null && primero.getId().equals(segundo.getId()));
    }

    private String unidadObligatoria(String unidad) {
        return textoObligatorio(unidad, "La unidad de medida del producto es obligatoria.");
    }

    private String textoObligatorio(String value, String mensaje) {
        String normalizado = normalizarTexto(value);
        if (normalizado == null) throw new IllegalArgumentException(mensaje);
        return normalizado;
    }

    private String nombreUsuario(User user) {
        if (user == null) return null;
        return user.getNombreCompleto() != null && !user.getNombreCompleto().isBlank()
                ? user.getNombreCompleto()
                : user.getUsername();
    }

    private void validarIdentidadAuditable(User actor) {
        if (actor == null || actor.getId() == null
                || actor.getUsername() == null || actor.getUsername().isBlank()
                || actor.getCedula() <= 0) {
            throw new IllegalStateException(
                    "El usuario debe tener identificador, username y cédula válidos para firmar o emitir una revisión.");
        }
    }

    private String nombreEstadoSeguimiento(Integer code) {
        return code == null ? "SIN_ESTADO" : EstadoSeguimientoOrdenArea.fromCode(code).name();
    }

    private String normalizarTexto(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String primerTexto(String... values) {
        for (String value : values) {
            String normalized = normalizarTexto(value);
            if (normalized != null) return normalized;
        }
        return "Etapa";
    }

    private String appendObservacion(String actual, String nueva) {
        return normalizarTexto(actual) == null ? nueva : actual + "\n" + nueva;
    }

    private String recortar(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
