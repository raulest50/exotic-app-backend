package exotic.app.planta.service.controles;

import exotic.app.planta.config.AppTime;
import exotic.app.planta.model.controles.*;
import exotic.app.planta.model.controles.dto.BloqueoControlDTO;
import exotic.app.planta.model.controles.dto.ResumenControlesBatchRecordDTO;
import exotic.app.planta.model.controles.dto.ControlRequeridoRevisionDTO;
import exotic.app.planta.model.inventarios.Lote;
import exotic.app.planta.model.produccion.batchrecord.BatchRecord;
import exotic.app.planta.model.produccion.batchrecord.BatchRecordEtapa;
import exotic.app.planta.model.produccion.batchrecord.EstadoBatchRecord;
import exotic.app.planta.model.produccion.SeguimientoOrdenArea;
import exotic.app.planta.model.produccion.fabricacion.OrdenFabricacionOperacion;
import exotic.app.planta.model.producto.Categoria;
import exotic.app.planta.model.producto.Producto;
import exotic.app.planta.model.producto.Terminado;
import exotic.app.planta.repo.controles.ControlRequeridoRepo;
import exotic.app.planta.repo.controles.DesviacionControlRepo;
import exotic.app.planta.repo.controles.VersionPlanControlRepo;
import exotic.app.planta.repo.controles.RevalidacionControlRepo;
import exotic.app.planta.repo.inventarios.LoteRepo;
import exotic.app.planta.repo.produccion.batchrecord.BatchRecordEtapaRepo;
import exotic.app.planta.repo.produccion.batchrecord.BatchRecordRepo;
import exotic.app.planta.repo.produccion.SeguimientoOrdenAreaRepo;
import exotic.app.planta.repo.produccion.fabricacion.OrdenFabricacionOperacionRepo;
import exotic.app.planta.model.users.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.time.LocalDateTime;
import org.springframework.data.domain.PageRequest;
import exotic.app.planta.model.controles.dto.ControlDTOs.LoteControlResponse;
import exotic.app.planta.model.controles.dto.ControlDTOs.OpcionAdicionExcepcionalResponse;
import exotic.app.planta.model.controles.dto.ControlDTOs.EtapaAdicionExcepcionalResponse;

@Service
@RequiredArgsConstructor
public class ControlWorkflowService {
    private final VersionPlanControlRepo versionRepo;
    private final ControlRequeridoRepo requeridoRepo;
    private final DesviacionControlRepo desviacionRepo;
    private final LoteRepo loteRepo;
    private final BatchRecordRepo batchRecordRepo;
    private final BatchRecordEtapaRepo etapaRepo;
    private final SeguimientoOrdenAreaRepo seguimientoRepo;
    private final OrdenFabricacionOperacionRepo operacionFabricacionRepo;
    private final RevalidacionControlRepo revalidacionRepo;

    /** Materializa una sola vez los planes vigentes que coinciden con el contexto congelado. */
    @Transactional
    public List<ControlRequerido> materializarRequisitos(BatchRecord record) {
        Objects.requireNonNull(record, "El batch record es obligatorio.");
        if (record.getId() == null || record.getLoteResultado() == null
                || record.getProductoResultado() == null || record.getManufacturingVersion() == null) {
            throw new IllegalArgumentException(
                    "El batch record debe estar persistido y tener lote, producto y version de manufactura.");
        }
        TipoOrdenControl tipoOrden = tipoOrden(record);
        Producto producto = record.getProductoResultado();
        Categoria categoria = resolverCategoria(record);
        List<ControlRequerido> creados = new ArrayList<>();
        for (VersionPlanControl version : versionesEfectivas(fechaReferencia(record))) {
            List<AplicabilidadPlanControl> reglasOrdenadas = version.getAplicabilidades().stream()
                    .sorted(ordenAplicabilidad())
                    .toList();
            for (AplicabilidadPlanControl regla : reglasOrdenadas) {
                if (!coincideProducto(regla, producto, categoria) || !coincideTipo(regla, tipoOrden)) continue;
                if (regla.getPuntoAplicacion() == PuntoAplicacionControl.LOTE_FINAL) {
                    if (!requeridoRepo.existsByBatchRecord_IdAndVersionPlan_IdAndBatchRecordEtapaIsNull(
                            record.getId(), version.getId())) {
                        creados.add(requeridoRepo.save(nuevoRequisito(
                                record, null, version, regla, producto, categoria, tipoOrden)));
                    }
                    continue;
                }
                for (BatchRecordEtapa etapa : record.getEtapas()) {
                    if (!coincideEtapa(regla, etapa)) continue;
                    if (!requeridoRepo.existsByBatchRecord_IdAndVersionPlan_IdAndBatchRecordEtapa_Id(
                            record.getId(), version.getId(), etapa.getId())) {
                        creados.add(requeridoRepo.save(nuevoRequisito(
                                record, etapa, version, regla, producto, categoria, tipoOrden)));
                    }
                }
            }
        }
        requeridoRepo.flush();
        return creados;
    }

    @Transactional(readOnly = true)
    public List<BloqueoControlDTO> validarBloqueos(BatchRecord record, PuntoExigenciaControl punto) {
        Objects.requireNonNull(record, "El batch record es obligatorio.");
        return requeridoRepo.findByBatchRecord_IdAndPuntoExigenciaSnapshot(record.getId(), punto)
                .stream().filter(item -> bloqueaTransicion(record, punto, item))
                .map(this::toBloqueo).toList();
    }

    @Transactional(readOnly = true)
    public List<BloqueoControlDTO> validarBloqueos(
            BatchRecord record, BatchRecordEtapa etapa, PuntoExigenciaControl punto) {
        Objects.requireNonNull(record, "El batch record es obligatorio.");
        Objects.requireNonNull(etapa, "La etapa es obligatoria.");
        if (etapa.getBatchRecord() == null || !record.getId().equals(etapa.getBatchRecord().getId())) {
            throw new IllegalArgumentException("La etapa no pertenece al batch record.");
        }
        return requeridoRepo.findByBatchRecordEtapa_IdAndPuntoExigenciaSnapshot(etapa.getId(), punto)
                .stream().filter(this::bloquea).map(this::toBloqueo).toList();
    }

