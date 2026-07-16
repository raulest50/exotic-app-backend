package exotic.app.planta.service.produccion;

import exotic.app.planta.model.organizacion.AreaOperativa;
import exotic.app.planta.model.empresa.JornadaLaboralVersion;
import exotic.app.planta.model.produccion.ActorTipoEventoSeguimiento;
import exotic.app.planta.model.produccion.EstadoDispensacionMateriales;
import exotic.app.planta.model.produccion.EstadoSeguimientoOrdenArea;
import exotic.app.planta.model.produccion.OrdenProduccion;
import exotic.app.planta.model.produccion.PoliticaDispensacionInicio;
import exotic.app.planta.model.produccion.SeguimientoOrdenArea;
import exotic.app.planta.model.produccion.SeguimientoOrdenAreaEvento;
import exotic.app.planta.model.produccion.ruprocatdesigner.RutaProcesoCatVersion;
import exotic.app.planta.model.produccion.ruprocatdesigner.RutaProcesoEdge;
import exotic.app.planta.model.produccion.ruprocatdesigner.RutaProcesoNode;
import exotic.app.planta.model.produccion.TipoEventoSeguimiento;
import exotic.app.planta.model.producto.Categoria;
import exotic.app.planta.model.producto.Terminado;
import exotic.app.planta.model.users.User;
import exotic.app.planta.repo.empresa.JornadaLaboralVersionRepo;
import exotic.app.planta.repo.produccion.SeguimientoOrdenAreaEventoRepo;
import exotic.app.planta.repo.produccion.SeguimientoOrdenAreaRepo;
import exotic.app.planta.repo.producto.procesos.AreaProduccionRepo;
import exotic.app.planta.repo.produccion.ruprocatdesigner.RutaProcesoCatVersionRepo;
import exotic.app.planta.repo.usuarios.UserRepository;
import exotic.app.planta.service.master.configs.MasterDirectiveService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
@Transactional(rollbackFor = Exception.class)
@RequiredArgsConstructor
public class SeguimientoOrdenAreaService {

    public static final int ALMACEN_GENERAL_AREA_ID = -1;

    private static final String NOTA_INICIALIZACION = "Inicializacion de seguimiento";
    private static final String NOTA_DEPENDENCIAS_RESUELTAS = "Paso habilitado automaticamente por resolucion de dependencias previas";
    private static final String NOTA_DISPENSACION_NO_BLOQUEANTE = "Dispensacion no bloqueante habilitada por directiva maestra";
    private static final String NOTA_DISPENSACION_NO_BLOQUEANTE_RETROACTIVA = "Aplicacion retroactiva de politica de dispensacion no bloqueante";
    private static final String NOTA_REVERSION_SUCESOR_CORRECCION = "Reversion automatica por correccion administrativa previa: ";
    private static final int MAX_MOTIVO_CORRECCION_LENGTH = 500;

    private final SeguimientoOrdenAreaRepo seguimientoRepo;
    private final SeguimientoOrdenAreaEventoRepo seguimientoEventoRepo;
    private final AreaProduccionRepo areaProduccionRepo;
    private final RutaProcesoCatVersionRepo rutaProcesoCatVersionRepo;
    private final JornadaLaboralVersionRepo jornadaLaboralVersionRepo;
    private final RutaProcesoEstimacionService rutaProcesoEstimacionService;
    private final ReporteProduccionLoteService reporteProduccionLoteService;
    private final UserRepository userRepository;
    private final MasterDirectiveService masterDirectiveService;
    private final Clock applicationClock;

    /**
     * Inicializa el seguimiento para una nueva orden de produccion.
     * Los nodos iniciales nacen en ESPERA y los demas nacen en COLA.
     */
    public void inicializarSeguimiento(OrdenProduccion orden) {
        if (orden == null || orden.getProducto() == null) {
            log.warn("No se puede inicializar seguimiento: orden o producto nulo");
            return;
        }

        if (!(orden.getProducto() instanceof Terminado terminado)) {
            log.info("Producto {} no es Terminado, no se inicializa seguimiento", orden.getProducto().getProductoId());
            return;
        }

        Categoria categoria = terminado.getCategoria();
        if (categoria == null) {
            log.info("Producto terminado {} no tiene categoria asignada", terminado.getProductoId());
            return;
        }

        Optional<RutaProcesoCatVersion> rutaVersionOpt = rutaProcesoCatVersionRepo.findByCategoriaIdAndEstado(
                categoria.getCategoriaId(),
                RutaProcesoCatVersion.Estado.VIGENTE
        );
        if (rutaVersionOpt.isEmpty()) {
            log.info("Categoria {} no tiene ruta de proceso vigente definida", categoria.getCategoriaId());
            return;
        }

        RutaProcesoCatVersion rutaVersion = rutaVersionOpt.get();
        if (rutaVersion.getNodes() == null || rutaVersion.getNodes().isEmpty()) {
            log.info("Ruta de proceso vigente para categoria {} no tiene nodos definidos", categoria.getCategoriaId());
            return;
        }

        orden.setRutaProcesoCatVersion(rutaVersion);
        if (orden.getJornadaLaboralVersion() == null) {
            jornadaLaboralVersionRepo.findFirstByEstadoOrderByVersionDesc(JornadaLaboralVersion.Estado.VIGENTE)
                    .ifPresent(orden::setJornadaLaboralVersion);
        }

        Set<Long> nodosConPredecesores = rutaVersion.getEdges().stream()
                .map(edge -> edge.getTargetNode().getId())
                .collect(Collectors.toSet());

        int posicion = 0;
        LocalDateTime ahora = LocalDateTime.now(applicationClock);
        SeguimientoOrdenArea seguimientoAlmacenGeneral = null;

        for (RutaProcesoNode node : rutaVersion.getNodes()) {
            if (node.getAreaOperativa() == null) {
                log.warn("Nodo {} no tiene area operativa asignada, se omite", node.getId());
                continue;
            }

            SeguimientoOrdenArea seguimiento = new SeguimientoOrdenArea();
            seguimiento.setOrdenProduccion(orden);
            seguimiento.setRutaProcesoNode(node);
            seguimiento.setAreaOperativa(node.getAreaOperativa());
            seguimiento.setPosicionSecuencia(posicion++);
            seguimiento.setFechaEstadoActual(ahora);
            seguimiento.setDuracionEstimadaMinutos(node.getDuracionEstimadaMinutos());
            seguimiento.setRequiereJornadaLaboral(node.isRequiereJornadaLaboral());

            boolean esNodoInicial = !nodosConPredecesores.contains(node.getId());
            if (esNodoInicial) {
                seguimiento.setEstadoEnum(EstadoSeguimientoOrdenArea.ESPERA);
                seguimiento.setFechaVisible(ahora);
            } else {
                seguimiento.setEstadoEnum(EstadoSeguimientoOrdenArea.COLA);
            }

            seguimiento = seguimientoRepo.save(seguimiento);
            registrarEvento(
                    seguimiento,
                    null,
                    seguimiento.getEstadoEnum(),
                    ActorTipoEventoSeguimiento.SYSTEM,
                    null,
                    NOTA_INICIALIZACION,
                    ahora
            );

            if (seguimiento.getAreaOperativa().getAreaId() == ALMACEN_GENERAL_AREA_ID) {
                seguimientoAlmacenGeneral = seguimiento;
            }
        }

        log.info("Seguimiento inicializado para orden {} con {} nodos", orden.getOrdenId(), posicion);

        if (seguimientoAlmacenGeneral != null && isPoliticaDispensacionNoBloqueante(orden)) {
            completarSeguimiento(
                    seguimientoAlmacenGeneral,
                    ActorTipoEventoSeguimiento.SYSTEM,
                    null,
                    NOTA_DISPENSACION_NO_BLOQUEANTE
            );
            log.info("Almacen General completado automaticamente a nivel de seguimiento para orden {}", orden.getOrdenId());
        }
    }

    @Transactional(readOnly = true)
    public DispensacionRetroactividadDTO previewRetroactividadDispensacionNoBloqueante() {
        boolean directivaActual = masterDirectiveService.isDispensacionNoBloqueaInicioProduccion();
        int candidatos = findCandidatosRetroactividadDispensacionNoBloqueante().size();

        DispensacionRetroactividadDTO dto = new DispensacionRetroactividadDTO();
        dto.setDirectivaActual(directivaActual);
        dto.setEjecutable(directivaActual && candidatos > 0);
        dto.setOrdenesCandidatas(candidatos);
        dto.setOrdenesAplicadas(0);
        dto.setOrdenesOmitidas(0);
        dto.setMensaje(buildRetroactividadMensaje(directivaActual, candidatos, false));
        return dto;
    }

