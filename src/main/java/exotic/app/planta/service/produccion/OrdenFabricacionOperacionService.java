package exotic.app.planta.service.produccion;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import exotic.app.planta.model.calidad.EstadoControlProcesoPlantilla;
import exotic.app.planta.model.inventarios.EstadoCalidadLote;
import exotic.app.planta.model.inventarios.Lote;
import exotic.app.planta.model.inventarios.Movimiento;
import exotic.app.planta.model.inventarios.TransaccionAlmacen;
import exotic.app.planta.model.organizacion.AreaOperativa;
import exotic.app.planta.model.producto.manufacturing.procesos.ProcesoProduccionDocumentoVersion;
import exotic.app.planta.model.produccion.*;
import exotic.app.planta.model.produccion.batchrecord.*;
import exotic.app.planta.model.produccion.dto.OrdenFabricacionDTOs;
import exotic.app.planta.model.produccion.fabricacion.*;
import exotic.app.planta.model.users.User;
import exotic.app.planta.repo.calidad.ControlProcesoPlantillaRepo;
import exotic.app.planta.repo.inventarios.LoteRepo;
import exotic.app.planta.repo.inventarios.TransaccionAlmacenHeaderRepo;
import exotic.app.planta.repo.producto.procesos.AreaProduccionRepo;
import exotic.app.planta.repo.producto.procesos.ProcesoProduccionDocumentoVersionRepo;
import exotic.app.planta.repo.produccion.batchrecord.BatchRecordEtapaRepo;
import exotic.app.planta.repo.produccion.batchrecord.BatchRecordRepo;
import exotic.app.planta.repo.produccion.fabricacion.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
@Slf4j
public class OrdenFabricacionOperacionService {

    private static final int ALMACEN_GENERAL_AREA_ID = -1;
    private static final BigDecimal CANTIDAD_TOLERANCIA = new BigDecimal("0.0001");
    private static final BigDecimal CANTIDAD_MAXIMA = new BigDecimal("99999999999999.9999");

    private final OrdenFabricacionRepo ordenRepo;
    private final OrdenFabricacionOperacionRepo operacionRepo;
    private final OrdenFabricacionOperacionDependenciaRepo dependenciaRepo;
    private final OrdenFabricacionOperacionEventoRepo eventoRepo;
    private final BatchRecordRepo batchRecordRepo;
    private final BatchRecordEtapaRepo etapaRepo;
    private final AreaProduccionRepo areaRepo;
    private final ProcesoProduccionDocumentoVersionRepo poeRepo;
    private final ControlProcesoPlantillaRepo plantillaRepo;
    private final LoteRepo loteRepo;
    private final TransaccionAlmacenHeaderRepo transaccionRepo;
    private final BatchRecordService batchRecordService;
    private final ObjectMapper objectMapper;
    private final Clock applicationClock;

    public void inicializar(OrdenFabricacion orden, BatchRecord record) {
        if (orden == null || orden.getOrdenFabricacionId() == null) {
            throw new IllegalArgumentException("La OF persistida es obligatoria.");
        }
        if (operacionRepo.existsByOrdenFabricacion_OrdenFabricacionId(
                orden.getOrdenFabricacionId())) {
            return;
        }

        Graph graph = parseGraph(orden.getManufacturingVersion().getProcesoProduccionJson());
        LocalDateTime ahora = LocalDateTime.now(applicationClock);
        boolean liberada = orden.getEstado() == EstadoOrdenFabricacion.LIBERADA
                || orden.getEstado() == EstadoOrdenFabricacion.EN_EJECUCION;

        Set<Integer> procesoIds = new LinkedHashSet<>();
        graph.nodes().values().forEach(node -> {
            if (node.procesoId() != null) procesoIds.add(node.procesoId());
        });
        Map<Integer, ProcesoProduccionDocumentoVersion> poePorProceso = procesoIds.isEmpty()
                ? Map.of()
                : poeRepo.findAllByProcesoProcesoIdInAndEstado(
                                procesoIds, ProcesoProduccionDocumentoVersion.Estado.VIGENTE)
                        .stream().collect(java.util.stream.Collectors.toMap(
                                documento -> documento.getProceso().getProcesoId(),
                                documento -> documento));

        Map<String, OrdenFabricacionOperacion> persisted = new LinkedHashMap<>();
        int secuencia = 0;
        for (String frontendId : graph.topologicalOrder()) {
            Node node = graph.nodes().get(frontendId);
            AreaOperativa area = areaRepo.findById(node.areaId())
                    .orElseThrow(() -> new IllegalStateException(
                            "La version de manufactura referencia un area inexistente: "
                                    + node.areaId()));
            if (area.getAreaId() == ALMACEN_GENERAL_AREA_ID) {
                throw new IllegalStateException(
                        "Almacen General no debe modelarse como operacion de una OF.");
            }
            OrdenFabricacionOperacion operacion = new OrdenFabricacionOperacion();
            operacion.setOrdenFabricacion(orden);
            operacion.setAreaOperativa(area);
            operacion.setFrontendNodeId(frontendId);
            operacion.setProcesoProduccionId(node.procesoId());
            operacion.setProcesoNombre(node.nombre());
            operacion.setPosicionSecuencia(secuencia);
            operacion.setFechaEstadoActual(ahora);
            operacion.setPoeDocumentoVersion(node.procesoId() == null
                    ? null : poePorProceso.get(node.procesoId()));
            boolean raiz = graph.predecessors().getOrDefault(frontendId, Set.of()).isEmpty();
            operacion.setEstadoEnum(liberada && raiz
                    ? EstadoSeguimientoOrdenArea.ESPERA
                    : EstadoSeguimientoOrdenArea.COLA);
            if (liberada && raiz) operacion.setFechaVisible(ahora);
            operacionRepo.saveAndFlush(operacion);
            persisted.put(frontendId, operacion);

            if (record != null) {
                BatchRecordEtapa etapa = new BatchRecordEtapa();
                etapa.setBatchRecord(record);
                etapa.setAreaOperativa(area);
                etapa.setOrdenFabricacionOperacion(operacion);
                etapa.setNombre(node.nombre());
                etapa.setSecuencia(secuencia);
                etapa.setEstado(EstadoBatchRecordEtapa.PENDIENTE);
                etapa.setControlProcesoPlantilla(plantillaRepo
                        .findFirstByAreaOperativa_AreaIdAndEstado(
                                area.getAreaId(), EstadoControlProcesoPlantilla.VIGENTE)
                        .orElse(null));
                etapaRepo.saveAndFlush(etapa);
                record.getEtapas().add(etapa);
                operacion.setBatchRecordEtapa(etapa);
            }
            secuencia++;

            registrarEvento(operacion, null, operacion.getEstadoEnum(),
                    ActorTipoEventoSeguimiento.SYSTEM, null,
                    "Inicializacion de operacion de orden de fabricacion",
                    TipoEventoSeguimiento.SISTEMA, null, ahora);
        }

        graph.predecessors().forEach((target, sources) -> sources.forEach(source -> {
            OrdenFabricacionOperacionDependencia dependencia =
                    new OrdenFabricacionOperacionDependencia();
            dependencia.setPredecesora(persisted.get(source));
            dependencia.setSucesora(persisted.get(target));
            dependenciaRepo.save(dependencia);
        }));
    }