    /**
     * Gate final: toda exigencia no informativa debe estar satisfecha y todo
     * ensayo marcado para revalidacion debe resolverse, aunque su politica
     * original fuera informativa.
     */
    @Transactional(readOnly = true)
    public List<BloqueoControlDTO> validarBloqueosLiberacion(BatchRecord record) {
        Objects.requireNonNull(record, "El batch record es obligatorio.");
        return requeridoRepo.findByBatchRecord_IdOrderByIdAsc(record.getId()).stream()
                .filter(this::bloqueaLiberacion)
                .collect(java.util.stream.Collectors.toMap(
                        ControlRequerido::getId,
                        this::toBloqueo,
                        (primero, segundo) -> primero,
                        LinkedHashMap::new))
                .values().stream().toList();
    }

    /** Variante para la mutacion definitiva; el llamador debe haber bloqueado primero el BatchRecord. */
    @Transactional
    public List<BloqueoControlDTO> validarBloqueosLiberacionParaDecision(BatchRecord record) {
        Objects.requireNonNull(record, "El batch record es obligatorio.");
        return requeridoRepo.findByBatchRecordIdForUpdate(record.getId()).stream()
                .filter(this::bloqueaLiberacion)
                .map(this::toBloqueo)
                .toList();
    }

    /**
     * Segunda mitad del gate regulatorio definitivo. El llamador mantiene el
     * lock del expediente y {@link #validarBloqueosLiberacionParaDecision}
     * bloquea sus requisitos antes de consultar las desviaciones.
     */
    @Transactional(readOnly = true)
    public void validarSinDesviacionesAbiertasParaDecision(BatchRecord record) {
        Objects.requireNonNull(record, "El batch record es obligatorio.");
        if (desviacionRepo.existsByControlRequerido_BatchRecord_IdAndEstadoNot(
                record.getId(), EstadoDesviacionControl.CERRADA)) {
            throw new IllegalStateException(
                    "El lote conserva desviaciones de controles sin cierre y no puede liberarse.");
        }
    }

    /** Los controles seleccionados en la devolucion bloquean aunque fueran informativos. */
    @Transactional(readOnly = true)
    public List<BloqueoControlDTO> validarBloqueosReenvio(BatchRecord record) {
        Objects.requireNonNull(record, "El batch record es obligatorio.");
        return requeridoRepo.findByBatchRecord_IdOrderByIdAsc(record.getId()).stream()
                .filter(r -> r.getAmbitoSnapshot() == AmbitoControl.PROCESO)
                .filter(ControlRequerido::isRequiereRepeticion)
                .filter(r -> r.getCicloRevisionNumero() != null
                        && r.getCicloRevisionNumero().longValue()
                        == record.getCicloRevisionActual())
                .filter(this::bloquea)
                .map(this::toBloqueo)
                .toList();
    }

    @Transactional
    public void marcarRepeticionRequerida(
            BatchRecord record, Collection<Long> requisitoIds, long cicloRevision) {
        Objects.requireNonNull(record, "El batch record es obligatorio.");
        if (requisitoIds == null || requisitoIds.isEmpty()) return;
        for (Long id : new LinkedHashSet<>(requisitoIds)) {
            ControlRequerido requerido = requeridoRepo.findByIdForUpdate(id)
                    .orElseThrow(() -> new NoSuchElementException("Control requerido no encontrado: " + id));
            if (requerido.getAmbitoSnapshot() != AmbitoControl.PROCESO) {
                throw new IllegalArgumentException("Solo se solicita repeticion de controles de proceso devueltos.");
            }
            if (requerido.getBatchRecord() == null
                    || !record.getId().equals(requerido.getBatchRecord().getId())) {
                throw new IllegalArgumentException(
                        "El control requerido no pertenece al batch record devuelto.");
            }
            requerido.setRequiereRepeticion(true);
            requerido.setCicloRevisionNumero(Math.toIntExact(cicloRevision));
            requerido.setEstado(EstadoControlRequerido.PENDIENTE);
        }
    }

    @Transactional
    public void prepararRevalidacionCalidad(BatchRecord record, long cicloRevision) {
        for (ControlRequerido requerido : requeridoRepo.findByBatchRecord_IdOrderByIdAsc(record.getId())) {
            if (requerido.getAmbitoSnapshot() != AmbitoControl.CALIDAD
                    || requerido.getEjecuciones().isEmpty()) continue;
            requerido.setRequiereRevalidacion(true);
            requerido.setCicloRevisionNumero(Math.toIntExact(cicloRevision));
            requerido.setEstado(EstadoControlRequerido.POR_REVALIDAR);
        }
    }

    @Transactional(readOnly = true)
    public ResumenControlesBatchRecordDTO resumenPorBatchRecord(Long batchRecordId) {
        List<ControlRequerido> items = requeridoRepo.findByBatchRecord_IdOrderByIdAsc(batchRecordId);
        return new ResumenControlesBatchRecordDTO(batchRecordId, items.size(),
                count(items, EstadoControlRequerido.PENDIENTE),
                count(items, EstadoControlRequerido.CONFORME),
                count(items, EstadoControlRequerido.NO_CONFORME),
                count(items, EstadoControlRequerido.ACEPTADO_POR_DESVIACION),
                count(items, EstadoControlRequerido.POR_REVALIDAR),
                desviacionRepo.countByControlRequerido_BatchRecord_IdAndEstadoNot(
                        batchRecordId, EstadoDesviacionControl.CERRADA));
    }

    @Transactional(readOnly = true)
    public List<ControlRequeridoRevisionDTO> controlesProcesoPorBatchRecord(Long batchRecordId) {
        return controlesPorBatchRecord(batchRecordId, AmbitoControl.PROCESO);
    }

    @Transactional(readOnly = true)
    public List<ControlRequeridoRevisionDTO> controlesCalidadPorBatchRecord(Long batchRecordId) {
        return controlesPorBatchRecord(batchRecordId, AmbitoControl.CALIDAD);
    }