    public DispensacionRetroactividadDTO aplicarRetroactividadDispensacionNoBloqueante() {
        boolean directivaActual = masterDirectiveService.isDispensacionNoBloqueaInicioProduccion();
        List<SeguimientoOrdenArea> candidatos = findCandidatosRetroactividadDispensacionNoBloqueante();

        DispensacionRetroactividadDTO dto = new DispensacionRetroactividadDTO();
        dto.setDirectivaActual(directivaActual);
        dto.setOrdenesCandidatas(candidatos.size());

        if (!directivaActual) {
            dto.setEjecutable(false);
            dto.setOrdenesAplicadas(0);
            dto.setOrdenesOmitidas(candidatos.size());
            dto.setMensaje(buildRetroactividadMensaje(false, candidatos.size(), true));
            return dto;
        }

        LocalDateTime ahora = LocalDateTime.now(applicationClock);
        for (SeguimientoOrdenArea seguimientoAlmacenGeneral : candidatos) {
            OrdenProduccion orden = seguimientoAlmacenGeneral.getOrdenProduccion();
            orden.setPoliticaDispensacionInicio(PoliticaDispensacionInicio.NO_BLOQUEANTE);
            orden.setFechaAplicacionPoliticaDispensacion(ahora);
            if (orden.getEstadoDispensacionMateriales() == null
                    || orden.getEstadoDispensacionMateriales() == EstadoDispensacionMateriales.PENDIENTE) {
                orden.setEstadoDispensacionMateriales(EstadoDispensacionMateriales.LIBERADA_SIN_DISPENSACION);
            }
            completarSeguimiento(
                    seguimientoAlmacenGeneral,
                    ActorTipoEventoSeguimiento.SYSTEM,
                    null,
                    NOTA_DISPENSACION_NO_BLOQUEANTE_RETROACTIVA
            );
            dto.getOrdenIdsAplicadas().add(orden.getOrdenId());
        }

        dto.setEjecutable(!candidatos.isEmpty());
        dto.setOrdenesAplicadas(candidatos.size());
        dto.setOrdenesOmitidas(0);
        dto.setMensaje(buildRetroactividadMensaje(true, candidatos.size(), true));
        return dto;
    }

    public SeguimientoOrdenAreaDTO reportarEnProceso(int ordenId, int areaId, Long userId, String observaciones) {
        SeguimientoOrdenArea seguimiento = seguimientoRepo
                .findByOrdenProduccion_OrdenIdAndAreaOperativa_AreaIdAndEstado(
                        ordenId,
                        areaId,
                        EstadoSeguimientoOrdenArea.ESPERA.getCode())
                .orElseThrow(() -> new IllegalArgumentException(
                        "No se encontro seguimiento en espera para orden " + ordenId + " y area " + areaId));

        User actor = requireUser(userId);
        validarUsuarioResponsableArea(seguimiento, actor);
        transicionarSeguimiento(
                seguimiento,
                EstadoSeguimientoOrdenArea.EN_PROCESO,
                ActorTipoEventoSeguimiento.USER,
                actor,
                observaciones
        );

        return toDTO(seguimiento);
    }

    public SeguimientoOrdenAreaDTO pausarProceso(int ordenId, int areaId, Long userId, String observaciones) {
        SeguimientoOrdenArea seguimiento = seguimientoRepo
                .findByOrdenProduccion_OrdenIdAndAreaOperativa_AreaIdAndEstado(
                        ordenId,
                        areaId,
                        EstadoSeguimientoOrdenArea.EN_PROCESO.getCode())
                .orElseThrow(() -> new IllegalArgumentException(
                        "No se encontro seguimiento en proceso para orden " + ordenId + " y area " + areaId));

        User actor = requireUser(userId);
        validarUsuarioResponsableArea(seguimiento, actor);
        transicionarSeguimiento(
                seguimiento,
                EstadoSeguimientoOrdenArea.ESPERA,
                ActorTipoEventoSeguimiento.USER,
                actor,
                observaciones
        );

        return toDTO(seguimiento);
    }

    /**
     * El cierre manual solo es valido para pasos actualmente en proceso.
     */
    public SeguimientoOrdenAreaDTO reportarCompletado(
            int ordenId,
            int areaId,
            Long userId,
            String observaciones,
            BigDecimal cantidadProducida
    ) {
        SeguimientoOrdenArea seguimiento = seguimientoRepo
                .findByOrdenProduccion_OrdenIdAndAreaOperativa_AreaIdAndEstado(
                        ordenId,
                        areaId,
                        EstadoSeguimientoOrdenArea.EN_PROCESO.getCode())
                .orElseThrow(() -> new IllegalArgumentException(
                        "No se encontro seguimiento en proceso para orden " + ordenId + " y area " + areaId));

        User actor = requireUser(userId);
        validarUsuarioResponsableArea(seguimiento, actor);
        boolean nodoFinal = esNodoFinalConRutaValida(seguimiento, true);
        if (nodoFinal && cantidadProducida == null) {
            throw new IllegalArgumentException("La cantidad producida es obligatoria para el area final.");
        }
        if (!nodoFinal && cantidadProducida != null) {
            throw new IllegalArgumentException("Solo el area final puede reportar unidades producidas.");
        }

        completarSeguimiento(seguimiento, ActorTipoEventoSeguimiento.USER, actor, observaciones);
        if (nodoFinal) {
            reporteProduccionLoteService.registrarPendiente(seguimiento, actor, cantidadProducida);
        }
        return toDTO(seguimiento);
    }

    public SeguimientoOrdenAreaDTO corregirEstadoAdministrativamente(
            int areaId,
            Long seguimientoId,
            Integer expectedEstado,
            Integer targetEstado,
            Long userId,
            String motivo
    ) {
        if (seguimientoId == null) {
            throw new IllegalArgumentException("Se requiere el identificador del seguimiento a corregir.");
        }
        if (expectedEstado == null) {
            throw new IllegalArgumentException("Se requiere el estado actual esperado para aplicar la correccion.");
        }
        if (targetEstado == null) {
            throw new IllegalArgumentException("Se requiere el estado destino para aplicar la correccion.");
        }

        User actor = requireUser(userId);
        String motivoNormalizado = normalizeMotivoCorreccion(motivo);
        SeguimientoOrdenArea seguimiento = seguimientoRepo.findById(seguimientoId)
                .orElseThrow(() -> new IllegalArgumentException("Seguimiento no encontrado: " + seguimientoId));

        validarCorreccionSobreSeguimiento(seguimiento, areaId, expectedEstado);

        EstadoSeguimientoOrdenArea estadoOrigen = seguimiento.getEstadoEnum();
        EstadoSeguimientoOrdenArea estadoDestino = EstadoSeguimientoOrdenArea.fromCode(targetEstado);
        validarEstadosCorreccion(seguimiento, estadoOrigen, estadoDestino);
        validarDependenciasCorreccion(seguimiento, estadoDestino);
        if (estadoDestino == EstadoSeguimientoOrdenArea.COMPLETADO
                && estadoOrigen != EstadoSeguimientoOrdenArea.COMPLETADO
                && esNodoFinalConRutaValida(seguimiento, true)) {
            throw new IllegalArgumentException(
                    "El area final debe completarse desde el tablero operativo reportando la cantidad producida.");
        }

        LocalDateTime ahora = LocalDateTime.now(applicationClock);
        if (estadoOrigen == EstadoSeguimientoOrdenArea.COMPLETADO
                && estadoDestino != EstadoSeguimientoOrdenArea.COMPLETADO) {
            revertirSucesoresAutoHabilitados(seguimiento, actor, motivoNormalizado, ahora);
            reporteProduccionLoteService.anularPendientePorSeguimiento(seguimiento, actor, motivoNormalizado);
        }

        SeguimientoOrdenAreaEvento eventoRevertido = findLatestUnrevertedEventForState(seguimiento, estadoOrigen)
                .orElse(null);
        aplicarEstadoPorCorreccion(seguimiento, estadoDestino, actor, motivoNormalizado, ahora);
        seguimientoRepo.save(seguimiento);
        registrarEvento(
                seguimiento,
                estadoOrigen,
                estadoDestino,
                ActorTipoEventoSeguimiento.USER,
                actor,
                motivoNormalizado,
                ahora,
                TipoEventoSeguimiento.CORRECCION_ADMINISTRATIVA,
                eventoRevertido
        );

        if (estadoDestino == EstadoSeguimientoOrdenArea.COLA
                || estadoDestino == EstadoSeguimientoOrdenArea.ESPERA) {
            clearOrdenFechaInicioIfNoOperativeWork(seguimiento.getOrdenProduccion());
        }

        if (estadoDestino == EstadoSeguimientoOrdenArea.COMPLETADO) {
            propagarVisibilidad(seguimiento.getOrdenProduccion().getOrdenId(), seguimiento.getRutaProcesoNode().getId());
        }

        return toDTO(seguimiento);
    }