    public OrdenFabricacionDTOs.OperacionResponse iniciar(
            Long operacionId, User actor, String observaciones) {
        Lock lock = lock(operacionId);
        OrdenFabricacionOperacion operacion = lock.operacion();
        validarResponsable(operacion, actor);
        OrdenFabricacion orden = lock.orden();
        if (orden.getEstado() != EstadoOrdenFabricacion.LIBERADA
                && orden.getEstado() != EstadoOrdenFabricacion.EN_EJECUCION) {
            throw new IllegalStateException("La OF debe estar liberada para iniciar una operacion.");
        }
        if (operacion.getEstadoEnum() != EstadoSeguimientoOrdenArea.ESPERA) {
            throw new IllegalStateException("Solo una operacion en espera puede iniciarse.");
        }
        if (orden.getPoliticaDispensacionInicio() == PoliticaDispensacionInicio.BLOQUEANTE
                && orden.getEstadoDispensacionMateriales() != EstadoDispensacionMateriales.PARCIAL
                && orden.getEstadoDispensacionMateriales() != EstadoDispensacionMateriales.COMPLETA
                && orden.getEstadoDispensacionMateriales()
                != EstadoDispensacionMateriales.LIBERADA_SIN_DISPENSACION) {
            throw new IllegalStateException(
                    "La politica de la OF exige registrar una dispensacion antes de iniciar.");
        }
        LocalDateTime ahora = LocalDateTime.now(applicationClock);
        transicionar(operacion, EstadoSeguimientoOrdenArea.EN_PROCESO, actor,
                observaciones, TipoEventoSeguimiento.OPERATIVO, null, ahora);
        if (orden.getFechaInicio() == null) orden.setFechaInicio(ahora);
        orden.setEstado(EstadoOrdenFabricacion.EN_EJECUCION);
        ordenRepo.save(orden);
        return toResponse(operacion);
    }

    public OrdenFabricacionDTOs.OperacionResponse pausar(
            Long operacionId, User actor, String observaciones) {
        Lock lock = lock(operacionId);
        OrdenFabricacionOperacion operacion = lock.operacion();
        validarResponsable(operacion, actor);
        if (operacion.getEstadoEnum() != EstadoSeguimientoOrdenArea.EN_PROCESO) {
            throw new IllegalStateException("Solo una operacion en proceso puede pausarse.");
        }
        transicionar(operacion, EstadoSeguimientoOrdenArea.ESPERA, actor,
                observaciones, TipoEventoSeguimiento.OPERATIVO, null,
                LocalDateTime.now(applicationClock));
        return toResponse(operacion);
    }

