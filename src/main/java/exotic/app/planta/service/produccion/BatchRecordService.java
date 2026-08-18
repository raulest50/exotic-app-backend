package exotic.app.planta.service.produccion;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import exotic.app.planta.model.calidad.*;
import exotic.app.planta.model.inventarios.EstadoCalidadLote;
import exotic.app.planta.model.inventarios.Lote;
import exotic.app.planta.model.inventarios.Movimiento;
import exotic.app.planta.model.inventarios.TransaccionAlmacen;
import exotic.app.planta.model.produccion.*;
import exotic.app.planta.model.produccion.batchrecord.*;
import exotic.app.planta.model.produccion.dto.BatchRecordDTOs;
import exotic.app.planta.model.produccion.fabricacion.OrdenFabricacion;
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

    public static final String ESQUEMA_VERSION = "batch-record-v2";
    public static final String PLANTILLA_PDF_VERSION = "batch-record-pdf-v2";
    private static final int ALMACEN_GENERAL_AREA_ID = -1;

    private final BatchRecordRepo batchRecordRepo;
    private final BatchRecordEtapaRepo etapaRepo;
    private final BatchRecordRevisionRepo revisionRepo;
    private final BatchRecordFirmaRepo firmaRepo;
    private final BatchRecordCorreccionRepo correccionRepo;
    private final BatchRecordDecisionCalidadRepo decisionRepo;
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

    public BatchRecordRevision cerrarOrdenFabricacion(
            OrdenFabricacion orden,
            BigDecimal cantidadObtenida,
            User actor,
            String motivo
    ) {
        if (orden == null || orden.getOrdenFabricacionId() == null) {
            throw new IllegalArgumentException("La orden de fabricacion es obligatoria.");
        }
        BatchRecord record = batchRecordRepo.findByOrdenFabricacion_OrdenFabricacionId(
                        orden.getOrdenFabricacionId())
                .orElseThrow(() -> new IllegalStateException(
                        "La orden de fabricacion no tiene expediente digital."));
        boolean pendientes = etapaRepo.findByBatchRecord_IdOrderBySecuenciaAscIdAsc(record.getId())
                .stream()
                .anyMatch(etapa -> etapa.getEstado() != EstadoBatchRecordEtapa.COMPLETADA
                        && etapa.getEstado() != EstadoBatchRecordEtapa.OMITIDA);
        if (pendientes) {
            throw new IllegalStateException("La OF todavia tiene etapas operativas pendientes.");
        }
        sincronizarConsumos(record);
        validarIdentidadAuditable(actor);

        record = batchRecordRepo.findByIdForUpdate(record.getId())
                .orElseThrow(() -> new NoSuchElementException("Expediente digital no encontrado."));
        if (record.getEstado() == EstadoBatchRecord.CERRADO) {
            throw new IllegalStateException("El expediente de la OF ya se encuentra cerrado.");
        }
        LocalDateTime ahora = LocalDateTime.now(applicationClock);
        int numero = revisionRepo.findTopByBatchRecord_IdOrderByNumeroDesc(record.getId())
                .map(ultima -> ultima.getNumero() + 1)
                .orElse(1);
        record.setRevisionDocumental(numero);
        record.setCantidadObtenida(cantidadObtenida);
        record.setEstado(EstadoBatchRecord.CERRADO);
        record.setCerradoEn(ahora);
        if (record.getIniciadoEn() == null) record.setIniciadoEn(ahora);

        String contenido = serializarCanonico(record, ahora);
        String hash = sha256(contenido);
        record.setContenidoSha256(hash);
        batchRecordRepo.saveAndFlush(record);

        BatchRecordRevision revision = new BatchRecordRevision();
        revision.setBatchRecord(record);
        revision.setNumero(numero);
        revision.setTipo(TipoRevisionBatchRecord.CIERRE);
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
        registrarFirmaRevision(
                record, revision, actor,
                AlcanceFirmaBatchRecord.REVISION_PRODUCCION,
                DecisionFirmaBatchRecord.CONFIRMA,
                "Confirmo el rendimiento final y el cierre del expediente de fabricacion.",
                motivo, "Responsable del area final", null, null);
        return revision;
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

    public void validarCorreccionPermitida(SeguimientoOrdenArea seguimiento) {
        if (seguimiento == null || seguimiento.getOrdenProduccion() == null) {
            return;
        }
        batchRecordRepo.findByOrdenProduccion_OrdenId(
                        seguimiento.getOrdenProduccion().getOrdenId())
                .ifPresent(record -> {
                    if (record.getEstado() == EstadoBatchRecord.APROBADO
                            || record.getEstado() == EstadoBatchRecord.CERRADO
                            || record.getEstado() == EstadoBatchRecord.ANULADO) {
                        throw new IllegalStateException(
                                record.getEstado() == EstadoBatchRecord.ANULADO
                                        ? "Un expediente anulado no admite reapertura."
                                        : "Un lote liberado por Calidad no admite reapertura ordinaria. "
                                        + "Debe bloquearse y evaluarse mediante el procedimiento excepcional de Calidad.");
                    }
                });
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

    public BatchRecordRevision enviarARevisionCalidad(
            OrdenProduccion orden,
            BigDecimal cantidadObtenida,
            User actor
    ) {
        BatchRecord record = batchRecordRepo.findByOrdenProduccion_OrdenId(orden.getOrdenId())
                .orElseThrow(() -> new IllegalStateException(
                        "La orden no tiene expediente digital para enviar a Calidad."));
        if (record.getEstado() == EstadoBatchRecord.APROBADO
                || record.getEstado() == EstadoBatchRecord.CERRADO
                || record.getEstado() == EstadoBatchRecord.ANULADO) {
            throw new IllegalStateException("El expediente no admite un nuevo envío a Calidad.");
        }
        boolean etapasPendientes = etapaRepo
                .findByBatchRecord_IdOrderBySecuenciaAscIdAsc(record.getId())
                .stream()
                .anyMatch(etapa -> etapa.getEstado() != EstadoBatchRecordEtapa.COMPLETADA
                        && etapa.getEstado() != EstadoBatchRecordEtapa.OMITIDA);
        if (etapasPendientes) {
            throw new IllegalStateException(
                    "No se puede enviar a Calidad mientras existan etapas operativas pendientes.");
        }

        sincronizarConsumos(record);
        record.setCantidadObtenida(cantidadObtenida);
        record.setEstado(EstadoBatchRecord.PENDIENTE_REVISION);
        record.setEnviadoRevisionEn(LocalDateTime.now(applicationClock));
        if (record.getIniciadoEn() == null) {
            record.setIniciadoEn(orden.getFechaInicio() != null
                    ? orden.getFechaInicio()
                    : record.getCreadoEn());
        }
        batchRecordRepo.saveAndFlush(record);
        return crearRevision(record, TipoRevisionBatchRecord.ENVIO_CALIDAD, actor,
                "Envío del lote a revisión de Calidad");
    }

    public BatchRecordDecisionCalidad registrarDecisionCalidad(
            BatchRecord record,
            User actor,
            DecisionCalidadBatchRecord decision,
            String motivo,
            String ipOrigen,
            String userAgent
    ) {
        if (record == null || actor == null || decision == null) {
            throw new IllegalArgumentException("La decisión de Calidad está incompleta.");
        }
        if (record.getEstado() != EstadoBatchRecord.PENDIENTE_REVISION) {
            throw new IllegalStateException(
                    "Solo un expediente pendiente de revisión admite una decisión de Calidad.");
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
            }
        }
        loteRepo.save(record.getLoteResultado());
        batchRecordRepo.saveAndFlush(record);

        BatchRecordDecisionCalidad evidencia = new BatchRecordDecisionCalidad();
        evidencia.setBatchRecord(record);
        evidencia.setDecision(decision);
        evidencia.setMotivo(textoObligatorio(motivo, "El motivo de la decisión es obligatorio."));
        evidencia.setDecididaEn(LocalDateTime.now(applicationClock));
        evidencia.setDecididaPor(actor);
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
                etapa.setEstado(EstadoBatchRecordEtapa.EN_EJECUCION);
                if (etapa.getIniciadaEn() == null) etapa.setIniciadaEn(evento.getFechaEvento());
                if (evento.getTipoEvento() == TipoEventoSeguimiento.CORRECCION_ADMINISTRATIVA) {
                    limpiarCierreActual(etapa);
                }
                if (record.getIniciadoEn() == null) record.setIniciadoEn(evento.getFechaEvento());
                record.setEstado(EstadoBatchRecord.EN_EJECUCION);
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
                etapa.setEstado(EstadoBatchRecordEtapa.EN_EJECUCION);
                if (etapa.getIniciadaEn() == null) etapa.setIniciadaEn(evento.getFechaEvento());
                if (evento.getTipoEvento() == TipoEventoSeguimiento.CORRECCION_ADMINISTRATIVA) {
                    limpiarCierreActualFabricacion(etapa);
                }
                if (record.getIniciadoEn() == null) record.setIniciadoEn(evento.getFechaEvento());
                record.setEstado(EstadoBatchRecord.EN_EJECUCION);
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
            record.setEstado(EstadoBatchRecord.EN_EJECUCION);
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
        record.setEstado(EstadoBatchRecord.EN_EJECUCION);
        record.setCantidadObtenida(null);
        record.setContenidoSha256(null);
        record.getLoteResultado().setEstadoCalidad(EstadoCalidadLote.SIN_CLASIFICAR);
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
            orden.put("tipo", "ORDEN_PRODUCCION");
            orden.put("id", record.getOrdenProduccion().getOrdenId());
        } else {
            orden.put("tipo", "ORDEN_FABRICACION");
            orden.put("id", record.getOrdenFabricacion().getOrdenFabricacionId());
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
        root.put("cerradoEn", record.getCerradoEn());
        root.put("observaciones", record.getObservaciones());

        root.put("etapas", etapaRepo.findByBatchRecord_IdOrderBySecuenciaAscIdAsc(record.getId())
                .stream().map(this::mapEtapaCanonica).toList());
        root.put("consumos", consumoRepo.findByBatchRecord_IdOrderByRegistradoEnAscIdAsc(record.getId())
                .stream().map(this::mapConsumoCanonico).toList());
        root.put("controles", ejecucionRepo.findByBatchRecord_IdOrderByFechaRegistroAscIdAsc(record.getId())
                .stream().map(this::mapControlCanonico).toList());
        root.put("desviaciones", desviacionRepo.findByBatchRecord_IdOrderByDetectadaEnAscIdAsc(record.getId())
                .stream().map(this::mapDesviacionCanonica).toList());
        root.put("correcciones", correccionRepo.findByBatchRecord_IdOrderByCorregidaEnAscIdAsc(record.getId())
                .stream().map(this::mapCorreccionCanonica).toList());
        root.put("decisionesCalidad", decisionRepo.findByBatchRecord_IdOrderByDecididaEnAscIdAsc(record.getId())
                .stream().map(this::mapDecisionCanonica).toList());
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