    /**
     * Mantiene la automatizacion existente de Almacen General cuando la dispensacion normal
     * registra exitosamente la salida de materiales.
     */
    public SeguimientoOrdenAreaDTO autoCompletarAlmacenGeneralPorDispensacion(int ordenId, Long userId, String observaciones) {
        Optional<SeguimientoOrdenArea> seguimientoOpt = seguimientoRepo
                .findByOrdenProduccion_OrdenIdAndAreaOperativa_AreaIdAndEstado(
                        ordenId,
                        ALMACEN_GENERAL_AREA_ID,
                        EstadoSeguimientoOrdenArea.ESPERA.getCode()
                );

        if (seguimientoOpt.isEmpty()) {
            log.info("No hay seguimiento en espera de Almacen General para orden {}. Se omite auto-completado.", ordenId);
            return null;
        }

        SeguimientoOrdenArea seguimiento = seguimientoOpt.get();
        if (seguimiento.getOrdenProduccion().getFechaInicio() == null) {
            seguimiento.getOrdenProduccion().setFechaInicio(LocalDateTime.now(applicationClock));
        }
        User actor = userId != null ? userRepository.findById(userId).orElse(null) : null;
        completarSeguimiento(seguimiento, ActorTipoEventoSeguimiento.SYSTEM, actor, observaciones);
        return toDTO(seguimiento);
    }

    @Transactional(readOnly = true)
    public boolean tieneAreaOperativaEnSeguimiento(int ordenId, int areaId) {
        return seguimientoRepo.findByOrdenProduccion_OrdenIdOrderByPosicionSecuenciaAsc(ordenId)
                .stream()
                .anyMatch(seguimiento -> seguimiento.getAreaOperativa() != null
                        && seguimiento.getAreaOperativa().getAreaId() == areaId);
    }

    @Transactional(readOnly = true)
    public Page<SeguimientoOrdenAreaDTO> getOrdenesPendientesPorArea(int areaId, Pageable pageable) {
        return seguimientoRepo.findOrdenesVisiblesByAreaId(areaId, pageable)
                .map(this::toDTO);
    }

    @Transactional(readOnly = true)
    public Page<SeguimientoOrdenAreaDTO> getOrdenesPendientesPorUsuario(Long userId, Pageable pageable) {
        return seguimientoRepo.findOrdenesVisiblesByResponsableUserId(userId, pageable)
                .map(this::toDTO);
    }

    @Transactional(readOnly = true)
    public TableroOperativoDTO getTableroOperativoUsuario(Long userId) {
        return getTableroOperativoUsuario(userId, TableroVista.HISTORICO);
    }

    @Transactional(readOnly = true)
    public TableroOperativoDTO getTableroOperativoUsuario(Long userId, TableroVista vista) {
        TableroVista effectiveVista = vista != null ? vista : TableroVista.HISTORICO;
        LocalDate periodStartDate = null;
        LocalDate periodEndDate = null;

        List<Integer> activeStates = List.of(
                EstadoSeguimientoOrdenArea.COLA.getCode(),
                EstadoSeguimientoOrdenArea.ESPERA.getCode(),
                EstadoSeguimientoOrdenArea.EN_PROCESO.getCode()
        );
        List<SeguimientoOrdenArea> seguimientos = new ArrayList<>(
                seguimientoRepo.findTableroActivosByResponsableUserId(userId, activeStates)
        );

        if (effectiveVista == TableroVista.HISTORICO) {
            seguimientos.addAll(seguimientoRepo.findTableroCompletadosByResponsableUserId(
                    userId,
                    EstadoSeguimientoOrdenArea.COMPLETADO.getCode()
            ));
        } else {
            LocalDate today = LocalDate.now(applicationClock);
            if (effectiveVista == TableroVista.HOY) {
                periodStartDate = today;
                periodEndDate = today;
            } else {
                periodStartDate = getCurrentIsoWeekMonday();
                periodEndDate = periodStartDate.plusDays(6);
            }

            seguimientos.addAll(
                    seguimientoRepo.findTableroCompletadosByResponsableUserIdAndFechaCompletadoBetween(
                            userId,
                            EstadoSeguimientoOrdenArea.COMPLETADO.getCode(),
                            periodStartDate.atStartOfDay(),
                            periodEndDate.plusDays(1).atStartOfDay()
                    )
            );
        }

        List<SeguimientoOrdenAreaDTO> tarjetas = seguimientos
                .stream()
                .map(this::toDTO)
                .toList();

        TableroOperativoDTO tablero = buildTableroOperativo(tarjetas);
        tablero.setVista(effectiveVista);
        tablero.setPeriodStartDate(periodStartDate);
        tablero.setPeriodEndDate(periodEndDate);
        return tablero;
    }