    public OrdenFabricacionDTOs.OperacionResponse completar(
            Long operacionId,
            User actor,
            OrdenFabricacionDTOs.OperacionCompletarRequest request
    ) {
        Lock lock = lock(operacionId);
        OrdenFabricacionOperacion operacion = lock.operacion();
        OrdenFabricacion orden = lock.orden();
        validarResponsable(operacion, actor);
        if (orden.getEstado() != EstadoOrdenFabricacion.EN_EJECUCION) {
            throw new IllegalStateException(
                    "La OF debe estar en ejecucion para completar una operacion.");
        }
        if (operacion.getEstadoEnum() != EstadoSeguimientoOrdenArea.EN_PROCESO) {
            throw new IllegalStateException("Solo una operacion en proceso puede completarse.");
        }
        List<OrdenFabricacionOperacion> operaciones = operacionRepo
                .findByOrdenFabricacion_OrdenFabricacionIdOrderByPosicionSecuenciaAsc(
                        orden.getOrdenFabricacionId());
        boolean cierraOrden = operaciones.stream()
                .filter(candidate -> !Objects.equals(candidate.getId(), operacion.getId()))
                .allMatch(candidate -> candidate.getEstadoEnum() == EstadoSeguimientoOrdenArea.COMPLETADO
                        || candidate.getEstadoEnum() == EstadoSeguimientoOrdenArea.OMITIDO);
        BigDecimal cantidadObtenida = validarCierreRequest(cierraOrden, orden, request);

        LocalDateTime ahora = LocalDateTime.now(applicationClock);
        transicionar(operacion, EstadoSeguimientoOrdenArea.COMPLETADO, actor,
                request == null ? null : request.getObservaciones(),
                TipoEventoSeguimiento.OPERATIVO, null, ahora);
        propagarSucesores(operacion, ahora);

        if (cierraOrden) {
            BigDecimal obtenida = cantidadObtenida;
            Lote lote = loteRepo.findByOrdenFabricacion_OrdenFabricacionId(
                            orden.getOrdenFabricacionId()).stream().findFirst()
                    .orElseThrow(() -> new IllegalStateException("La OF no tiene lote de resultado."));
            LocalDate fechaProduccion = LocalDate.now(applicationClock);
            lote.setProductionDate(fechaProduccion);
            lote.setExpirationDate(request.getFechaVencimiento());
            BatchRecord expediente = batchRecordRepo
                    .findByOrdenFabricacion_OrdenFabricacionId(
                            orden.getOrdenFabricacionId())
                    .orElse(null);
            lote.setEstadoCalidad(expediente == null
                    ? EstadoCalidadLote.SIN_CLASIFICAR
                    : EstadoCalidadLote.CUARENTENA);
            loteRepo.save(lote);

            orden.setEstado(expediente == null
                    ? EstadoOrdenFabricacion.CERRADA
                    : EstadoOrdenFabricacion.FABRICACION_COMPLETADA);
            orden.setFechaFinal(ahora);
            ordenRepo.saveAndFlush(orden);
            if (expediente == null) {
                crearEntradaResultado(orden, lote, obtenida, actor);
            } else {
                batchRecordService.prepararRevisionCalidad(orden, obtenida);
            }
        }
        return toResponse(operacion);
    }