    private List<ControlRequeridoRevisionDTO> controlesPorBatchRecord(
            Long batchRecordId,
            AmbitoControl ambito) {
        return requeridoRepo.findByBatchRecord_IdOrderByIdAsc(batchRecordId).stream()
                .filter(r -> r.getAmbitoSnapshot() == ambito)
                .map(r -> {
                    EjecucionControl ultima = r.getEjecuciones().stream().findFirst().orElse(null);
                    return new ControlRequeridoRevisionDTO(r.getId(), r.getPlanCodigoSnapshot(),
                            r.getPlanNombreSnapshot(), r.getVersionNumeroSnapshot(), r.getAmbitoSnapshot(),
                            r.getEstado(), r.getOrigen(), r.getPuntoAplicacionSnapshot(), r.getMomentoSnapshot(),
                            r.getPuntoExigenciaSnapshot(),
                            r.getBatchRecordEtapa() == null ? null : r.getBatchRecordEtapa().getId(),
                            r.getBatchRecordEtapa() == null ? null : r.getBatchRecordEtapa().getNombre(),
                            r.getAreaOperativaIdSnapshot(), r.getAreaOperativaNombreSnapshot(),
                            r.isRequiereRepeticion(),
                            r.isRequiereRevalidacion(), r.isAgregadoExcepcionalmente(),
                            r.getMotivoAdicion(),
                            r.getAgregadoPor() == null ? null : r.getAgregadoPor().getUsername(),
                            r.getRevisionAdicion() == null ? null : r.getRevisionAdicion().getId(),
                            r.getFirmaAdicion() == null ? null : r.getFirmaAdicion().getId(),
                            ultima == null ? null : ultima.getId(),
                            ultima == null ? null : ultima.getResultado(),
                            ultima == null ? null : ultima.getFechaRegistro(),
                            ultima == null ? null : ultima.getUsuario().getUsername(),
                            ultima == null || ultima.getLegacyEjecucion() == null
                                    ? null : ultima.getLegacyEjecucion().getId());
                }).toList();
    }

