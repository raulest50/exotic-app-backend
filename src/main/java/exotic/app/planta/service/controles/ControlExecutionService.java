package exotic.app.planta.service.controles;

import exotic.app.planta.config.AppTime;
import exotic.app.planta.model.calidad.ControlProcesoEjecucion;
import exotic.app.planta.model.controles.*;
import exotic.app.planta.model.controles.dto.ControlDTOs.*;
import exotic.app.planta.model.users.User;
import exotic.app.planta.model.produccion.batchrecord.BatchRecord;
import exotic.app.planta.model.produccion.batchrecord.EstadoBatchRecord;
import exotic.app.planta.repo.controles.ControlRequeridoRepo;
import exotic.app.planta.repo.controles.DesviacionControlRepo;
import exotic.app.planta.repo.controles.EjecucionControlRepo;
import exotic.app.planta.repo.controles.RevalidacionControlRepo;
import exotic.app.planta.repo.produccion.batchrecord.BatchRecordRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ControlExecutionService {
    private final ControlRequeridoRepo requeridoRepo;
    private final EjecucionControlRepo ejecucionRepo;
    private final DesviacionControlRepo desviacionRepo;
    private final RevalidacionControlRepo revalidacionRepo;
    private final ControlPlanService planService;
    private final BatchRecordRepo batchRecordRepo;
    private final LegacyControlExecutionProjection legacyProjection;

    @Transactional(readOnly = true)
    public Page<PendienteResponse> pendientes(
            AmbitoControl ambito, Long loteId, Long batchRecordId, Long batchRecordEtapaId,
            Integer areaId,
            TipoOrdenControl tipoOrden, MomentoControl momento,
            List<EstadoControlRequerido> estados, LocalDate vencimientoDesde,
            LocalDate vencimientoHasta, String search, int page, int size) {
        validarPaginacion(page, size);
        if (vencimientoDesde != null && vencimientoHasta != null
                && vencimientoHasta.isBefore(vencimientoDesde)) {
            throw new IllegalArgumentException("El vencimiento final no puede ser anterior al inicial.");
        }
        List<EstadoControlRequerido> filtroEstados = estados == null || estados.isEmpty()
                ? List.of(EstadoControlRequerido.PENDIENTE, EstadoControlRequerido.NO_CONFORME,
                EstadoControlRequerido.POR_REVALIDAR) : estados;
        return requeridoRepo.buscarPendientes(ambito,
                        filtroEstados, loteId, batchRecordId, batchRecordEtapaId, areaId,
                        tipoOrden, momento, vencimientoDesde, vencimientoHasta,
                        limpiarNullable(search),
                        PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "creadoEn")))
                .map(this::toPendiente);
    }

    @Transactional
    public EjecucionDetalleResponse ejecutar(
            AmbitoControl ambito, User actor, EjecucionWriteRequest request) {
        return ejecutarInterno(ambito, actor, request, null);
    }

    /**
     * Entry point used only by the temporary V077 adapter. The supplied legacy
     * execution and the neutral execution are committed or rolled back together.
     */
    @Transactional
    public EjecucionDetalleResponse ejecutarDesdeLegado(
            AmbitoControl ambito,
            User actor,
            EjecucionWriteRequest request,
            ControlProcesoEjecucion legacyExecution) {
        if (legacyExecution == null || legacyExecution.getId() == null) {
            throw new IllegalArgumentException("La ejecucion legada debe estar persistida antes de sincronizarse.");
        }
        if (actor == null || actor.getId() == null || legacyExecution.getUsuario() == null
                || !actor.getId().equals(legacyExecution.getUsuario().getId())
                || legacyExecution.getFechaRegistro() == null) {
            throw new IllegalArgumentException(
                    "La ejecucion legada debe conservar actor y fecha autenticados.");
        }
        if (ejecucionRepo.findByLegacyEjecucion_Id(legacyExecution.getId()).isPresent()) {
            throw new IllegalStateException("La ejecucion legada ya fue sincronizada.");
        }
        return ejecutarInterno(ambito, actor, request, legacyExecution);
    }

    private EjecucionDetalleResponse ejecutarInterno(
            AmbitoControl ambito,
            User actor,
            EjecucionWriteRequest request,
            ControlProcesoEjecucion legacyExecution) {
        ControlRequerido requerido = bloquearRequisitoConExpediente(request.controlRequeridoId());
        if (requerido.getAmbitoSnapshot() != ambito) {
            throw new NoSuchElementException("El control requerido no pertenece a este ambito.");
        }
        validarEstadoExpediente(requerido);
        if (requerido.getEstado() == EstadoControlRequerido.ACEPTADO_POR_DESVIACION) {
            throw new IllegalStateException("El control ya fue dispuesto y no admite ejecuciones ordinarias.");
        }
        if (desviacionTerminalRechazo(requerido.getId())) {
            throw new IllegalStateException("El control fue rechazado por disposicion y no admite repeticiones ordinarias.");
        }

        List<EjecucionControl> anteriores = ejecucionRepo
                .findByControlRequerido_IdOrderByFechaRegistroDescIdDesc(requerido.getId());
        EjecucionControl repeticionDe = validarRepeticion(requerido, anteriores, request);
        Map<Long, CaracteristicaPlanControl> caracteristicas = requerido.getVersionPlan().getCaracteristicas()
                .stream().collect(Collectors.toMap(CaracteristicaPlanControl::getId, Function.identity()));
        List<MuestraEjecucionControl> muestras = construirMuestras(request, caracteristicas);
        validarMatrizCompleta(muestras, caracteristicas.values());
        ResultadoEjecucionControl resultado = evaluar(muestras);

        EjecucionControl ejecucion = new EjecucionControl();
        ejecucion.setControlRequerido(requerido);
        ejecucion.setRepeticionDe(repeticionDe);
        ejecucion.setMotivoRepeticion(limpiarNullable(request.motivoRepeticion()));
        ejecucion.setUsuario(actor);
        ejecucion.setFechaRegistro(legacyExecution == null
                ? AppTime.now() : legacyExecution.getFechaRegistro());
        ejecucion.setResultado(resultado);
        ejecucion.setObservaciones(limpiarNullable(request.observaciones()));
        for (MuestraEjecucionControl muestra : muestras) {
            muestra.setEjecucion(ejecucion);
            ejecucion.getMuestras().add(muestra);
        }
        if (legacyExecution != null) {
            validarVinculoLegado(requerido, legacyExecution);
            legacyExecution.setResultado(exotic.app.planta.model.calidad.ResultadoControlProceso.valueOf(
                    resultado.name()));
            ejecucion.setLegacyEjecucion(legacyExecution);
            if (requerido.getOrigen() == OrigenControlRequerido.LEGACY
                    && requerido.getLegacyEjecucion() == null) {
                requerido.setLegacyEjecucion(legacyExecution);
            }
        } else if (requerido.getVersionPlan().getLegacyPlantilla() != null) {
            ejecucion.setLegacyEjecucion(legacyProjection.createMirror(
                    requerido, actor, ejecucion.getFechaRegistro(), resultado,
                    ejecucion.getObservaciones(), muestras));
        }
        ejecucionRepo.saveAndFlush(ejecucion);

        requerido.setRequiereRevalidacion(false);
        if (resultado == ResultadoEjecucionControl.NO_CONFORME) {
            requerido.setEstado(EstadoControlRequerido.NO_CONFORME);
            abrirDesviacion(requerido, ejecucion, actor);
        } else if (desviacionRepo.existsByControlRequerido_IdAndEstadoNot(
                requerido.getId(), EstadoDesviacionControl.CERRADA)) {
            requerido.setEstado(EstadoControlRequerido.NO_CONFORME);
        } else {
            requerido.setEstado(EstadoControlRequerido.CONFORME);
            requerido.setRequiereRepeticion(false);
        }
        requeridoRepo.flush();
        return toDetalle(ejecucion);
    }

    private void validarVinculoLegado(
            ControlRequerido requerido, ControlProcesoEjecucion legacyExecution) {
        if (requerido.getVersionPlan().getLegacyPlantilla() == null
                || legacyExecution.getPlantilla() == null
                || !requerido.getVersionPlan().getLegacyPlantilla().getId().equals(
                legacyExecution.getPlantilla().getId())
                || legacyExecution.getLote() == null
                || !requerido.getLote().getId().equals(legacyExecution.getLote().getId())) {
            throw new IllegalArgumentException(
                    "La ejecucion legada no corresponde al requisito neutral resuelto.");
        }
    }

    @Transactional(readOnly = true)
    public Page<EjecucionResumenResponse> historial(
            AmbitoControl ambito, Long loteId, Long batchRecordId, String search,
            ResultadoEjecucionControl resultado, LocalDate desde, LocalDate hasta, int page, int size) {
        validarPaginacion(page, size);
        if (desde != null && hasta != null && hasta.isBefore(desde)) {
            throw new IllegalArgumentException("La fecha final no puede ser anterior a la inicial.");
        }
        return ejecucionRepo.buscar(ambito, loteId, batchRecordId, limpiarNullable(search), resultado,
                        desde == null ? null : desde.atStartOfDay(),
                        hasta == null ? null : hasta.plusDays(1).atStartOfDay(),
                        PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "fechaRegistro")))
                .map(this::toResumen);
    }

    /** Evidencia detallada y de solo lectura para la revisión regulatoria del expediente. */
    @Transactional(readOnly = true)
    public List<EjecucionDetalleResponse> evidenciaPorBatchRecord(
            AmbitoControl ambito,
            Long batchRecordId) {
        return ejecucionRepo
                .findByControlRequerido_BatchRecord_IdAndControlRequerido_AmbitoSnapshotOrderByFechaRegistroAscIdAsc(
                        batchRecordId, ambito)
                .stream()
                .map(this::toDetalle)
                .toList();
    }

    @Transactional(readOnly = true)
    public EjecucionDetalleResponse detalle(AmbitoControl ambito, Long id) {
        return toDetalle(ejecucionRepo.findByIdAndControlRequerido_AmbitoSnapshot(id, ambito)
                .orElseThrow(() -> new NoSuchElementException("Ejecucion no encontrada en este ambito.")));
    }

    @Transactional
    public RevalidacionResponse revalidar(
            AmbitoControl ambito, Long requisitoId, User actor, RevalidacionWriteRequest request) {
        if (ambito != AmbitoControl.CALIDAD) {
            throw new IllegalArgumentException("La revalidacion solo existe para ensayos de Calidad.");
        }
        ControlRequerido requerido = bloquearRequisitoConExpediente(requisitoId);
        if (requerido.getAmbitoSnapshot() != AmbitoControl.CALIDAD
                || requerido.getEstado() != EstadoControlRequerido.POR_REVALIDAR
                || !requerido.isRequiereRevalidacion()) {
            throw new IllegalStateException("El ensayo no esta pendiente de revalidacion.");
        }
        validarEstadoExpediente(requerido);
        Integer ciclo = requerido.getCicloRevisionNumero();
        if (ciclo == null || ciclo <= 0 || requerido.getBatchRecord() == null
                || requerido.getBatchRecord().getCicloRevisionActual() != ciclo) {
            throw new IllegalStateException("El ciclo de revalidacion ya no es el vigente.");
        }
        if (revalidacionRepo.existsByControlRequerido_IdAndCicloRevisionNumero(requisitoId, ciclo)) {
            throw new IllegalStateException("El ensayo ya fue revalidado en este ciclo.");
        }
        EjecucionControl ejecucion = ejecucionRepo
                .findByControlRequerido_IdOrderByFechaRegistroDescIdDesc(requisitoId)
                .stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("El ensayo no tiene una ejecucion para revalidar."));
        if (ejecucion.getResultado() != ResultadoEjecucionControl.CONFORME
                || desviacionRepo.existsByControlRequerido_IdAndEstadoNot(
                requisitoId, EstadoDesviacionControl.CERRADA)) {
            throw new IllegalStateException("Solo puede revalidarse un resultado conforme sin desviaciones abiertas.");
        }
        RevalidacionControl evento = new RevalidacionControl();
        evento.setControlRequerido(requerido);
        evento.setEjecucionRevalidada(ejecucion);
        evento.setCicloRevisionNumero(ciclo);
        evento.setJustificacion(request.justificacion().trim());
        evento.setConfirmadaEn(AppTime.now());
        evento.setConfirmadaPor(actor);
        revalidacionRepo.saveAndFlush(evento);
        requerido.setRequiereRevalidacion(false);
        requerido.setEstado(EstadoControlRequerido.CONFORME);
        requeridoRepo.flush();
        return new RevalidacionResponse(evento.getId(), requisitoId, ejecucion.getId(), ciclo,
                evento.getJustificacion(), evento.getConfirmadaEn(), actor.getUsername());
    }

    public PendienteResponse toPendiente(ControlRequerido item) {
        EjecucionControl ultima = item.getEjecuciones().stream().findFirst().orElse(null);
        return new PendienteResponse(item.getId(), item.getEstado(), item.getVersionPlan().getPlan().getId(),
                item.getPlanCodigoSnapshot(), item.getPlanNombreSnapshot(), item.getVersionPlan().getId(),
                item.getVersionNumeroSnapshot(), item.getVersionPlan().getProposito(), item.getAmbitoSnapshot(),
                item.getLote().getId(), item.getLote().getBatchNumber(), item.getProductoIdSnapshot(),
                item.getProductoNombreSnapshot(), item.getTipoOrdenSnapshot(),
                item.getLote().getOrdenProduccion() == null ? null
                        : item.getLote().getOrdenProduccion().getOrdenId(),
                item.getLote().getOrdenFabricacion() == null ? null
                        : item.getLote().getOrdenFabricacion().getOrdenFabricacionId(),
                item.getLote().getExpirationDate(),
                item.getBatchRecord() == null ? null : item.getBatchRecord().getId(),
                item.getBatchRecord() == null ? null : item.getBatchRecord().getCodigo(),
                item.getBatchRecordEtapa() == null ? null : item.getBatchRecordEtapa().getId(),
                item.getBatchRecordEtapa() == null ? null : item.getBatchRecordEtapa().getNombre(),
                 item.getAreaOperativaIdSnapshot(), item.getAreaOperativaNombreSnapshot(),
                 item.getProcesoIdSnapshot(), item.getProcesoNombreSnapshot(),
                 item.getMomentoSnapshot(), item.getPuntoExigenciaSnapshot(),
                 item.isRequiereRepeticion(), item.isRequiereRevalidacion(),
                 item.isAgregadoExcepcionalmente(), item.getMotivoAdicion(),
                 item.getAgregadoPor() == null ? null : item.getAgregadoPor().getUsername(),
                 item.getRevisionAdicion() == null ? null : item.getRevisionAdicion().getId(),
                 item.getFirmaAdicion() == null ? null : item.getFirmaAdicion().getId(),
                 ultima == null ? null : ultima.getId(), ultima == null ? null : ultima.getFechaRegistro(),
                 item.getVersionPlan().getCaracteristicas().stream().map(planService::toResponse).toList());
    }

    private EjecucionControl validarRepeticion(
            ControlRequerido requerido, List<EjecucionControl> anteriores, EjecucionWriteRequest request) {
        if (anteriores.isEmpty()) {
            if (request.repeticionDeId() != null || limpiarNullable(request.motivoRepeticion()) != null) {
                throw new IllegalArgumentException("La primera ejecucion no es una repeticion.");
            }
            return null;
        }
        if (request.repeticionDeId() == null || limpiarNullable(request.motivoRepeticion()) == null) {
            throw new IllegalArgumentException("Toda ejecucion posterior requiere ejecucion anterior y motivo.");
        }
        EjecucionControl ultima = anteriores.getFirst();
        if (!ultima.getId().equals(request.repeticionDeId())) {
            throw new IllegalArgumentException("La repeticion debe referenciar la ejecucion mas reciente.");
        }
        if (!ultima.getControlRequerido().getId().equals(requerido.getId())) {
            throw new IllegalArgumentException("La ejecucion anterior pertenece a otro requisito.");
        }
        return ultima;
    }

    private List<MuestraEjecucionControl> construirMuestras(
            EjecucionWriteRequest request, Map<Long, CaracteristicaPlanControl> caracteristicas) {
        if (request.muestras() == null || request.muestras().isEmpty()) {
            throw new IllegalArgumentException("La matriz de muestras es obligatoria.");
        }
        Set<String> claves = new HashSet<>();
        List<MuestraEjecucionControl> result = new ArrayList<>();
        for (MuestraWriteRequest muestraRequest : request.muestras()) {
            CaracteristicaPlanControl caracteristica = caracteristicas.get(muestraRequest.caracteristicaId());
            if (caracteristica == null) {
                throw new IllegalArgumentException("Una muestra referencia una caracteristica ajena al plan.");
            }
            String clave = caracteristica.getId() + ":" + muestraRequest.numeroMuestra();
            if (!claves.add(clave)) {
                throw new IllegalArgumentException("La muestra esta repetida: " + clave);
            }
            if (muestraRequest.numeroMuestra() < 1
                    || muestraRequest.numeroMuestra() > caracteristica.getCantidadMuestras()) {
                throw new IllegalArgumentException("Numero de muestra fuera del rango configurado.");
            }
            MuestraEjecucionControl muestra = new MuestraEjecucionControl();
            muestra.setCaracteristica(caracteristica);
            muestra.setNumeroMuestra(muestraRequest.numeroMuestra());
            Set<Integer> indices = new HashSet<>();
            for (LecturaWriteRequest lecturaRequest : muestraRequest.lecturas()) {
                if (!indices.add(lecturaRequest.indiceUnidad())) {
                    throw new IllegalArgumentException("El indice de lectura esta repetido.");
                }
                if (lecturaRequest.indiceUnidad() < 1
                        || lecturaRequest.indiceUnidad() > caracteristica.getUnidadesPorMuestra()) {
                    throw new IllegalArgumentException("Indice de lectura fuera del rango configurado.");
                }
                LecturaEjecucionControl lectura = construirLectura(caracteristica, lecturaRequest);
                lectura.setMuestra(muestra);
                muestra.getLecturas().add(lectura);
            }
            result.add(muestra);
        }
        return result;
    }

    private LecturaEjecucionControl construirLectura(
            CaracteristicaPlanControl caracteristica, LecturaWriteRequest request) {
        boolean numerico = request.valorNumerico() != null;
        boolean booleano = request.valorBooleano() != null;
        if (numerico == booleano) {
            throw new IllegalArgumentException("Cada lectura debe contener exactamente un valor.");
        }
        LecturaEjecucionControl lectura = new LecturaEjecucionControl();
        lectura.setIndiceUnidad(request.indiceUnidad());
        if (caracteristica.getTipo() == TipoCaracteristicaControl.NUMERICA) {
            BigDecimal valor = numerico ? normalizarDecimal(request.valorNumerico()) : null;
            if (!numerico || !cabeNumeric20_8(valor)) {
                throw new IllegalArgumentException("La lectura numerica excede NUMERIC(20,8) o no corresponde al tipo.");
            }
            if (valor.scale() > caracteristica.getEscalaVisible()) {
                throw new IllegalArgumentException("La lectura excede la escala visible configurada ("
                        + caracteristica.getEscalaVisible() + ").");
            }
            lectura.setValorNumerico(valor);
        } else {
            if (!booleano) {
                throw new IllegalArgumentException("La lectura debe ser booleana.");
            }
            lectura.setValorBooleano(request.valorBooleano());
        }
        return lectura;
    }

    private void validarMatrizCompleta(
            List<MuestraEjecucionControl> muestras, Collection<CaracteristicaPlanControl> caracteristicas) {
        for (CaracteristicaPlanControl caracteristica : caracteristicas) {
            List<MuestraEjecucionControl> deCaracteristica = muestras.stream()
                    .filter(m -> m.getCaracteristica().getId().equals(caracteristica.getId())).toList();
            if (deCaracteristica.size() != caracteristica.getCantidadMuestras()
                    || deCaracteristica.stream().anyMatch(m ->
                    m.getLecturas().size() != caracteristica.getUnidadesPorMuestra())) {
                throw new IllegalArgumentException("La matriz de muestras esta incompleta para "
                        + caracteristica.getNombre() + ".");
            }
        }
        int esperadas = caracteristicas.stream().mapToInt(CaracteristicaPlanControl::getCantidadMuestras).sum();
        if (muestras.size() != esperadas) {
            throw new IllegalArgumentException("La matriz contiene muestras no esperadas.");
        }
    }

    private ResultadoEjecucionControl evaluar(List<MuestraEjecucionControl> muestras) {
        for (MuestraEjecucionControl muestra : muestras) {
            CaracteristicaPlanControl c = muestra.getCaracteristica();
            for (LecturaEjecucionControl lectura : muestra.getLecturas()) {
                if (c.getTipo() == TipoCaracteristicaControl.BOOLEANA) {
                    if (!Objects.equals(c.getValorBooleanoEsperado(), lectura.getValorBooleano())) {
                        return ResultadoEjecucionControl.NO_CONFORME;
                    }
                } else {
                    BigDecimal valor = lectura.getValorNumerico();
                    if ((c.getLimiteInferior() != null && valor.compareTo(c.getLimiteInferior()) < 0)
                            || (c.getLimiteSuperior() != null && valor.compareTo(c.getLimiteSuperior()) > 0)) {
                        return ResultadoEjecucionControl.NO_CONFORME;
                    }
                }
            }
        }
        return ResultadoEjecucionControl.CONFORME;
    }

    private void abrirDesviacion(ControlRequerido requerido, EjecucionControl ejecucion, User actor) {
        DesviacionControl desviacion = new DesviacionControl();
        desviacion.setControlRequerido(requerido);
        desviacion.setEjecucionOrigen(ejecucion);
        desviacion.setAmbito(requerido.getAmbitoSnapshot());
        desviacion.setEstado(EstadoDesviacionControl.ABIERTA);
        desviacion.setAbiertaEn(AppTime.now());
        desviacion.setAbiertaPor(actor);
        desviacionRepo.save(desviacion);
    }

    private EjecucionResumenResponse toResumen(EjecucionControl item) {
        ControlRequerido r = item.getControlRequerido();
        return new EjecucionResumenResponse(item.getId(), r.getId(),
                item.getRepeticionDe() == null ? null : item.getRepeticionDe().getId(), r.getAmbitoSnapshot(),
                r.getVersionPlan().getPlan().getId(), r.getPlanCodigoSnapshot(), r.getPlanNombreSnapshot(),
                r.getVersionNumeroSnapshot(), r.getLote().getId(), r.getLote().getBatchNumber(),
                r.getProductoIdSnapshot(), r.getProductoNombreSnapshot(), r.getTipoOrdenSnapshot(),
                r.getLote().getOrdenProduccion() == null ? null : r.getLote().getOrdenProduccion().getOrdenId(),
                r.getLote().getOrdenFabricacion() == null ? null
                        : r.getLote().getOrdenFabricacion().getOrdenFabricacionId(),
                r.getBatchRecord() == null ? null : r.getBatchRecord().getId(),
                r.getBatchRecord() == null ? null : r.getBatchRecord().getCodigo(),
                r.getBatchRecordEtapa() == null ? null : r.getBatchRecordEtapa().getId(),
                 r.getBatchRecordEtapa() == null ? null : r.getBatchRecordEtapa().getNombre(),
                 r.getAreaOperativaIdSnapshot(), r.getAreaOperativaNombreSnapshot(),
                 r.getProcesoIdSnapshot(), r.getProcesoNombreSnapshot(),
                 r.isAgregadoExcepcionalmente(), r.getMotivoAdicion(),
                 r.getAgregadoPor() == null ? null : r.getAgregadoPor().getUsername(),
                 r.getRevisionAdicion() == null ? null : r.getRevisionAdicion().getId(),
                 r.getFirmaAdicion() == null ? null : r.getFirmaAdicion().getId(),
                 item.getUsuario().getUsername(), item.getUsuario().getNombreCompleto(), item.getFechaRegistro(),
                item.getResultado(), item.getObservaciones(), item.getMotivoRepeticion(),
                desviacionRepo.findByEjecucionOrigen_Id(item.getId())
                        .map(DesviacionControl::getId).orElse(null));
    }

    private EjecucionDetalleResponse toDetalle(EjecucionControl item) {
        Long desviacionId = desviacionRepo.findByEjecucionOrigen_Id(item.getId())
                .map(DesviacionControl::getId).orElse(null);
        return new EjecucionDetalleResponse(toResumen(item), item.getMuestras().stream().map(m ->
                new MuestraResponse(m.getId(), m.getCaracteristica().getId(), m.getCaracteristica().getNombre(),
                        m.getCaracteristica().getTipo(), m.getCaracteristica().getUnidadSimboloSnapshot(),
                        m.getCaracteristica().getEscalaVisible(), m.getCaracteristica().getObjetivo(),
                        m.getCaracteristica().getLimiteInferior(), m.getCaracteristica().getLimiteSuperior(),
                        m.getCaracteristica().getValorBooleanoEsperado(), m.getNumeroMuestra(),
                        m.getLecturas().stream().map(l ->
                        new LecturaResponse(l.getId(), l.getIndiceUnidad(),
                                l.getValorNumerico(), l.getValorBooleano(),
                                lecturaConforme(m.getCaracteristica(), l))).toList())).toList(), desviacionId);
    }

    private void validarPaginacion(int page, int size) {
        if (page < 0 || size < 1 || size > 200) {
            throw new IllegalArgumentException("Paginacion fuera de rango.");
        }
    }

    private String limpiarNullable(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private boolean cabeNumeric20_8(BigDecimal value) {
        if (value == null || value.scale() < 0 || value.scale() > 8) return false;
        return Math.max(0, value.precision() - value.scale()) <= 12;
    }

    private BigDecimal normalizarDecimal(BigDecimal value) {
        BigDecimal normalizado = value.stripTrailingZeros();
        return normalizado.scale() < 0 ? normalizado.setScale(0) : normalizado;
    }

    private boolean desviacionTerminalRechazo(Long requisitoId) {
        return desviacionRepo.existsByControlRequerido_IdAndEstadoAndDisposicion(
                requisitoId, EstadoDesviacionControl.CERRADA, DisposicionDesviacionControl.RECHAZAR);
    }

    private boolean lecturaConforme(CaracteristicaPlanControl c, LecturaEjecucionControl lectura) {
        if (c.getTipo() == TipoCaracteristicaControl.BOOLEANA) {
            return Objects.equals(c.getValorBooleanoEsperado(), lectura.getValorBooleano());
        }
        BigDecimal valor = lectura.getValorNumerico();
        return valor != null
                && (c.getLimiteInferior() == null || valor.compareTo(c.getLimiteInferior()) >= 0)
                && (c.getLimiteSuperior() == null || valor.compareTo(c.getLimiteSuperior()) <= 0);
    }

    /**
     * Mantiene el mismo orden de bloqueos que las transiciones del expediente:
     * primero BatchRecord y despues el requisito. De este modo una liberacion y
     * una ejecucion concurrentes no pueden aceptar estados mutuamente obsoletos.
     */
    private ControlRequerido bloquearRequisitoConExpediente(Long requisitoId) {
        ControlRequerido referencia = requeridoRepo.findById(requisitoId)
                .orElseThrow(() -> new NoSuchElementException("Control requerido no encontrado."));
        if (referencia.getBatchRecord() != null) {
            batchRecordRepo.findByIdForUpdate(referencia.getBatchRecord().getId())
                    .orElseThrow(() -> new NoSuchElementException("Batch record no encontrado."));
        }
        return requeridoRepo.findByIdForUpdate(requisitoId)
                .orElseThrow(() -> new NoSuchElementException("Control requerido no encontrado."));
    }

    private void validarEstadoExpediente(ControlRequerido requerido) {
        BatchRecord record = requerido.getBatchRecord();
        if (record == null) return;
        EstadoBatchRecord estado = record.getEstado();
        boolean permitido;
        if (requerido.getAmbitoSnapshot() == AmbitoControl.PROCESO) {
            boolean correccion = estado == EstadoBatchRecord.DEVUELTO_PRODUCCION
                    || estado == EstadoBatchRecord.EN_CORRECCION;
            permitido = estado == EstadoBatchRecord.EN_EJECUCION
                    || (correccion && requerido.isRequiereRepeticion()
                    && requerido.getCicloRevisionNumero() != null
                    && requerido.getCicloRevisionNumero().longValue()
                    == record.getCicloRevisionActual());
        } else if (requerido.getMomentoSnapshot() == MomentoControl.REVISION_FINAL) {
            permitido = estado == EstadoBatchRecord.PENDIENTE_REVISION;
        } else {
            permitido = estado == EstadoBatchRecord.EN_EJECUCION
                    || estado == EstadoBatchRecord.EN_CORRECCION
                    || (estado == EstadoBatchRecord.PENDIENTE_REVISION
                    && (requerido.isRequiereRepeticion()
                    || requerido.isRequiereRevalidacion()
                    || requerido.getEstado() == EstadoControlRequerido.POR_REVALIDAR));
        }
        if (!permitido) {
            throw new IllegalStateException("El estado " + estado
                    + " del expediente no admite esta ejecucion de "
                    + requerido.getAmbitoSnapshot() + ".");
        }
    }
}