    public OrdenFabricacionDTOs.OperacionResponse corregir(
            Long operacionId,
            User actor,
            OrdenFabricacionDTOs.CorreccionOperacionRequest request
    ) {
        if (request == null || request.getEstadoEsperado() == null
                || request.getEstadoDestino() == null
                || request.getMotivo() == null || request.getMotivo().isBlank()) {
            throw new IllegalArgumentException("Estado esperado, destino y motivo son obligatorios.");
        }
        Lock lock = lock(operacionId);
        OrdenFabricacionOperacion operacion = lock.operacion();
        OrdenFabricacion orden = lock.orden();
        if (orden.getEstado() == EstadoOrdenFabricacion.CERRADA
                || orden.getEstado() == EstadoOrdenFabricacion.CANCELADA) {
            throw new IllegalStateException("Una OF cerrada o cancelada no admite correcciones.");
        }
        boolean expedienteEnCorreccion = batchRecordService.estaDevueltoAProduccion(orden);
        boolean correccionCalidad = orden.getEstado() == EstadoOrdenFabricacion.FABRICACION_COMPLETADA
                && expedienteEnCorreccion;
        if (expedienteEnCorreccion) {
            batchRecordService.validarCorreccionPermitida(operacion);
        }
        if (orden.getEstado() != EstadoOrdenFabricacion.LIBERADA
                && orden.getEstado() != EstadoOrdenFabricacion.EN_EJECUCION
                && !correccionCalidad) {
            throw new IllegalStateException(
                    "Solo una OF liberada o en ejecucion admite correcciones operativas.");
        }
        if (operacion.getEstado() != request.getEstadoEsperado()) {
            throw new IllegalStateException("El estado cambio; actualice el tablero antes de corregir.");
        }
        EstadoSeguimientoOrdenArea destino = EstadoSeguimientoOrdenArea.fromCode(
                request.getEstadoDestino());
        if (destino == EstadoSeguimientoOrdenArea.COMPLETADO
                || destino == EstadoSeguimientoOrdenArea.OMITIDO) {
            throw new IllegalArgumentException(
                    "La terminacion debe registrarse desde el flujo operativo normal.");
        }
        if (destino != EstadoSeguimientoOrdenArea.COLA
                && dependenciaRepo.countPredecesorasPendientes(operacionId) > 0) {
            throw new IllegalStateException("La operacion conserva predecesoras pendientes.");
        }
        boolean reabreCompletada = operacion.getEstadoEnum()
                == EstadoSeguimientoOrdenArea.COMPLETADO;
        List<OrdenFabricacionOperacionDependencia> sucesores = reabreCompletada
                ? dependenciaRepo.findByPredecesoraId(operacionId)
                : List.of();
        if (reabreCompletada) {
            boolean sucesorAvanzado = sucesores.stream()
                    .map(OrdenFabricacionOperacionDependencia::getSucesora)
                    .anyMatch(sucesor -> sucesor.getEstadoEnum() != EstadoSeguimientoOrdenArea.COLA
                            && sucesor.getEstadoEnum() != EstadoSeguimientoOrdenArea.ESPERA);
            if (sucesorAvanzado) {
                throw new IllegalStateException(
                        "No puede reabrirse porque una operacion sucesora ya tiene ejecucion.");
            }
        }
        OrdenFabricacionOperacionEvento revertido = eventoRepo
                .findByOperacion_IdOrderByFechaEventoAscIdAsc(operacionId).stream()
                .filter(evento -> evento.getEstadoDestino() == operacion.getEstado())
                .reduce((ignored, current) -> current).orElse(null);
        LocalDateTime ahora = LocalDateTime.now(applicationClock);
        if (reabreCompletada) {
            for (OrdenFabricacionOperacionDependencia dependencia : sucesores) {
                OrdenFabricacionOperacion sucesor = dependencia.getSucesora();
                if (sucesor.getEstadoEnum() == EstadoSeguimientoOrdenArea.ESPERA) {
                    transicionar(sucesor, EstadoSeguimientoOrdenArea.COLA, null,
                            "Etapa devuelta a cola por reapertura administrativa de una predecesora",
                            TipoEventoSeguimiento.SISTEMA, null, ahora);
                }
            }
        }
        transicionar(operacion, destino, actor, request.getMotivo(),
                TipoEventoSeguimiento.CORRECCION_ADMINISTRATIVA, revertido,
                ahora);
        if (destino == EstadoSeguimientoOrdenArea.EN_PROCESO
                && (orden.getEstado() == EstadoOrdenFabricacion.LIBERADA
                || correccionCalidad)) {
            orden.setEstado(EstadoOrdenFabricacion.EN_EJECUCION);
            if (orden.getFechaInicio() == null) orden.setFechaInicio(ahora);
            if (correccionCalidad) orden.setFechaFinal(null);
            ordenRepo.save(orden);
        }
        return toResponse(operacion);
    }

    public void liberar(OrdenFabricacion ordenSolicitada) {
        if (ordenSolicitada == null || ordenSolicitada.getOrdenFabricacionId() == null) return;
        OrdenFabricacion orden = ordenRepo.findByIdForUpdate(
                        ordenSolicitada.getOrdenFabricacionId())
                .orElseThrow(() -> new NoSuchElementException(
                        "Orden de fabricacion no encontrada."));
        if (orden.getEstado() != EstadoOrdenFabricacion.PLANIFICADA) return;
        LocalDateTime ahora = LocalDateTime.now(applicationClock);
        orden.setEstado(EstadoOrdenFabricacion.LIBERADA);
        orden.setLiberadaEn(ahora);
        ordenRepo.save(orden);
        List<OrdenFabricacionOperacion> operaciones = operacionRepo
                .findByOrdenFabricacion_OrdenFabricacionIdOrderByPosicionSecuenciaAsc(
                        orden.getOrdenFabricacionId());
        for (OrdenFabricacionOperacion operacion : operaciones) {
            if (operacion.getEstadoEnum() == EstadoSeguimientoOrdenArea.COLA
                    && dependenciaRepo.countPredecesorasPendientes(operacion.getId()) == 0) {
                transicionar(operacion, EstadoSeguimientoOrdenArea.ESPERA, null,
                        "Liberacion programada de la OF", TipoEventoSeguimiento.SISTEMA,
                        null, ahora);
            }
        }
    }