    /**
     * Fotografia documental completa del motor neutral. No consulta las tablas
     * legacy y mantiene orden estable para que el hash de batch-record-v3 sea
     * reproducible.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> documentoCanonicoPorBatchRecord(Long batchRecordId) {
        Map<String, Object> documento = new TreeMap<>();
        documento.put("requisitos", requeridoRepo.findByBatchRecord_IdOrderByIdAsc(batchRecordId)
                .stream().map(this::mapRequisitoCanonico).toList());
        return documento;
    }

    private Map<String, Object> mapRequisitoCanonico(ControlRequerido r) {
        Map<String, Object> data = new TreeMap<>();
        data.put("id", r.getId());
        data.put("planCodigo", r.getPlanCodigoSnapshot());
        data.put("planNombre", r.getPlanNombreSnapshot());
        data.put("versionNumero", r.getVersionNumeroSnapshot());
        data.put("versionId", r.getVersionPlan().getId());
        data.put("proposito", r.getVersionPlan().getProposito());
        data.put("responsableEjecucion", r.getVersionPlan().getResponsableEjecucion());
        data.put("responsableRevision", r.getVersionPlan().getResponsableRevision());
        data.put("responsableDisposicion", r.getVersionPlan().getResponsableDisposicion());
        data.put("versionPublicadaEn", r.getVersionPlan().getPublicadaEn());
        data.put("ambito", nombre(r.getAmbitoSnapshot()));
        data.put("origen", nombre(r.getOrigen()));
        data.put("estado", nombre(r.getEstado()));
        data.put("cicloRevisionNumero", r.getCicloRevisionNumero());
        data.put("requiereRepeticion", r.isRequiereRepeticion());
        data.put("requiereRevalidacion", r.isRequiereRevalidacion());
        data.put("agregadoExcepcionalmente", r.isAgregadoExcepcionalmente());
        data.put("motivoAdicion", r.getMotivoAdicion());
        data.put("agregadoPor", identidad(r.getAgregadoPor()));
        data.put("revisionAdicion", r.getRevisionAdicion() == null ? null : r.getRevisionAdicion().getId());
        data.put("firmaAdicion", r.getFirmaAdicion() == null ? null : r.getFirmaAdicion().getId());
        data.put("puntoAplicacion", nombre(r.getPuntoAplicacionSnapshot()));
        data.put("momento", nombre(r.getMomentoSnapshot()));
        data.put("puntoExigencia", nombre(r.getPuntoExigenciaSnapshot()));
        data.put("productoId", r.getProductoIdSnapshot());
        data.put("productoNombre", r.getProductoNombreSnapshot());
        data.put("categoriaId", r.getCategoriaIdSnapshot());
        data.put("categoriaNombre", r.getCategoriaNombreSnapshot());
        data.put("tipoOrden", nombre(r.getTipoOrdenSnapshot()));
        data.put("areaId", r.getAreaOperativaIdSnapshot());
        data.put("areaNombre", r.getAreaOperativaNombreSnapshot());
        data.put("procesoId", r.getProcesoIdSnapshot());
        data.put("procesoNombre", r.getProcesoNombreSnapshot());
        data.put("manufacturingVersionId", r.getManufacturingVersionIdSnapshot());
        data.put("rutaVersionId", r.getRutaVersionIdSnapshot());
        data.put("rutaNodoId", r.getRutaNodoIdSnapshot());
        data.put("ordenFabricacionOperacionId", r.getOrdenFabricacionOperacionIdSnapshot());
        data.put("frontendNodeId", r.getFrontendNodeIdSnapshot());
        data.put("nodoNombre", r.getNodoNombreSnapshot());
        data.put("etapaId", r.getBatchRecordEtapa() == null ? null : r.getBatchRecordEtapa().getId());
        data.put("etapaNombre", r.getBatchRecordEtapa() == null ? null : r.getBatchRecordEtapa().getNombre());
        data.put("caracteristicas", r.getVersionPlan().getCaracteristicas().stream()
                .sorted(Comparator.comparing(CaracteristicaPlanControl::getOrden)
                        .thenComparing(CaracteristicaPlanControl::getId))
                .map(this::mapCaracteristicaCanonica).toList());
        data.put("ejecuciones", r.getEjecuciones().stream()
                .sorted(Comparator.comparing(EjecucionControl::getId))
                .map(this::mapEjecucionCanonica).toList());
        data.put("revalidaciones", revalidacionRepo
                .findByControlRequerido_IdOrderByIdAsc(r.getId()).stream()
                .map(this::mapRevalidacionCanonica).toList());
        data.put("desviaciones", desviacionRepo
                .findByControlRequerido_IdOrderByIdAsc(r.getId()).stream()
                .map(this::mapDesviacionCanonica).toList());
        return data;
    }

    private Map<String, Object> mapCaracteristicaCanonica(CaracteristicaPlanControl c) {
        Map<String, Object> data = new TreeMap<>();
        data.put("id", c.getId());
        data.put("orden", c.getOrden());
        data.put("nombre", c.getNombre());
        data.put("tipo", nombre(c.getTipo()));
        data.put("magnitudCodigo", c.getMagnitudCodigoSnapshot());
        data.put("magnitudNombre", c.getMagnitudNombreSnapshot());
        data.put("magnitudSimbolo", c.getMagnitudSimboloSnapshot());
        data.put("unidadCodigo", c.getUnidadCodigoSnapshot());
        data.put("unidadNombre", c.getUnidadNombreSnapshot());
        data.put("unidadSimbolo", c.getUnidadSimboloSnapshot());
        data.put("cantidadMuestras", c.getCantidadMuestras());
        data.put("unidadesPorMuestra", c.getUnidadesPorMuestra());
        data.put("escalaVisible", c.getEscalaVisible());
        data.put("objetivo", decimal(c.getObjetivo()));
        data.put("limiteInferior", decimal(c.getLimiteInferior()));
        data.put("limiteSuperior", decimal(c.getLimiteSuperior()));
        data.put("valorBooleanoEsperado", c.getValorBooleanoEsperado());
        data.put("legadoSinLimites", c.isLegadoSinLimites());
        data.put("requiereDepuracion", c.isRequiereDepuracion());
        return data;
    }

    private Map<String, Object> mapEjecucionCanonica(EjecucionControl e) {
        Map<String, Object> data = new TreeMap<>();
        data.put("id", e.getId());
        data.put("repeticionDeId", e.getRepeticionDe() == null ? null : e.getRepeticionDe().getId());
        data.put("usuario", identidad(e.getUsuario()));
        data.put("fechaRegistro", e.getFechaRegistro());
        data.put("resultado", nombre(e.getResultado()));
        data.put("observaciones", e.getObservaciones());
        data.put("motivoRepeticion", e.getMotivoRepeticion());
        data.put("legacyEjecucionId", e.getLegacyEjecucion() == null ? null : e.getLegacyEjecucion().getId());
        data.put("muestras", e.getMuestras().stream()
                .sorted(Comparator
                        .comparing((MuestraEjecucionControl m) -> m.getCaracteristica().getOrden())
                        .thenComparing(MuestraEjecucionControl::getNumeroMuestra)
                        .thenComparing(MuestraEjecucionControl::getId))
                .map(m -> {
                    Map<String, Object> muestra = new TreeMap<>();
                    muestra.put("id", m.getId());
                    muestra.put("caracteristicaId", m.getCaracteristica().getId());
                    muestra.put("numeroMuestra", m.getNumeroMuestra());
                    muestra.put("lecturas", m.getLecturas().stream()
                            .sorted(Comparator.comparing(LecturaEjecucionControl::getIndiceUnidad)
                                    .thenComparing(LecturaEjecucionControl::getId))
                            .map(l -> {
                                Map<String, Object> lectura = new TreeMap<>();
                                lectura.put("id", l.getId());
                                lectura.put("indiceUnidad", l.getIndiceUnidad());
                                lectura.put("valorNumerico", decimal(l.getValorNumerico()));
                                lectura.put("valorBooleano", l.getValorBooleano());
                                return lectura;
                            }).toList());
                    return muestra;
                }).toList());
        return data;
    }

    private Map<String, Object> mapRevalidacionCanonica(RevalidacionControl r) {
        Map<String, Object> data = new TreeMap<>();
        data.put("id", r.getId());
        data.put("ejecucionRevalidadaId", r.getEjecucionRevalidada().getId());
        data.put("cicloRevisionNumero", r.getCicloRevisionNumero());
        data.put("justificacion", r.getJustificacion());
        data.put("confirmadaEn", r.getConfirmadaEn());
        data.put("confirmadaPor", identidad(r.getConfirmadaPor()));
        return data;
    }

    private Map<String, Object> mapDesviacionCanonica(DesviacionControl d) {
        Map<String, Object> data = new TreeMap<>();
        data.put("id", d.getId());
        data.put("ejecucionOrigenId", d.getEjecucionOrigen().getId());
        data.put("ambito", nombre(d.getAmbito()));
        data.put("estado", nombre(d.getEstado()));
        data.put("disposicion", nombre(d.getDisposicion()));
        data.put("investigacion", d.getInvestigacion());
        data.put("resolucion", d.getResolucion());
        data.put("justificacionDisposicion", d.getJustificacionDisposicion());
        data.put("abiertaEn", d.getAbiertaEn());
        data.put("abiertaPor", identidad(d.getAbiertaPor()));
        data.put("resueltaEn", d.getResueltaEn());
        data.put("resueltaPor", identidad(d.getResueltaPor()));
        data.put("cerradaEn", d.getCerradaEn());
        data.put("cerradaPor", identidad(d.getCerradaPor()));
        return data;
    }

    private Map<String, Object> identidad(User user) {
        if (user == null) return null;
        Map<String, Object> data = new TreeMap<>();
        data.put("id", user.getId());
        data.put("username", user.getUsername());
        data.put("nombre", user.getNombreCompleto());
        data.put("cedula", Long.toString(user.getCedula()));
        return data;
    }

    private String nombre(Enum<?> value) {
        return value == null ? null : value.name();
    }

    private String decimal(java.math.BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }

    @Transactional
    public List<ControlRequerido> resolverIndependientes(AmbitoControl ambito, Long loteId) {
        Lote lote = loteRepo.findById(loteId)
                .orElseThrow(() -> new NoSuchElementException("Lote no encontrado."));
        if (lote.getOrdenProduccion() == null && lote.getOrdenFabricacion() == null) {
            throw new IllegalArgumentException("Solo se admiten lotes originados en OP u OF.");
        }
        if (batchRecordRepo.findByLoteResultado_Id(loteId).isPresent()) {
            throw new IllegalStateException(
                    "El lote ya tiene Batch Record; sus controles deben ejecutarse desde el expediente.");
        }
        Producto producto = resolverProducto(lote);
        Categoria categoria = resolverCategoria(lote);
        TipoOrdenControl tipo = lote.getOrdenFabricacion() == null ? TipoOrdenControl.OP : TipoOrdenControl.OF;
        for (VersionPlanControl version : versionesEfectivas(fechaReferencia(lote))) {
            if (version.getPlan().getAmbito() != ambito) continue;
            Set<String> deduplicadas = new HashSet<>();
            List<AplicabilidadPlanControl> reglasOrdenadas = version.getAplicabilidades().stream()
                    .sorted(ordenAplicabilidad())
                    .toList();
            for (AplicabilidadPlanControl regla : reglasOrdenadas) {
                if (!coincideProducto(regla, producto, categoria) || !coincideTipo(regla, tipo)) continue;
                for (ContextoOperacion contexto : resolverContextosIndependientes(lote, regla)) {
                    String clave = contexto.clave();
                    if (!deduplicadas.add(clave)) continue;
                    if (requeridoRepo
                            .existsByLote_IdAndVersionPlan_IdAndPuntoAplicacionSnapshotAndAreaOperativaIdSnapshotAndProcesoIdSnapshotAndRutaNodoIdSnapshotAndOrdenFabricacionOperacionIdSnapshotAndOrigen(
                                    loteId, version.getId(), regla.getPuntoAplicacion(),
                                    regla.getAreaOperativa() == null ? null : regla.getAreaOperativa().getAreaId(),
                                    regla.getProceso() == null ? null : regla.getProceso().getProcesoId(),
                                    contexto.rutaNodoId(), contexto.operacionFabricacionId(),
                                    OrigenControlRequerido.INDEPENDIENTE)) continue;
                    Long manufacturingVersionId = resolverManufacturingVersionId(lote);
                    if (manufacturingVersionId == null) {
                        throw new IllegalStateException(
                                "El lote no tiene una version de manufactura verificable; depure su origen antes de registrar controles nuevos.");
                    }
                    ControlRequerido item = baseRequisito(version, regla, lote, producto, categoria, tipo);
                    item.setOrigen(OrigenControlRequerido.INDEPENDIENTE);
                    item.setManufacturingVersionIdSnapshot(manufacturingVersionId);
                    item.setRutaVersionIdSnapshot(contexto.rutaVersionId());
                    item.setRutaNodoIdSnapshot(contexto.rutaNodoId());
                    item.setOrdenFabricacionOperacionIdSnapshot(contexto.operacionFabricacionId());
                    item.setFrontendNodeIdSnapshot(contexto.frontendNodeId());
                    item.setNodoNombreSnapshot(contexto.nodoNombre());
                    requeridoRepo.save(item);
                }
            }
        }
        requeridoRepo.flush();
        return requeridoRepo.findByLote_IdAndOrigenAndAmbitoSnapshotOrderByIdAsc(
                loteId, OrigenControlRequerido.INDEPENDIENTE, ambito);
    }

    @Transactional(readOnly = true)
    public List<LoteControlResponse> buscarLotes(String search, int size) {
        if (size < 1 || size > 100) {
            throw new IllegalArgumentException("El tamano de busqueda debe estar entre 1 y 100.");
        }
        String filtro = search == null || search.trim().isEmpty() ? null : search.trim();
        return loteRepo.searchLotesManufactura(filtro, PageRequest.of(0, size)).stream().map(lote -> {
            Producto producto = resolverProducto(lote);
            BatchRecord record = batchRecordRepo.findByLoteResultado_Id(lote.getId()).orElse(null);
            return new LoteControlResponse(lote.getId(), lote.getBatchNumber(),
                    producto == null ? null : producto.getProductoId(),
                    producto == null ? null : producto.getNombre(),
                    lote.getOrdenFabricacion() == null ? TipoOrdenControl.OP : TipoOrdenControl.OF,
                    record == null ? null : record.getId(), record == null ? null : record.getCodigo());
        }).toList();
    }

    /** Adicion auditable; la version vigente y la regla aplicable siempre se resuelven en servidor. */
    @Transactional(readOnly = true)
    public List<OpcionAdicionExcepcionalResponse> opcionesAdicionExcepcional(
            AmbitoControl ambito, Long batchRecordId, Long etapaId) {
        BatchRecord record = batchRecordRepo.findById(batchRecordId)
                .orElseThrow(() -> new NoSuchElementException("Batch record no encontrado."));
        validarEstadoAdicionExcepcional(record, ambito);
        BatchRecordEtapa etapa = etapaId == null ? null : etapaRepo
                .findByIdAndBatchRecord_Id(etapaId, batchRecordId)
                .orElseThrow(() -> new IllegalArgumentException("La etapa no pertenece al expediente."));
        Producto producto = record.getProductoResultado();
        Categoria categoria = resolverCategoria(record);
        TipoOrdenControl tipo = tipoOrden(record);
        Map<Long, OpcionAdicionExcepcionalResponse> opciones = new LinkedHashMap<>();
        versionRepo.findByEstado(EstadoVersionPlanControl.VIGENTE).stream()
                .filter(v -> v.getPlan().getAmbito() == ambito)
                .sorted(Comparator.comparing(v -> v.getPlan().getCodigo()))
                .forEach(version -> version.getAplicabilidades().stream()
                        .filter(a -> coincideProducto(a, producto, categoria) && coincideTipo(a, tipo))
                        .filter(a -> etapa == null
                                ? a.getPuntoAplicacion() == PuntoAplicacionControl.LOTE_FINAL
                                : a.getPuntoAplicacion() == PuntoAplicacionControl.SALIDA_OPERACION
                                && coincideEtapa(a, etapa))
                        .sorted(ordenAplicabilidad())
                        .findFirst().ifPresent(regla -> opciones.putIfAbsent(version.getPlan().getId(),
                                new OpcionAdicionExcepcionalResponse(version.getPlan().getId(),
                                        version.getPlan().getCodigo(), version.getPlan().getNombre(),
                                        version.getId(), version.getNumero(), version.getProposito(),
                                        regla.getPuntoAplicacion(), regla.getMomento(),
                                        regla.getPuntoExigencia()))));
        return List.copyOf(opciones.values());
    }