    @Transactional(readOnly = true)
    public List<SeguimientoOrdenAreaDTO> getProgresoOrden(int ordenId) {
        return seguimientoRepo.findByOrdenProduccion_OrdenIdOrderByPosicionSecuenciaAsc(ordenId)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public OrdenSeguimientoDetalleDTO getDetalleOrden(int ordenId) {
        List<SeguimientoOrdenArea> ruta = seguimientoRepo.findDetalleByOrdenId(ordenId);
        if (ruta.isEmpty()) {
            throw new IllegalArgumentException("No se encontro seguimiento para la orden " + ordenId);
        }

        OrdenProduccion orden = ruta.get(0).getOrdenProduccion();
        OrdenSeguimientoDetalleDTO detalle = new OrdenSeguimientoDetalleDTO();
        detalle.setOrdenId(orden.getOrdenId());
        detalle.setLoteAsignado(orden.getLoteAsignado());
        detalle.setProductoId(orden.getProducto().getProductoId());
        detalle.setProductoNombre(orden.getProducto().getNombre());
        detalle.setCantidadProducir(orden.getCantidadProducir());
        detalle.setEstadoOrden(orden.getEstadoOrden());
        detalle.setPoliticaDispensacionInicio(orden.getPoliticaDispensacionInicio() != null
                ? orden.getPoliticaDispensacionInicio().name()
                : null);
        detalle.setFechaAplicacionPoliticaDispensacion(orden.getFechaAplicacionPoliticaDispensacion());
        detalle.setEstadoDispensacionMateriales(orden.getEstadoDispensacionMateriales() != null
                ? orden.getEstadoDispensacionMateriales().name()
                : null);
        detalle.setOrdenObservaciones(orden.getObservaciones());
        detalle.setFechaCreacion(orden.getFechaCreacion());
        detalle.setFechaInicio(orden.getFechaInicio());
        detalle.setFechaFinal(orden.getFechaFinal());
        detalle.setFechaFinalPlanificada(orden.getFechaFinalPlanificada());
        RutaProcesoEstimacionService.RutaProcesoEstimacionDTO estimacion =
                rutaProcesoEstimacionService.estimarOrden(orden, ruta);
        if (estimacion != null) {
            detalle.setFechaInicioEstimacion(estimacion.getFechaInicioEstimacion());
            detalle.setFechaFinalEstimada(estimacion.getFechaFinalEstimada());
            detalle.setDuracionCalendarioRutaCriticaMinutos(estimacion.getDuracionCalendarioRutaCriticaMinutos());
        }
        detalle.setRutaEstados(ruta.stream().map(this::toRutaEstadoDTO).toList());
        return detalle;
    }

    @Transactional(readOnly = true)
    public AreaOperativaTableroDTO getTableroAreaPorFecha(int areaId, LocalDate fecha) {
        AreaOperativa area = areaProduccionRepo.findById(areaId)
                .orElseThrow(() -> new IllegalArgumentException("Area operativa no encontrada: " + areaId));

        LocalDate fechaConsulta = fecha == null ? LocalDate.now(applicationClock) : fecha;
        LocalDate hoy = LocalDate.now(applicationClock);
        if (fechaConsulta.isAfter(hoy)) {
            fechaConsulta = hoy;
        }

        LocalDateTime instanteFoto = resolveInstanteFoto(fechaConsulta);
        List<SeguimientoOrdenArea> seguimientos = seguimientoRepo.findTableroByAreaId(areaId);
        Map<Long, List<SeguimientoOrdenAreaEvento>> eventosPorSeguimiento = loadEventosPorSeguimiento(seguimientos);

        List<SeguimientoOrdenAreaDTO> snapshot = seguimientos.stream()
                .map(seguimiento -> toSnapshotDTO(
                        seguimiento,
                        eventosPorSeguimiento.getOrDefault(seguimiento.getId(), List.of()),
                        instanteFoto))
                .filter(java.util.Objects::nonNull)
                .toList();

        TableroOperativoDTO tablero = buildTableroOperativo(snapshot);

        AreaOperativaTableroDTO dto = new AreaOperativaTableroDTO();
        dto.setAreaId(area.getAreaId());
        dto.setAreaNombre(area.getNombre());
        dto.setAreaDescripcion(area.getDescripcion());
        if (area.getResponsableArea() != null) {
            dto.setResponsableArea(toResponsableResumen(area.getResponsableArea()));
            resolveUltimoReporteResponsable(area, instanteFoto).ifPresent(evento -> {
                dto.setUltimaFechaReporteResponsable(evento.getFechaEvento());
                if (evento.getSeguimientoOrdenArea() != null
                        && evento.getSeguimientoOrdenArea().getOrdenProduccion() != null) {
                    OrdenProduccion orden = evento.getSeguimientoOrdenArea().getOrdenProduccion();
                    dto.setUltimaOrdenReporteResponsableId(orden.getOrdenId());
                    dto.setUltimaOrdenReporteResponsableLote(orden.getLoteAsignado());
                }
            });
        }
        dto.setFechaConsulta(fechaConsulta);
        dto.setInstanteFoto(instanteFoto);
        dto.setResumen(tablero.getResumen());
        dto.setCola(tablero.getCola());
        dto.setEspera(tablero.getEspera());
        dto.setEnProceso(tablero.getEnProceso());
        dto.setCompletado(tablero.getCompletado());
        dto.setPromedioMinutosEspera(promedioMinutosEnEstado(dto.getEspera()));
        dto.setPromedioMinutosEnProceso(promedioMinutosEnEstado(dto.getEnProceso()));
        dto.setOrdenMasAtrasada(resolveOrdenMasAtrasada(snapshot));
        return dto;
    }

    @Transactional(readOnly = true)
    public boolean tieneSegumiento(int ordenId) {
        return seguimientoRepo.existsByOrdenProduccion_OrdenId(ordenId);
    }

    private void completarSeguimiento(
            SeguimientoOrdenArea seguimiento,
            ActorTipoEventoSeguimiento actorTipo,
            User actor,
            String observaciones
    ) {
        transicionarSeguimiento(
                seguimiento,
                EstadoSeguimientoOrdenArea.COMPLETADO,
                actorTipo,
                actor,
                observaciones
        );
        propagarVisibilidad(seguimiento.getOrdenProduccion().getOrdenId(), seguimiento.getRutaProcesoNode().getId());
    }

    private void propagarVisibilidad(int ordenId, Long sourceNodeId) {
        List<SeguimientoOrdenArea> sucesoresPendientes = seguimientoRepo.findSucesoresPendientes(ordenId, sourceNodeId);

        for (SeguimientoOrdenArea sucesor : sucesoresPendientes) {
            long predecesoresNoCompletados = seguimientoRepo.countPredecesoresNoCompletados(
                    ordenId, sucesor.getRutaProcesoNode().getId());

            if (predecesoresNoCompletados == 0) {
                transicionarSeguimiento(
                        sucesor,
                        EstadoSeguimientoOrdenArea.ESPERA,
                        ActorTipoEventoSeguimiento.SYSTEM,
                        null,
                        NOTA_DEPENDENCIAS_RESUELTAS
                );
                log.info("Nodo {} ahora en espera para orden {}", sucesor.getRutaProcesoNode().getId(), ordenId);
            }
        }
    }

    private void transicionarSeguimiento(
            SeguimientoOrdenArea seguimiento,
            EstadoSeguimientoOrdenArea estadoDestino,
            ActorTipoEventoSeguimiento actorTipo,
            User actor,
            String nota
    ) {
        EstadoSeguimientoOrdenArea estadoOrigen = seguimiento.getEstadoEnum();
        validarTransicion(seguimiento, estadoOrigen, estadoDestino, actorTipo);

        LocalDateTime ahora = LocalDateTime.now(applicationClock);
        String notaNormalizada = normalizeNota(nota);

        seguimiento.setEstadoEnum(estadoDestino);
        seguimiento.setFechaEstadoActual(ahora);

        if (estadoDestino == EstadoSeguimientoOrdenArea.EN_PROCESO
                && actorTipo == ActorTipoEventoSeguimiento.USER
                && seguimiento.getOrdenProduccion().getFechaInicio() == null) {
            seguimiento.getOrdenProduccion().setFechaInicio(ahora);
        }

        if (estadoDestino == EstadoSeguimientoOrdenArea.ESPERA && seguimiento.getFechaVisible() == null) {
            seguimiento.setFechaVisible(ahora);
        }

        if (estadoDestino == EstadoSeguimientoOrdenArea.COMPLETADO) {
            seguimiento.setFechaCompletado(ahora);
            seguimiento.setUsuarioReporta(actor);
            seguimiento.setObservaciones(notaNormalizada);
        }

        seguimientoRepo.save(seguimiento);
        registrarEvento(seguimiento, estadoOrigen, estadoDestino, actorTipo, actor, notaNormalizada, ahora);
    }

    private boolean isPoliticaDispensacionNoBloqueante(OrdenProduccion orden) {
        return orden != null
                && orden.getPoliticaDispensacionInicio() == PoliticaDispensacionInicio.NO_BLOQUEANTE;
    }

    private List<SeguimientoOrdenArea> findCandidatosRetroactividadDispensacionNoBloqueante() {
        return seguimientoRepo.findCandidatosRetroactividadDispensacionNoBloqueante(
                ALMACEN_GENERAL_AREA_ID,
                EstadoSeguimientoOrdenArea.ESPERA.getCode(),
                EstadoSeguimientoOrdenArea.COLA.getCode()
        );
    }

    private String buildRetroactividadMensaje(boolean directivaActual, int candidatos, boolean ejecucion) {
        if (!directivaActual) {
            return "La directiva de dispensacion no bloqueante esta apagada. No se re-bloquean ordenes retroactivamente.";
        }
        if (candidatos == 0) {
            return "No hay ordenes activas candidatas para liberacion retroactiva.";
        }
        return ejecucion
                ? "Politica no bloqueante aplicada retroactivamente a " + candidatos + " orden(es)."
                : "Hay " + candidatos + " orden(es) activas candidatas para liberacion retroactiva.";
    }

    private void validarTransicion(
            SeguimientoOrdenArea seguimiento,
            EstadoSeguimientoOrdenArea estadoOrigen,
            EstadoSeguimientoOrdenArea estadoDestino,
            ActorTipoEventoSeguimiento actorTipo
    ) {
        if (estadoOrigen == estadoDestino) {
            throw new IllegalArgumentException("La orden ya se encuentra en estado " + estadoDestino.getDescripcion() + ".");
        }

        if (estadoOrigen == EstadoSeguimientoOrdenArea.COMPLETADO) {
            throw new IllegalArgumentException("No se puede modificar un seguimiento ya completado.");
        }

        if (estadoOrigen == EstadoSeguimientoOrdenArea.OMITIDO) {
            throw new IllegalArgumentException("No se puede modificar un seguimiento omitido.");
        }

        if (seguimiento.getAreaOperativa() != null
                && seguimiento.getAreaOperativa().getAreaId() == ALMACEN_GENERAL_AREA_ID
                && actorTipo == ActorTipoEventoSeguimiento.SYSTEM
                && estadoOrigen == EstadoSeguimientoOrdenArea.ESPERA
                && estadoDestino == EstadoSeguimientoOrdenArea.COMPLETADO) {
            return;
        }

        boolean transitionAllowed = switch (estadoOrigen) {
            case COLA -> estadoDestino == EstadoSeguimientoOrdenArea.ESPERA;
            case ESPERA -> estadoDestino == EstadoSeguimientoOrdenArea.EN_PROCESO;
            case EN_PROCESO -> estadoDestino == EstadoSeguimientoOrdenArea.ESPERA
                    || estadoDestino == EstadoSeguimientoOrdenArea.COMPLETADO;
            case COMPLETADO, OMITIDO -> false;
        };

        if (!transitionAllowed) {
            throw new IllegalArgumentException(
                    "Transicion no permitida de " + estadoOrigen.getDescripcion() + " a " + estadoDestino.getDescripcion() + ".");
        }
    }

    private void validarCorreccionSobreSeguimiento(
            SeguimientoOrdenArea seguimiento,
            int areaId,
            int expectedEstado
    ) {
        if (seguimiento.getAreaOperativa() == null || seguimiento.getAreaOperativa().getAreaId() != areaId) {
            throw new IllegalArgumentException("El seguimiento no pertenece al area operativa seleccionada.");
        }
        if (areaId == ALMACEN_GENERAL_AREA_ID) {
            throw new IllegalArgumentException("Las correcciones administrativas de Almacen General no estan habilitadas.");
        }
        if (seguimiento.getOrdenProduccion() == null || seguimiento.getOrdenProduccion().getEstadoOrden() == -1) {
            throw new IllegalArgumentException("No se puede corregir una orden cancelada.");
        }
        if (seguimiento.getOrdenProduccion().getEstadoOrden() == 2) {
            throw new IllegalArgumentException("No se puede corregir una orden finalizada.");
        }
        if (seguimiento.getEstado() != expectedEstado) {
            throw new IllegalArgumentException("El estado de la orden cambio. Actualice el tablero antes de corregir.");
        }
    }

    private void validarEstadosCorreccion(
            SeguimientoOrdenArea seguimiento,
            EstadoSeguimientoOrdenArea estadoOrigen,
            EstadoSeguimientoOrdenArea estadoDestino
    ) {
        if (estadoOrigen == estadoDestino) {
            throw new IllegalArgumentException("La orden ya se encuentra en estado " + estadoDestino.getDescripcion() + ".");
        }
        if (estadoOrigen == EstadoSeguimientoOrdenArea.OMITIDO || estadoDestino == EstadoSeguimientoOrdenArea.OMITIDO) {
            throw new IllegalArgumentException("El estado omitido no esta disponible para correcciones administrativas.");
        }
        if (seguimiento.getRutaProcesoNode() == null) {
            throw new IllegalArgumentException("El seguimiento no tiene nodo de ruta de proceso asociado.");
        }
    }

    private void validarDependenciasCorreccion(
            SeguimientoOrdenArea seguimiento,
            EstadoSeguimientoOrdenArea estadoDestino
    ) {
        if (estadoDestino == EstadoSeguimientoOrdenArea.COLA) {
            return;
        }

        long predecesoresNoCompletados = seguimientoRepo.countPredecesoresNoCompletados(
                seguimiento.getOrdenProduccion().getOrdenId(),
                seguimiento.getRutaProcesoNode().getId()
        );
        if (predecesoresNoCompletados > 0) {
            throw new IllegalArgumentException("No se puede mover la orden a este estado porque tiene pasos predecesores pendientes.");
        }
    }

    private void revertirSucesoresAutoHabilitados(
            SeguimientoOrdenArea seguimientoCorregido,
            User actor,
            String motivo,
            LocalDateTime ahora
    ) {
        Set<Long> descendantNodeIds = collectDescendantNodeIds(seguimientoCorregido);
        if (descendantNodeIds.isEmpty()) {
            return;
        }

        List<SeguimientoOrdenArea> descendants = seguimientoRepo
                .findByOrdenProduccion_OrdenIdOrderByPosicionSecuenciaAsc(
                        seguimientoCorregido.getOrdenProduccion().getOrdenId())
                .stream()
                .filter(candidate -> candidate.getRutaProcesoNode() != null)
                .filter(candidate -> descendantNodeIds.contains(candidate.getRutaProcesoNode().getId()))
                .toList();

        List<Long> descendantSeguimientoIds = descendants.stream()
                .map(SeguimientoOrdenArea::getId)
                .toList();

        if (!descendantSeguimientoIds.isEmpty()
                && seguimientoEventoRepo.existsUnrevertedEventBySeguimientoIdsAndActorTipoAndTipoEvento(
                        descendantSeguimientoIds,
                        ActorTipoEventoSeguimiento.USER,
                        TipoEventoSeguimiento.OPERATIVO
                )) {
            throw new IllegalArgumentException(
                    "No se puede revertir el estado completado porque un paso sucesor ya tiene trabajo operativo registrado.");
        }

        for (SeguimientoOrdenArea descendant : descendants) {
            EstadoSeguimientoOrdenArea estadoActual = descendant.getEstadoEnum();
            if (estadoActual == EstadoSeguimientoOrdenArea.COLA) {
                continue;
            }
            if (estadoActual != EstadoSeguimientoOrdenArea.ESPERA) {
                throw new IllegalArgumentException(
                        "No se puede revertir el estado completado porque un paso sucesor ya avanzo.");
            }

            SeguimientoOrdenAreaEvento eventoRevertido = findLatestUnrevertedEventForState(descendant, estadoActual)
                    .orElse(null);
            String notaReversion = buildNotaReversionCorreccion(motivo);
            aplicarEstadoPorCorreccion(descendant, EstadoSeguimientoOrdenArea.COLA, actor, notaReversion, ahora);
            seguimientoRepo.save(descendant);
            registrarEvento(
                    descendant,
                    estadoActual,
                    EstadoSeguimientoOrdenArea.COLA,
                    ActorTipoEventoSeguimiento.USER,
                    actor,
                    notaReversion,
                    ahora,
                    TipoEventoSeguimiento.CORRECCION_ADMINISTRATIVA,
                    eventoRevertido
            );
        }
    }

    private Set<Long> collectDescendantNodeIds(SeguimientoOrdenArea seguimiento) {
        if (seguimiento.getRutaProcesoNode() == null
                || seguimiento.getRutaProcesoNode().getRutaProcesoCatVersion() == null
                || seguimiento.getRutaProcesoNode().getRutaProcesoCatVersion().getEdges() == null) {
            return Set.of();
        }

        Map<Long, List<Long>> adjacency = new HashMap<>();
        for (RutaProcesoEdge edge : seguimiento.getRutaProcesoNode().getRutaProcesoCatVersion().getEdges()) {
            if (edge.getSourceNode() == null || edge.getTargetNode() == null) {
                continue;
            }
            adjacency.computeIfAbsent(edge.getSourceNode().getId(), ignored -> new ArrayList<>())
                    .add(edge.getTargetNode().getId());
        }

        Set<Long> visited = new HashSet<>();
        List<Long> pending = new ArrayList<>(adjacency.getOrDefault(seguimiento.getRutaProcesoNode().getId(), List.of()));
        while (!pending.isEmpty()) {
            Long nodeId = pending.remove(0);
            if (!visited.add(nodeId)) {
                continue;
            }
            pending.addAll(adjacency.getOrDefault(nodeId, List.of()));
        }
        return visited;
    }

    private Optional<SeguimientoOrdenAreaEvento> findLatestUnrevertedEventForState(
            SeguimientoOrdenArea seguimiento,
            EstadoSeguimientoOrdenArea estado
    ) {
        if (seguimiento == null || seguimiento.getId() == null || estado == null) {
            return Optional.empty();
        }
        return seguimientoEventoRepo
                .findEventosActualesNoRevertidos(seguimiento.getId(), estado.getCode())
                .stream()
                .findFirst();
    }

    private void aplicarEstadoPorCorreccion(
            SeguimientoOrdenArea seguimiento,
            EstadoSeguimientoOrdenArea estadoDestino,
            User actor,
            String motivo,
            LocalDateTime ahora
    ) {
        seguimiento.setEstadoEnum(estadoDestino);
        seguimiento.setFechaEstadoActual(ahora);

        if (estadoDestino == EstadoSeguimientoOrdenArea.COLA) {
            seguimiento.setFechaVisible(null);
        } else if (seguimiento.getFechaVisible() == null) {
            seguimiento.setFechaVisible(ahora);
        }

        if ((estadoDestino == EstadoSeguimientoOrdenArea.EN_PROCESO
                || estadoDestino == EstadoSeguimientoOrdenArea.COMPLETADO)
                && seguimiento.getOrdenProduccion().getFechaInicio() == null) {
            seguimiento.getOrdenProduccion().setFechaInicio(ahora);
        }

        if (estadoDestino == EstadoSeguimientoOrdenArea.COMPLETADO) {
            seguimiento.setFechaCompletado(ahora);
            seguimiento.setUsuarioReporta(actor);
            seguimiento.setObservaciones(motivo);
            return;
        }

        seguimiento.setFechaCompletado(null);
        seguimiento.setUsuarioReporta(null);
        seguimiento.setObservaciones(null);
    }

    private void clearOrdenFechaInicioIfNoOperativeWork(OrdenProduccion orden) {
        if (orden == null || orden.getFechaInicio() == null) {
            return;
        }

        boolean hasOperativeWork = seguimientoEventoRepo.existsUserStartOrCompletionEventByOrdenId(
                orden.getOrdenId(),
                ActorTipoEventoSeguimiento.USER,
                TipoEventoSeguimiento.OPERATIVO,
                List.of(
                        EstadoSeguimientoOrdenArea.EN_PROCESO.getCode(),
                        EstadoSeguimientoOrdenArea.COMPLETADO.getCode()
                )
        );
        if (!hasOperativeWork) {
            orden.setFechaInicio(null);
        }
    }

    private void registrarEvento(
            SeguimientoOrdenArea seguimiento,
            EstadoSeguimientoOrdenArea estadoOrigen,
            EstadoSeguimientoOrdenArea estadoDestino,
            ActorTipoEventoSeguimiento actorTipo,
            User actor,
            String nota,
            LocalDateTime fechaEvento
    ) {
        TipoEventoSeguimiento tipoEvento = actorTipo == ActorTipoEventoSeguimiento.SYSTEM
                ? TipoEventoSeguimiento.SISTEMA
                : TipoEventoSeguimiento.OPERATIVO;
        registrarEvento(
                seguimiento,
                estadoOrigen,
                estadoDestino,
                actorTipo,
                actor,
                nota,
                fechaEvento,
                tipoEvento,
                null
        );
    }

    private void registrarEvento(
            SeguimientoOrdenArea seguimiento,
            EstadoSeguimientoOrdenArea estadoOrigen,
            EstadoSeguimientoOrdenArea estadoDestino,
            ActorTipoEventoSeguimiento actorTipo,
            User actor,
            String nota,
            LocalDateTime fechaEvento,
            TipoEventoSeguimiento tipoEvento,
            SeguimientoOrdenAreaEvento eventoRevertido
    ) {
        SeguimientoOrdenAreaEvento evento = new SeguimientoOrdenAreaEvento();
        evento.setSeguimientoOrdenArea(seguimiento);
        evento.setEstadoOrigen(estadoOrigen != null ? estadoOrigen.getCode() : null);
        evento.setEstadoDestino(estadoDestino.getCode());
        evento.setFechaEvento(fechaEvento);
        evento.setActorTipo(actorTipo);
        evento.setTipoEvento(tipoEvento);
        evento.setEventoRevertido(eventoRevertido);
        evento.setUsuario(actor);
        evento.setNota(normalizeNota(nota));
        seguimientoEventoRepo.save(evento);
    }

    private User requireUser(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("Se requiere un usuario para registrar esta transicion.");
        }
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado con ID: " + userId));
    }