    /**
     * Completa únicamente la proyección estructural creada por la migración para
     * OF legadas. No inventa eventos, firmas, controles ni POE históricos.
     */
    public void backfillProyeccionLegada(Long ordenFabricacionId) {
        OrdenFabricacion orden = ordenRepo.findByIdForUpdate(ordenFabricacionId)
                .orElseThrow(() -> new NoSuchElementException(
                        "Orden de fabricacion legada no encontrada."));
        List<OrdenFabricacionOperacion> operaciones = operacionRepo
                .findByOrdenFabricacion_OrdenFabricacionIdOrderByPosicionSecuenciaAsc(
                        ordenFabricacionId);
        if (operaciones.isEmpty()) return;

        Graph graph = parseGraph(orden.getManufacturingVersion().getProcesoProduccionJson());
        Map<String, OrdenFabricacionOperacion> porFrontendId = operaciones.stream()
                .collect(java.util.stream.Collectors.toMap(
                        OrdenFabricacionOperacion::getFrontendNodeId,
                        operacion -> operacion,
                        (primera, ignored) -> primera,
                        LinkedHashMap::new));
        if (!porFrontendId.keySet().containsAll(graph.nodes().keySet())) {
            log.warn("La OF legada {} no permite reconstruir todas sus dependencias: "
                            + "la proyeccion y el snapshot no comparten los mismos frontendId.",
                    ordenFabricacionId);
            return;
        }

        graph.predecessors().forEach((target, sources) -> sources.forEach(source -> {
            OrdenFabricacionOperacion predecesora = porFrontendId.get(source);
            OrdenFabricacionOperacion sucesora = porFrontendId.get(target);
            if (!dependenciaRepo.existsByPredecesora_IdAndSucesora_Id(
                    predecesora.getId(), sucesora.getId())) {
                OrdenFabricacionOperacionDependencia dependencia =
                        new OrdenFabricacionOperacionDependencia();
                dependencia.setPredecesora(predecesora);
                dependencia.setSucesora(sucesora);
                dependenciaRepo.save(dependencia);
            }
        }));

        if (orden.getEstado() == EstadoOrdenFabricacion.LIBERADA
                || orden.getEstado() == EstadoOrdenFabricacion.EN_EJECUCION) {
            LocalDateTime ahora = LocalDateTime.now(applicationClock);
            for (String frontendId : graph.topologicalOrder()) {
                if (!graph.predecessors().getOrDefault(frontendId, Set.of()).isEmpty()) continue;
                OrdenFabricacionOperacion raiz = porFrontendId.get(frontendId);
                if (raiz.getEstadoEnum() == EstadoSeguimientoOrdenArea.COLA) {
                    raiz.setEstadoEnum(EstadoSeguimientoOrdenArea.ESPERA);
                    raiz.setFechaVisible(ahora);
                    raiz.setFechaEstadoActual(ahora);
                    operacionRepo.save(raiz);
                }
            }
        }
    }

    @Transactional(readOnly = true)
    public List<Long> listarOrdenesPendientesBackfillLegado() {
        return operacionRepo.findOrdenIdsPendientesBackfillLegado(
                EstadoOrdenFabricacion.CANCELADA);
    }

    @Scheduled(fixedDelayString = "${app.orden-fabricacion.release-check-ms:60000}")
    public void liberarProgramadas() {
        LocalDateTime ahora = LocalDateTime.now(applicationClock);
        for (OrdenFabricacion orden : ordenRepo.findPendientesLiberacion(
                List.of(EstadoOrdenFabricacion.PLANIFICADA), ahora)) {
            try {
                liberar(orden);
            } catch (RuntimeException error) {
                log.error("No se pudo liberar automaticamente la OF {}",
                        orden.getOrdenFabricacionId(), error);
            }
        }
    }