    @Transactional(readOnly = true)
    public List<EtapaAdicionExcepcionalResponse> etapasAdicionExcepcional(
            AmbitoControl ambito, Long batchRecordId) {
        BatchRecord record = batchRecordRepo.findById(batchRecordId)
                .orElseThrow(() -> new NoSuchElementException("Batch record no encontrado."));
        validarEstadoAdicionExcepcional(record, ambito);
        return etapaRepo.findByBatchRecord_IdOrderBySecuenciaAscIdAsc(batchRecordId).stream()
                .map(e -> new EtapaAdicionExcepcionalResponse(e.getId(), e.getSecuencia(), e.getNombre(),
                        e.getAreaOperativa().getAreaId(), e.getAreaOperativa().getNombre()))
                .toList();
    }

    @Transactional
    public ControlRequerido agregarExcepcional(
            AmbitoControl ambito, User actor, Long batchRecordId, Long planId,
            Long etapaId, String motivo) {
        String motivoLimpio = motivo == null ? null : motivo.trim();
        if (motivoLimpio == null || motivoLimpio.isEmpty()) {
            throw new IllegalArgumentException("El motivo de la adicion excepcional es obligatorio.");
        }
        BatchRecord record = batchRecordRepo.findByIdForUpdate(batchRecordId)
                .orElseThrow(() -> new NoSuchElementException("Batch record no encontrado."));
        validarEstadoAdicionExcepcional(record, ambito);
        VersionPlanControl version = versionRepo
                .findFirstByPlan_IdAndEstado(planId, EstadoVersionPlanControl.VIGENTE)
                .filter(v -> v.getPlan().getAmbito() == ambito)
                .orElseThrow(() -> new NoSuchElementException("El plan no tiene version vigente en este ambito."));
        BatchRecordEtapa etapa = etapaId == null ? null : etapaRepo
                .findByIdAndBatchRecord_Id(etapaId, batchRecordId)
                .orElseThrow(() -> new IllegalArgumentException("La etapa no pertenece al expediente."));
        Producto producto = record.getProductoResultado();
        Categoria categoria = resolverCategoria(record);
        TipoOrdenControl tipo = tipoOrden(record);
        List<AplicabilidadPlanControl> candidatas = version.getAplicabilidades().stream()
                .filter(a -> coincideProducto(a, producto, categoria) && coincideTipo(a, tipo))
                .filter(a -> etapa == null
                        ? a.getPuntoAplicacion() == PuntoAplicacionControl.LOTE_FINAL
                        : a.getPuntoAplicacion() == PuntoAplicacionControl.SALIDA_OPERACION
                        && coincideEtapa(a, etapa))
                .sorted(ordenAplicabilidad())
                .toList();
        if (candidatas.isEmpty()) {
            throw new IllegalArgumentException("El plan vigente no aplica al punto solicitado del expediente.");
        }
        AplicabilidadPlanControl regla = candidatas.getFirst();
        ControlRequerido item = nuevoRequisito(record, etapa, version, regla, producto, categoria, tipo);
        item.setAgregadoExcepcionalmente(true);
        item.setMotivoAdicion(motivoLimpio);
        item.setAgregadoPor(actor);
        if (ambito == AmbitoControl.PROCESO
                && (record.getEstado() == EstadoBatchRecord.DEVUELTO_PRODUCCION
                || record.getEstado() == EstadoBatchRecord.EN_CORRECCION)) {
            // En correccion la bandeja y la ejecucion solo admiten los controles
            // seleccionados para el ciclo vigente. Una adicion excepcional debe
            // incorporarse a ese mismo alcance para no quedar invisible e inejecutable.
            item.setRequiereRepeticion(true);
            item.setCicloRevisionNumero(Math.toIntExact(record.getCicloRevisionActual()));
        }
        return requeridoRepo.saveAndFlush(item);
    }