    private String normalizeNota(String nota) {
        if (nota == null) {
            return null;
        }
        String trimmed = nota.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizeMotivoCorreccion(String motivo) {
        String normalized = normalizeNota(motivo);
        if (normalized == null) {
            throw new IllegalArgumentException("El motivo de la correccion administrativa es obligatorio.");
        }
        if (normalized.length() > MAX_MOTIVO_CORRECCION_LENGTH) {
            throw new IllegalArgumentException("El motivo de la correccion no puede superar 500 caracteres.");
        }
        return normalized;
    }

    private String buildNotaReversionCorreccion(String motivo) {
        String nota = NOTA_REVERSION_SUCESOR_CORRECCION + motivo;
        if (nota.length() <= MAX_MOTIVO_CORRECCION_LENGTH) {
            return nota;
        }
        return nota.substring(0, MAX_MOTIVO_CORRECCION_LENGTH);
    }

    private SeguimientoOrdenAreaDTO toDTO(SeguimientoOrdenArea entity) {
        return toDTO(entity, LocalDateTime.now(applicationClock));
    }

    private SeguimientoOrdenAreaDTO toDTO(SeguimientoOrdenArea entity, LocalDateTime instanteReferencia) {
        SeguimientoOrdenAreaDTO dto = new SeguimientoOrdenAreaDTO();
        dto.setId(entity.getId());
        dto.setOrdenId(entity.getOrdenProduccion().getOrdenId());
        dto.setLoteAsignado(entity.getOrdenProduccion().getLoteAsignado());
        dto.setProductoId(entity.getOrdenProduccion().getProducto().getProductoId());
        dto.setProductoNombre(entity.getOrdenProduccion().getProducto().getNombre());
        dto.setTipoUnidades(entity.getOrdenProduccion().getProducto().getTipoUnidades());
        dto.setCantidadProducir(entity.getOrdenProduccion().getCantidadProducir());
        dto.setEstadoOrden(entity.getOrdenProduccion().getEstadoOrden());
        dto.setPoliticaDispensacionInicio(entity.getOrdenProduccion().getPoliticaDispensacionInicio() != null
                ? entity.getOrdenProduccion().getPoliticaDispensacionInicio().name()
                : null);
        dto.setFechaAplicacionPoliticaDispensacion(entity.getOrdenProduccion().getFechaAplicacionPoliticaDispensacion());
        dto.setEstadoDispensacionMateriales(entity.getOrdenProduccion().getEstadoDispensacionMateriales() != null
                ? entity.getOrdenProduccion().getEstadoDispensacionMateriales().name()
                : null);
        dto.setOrdenObservaciones(entity.getOrdenProduccion().getObservaciones());
        dto.setFechaFinalPlanificada(entity.getOrdenProduccion().getFechaFinalPlanificada());

        dto.setNodeId(entity.getRutaProcesoNode().getId());
        dto.setNodeLabel(entity.getRutaProcesoNode().getLabel());
        dto.setEsNodoFinal(esNodoFinalConRutaValida(entity, false));

        AreaOperativa area = entity.getAreaOperativa();
        dto.setAreaId(area.getAreaId());
        dto.setAreaNombre(area.getNombre());

        dto.setEstado(entity.getEstado());
        dto.setEstadoDescripcion(getEstadoDescripcion(entity.getEstado()));
        dto.setPosicionSecuencia(entity.getPosicionSecuencia());
        dto.setFechaCreacion(entity.getFechaCreacion());
        dto.setFechaVisible(entity.getFechaVisible());
        dto.setFechaEstadoActual(entity.getFechaEstadoActual());
        dto.setFechaCompletado(entity.getFechaCompletado());
        dto.setDuracionEstimadaMinutos(entity.getDuracionEstimadaMinutos());
        dto.setRequiereJornadaLaboral(entity.isRequiereJornadaLaboral());
        dto.setObservaciones(entity.getObservaciones());
        dto.setMinutosEnEstadoActual(calculateMinutesBetween(entity.getFechaEstadoActual(), instanteReferencia));

        if (entity.getUsuarioReporta() != null) {
            dto.setUsuarioReportaId(entity.getUsuarioReporta().getId());
            dto.setUsuarioReportaNombre(entity.getUsuarioReporta().getNombreCompleto());
        }

        return dto;
    }

    private String getEstadoDescripcion(int estado) {
        return EstadoSeguimientoOrdenArea.fromCode(estado).getDescripcion();
    }

    private void validarUsuarioResponsableArea(SeguimientoOrdenArea seguimiento, User actor) {
        User responsable = seguimiento.getAreaOperativa() != null
                ? seguimiento.getAreaOperativa().getResponsableArea()
                : null;
        if (responsable == null || !responsable.getId().equals(actor.getId())) {
            throw new AccessDeniedException("El usuario no es responsable del area operativa indicada.");
        }
    }

    private boolean esNodoFinalConRutaValida(SeguimientoOrdenArea seguimiento, boolean failOnInvalid) {
        OrdenProduccion orden = seguimiento.getOrdenProduccion();
        RutaProcesoCatVersion version = orden != null ? orden.getRutaProcesoCatVersion() : null;
        try {
            RutaProcesoNode terminal = resolverTerminalRutaValida(version);
            return seguimiento.getRutaProcesoNode() != null
                    && terminal.getId().equals(seguimiento.getRutaProcesoNode().getId());
        } catch (IllegalStateException error) {
            if (failOnInvalid) {
                throw error;
            }
            return false;
        }
    }

    private RutaProcesoNode resolverTerminalRutaValida(RutaProcesoCatVersion version) {
        if (version == null || version.getNodes() == null || version.getNodes().size() < 2) {
            throw new IllegalStateException(
                    "La version de ruta de la OP debe incluir Almacen General y al menos un area productiva.");
        }

        Map<Long, RutaProcesoNode> nodesById = new LinkedHashMap<>();
        Map<Long, Integer> indegree = new LinkedHashMap<>();
        Map<Long, Set<Long>> adjacency = new LinkedHashMap<>();
        Set<Integer> areaIds = new HashSet<>();
        int warehouseCount = 0;

        for (RutaProcesoNode node : version.getNodes()) {
            if (node == null || node.getId() == null || node.getAreaOperativa() == null) {
                throw new IllegalStateException("La version de ruta de la OP contiene nodos incompletos.");
            }
            if (nodesById.put(node.getId(), node) != null
                    || !areaIds.add(node.getAreaOperativa().getAreaId())) {
                throw new IllegalStateException("La version de ruta de la OP contiene nodos o areas repetidas.");
            }
            if (node.getAreaOperativa().getAreaId() == ALMACEN_GENERAL_AREA_ID) {
                warehouseCount++;
            }
            indegree.put(node.getId(), 0);
            adjacency.put(node.getId(), new LinkedHashSet<>());
        }
        if (warehouseCount != 1) {
            throw new IllegalStateException("La version de ruta de la OP debe contener un unico Almacen General.");
        }

        for (RutaProcesoEdge edge : Optional.ofNullable(version.getEdges()).orElse(List.of())) {
            Long sourceId = edge != null && edge.getSourceNode() != null
                    ? edge.getSourceNode().getId()
                    : null;
            Long targetId = edge != null && edge.getTargetNode() != null
                    ? edge.getTargetNode().getId()
                    : null;
            if (sourceId == null || targetId == null
                    || !nodesById.containsKey(sourceId) || !nodesById.containsKey(targetId)
                    || sourceId.equals(targetId)) {
                throw new IllegalStateException("La version de ruta de la OP contiene conexiones invalidas.");
            }
            if (!adjacency.get(sourceId).add(targetId)) {
                throw new IllegalStateException("La version de ruta de la OP contiene conexiones repetidas.");
            }
            indegree.put(targetId, indegree.get(targetId) + 1);
        }

        List<Long> rootIds = indegree.entrySet().stream()
                .filter(entry -> entry.getValue() == 0)
                .map(Map.Entry::getKey)
                .toList();
        if (rootIds.size() != 1
                || nodesById.get(rootIds.get(0)).getAreaOperativa().getAreaId() != ALMACEN_GENERAL_AREA_ID) {
            throw new IllegalStateException(
                    "La version de ruta de la OP debe tener Almacen General como unica raiz.");
        }

        List<Long> terminalIds = adjacency.entrySet().stream()
                .filter(entry -> entry.getValue().isEmpty())
                .map(Map.Entry::getKey)
                .toList();
        if (terminalIds.size() != 1
                || nodesById.get(terminalIds.get(0)).getAreaOperativa().getAreaId()
                == ALMACEN_GENERAL_AREA_ID) {
            throw new IllegalStateException(
                    "La version de ruta de la OP debe tener una unica area productiva final.");
        }

        Set<Long> reachable = new HashSet<>();
        Deque<Long> pending = new ArrayDeque<>();
        pending.add(rootIds.get(0));
        reachable.add(rootIds.get(0));
        while (!pending.isEmpty()) {
            for (Long targetId : adjacency.get(pending.removeFirst())) {
                if (reachable.add(targetId)) {
                    pending.addLast(targetId);
                }
            }
        }
        if (reachable.size() != nodesById.size()) {
            throw new IllegalStateException("La version de ruta de la OP contiene nodos desconectados.");
        }

        Map<Long, Integer> remainingIndegree = new HashMap<>(indegree);
        Deque<Long> zeroIndegree = remainingIndegree.entrySet().stream()
                .filter(entry -> entry.getValue() == 0)
                .map(Map.Entry::getKey)
                .collect(Collectors.toCollection(ArrayDeque::new));
        int processed = 0;
        while (!zeroIndegree.isEmpty()) {
            Long sourceId = zeroIndegree.removeFirst();
            processed++;
            for (Long targetId : adjacency.get(sourceId)) {
                int next = remainingIndegree.get(targetId) - 1;
                remainingIndegree.put(targetId, next);
                if (next == 0) {
                    zeroIndegree.addLast(targetId);
                }
            }
        }
        if (processed != nodesById.size()) {
            throw new IllegalStateException("La version de ruta de la OP contiene ciclos.");
        }
        return nodesById.get(terminalIds.get(0));
    }

    private Map<Long, List<SeguimientoOrdenAreaEvento>> loadEventosPorSeguimiento(List<SeguimientoOrdenArea> seguimientos) {
        List<Long> seguimientoIds = seguimientos.stream()
                .map(SeguimientoOrdenArea::getId)
                .toList();

        if (seguimientoIds.isEmpty()) {
            return Map.of();
        }

        return seguimientoEventoRepo.findBySeguimientoOrdenArea_IdInOrderByFechaEventoAscIdAsc(seguimientoIds)
                .stream()
                .collect(Collectors.groupingBy(
                        evento -> evento.getSeguimientoOrdenArea().getId(),
                        HashMap::new,
                        Collectors.toList()
                ));
    }

    private LocalDateTime resolveInstanteFoto(LocalDate fechaConsulta) {
        LocalDate hoy = LocalDate.now(applicationClock);
        if (!fechaConsulta.isBefore(hoy)) {
            return LocalDateTime.now(applicationClock);
        }
        return LocalDateTime.of(fechaConsulta, LocalTime.MAX);
    }

    private SeguimientoOrdenAreaDTO toSnapshotDTO(
            SeguimientoOrdenArea entity,
            List<SeguimientoOrdenAreaEvento> eventos,
            LocalDateTime instanteFoto
    ) {
        if (entity.getFechaCreacion() != null && entity.getFechaCreacion().isAfter(instanteFoto)) {
            return null;
        }

        SeguimientoOrdenAreaEvento ultimoEvento = filterUnrevertedEvents(eventos).stream()
                .filter(evento -> !evento.getFechaEvento().isAfter(instanteFoto))
                .reduce((ignored, current) -> current)
                .orElse(null);

        if (ultimoEvento == null) {
            return null;
        }

        SeguimientoOrdenAreaDTO dto = toDTO(entity, instanteFoto);
        dto.setEstado(ultimoEvento.getEstadoDestino());
        dto.setEstadoDescripcion(getEstadoDescripcion(ultimoEvento.getEstadoDestino()));
        dto.setFechaEstadoActual(ultimoEvento.getFechaEvento());
        dto.setMinutosEnEstadoActual(calculateMinutesBetween(ultimoEvento.getFechaEvento(), instanteFoto));

        if (dto.getFechaVisible() != null && dto.getFechaVisible().isAfter(instanteFoto)) {
            dto.setFechaVisible(null);
        }

        if (dto.getFechaCompletado() != null && dto.getFechaCompletado().isAfter(instanteFoto)) {
            dto.setFechaCompletado(null);
        }

        dto.setObservaciones(ultimoEvento.getNota());
        if (ultimoEvento.getUsuario() != null) {
            dto.setUsuarioReportaId(ultimoEvento.getUsuario().getId());
            dto.setUsuarioReportaNombre(ultimoEvento.getUsuario().getNombreCompleto());
        } else {
            dto.setUsuarioReportaId(null);
            dto.setUsuarioReportaNombre(null);
        }

        return dto;
    }

    private List<SeguimientoOrdenAreaEvento> filterUnrevertedEvents(List<SeguimientoOrdenAreaEvento> eventos) {
        if (eventos == null || eventos.isEmpty()) {
            return List.of();
        }
        Set<Long> revertedEventIds = eventos.stream()
                .map(SeguimientoOrdenAreaEvento::getEventoRevertido)
                .filter(java.util.Objects::nonNull)
                .map(SeguimientoOrdenAreaEvento::getId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());

        if (revertedEventIds.isEmpty()) {
            return eventos;
        }

        return eventos.stream()
                .filter(evento -> evento.getId() == null || !revertedEventIds.contains(evento.getId()))
                .toList();
    }

    private RutaEstadoDTO toRutaEstadoDTO(SeguimientoOrdenArea entity) {
        RutaEstadoDTO dto = new RutaEstadoDTO();
        dto.setSeguimientoId(entity.getId());
        dto.setNodeId(entity.getRutaProcesoNode().getId());
        dto.setNodeLabel(entity.getRutaProcesoNode().getLabel());
        dto.setAreaId(entity.getAreaOperativa().getAreaId());
        dto.setAreaNombre(entity.getAreaOperativa().getNombre());
        dto.setEstado(entity.getEstado());
        dto.setEstadoDescripcion(getEstadoDescripcion(entity.getEstado()));
        dto.setFechaVisible(entity.getFechaVisible());
        dto.setFechaEstadoActual(entity.getFechaEstadoActual());
        dto.setFechaCompletado(entity.getFechaCompletado());
        dto.setDuracionEstimadaMinutos(entity.getDuracionEstimadaMinutos());
        dto.setRequiereJornadaLaboral(entity.isRequiereJornadaLaboral());
        dto.setObservaciones(entity.getObservaciones());
        if (entity.getUsuarioReporta() != null) {
            dto.setUsuarioReportaId(entity.getUsuarioReporta().getId());
            dto.setUsuarioReportaNombre(entity.getUsuarioReporta().getNombreCompleto());
        }
        return dto;
    }

    private TableroOperativoDTO buildTableroOperativo(List<SeguimientoOrdenAreaDTO> tarjetas) {
        TableroOperativoDTO dto = new TableroOperativoDTO();
        dto.setCola(filterAndSortByEstado(tarjetas, EstadoSeguimientoOrdenArea.COLA));
        dto.setEspera(filterAndSortByEstado(tarjetas, EstadoSeguimientoOrdenArea.ESPERA));
        dto.setEnProceso(filterAndSortByEstado(tarjetas, EstadoSeguimientoOrdenArea.EN_PROCESO));
        dto.setCompletado(filterAndSortByEstado(tarjetas, EstadoSeguimientoOrdenArea.COMPLETADO));

        EstadoResumenDTO resumen = new EstadoResumenDTO();
        resumen.setCola((long) dto.getCola().size());
        resumen.setEspera((long) dto.getEspera().size());
        resumen.setEnProceso((long) dto.getEnProceso().size());
        resumen.setCompletado((long) dto.getCompletado().size());
        resumen.setTotal(resumen.getCola() + resumen.getEspera() + resumen.getEnProceso() + resumen.getCompletado());
        dto.setResumen(resumen);
        return dto;
    }

    private List<SeguimientoOrdenAreaDTO> filterAndSortByEstado(
            List<SeguimientoOrdenAreaDTO> tarjetas,
            EstadoSeguimientoOrdenArea estado
    ) {
        Comparator<SeguimientoOrdenAreaDTO> comparator;
        if (estado == EstadoSeguimientoOrdenArea.COMPLETADO) {
            comparator = Comparator.comparing(
                    SeguimientoOrdenAreaDTO::getFechaCompletado,
                    Comparator.nullsLast(Comparator.reverseOrder())
            );
        } else {
            comparator = Comparator
                    .comparing(
                            SeguimientoOrdenAreaDTO::getMinutosEnEstadoActual,
                            Comparator.nullsLast(Comparator.reverseOrder())
                    )
                    .thenComparing(
                            SeguimientoOrdenAreaDTO::getFechaEstadoActual,
                            Comparator.nullsLast(Comparator.naturalOrder())
                    );
        }

        return tarjetas.stream()
                .filter(card -> card.getEstado() == estado.getCode())
                .sorted(comparator)
                .toList();
    }

    private Double promedioMinutosEnEstado(List<SeguimientoOrdenAreaDTO> tarjetas) {
        var average = tarjetas.stream()
                .map(SeguimientoOrdenAreaDTO::getMinutosEnEstadoActual)
                .filter(java.util.Objects::nonNull)
                .mapToLong(Long::longValue)
                .average();
        return average.isPresent() ? average.getAsDouble() : null;
    }

    private OrdenMasAtrasadaDTO resolveOrdenMasAtrasada(List<SeguimientoOrdenAreaDTO> tarjetas) {
        return tarjetas.stream()
                .filter(card -> card.getEstado() != EstadoSeguimientoOrdenArea.COMPLETADO.getCode())
                .filter(card -> card.getMinutosEnEstadoActual() != null)
                .max(Comparator.comparing(SeguimientoOrdenAreaDTO::getMinutosEnEstadoActual))
                .map(card -> {
                    OrdenMasAtrasadaDTO dto = new OrdenMasAtrasadaDTO();
                    dto.setOrdenId(card.getOrdenId());
                    dto.setLoteAsignado(card.getLoteAsignado());
                    dto.setProductoNombre(card.getProductoNombre());
                    dto.setEstado(card.getEstado());
                    dto.setEstadoDescripcion(card.getEstadoDescripcion());
                    dto.setMinutosEnEstadoActual(card.getMinutosEnEstadoActual());
                    return dto;
                })
                .orElse(null);
    }

    private LocalDate getCurrentIsoWeekMonday() {
        LocalDate today = LocalDate.now(applicationClock);
        return today.minusDays(today.getDayOfWeek().getValue() - 1L);
    }

    private ResponsableAreaResumenDTO toResponsableResumen(User user) {
        ResponsableAreaResumenDTO dto = new ResponsableAreaResumenDTO();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setNombreCompleto(user.getNombreCompleto());
        return dto;
    }

    private Optional<SeguimientoOrdenAreaEvento> resolveUltimoReporteResponsable(
            AreaOperativa area,
            LocalDateTime instanteFoto
    ) {
        if (area == null || area.getResponsableArea() == null || instanteFoto == null) {
            return Optional.empty();
        }
        return seguimientoEventoRepo
                .findReportesOperativosResponsableBefore(
                        area.getAreaId(),
                        ActorTipoEventoSeguimiento.USER,
                        TipoEventoSeguimiento.OPERATIVO,
                        area.getResponsableArea().getId(),
                        instanteFoto
                )
                .stream()
                .findFirst();
    }

    private Long calculateMinutesBetween(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null) {
            return null;
        }
        long minutes = Duration.between(start, end).toMinutes();
        return Math.max(minutes, 0);
    }