    @Transactional(readOnly = true)
    public List<OrdenFabricacionDTOs.OperacionResponse> listar(Long ordenFabricacionId) {
        return operacionRepo
                .findByOrdenFabricacion_OrdenFabricacionIdOrderByPosicionSecuenciaAsc(
                        ordenFabricacionId)
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public boolean esResponsableDeAlgunaOperacion(Long ordenFabricacionId, Long userId) {
        return ordenFabricacionId != null && userId != null
                && operacionRepo
                .existsByOrdenFabricacion_OrdenFabricacionIdAndAreaOperativa_ResponsableArea_Id(
                        ordenFabricacionId, userId);
    }

    private void propagarSucesores(OrdenFabricacionOperacion source, LocalDateTime ahora) {
        for (OrdenFabricacionOperacionDependencia dependencia
                : dependenciaRepo.findByPredecesoraId(source.getId())) {
            OrdenFabricacionOperacion sucesor = dependencia.getSucesora();
            if (sucesor.getEstadoEnum() == EstadoSeguimientoOrdenArea.COLA
                    && dependenciaRepo.countPredecesorasPendientes(sucesor.getId()) == 0) {
                transicionar(sucesor, EstadoSeguimientoOrdenArea.ESPERA, null,
                        "Operacion habilitada al completar sus predecesoras",
                        TipoEventoSeguimiento.SISTEMA, null, ahora);
            }
        }
    }

    private void transicionar(
            OrdenFabricacionOperacion operacion,
            EstadoSeguimientoOrdenArea destino,
            User actor,
            String nota,
            TipoEventoSeguimiento tipo,
            OrdenFabricacionOperacionEvento revertido,
            LocalDateTime ahora
    ) {
        EstadoSeguimientoOrdenArea origen = operacion.getEstadoEnum();
        if (origen == destino) throw new IllegalStateException("La operacion ya tiene ese estado.");
        operacion.setEstadoEnum(destino);
        operacion.setFechaEstadoActual(ahora);
        if (destino == EstadoSeguimientoOrdenArea.COLA) {
            operacion.setFechaVisible(null);
        } else if (operacion.getFechaVisible() == null) {
            operacion.setFechaVisible(ahora);
        }
        if (destino == EstadoSeguimientoOrdenArea.COMPLETADO) {
            operacion.setFechaCompletado(ahora);
            operacion.setUsuarioReporta(actor);
            operacion.setObservaciones(truncate(normalize(nota), 500));
        } else if (tipo == TipoEventoSeguimiento.CORRECCION_ADMINISTRATIVA) {
            operacion.setFechaCompletado(null);
            operacion.setUsuarioReporta(null);
            operacion.setObservaciones(null);
        }
        operacionRepo.saveAndFlush(operacion);
        registrarEvento(operacion, origen, destino,
                actor == null ? ActorTipoEventoSeguimiento.SYSTEM : ActorTipoEventoSeguimiento.USER,
                actor, nota, tipo, revertido, ahora);
    }

    private OrdenFabricacionOperacionEvento registrarEvento(
            OrdenFabricacionOperacion operacion,
            EstadoSeguimientoOrdenArea origen,
            EstadoSeguimientoOrdenArea destino,
            ActorTipoEventoSeguimiento actorTipo,
            User actor,
            String nota,
            TipoEventoSeguimiento tipo,
            OrdenFabricacionOperacionEvento revertido,
            LocalDateTime ahora
    ) {
        OrdenFabricacionOperacionEvento evento = new OrdenFabricacionOperacionEvento();
        evento.setOperacion(operacion);
        evento.setEstadoOrigen(origen == null ? null : origen.getCode());
        evento.setEstadoDestino(destino.getCode());
        evento.setFechaEvento(ahora);
        evento.setActorTipo(actorTipo);
        evento.setTipoEvento(tipo);
        evento.setEventoRevertido(revertido);
        evento.setUsuario(actor);
        evento.setNota(truncate(normalize(nota), 500));
        eventoRepo.saveAndFlush(evento);
        batchRecordService.sincronizarEventoFabricacion(evento);
        return evento;
    }

    public void cerrarTrasLiberacionCalidad(
            Long ordenFabricacionId,
            User actor
    ) {
        OrdenFabricacion orden = ordenRepo.findByIdForUpdate(ordenFabricacionId)
                .orElseThrow(() -> new NoSuchElementException(
                        "Orden de fabricación no encontrada."));
        if (orden.getEstado() == EstadoOrdenFabricacion.CERRADA) return;
        if (orden.getEstado() != EstadoOrdenFabricacion.FABRICACION_COMPLETADA) {
            throw new IllegalStateException(
                    "La OF debe tener su fabricación completada antes de la liberación.");
        }
        BatchRecord record = batchRecordRepo.findByOrdenFabricacion_OrdenFabricacionId(
                        ordenFabricacionId)
                .orElseThrow(() -> new IllegalStateException(
                        "La OF no tiene expediente digital."));
        if (record.getEstado() != EstadoBatchRecord.APROBADO
                || record.getLoteResultado().getEstadoCalidad() != EstadoCalidadLote.LIBERADO) {
            throw new IllegalStateException(
                    "El lote intermedio debe estar liberado por Calidad.");
        }
        Lote lote = record.getLoteResultado();
        int entidadId = Math.toIntExact(ordenFabricacionId);
        if (transaccionRepo.countByTipoEntidadCausanteAndIdEntidadCausante(
                TransaccionAlmacen.TipoEntidadCausante.OF, entidadId) == 0) {
            crearEntradaResultado(orden, lote, record.getCantidadObtenida(), actor);
        }
        orden.setEstado(EstadoOrdenFabricacion.CERRADA);
        ordenRepo.saveAndFlush(orden);
        batchRecordService.cerrarPorIngresoAlmacen(orden, actor);
    }

    private void crearEntradaResultado(
            OrdenFabricacion orden, Lote lote, BigDecimal cantidad, User actor) {
        TransaccionAlmacen transaccion = new TransaccionAlmacen();
        transaccion.setTipoEntidadCausante(TransaccionAlmacen.TipoEntidadCausante.OF);
        transaccion.setIdEntidadCausante(Math.toIntExact(orden.getOrdenFabricacionId()));
        transaccion.setUsuarioAprobador(actor);
        transaccion.setUsuariosResponsables(List.of(actor));
        transaccion.setObservaciones("Ingreso de lote intermedio resultante de la OF "
                + orden.getOrdenFabricacionId());

        Movimiento movimiento = new Movimiento();
        movimiento.setCantidad(cantidad.doubleValue());
        movimiento.setProducto(orden.getSemiTerminado());
        movimiento.setTipoMovimiento(Movimiento.TipoMovimiento.BACKFLUSH);
        movimiento.setAfectaInventario(true);
        movimiento.setAlmacen(Movimiento.Almacen.GENERAL);
        movimiento.setLote(lote);
        movimiento.setTransaccionAlmacen(transaccion);
        transaccion.setMovimientosTransaccion(List.of(movimiento));
        transaccionRepo.saveAndFlush(transaccion);
    }

    private BigDecimal validarCierreRequest(
            boolean cierraOrden,
            OrdenFabricacion orden,
            OrdenFabricacionDTOs.OperacionCompletarRequest request
    ) {
        if (!cierraOrden) {
            if (request != null && (request.getCantidadObtenida() != null
                    || request.getFechaVencimiento() != null
                    || normalize(request.getMotivoDiferenciaCantidad()) != null)) {
                throw new IllegalArgumentException(
                        "Rendimiento y vencimiento solo se reportan al cerrar la ultima operacion.");
            }
            return null;
        }
        if (request == null || request.getCantidadObtenida() == null
                || request.getCantidadObtenida().signum() <= 0) {
            throw new IllegalArgumentException(
                    "La cantidad realmente obtenida es obligatoria y debe ser positiva.");
        }
        BigDecimal cantidadObtenida = request.getCantidadObtenida()
                .setScale(4, java.math.RoundingMode.HALF_UP);
        if (cantidadObtenida.signum() <= 0) {
            throw new IllegalArgumentException(
                    "La cantidad obtenida es menor que la precision admitida (0.0001).");
        }
        if (cantidadObtenida.compareTo(CANTIDAD_MAXIMA) > 0) {
            throw new IllegalArgumentException(
                    "La cantidad obtenida excede el valor maximo admitido.");
        }
        if (request.getFechaVencimiento() != null
                && !request.getFechaVencimiento().isAfter(LocalDate.now(applicationClock))) {
            throw new IllegalArgumentException(
                    "La fecha de vencimiento debe ser posterior a la fecha de fabricacion.");
        }
        boolean difiere = cantidadObtenida
                .subtract(orden.getCantidadPlanificada()).abs()
                .compareTo(CANTIDAD_TOLERANCIA) > 0;
        if (difiere && normalize(request.getMotivoDiferenciaCantidad()) == null) {
            throw new IllegalArgumentException(
                    "Explique la diferencia entre la cantidad planificada y la obtenida.");
        }
        return cantidadObtenida;
    }

    private String buildMotivoCierre(
            OrdenFabricacionDTOs.OperacionCompletarRequest request,
            OrdenFabricacion orden
    ) {
        String diferencia = normalize(request.getMotivoDiferenciaCantidad());
        return diferencia == null
                ? "Cierre operativo de OF con cantidad planificada cumplida"
                : "Cierre operativo de OF. Diferencia de rendimiento: " + diferencia;
    }

    private Lock lock(Long operacionId) {
        OrdenFabricacionOperacion preliminary = operacionRepo.findById(operacionId)
                .orElseThrow(() -> new NoSuchElementException("Operacion de OF no encontrada."));
        OrdenFabricacion orden = ordenRepo.findByIdForUpdate(
                        preliminary.getOrdenFabricacion().getOrdenFabricacionId())
                .orElseThrow(() -> new NoSuchElementException("Orden de fabricacion no encontrada."));
        OrdenFabricacionOperacion operacion = operacionRepo.findByIdForUpdate(operacionId)
                .orElseThrow(() -> new NoSuchElementException("Operacion de OF no encontrada."));
        return new Lock(orden, operacion);
    }

    private void validarResponsable(OrdenFabricacionOperacion operacion, User actor) {
        if (actor == null || actor.getId() == null
                || operacion.getAreaOperativa().getResponsableArea() == null
                || !actor.getId().equals(
                operacion.getAreaOperativa().getResponsableArea().getId())) {
            throw new AccessDeniedException(
                    "Solo el responsable autenticado del area puede reportar esta operacion.");
        }
    }

    private Graph parseGraph(String json) {
        if (json == null || json.isBlank()) {
            throw new IllegalStateException("La OF no tiene un proceso de manufactura congelado.");
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode nodesJson = root.path("nodes");
            JsonNode edgesJson = root.path("edges");
            if (!nodesJson.isArray() || !edgesJson.isArray()) {
                throw new IllegalStateException("El proceso congelado no contiene nodes y edges validos.");
            }
            Map<String, JsonNode> allNodes = new LinkedHashMap<>();
            Map<String, Node> processNodes = new LinkedHashMap<>();
            for (JsonNode node : nodesJson) {
                String id = requiredText(node, "frontendId");
                if (allNodes.put(id, node) != null) {
                    throw new IllegalStateException("El proceso contiene frontendId duplicado: " + id);
                }
                if ("PROCESO".equalsIgnoreCase(node.path("nodeType").asText())) {
                    int areaId = node.path("areaOperativaId").asInt(Integer.MIN_VALUE);
                    if (areaId == Integer.MIN_VALUE) {
                        throw new IllegalStateException("El nodo " + id + " no tiene area operativa.");
                    }
                    Integer procesoId = node.hasNonNull("procesoId")
                            ? node.get("procesoId").asInt() : null;
                    String nombre = truncate(firstText(
                            node.path("procesoNombre").asText(null),
                            node.path("label").asText(null), "Etapa " + id), 200);
                    processNodes.put(id, new Node(id, areaId, procesoId, nombre));
                }
            }
            if (processNodes.isEmpty()) {
                throw new IllegalStateException("El proceso de la OF no contiene operaciones productivas.");
            }
            Map<String, Set<String>> adjacency = new LinkedHashMap<>();
            allNodes.keySet().forEach(id -> adjacency.put(id, new LinkedHashSet<>()));
            for (JsonNode edge : edgesJson) {
                String source = requiredText(edge, "sourceFrontendId");
                String target = requiredText(edge, "targetFrontendId");
                if (!allNodes.containsKey(source) || !allNodes.containsKey(target)) {
                    throw new IllegalStateException("Una conexion referencia nodos inexistentes.");
                }
                adjacency.get(source).add(target);
            }

            Map<String, Set<String>> predecessors = new LinkedHashMap<>();
            processNodes.keySet().forEach(id -> predecessors.put(id, new LinkedHashSet<>()));
            for (String source : processNodes.keySet()) {
                Deque<String> pending = new ArrayDeque<>(adjacency.getOrDefault(source, Set.of()));
                Set<String> visited = new HashSet<>();
                while (!pending.isEmpty()) {
                    String current = pending.removeFirst();
                    if (!visited.add(current)) continue;
                    if (processNodes.containsKey(current)) {
                        predecessors.get(current).add(source);
                    } else {
                        pending.addAll(adjacency.getOrDefault(current, Set.of()));
                    }
                }
            }
            List<String> order = topologicalOrder(processNodes.keySet(), predecessors);
            return new Graph(processNodes, predecessors, order);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("No se pudo interpretar el proceso congelado de la OF.", exception);
        }
    }

    private List<String> topologicalOrder(
            Set<String> nodes, Map<String, Set<String>> predecessors) {
        Map<String, Integer> indegree = new LinkedHashMap<>();
        Map<String, Set<String>> successors = new LinkedHashMap<>();
        nodes.forEach(node -> {
            indegree.put(node, predecessors.getOrDefault(node, Set.of()).size());
            successors.put(node, new LinkedHashSet<>());
        });
        predecessors.forEach((target, sources) -> sources.forEach(source ->
                successors.get(source).add(target)));
        Deque<String> ready = new ArrayDeque<>();
        indegree.forEach((node, degree) -> { if (degree == 0) ready.add(node); });
        List<String> result = new ArrayList<>();
        while (!ready.isEmpty()) {
            String source = ready.removeFirst();
            result.add(source);
            for (String target : successors.get(source)) {
                int next = indegree.computeIfPresent(target, (ignored, value) -> value - 1);
                if (next == 0) ready.addLast(target);
            }
        }
        if (result.size() != nodes.size()) {
            throw new IllegalStateException("El proceso congelado de la OF contiene ciclos.");
        }
        return result;
    }

    public OrdenFabricacionDTOs.OperacionResponse toResponse(
            OrdenFabricacionOperacion operacion) {
        BatchRecordEtapa etapa = operacion.getBatchRecordEtapa();
        ProcesoProduccionDocumentoVersion poe = operacion.getPoeDocumentoVersion();
        return OrdenFabricacionDTOs.OperacionResponse.builder()
                .id(operacion.getId())
                .ordenFabricacionId(operacion.getOrdenFabricacion().getOrdenFabricacionId())
                .frontendNodeId(operacion.getFrontendNodeId())
                .procesoProduccionId(operacion.getProcesoProduccionId())
                .procesoNombre(operacion.getProcesoNombre())
                .areaOperativaId(operacion.getAreaOperativa().getAreaId())
                .areaOperativaNombre(operacion.getAreaOperativa().getNombre())
                .posicionSecuencia(operacion.getPosicionSecuencia())
                .estado(operacion.getEstado())
                .estadoDescripcion(operacion.getEstadoEnum().getDescripcion())
                .fechaEstadoActual(operacion.getFechaEstadoActual())
                .fechaVisible(operacion.getFechaVisible())
                .fechaCompletado(operacion.getFechaCompletado())
                .usuarioReporta(nombreUsuario(operacion.getUsuarioReporta()))
                .observaciones(operacion.getObservaciones())
                .batchRecordEtapaId(etapa == null ? null : etapa.getId())
                .poeDocumentoVersionId(poe == null ? null : poe.getId())
                .poeVersion(poe == null ? null : poe.getVersion())
                .poeNombreArchivo(poe == null ? null : poe.getNombreArchivoOriginal())
                .build();
    }

    private String nombreUsuario(User user) {
        if (user == null) return null;
        return user.getNombreCompleto() == null || user.getNombreCompleto().isBlank()
                ? user.getUsername() : user.getNombreCompleto();
    }

    private String requiredText(JsonNode node, String field) {
        String value = node.path(field).asText(null);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("El proceso congelado no contiene " + field + ".");
        }
        return value;
    }

    private String firstText(String... values) {
        for (String value : values) if (value != null && !value.isBlank()) return value.trim();
        return "Etapa";
    }

    private String normalize(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }

    private record Lock(OrdenFabricacion orden, OrdenFabricacionOperacion operacion) {}
    private record Node(String id, int areaId, Integer procesoId, String nombre) {}
    private record Graph(
            Map<String, Node> nodes,
            Map<String, Set<String>> predecessors,
            List<String> topologicalOrder
    ) {}
}