    private ControlRequerido nuevoRequisito(
            BatchRecord record, BatchRecordEtapa etapa, VersionPlanControl version,
            AplicabilidadPlanControl regla, Producto producto, Categoria categoria,
            TipoOrdenControl tipoOrden) {
        ControlRequerido item = baseRequisito(
                version, regla, record.getLoteResultado(), producto, categoria, tipoOrden);
        item.setOrigen(OrigenControlRequerido.BATCH_RECORD);
        item.setBatchRecord(record);
        item.setBatchRecordEtapa(etapa);
        if (record.getManufacturingVersion() == null || record.getManufacturingVersion().getId() == null) {
            throw new IllegalStateException(
                    "No se puede congelar un requisito sin version de manufactura verificable.");
        }
        item.setManufacturingVersionIdSnapshot(record.getManufacturingVersion().getId());
        if (etapa != null && etapa.getSeguimientoOrdenArea() != null
                && etapa.getSeguimientoOrdenArea().getRutaProcesoNode() != null) {
            var node = etapa.getSeguimientoOrdenArea().getRutaProcesoNode();
            item.setRutaNodoIdSnapshot(node.getId());
            item.setFrontendNodeIdSnapshot(node.getFrontendId());
            item.setNodoNombreSnapshot(node.getLabel());
            if (node.getRutaProcesoCatVersion() != null) {
                item.setRutaVersionIdSnapshot(node.getRutaProcesoCatVersion().getId());
            }
        }
        if (etapa != null && etapa.getOrdenFabricacionOperacion() != null) {
            var operacion = etapa.getOrdenFabricacionOperacion();
            item.setOrdenFabricacionOperacionIdSnapshot(operacion.getId());
            item.setFrontendNodeIdSnapshot(operacion.getFrontendNodeId());
            item.setNodoNombreSnapshot(operacion.getProcesoNombre());
        }
        return item;
    }

    private ControlRequerido baseRequisito(
            VersionPlanControl version, AplicabilidadPlanControl regla, Lote lote,
            Producto producto, Categoria categoria, TipoOrdenControl tipoOrden) {
        ControlRequerido item = new ControlRequerido();
        item.setVersionPlan(version);
        item.setAplicabilidad(regla);
        item.setLote(lote);
        item.setEstado(EstadoControlRequerido.PENDIENTE);
        item.setCreadoEn(AppTime.now());
        item.setPlanCodigoSnapshot(version.getPlan().getCodigo());
        item.setPlanNombreSnapshot(version.getPlan().getNombre());
        item.setAmbitoSnapshot(version.getPlan().getAmbito());
        item.setVersionNumeroSnapshot(version.getNumero());
        item.setProductoIdSnapshot(producto == null ? null : producto.getProductoId());
        item.setProductoNombreSnapshot(producto == null ? null : producto.getNombre());
        item.setCategoriaIdSnapshot(categoria == null ? null : categoria.getCategoriaId());
        item.setCategoriaNombreSnapshot(categoria == null ? null : categoria.getCategoriaNombre());
        item.setTipoOrdenSnapshot(tipoOrden);
        item.setPuntoAplicacionSnapshot(regla.getPuntoAplicacion());
        item.setAreaOperativaIdSnapshot(regla.getAreaOperativa() == null
                ? null : regla.getAreaOperativa().getAreaId());
        item.setAreaOperativaNombreSnapshot(regla.getAreaOperativa() == null
                ? null : regla.getAreaOperativa().getNombre());
        item.setProcesoIdSnapshot(regla.getProceso() == null ? null : regla.getProceso().getProcesoId());
        item.setProcesoNombreSnapshot(regla.getProceso() == null ? null : regla.getProceso().getNombre());
        item.setMomentoSnapshot(regla.getMomento());
        item.setPuntoExigenciaSnapshot(regla.getPuntoExigencia());
        return item;
    }