    @Data
    public static class SeguimientoOrdenAreaDTO {
        private Long id;
        private int ordenId;
        private String loteAsignado;
        private String productoId;
        private String productoNombre;
        private String tipoUnidades;
        private double cantidadProducir;
        private int estadoOrden;
        private String politicaDispensacionInicio;
        private LocalDateTime fechaAplicacionPoliticaDispensacion;
        private String estadoDispensacionMateriales;
        private String ordenObservaciones;
        private LocalDateTime fechaFinalPlanificada;

        private Long nodeId;
        private String nodeLabel;
        private boolean esNodoFinal;

        private int areaId;
        private String areaNombre;

        private int estado;
        private String estadoDescripcion;
        private Integer posicionSecuencia;

        private LocalDateTime fechaCreacion;
        private LocalDateTime fechaVisible;
        private LocalDateTime fechaEstadoActual;
        private LocalDateTime fechaCompletado;
        private Long minutosEnEstadoActual;
        private int duracionEstimadaMinutos;
        private boolean requiereJornadaLaboral;

        private Long usuarioReportaId;
        private String usuarioReportaNombre;
        private String observaciones;
    }

    @Data
    public static class EstadoResumenDTO {
        private Long total;
        private Long cola;
        private Long espera;
        private Long enProceso;
        private Long completado;
    }