    private boolean coincideProducto(AplicabilidadPlanControl regla, Producto producto, Categoria categoria) {
        if (producto == null) return false;
        if (regla.getProductosExcluidos().stream()
                .anyMatch(p -> p.getProductoId().equals(producto.getProductoId()))) return false;
        if (regla.isLegadoGlobal()) return true;
        if (regla.getProducto() != null) {
            return regla.getProducto().getProductoId().equals(producto.getProductoId());
        }
        return regla.getCategoria() != null && categoria != null
                && regla.getCategoria().getCategoriaId() == categoria.getCategoriaId();
    }

    private boolean coincideTipo(AplicabilidadPlanControl regla, TipoOrdenControl actual) {
        return regla.getTipoOrden() == TipoOrdenControl.AMBAS || regla.getTipoOrden() == actual;
    }

    private boolean coincideEtapa(AplicabilidadPlanControl regla, BatchRecordEtapa etapa) {
        if (regla.getAreaOperativa() != null
                && regla.getAreaOperativa().getAreaId() != etapa.getAreaOperativa().getAreaId()) return false;
        if (regla.getProceso() == null) return true;
        int esperado = regla.getProceso().getProcesoId();
        if (etapa.getSeguimientoOrdenArea() != null
                && etapa.getSeguimientoOrdenArea().getRutaProcesoNode() != null
                && etapa.getSeguimientoOrdenArea().getRutaProcesoNode().getProcesoProduccion() != null) {
            return etapa.getSeguimientoOrdenArea().getRutaProcesoNode().getProcesoProduccion().getProcesoId() == esperado;
        }
        return etapa.getOrdenFabricacionOperacion() != null
                && Objects.equals(etapa.getOrdenFabricacionOperacion().getProcesoProduccionId(), esperado);
    }

    private Categoria resolverCategoria(BatchRecord record) {
        if (record.getProductoResultado() instanceof Terminado terminado) return terminado.getCategoria();
        if (record.getOrdenFabricacion() != null
                && record.getOrdenFabricacion().getOrdenProduccionOrigen() != null
                && record.getOrdenFabricacion().getOrdenProduccionOrigen().getProducto()
                instanceof Terminado terminadoOrigen) {
            return terminadoOrigen.getCategoria();
        }
        return null;
    }

    private Categoria resolverCategoria(Lote lote) {
        Producto producto = resolverProducto(lote);
        if (producto instanceof Terminado terminado) return terminado.getCategoria();
        if (lote.getOrdenFabricacion() != null
                && lote.getOrdenFabricacion().getOrdenProduccionOrigen() != null
                && lote.getOrdenFabricacion().getOrdenProduccionOrigen().getProducto()
                instanceof Terminado terminadoOrigen) {
            return terminadoOrigen.getCategoria();
        }
        return null;
    }

    private Producto resolverProducto(Lote lote) {
        if (lote.getProducto() != null) return lote.getProducto();
        if (lote.getOrdenProduccion() != null) return lote.getOrdenProduccion().getProducto();
        return lote.getOrdenFabricacion() == null ? null : lote.getOrdenFabricacion().getSemiTerminado();
    }

    private Long resolverManufacturingVersionId(Lote lote) {
        if (lote.getOrdenProduccion() != null && lote.getOrdenProduccion().getManufacturingVersion() != null) {
            return lote.getOrdenProduccion().getManufacturingVersion().getId();
        }
        if (lote.getOrdenFabricacion() != null && lote.getOrdenFabricacion().getManufacturingVersion() != null) {
            return lote.getOrdenFabricacion().getManufacturingVersion().getId();
        }
        return null;
    }

    /**
     * La version aplicable queda determinada por la fecha de creacion de la
     * orden/lote, no por la fecha en que un usuario abre la bandeja. Esto evita
     * que una publicacion posterior altere lotes ya activos.
     */
    private List<VersionPlanControl> versionesEfectivas(LocalDateTime fechaReferencia) {
        List<VersionPlanControl> candidatas = versionRepo.findByEstadoIn(
                List.of(EstadoVersionPlanControl.VIGENTE, EstadoVersionPlanControl.RETIRADA));
        Map<Long, List<VersionPlanControl>> porPlan = candidatas.stream()
                .collect(java.util.stream.Collectors.groupingBy(v -> v.getPlan().getId()));
        List<VersionPlanControl> result = new ArrayList<>();
        for (List<VersionPlanControl> versiones : porPlan.values()) {
            Optional<VersionPlanControl> efectiva = fechaReferencia == null ? Optional.empty()
                    : versiones.stream()
                    .filter(v -> v.getPublicadaEn() != null && !v.getPublicadaEn().isAfter(fechaReferencia))
                    .filter(v -> v.getRetiradaEn() == null || fechaReferencia.isBefore(v.getRetiradaEn()))
                    .max(Comparator.comparing(VersionPlanControl::getPublicadaEn)
                            .thenComparing(VersionPlanControl::getNumero));
            if (efectiva.isEmpty()) {
                // Las versiones migradas no poseen una fecha regulatoria historica fiable.
                // Solo la legacy que seguia vigente al corte se usa como fallback explícito.
                efectiva = versiones.stream()
                        .filter(v -> v.getLegacyPlantilla() != null
                                && v.getEstado() == EstadoVersionPlanControl.VIGENTE)
                        .max(Comparator.comparing(VersionPlanControl::getNumero));
            }
            efectiva.ifPresent(result::add);
        }
        return result.stream().sorted(Comparator.comparing(v -> v.getPlan().getCodigo())).toList();
    }

    private LocalDateTime fechaReferencia(BatchRecord record) {
        if (record.getOrdenFabricacion() != null) return record.getOrdenFabricacion().getFechaCreacion();
        return record.getOrdenProduccion() == null ? record.getCreadoEn()
                : record.getOrdenProduccion().getFechaCreacion();
    }

    private LocalDateTime fechaReferencia(Lote lote) {
        if (lote.getOrdenFabricacion() != null) return lote.getOrdenFabricacion().getFechaCreacion();
        return lote.getOrdenProduccion() == null ? null : lote.getOrdenProduccion().getFechaCreacion();
    }

    private void validarEstadoAdicionExcepcional(BatchRecord record, AmbitoControl ambito) {
        EstadoBatchRecord estado = record.getEstado();
        if (estado == EstadoBatchRecord.APROBADO || estado == EstadoBatchRecord.RECHAZADO
                || estado == EstadoBatchRecord.CERRADO || estado == EstadoBatchRecord.ANULADO) {
            throw new IllegalStateException(
                    "Un expediente terminal no admite requisitos nuevos por una via lateral.");
        }
        if (ambito == AmbitoControl.PROCESO
                && estado != EstadoBatchRecord.EN_EJECUCION
                && estado != EstadoBatchRecord.DEVUELTO_PRODUCCION
                && estado != EstadoBatchRecord.EN_CORRECCION) {
            throw new IllegalStateException(
                    "Un requisito de proceso solo puede agregarse durante fabricacion o correccion.");
        }
        if (ambito == AmbitoControl.CALIDAD
                && estado != EstadoBatchRecord.EN_EJECUCION
                && estado != EstadoBatchRecord.PENDIENTE_REVISION) {
            throw new IllegalStateException(
                    "Un requisito de Calidad solo puede agregarse durante fabricacion o revision de Calidad.");
        }
    }