    @Data
    public static class TableroOperativoDTO {
        private TableroVista vista;
        private LocalDate periodStartDate;
        private LocalDate periodEndDate;
        private EstadoResumenDTO resumen;
        private List<SeguimientoOrdenAreaDTO> cola = new ArrayList<>();
        private List<SeguimientoOrdenAreaDTO> espera = new ArrayList<>();
        private List<SeguimientoOrdenAreaDTO> enProceso = new ArrayList<>();
        private List<SeguimientoOrdenAreaDTO> completado = new ArrayList<>();
    }

    public enum TableroVista {
        HOY,
        HISTORICO,
        SEMANA_ACTUAL
    }

    @Data
    public static class RutaEstadoDTO {
        private Long seguimientoId;
        private Long nodeId;
        private String nodeLabel;
        private Integer areaId;
        private String areaNombre;
        private int estado;
        private String estadoDescripcion;
        private LocalDateTime fechaVisible;
        private LocalDateTime fechaEstadoActual;
        private LocalDateTime fechaCompletado;
        private int duracionEstimadaMinutos;
        private boolean requiereJornadaLaboral;
        private Long usuarioReportaId;
        private String usuarioReportaNombre;
        private String observaciones;
    }

    @Data
    public static class OrdenSeguimientoDetalleDTO {
        private int ordenId;
        private String loteAsignado;
        private String productoId;
        private String productoNombre;
        private double cantidadProducir;
        private int estadoOrden;
        private String politicaDispensacionInicio;
        private LocalDateTime fechaAplicacionPoliticaDispensacion;
        private String estadoDispensacionMateriales;
        private String ordenObservaciones;
        private LocalDateTime fechaCreacion;
        private LocalDateTime fechaInicio;
        private LocalDateTime fechaFinal;
        private LocalDateTime fechaFinalPlanificada;
        private LocalDateTime fechaInicioEstimacion;
        private LocalDateTime fechaFinalEstimada;
        private Long duracionCalendarioRutaCriticaMinutos;
        private List<RutaEstadoDTO> rutaEstados = new ArrayList<>();
    }

    @Data
    public static class ResponsableAreaResumenDTO {
        private Long id;
        private String username;
        private String nombreCompleto;
    }

    @Data
    public static class DispensacionRetroactividadDTO {
        private boolean directivaActual;
        private boolean ejecutable;
        private int ordenesCandidatas;
        private int ordenesAplicadas;
        private int ordenesOmitidas;
        private List<Integer> ordenIdsAplicadas = new ArrayList<>();
        private String mensaje;
    }

    @Data
    public static class OrdenMasAtrasadaDTO {
        private int ordenId;
        private String loteAsignado;
        private String productoNombre;
        private int estado;
        private String estadoDescripcion;
        private Long minutosEnEstadoActual;
    }

    @Data
    public static class AreaOperativaTableroDTO {
        private Integer areaId;
        private String areaNombre;
        private String areaDescripcion;
        private ResponsableAreaResumenDTO responsableArea;
        private LocalDate fechaConsulta;
        private LocalDateTime instanteFoto;
        private LocalDateTime ultimaFechaReporteResponsable;
        private Integer ultimaOrdenReporteResponsableId;
        private String ultimaOrdenReporteResponsableLote;
        private EstadoResumenDTO resumen;
        private Double promedioMinutosEspera;
        private Double promedioMinutosEnProceso;
        private OrdenMasAtrasadaDTO ordenMasAtrasada;
        private List<SeguimientoOrdenAreaDTO> cola = new ArrayList<>();
        private List<SeguimientoOrdenAreaDTO> espera = new ArrayList<>();
        private List<SeguimientoOrdenAreaDTO> enProceso = new ArrayList<>();
        private List<SeguimientoOrdenAreaDTO> completado = new ArrayList<>();
    }

    @Data
    public static class ReportarCompletadoRequest {
        private int ordenId;
        private int areaId;
        private String observaciones;
        private BigDecimal cantidadProducida;
    }
}