    /**
     * Resuelve cada punto operativo real del lote. Una regla de salida se materializa
     * una vez por cada nodo u operacion concreta que coincida con area+proceso; la clave
     * de contexto permite deduplicar reglas superpuestas sin perder salidas repetidas.
     */
    private List<ContextoOperacion> resolverContextosIndependientes(
            Lote lote, AplicabilidadPlanControl regla) {
        if (regla.getPuntoAplicacion() == PuntoAplicacionControl.LOTE_FINAL) {
            return List.of(ContextoOperacion.loteFinal());
        }
        int areaId = regla.getAreaOperativa().getAreaId();
        int procesoId = regla.getProceso().getProcesoId();
        if (lote.getOrdenProduccion() != null) {
            List<SeguimientoOrdenArea> coincidencias = seguimientoRepo
                    .findByOrdenProduccion_OrdenIdOrderByPosicionSecuenciaAsc(
                            lote.getOrdenProduccion().getOrdenId())
                    .stream()
                    .filter(s -> s.getAreaOperativa() != null
                            && s.getAreaOperativa().getAreaId() == areaId)
                    .filter(s -> s.getRutaProcesoNode() != null
                            && s.getRutaProcesoNode().getProcesoProduccion() != null
                            && s.getRutaProcesoNode().getProcesoProduccion().getProcesoId() == procesoId)
                    .toList();
            return coincidencias.stream().map(seguimiento -> {
                var nodo = seguimiento.getRutaProcesoNode();
                return new ContextoOperacion(
                        regla.getPuntoAplicacion() + ":OP:" + nodo.getId(),
                        nodo.getRutaProcesoCatVersion() == null ? null
                                : nodo.getRutaProcesoCatVersion().getId(),
                        nodo.getId(), null, nodo.getFrontendId(), nodo.getLabel());
            }).toList();
        }
        List<OrdenFabricacionOperacion> coincidencias = operacionFabricacionRepo
                .findByOrdenFabricacion_OrdenFabricacionIdOrderByPosicionSecuenciaAsc(
                        lote.getOrdenFabricacion().getOrdenFabricacionId())
                .stream()
                .filter(o -> o.getAreaOperativa() != null
                        && o.getAreaOperativa().getAreaId() == areaId)
                .filter(o -> Objects.equals(o.getProcesoProduccionId(), procesoId))
                .toList();
        return coincidencias.stream().map(operacion -> new ContextoOperacion(
                regla.getPuntoAplicacion() + ":OF:" + operacion.getId(),
                null, null, operacion.getId(), operacion.getFrontendNodeId(),
                operacion.getProcesoNombre())).toList();
    }

    private Comparator<AplicabilidadPlanControl> ordenAplicabilidad() {
        return Comparator
                .comparing((AplicabilidadPlanControl a) -> a.getProducto() == null ? 1 : 0)
                .thenComparing(a -> a.getTipoOrden() == TipoOrdenControl.AMBAS ? 1 : 0)
                .thenComparing(AplicabilidadPlanControl::getId,
                        Comparator.nullsLast(Comparator.naturalOrder()));
    }

    private record ContextoOperacion(
            String clave, Long rutaVersionId, Long rutaNodoId,
            Long operacionFabricacionId, String frontendNodeId, String nodoNombre) {
        private static ContextoOperacion loteFinal() {
            return new ContextoOperacion(PuntoAplicacionControl.LOTE_FINAL.name(),
                    null, null, null, null, null);
        }
    }

    private TipoOrdenControl tipoOrden(BatchRecord record) {
        return record.getOrdenFabricacion() == null ? TipoOrdenControl.OP : TipoOrdenControl.OF;
    }

    private boolean bloquea(ControlRequerido item) {
        return item.getEstado() != EstadoControlRequerido.CONFORME
                && item.getEstado() != EstadoControlRequerido.ACEPTADO_POR_DESVIACION;
    }

    private boolean bloqueaTransicion(
            BatchRecord record, PuntoExigenciaControl punto, ControlRequerido item) {
        if (punto == PuntoExigenciaControl.ENVIO_CALIDAD
                && item.getAmbitoSnapshot() == AmbitoControl.CALIDAD
                && item.isRequiereRevalidacion()
                && item.getCicloRevisionNumero() != null
                && item.getCicloRevisionNumero().longValue()
                == record.getCicloRevisionActual() + 1) {
            // La revalidacion nace al devolver, pero se ejecuta por Calidad una
            // vez creado el ciclo siguiente. No puede impedir el reenvio que
            // precisamente habilita esa revision.
            return false;
        }
        return bloquea(item);
    }

    private boolean bloqueaLiberacion(ControlRequerido item) {
        // RECHAZAR es una disposicion terminal del requisito, no una simple
        // politica de exigencia. Tambien bloquea cuando el plan era informativo.
        if (desviacionRepo.existsByControlRequerido_IdAndEstadoAndDisposicion(
                item.getId(), EstadoDesviacionControl.CERRADA,
                DisposicionDesviacionControl.RECHAZAR)) {
            return true;
        }
        if (item.isRequiereRepeticion()
                || item.isRequiereRevalidacion()
                || item.getEstado() == EstadoControlRequerido.POR_REVALIDAR) {
            return true;
        }
        return item.getPuntoExigenciaSnapshot() != PuntoExigenciaControl.INFORMATIVO
                && bloquea(item);
    }

    private BloqueoControlDTO toBloqueo(ControlRequerido item) {
        return new BloqueoControlDTO(item.getId(), item.getPlanCodigoSnapshot(), item.getPlanNombreSnapshot(),
                item.getAmbitoSnapshot(), item.getEstado(), item.getPuntoExigenciaSnapshot(),
                "El control " + item.getPlanCodigoSnapshot() + " esta " + item.getEstado() + ".");
    }

    private long count(List<ControlRequerido> items, EstadoControlRequerido estado) {
        return items.stream().filter(i -> i.getEstado() == estado).count();
    }
}
