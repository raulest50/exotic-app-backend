package exotic.app.planta.service.produccion;

import exotic.app.planta.model.organizacion.AreaOperativa;
import exotic.app.planta.model.produccion.ActorTipoEventoSeguimiento;
import exotic.app.planta.model.produccion.EstadoSeguimientoOrdenArea;
import exotic.app.planta.model.produccion.OrdenProduccion;
import exotic.app.planta.model.produccion.SeguimientoOrdenArea;
import exotic.app.planta.model.produccion.SeguimientoOrdenAreaEvento;
import exotic.app.planta.model.produccion.ruprocatdesigner.RutaProcesoCat;
import exotic.app.planta.model.produccion.ruprocatdesigner.RutaProcesoNode;
import exotic.app.planta.model.producto.Categoria;
import exotic.app.planta.model.producto.Terminado;
import exotic.app.planta.model.users.User;
import exotic.app.planta.repo.produccion.SeguimientoOrdenAreaEventoRepo;
import exotic.app.planta.repo.produccion.SeguimientoOrdenAreaRepo;
import exotic.app.planta.repo.producto.procesos.AreaProduccionRepo;
import exotic.app.planta.repo.produccion.ruprocatdesigner.RutaProcesoCatRepo;
import exotic.app.planta.repo.usuarios.UserRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
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

    private final SeguimientoOrdenAreaRepo seguimientoRepo;
    private final SeguimientoOrdenAreaEventoRepo seguimientoEventoRepo;
    private final AreaProduccionRepo areaProduccionRepo;
    private final RutaProcesoCatRepo rutaProcesoCatRepo;
    private final UserRepository userRepository;
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

        Optional<RutaProcesoCat> rutaOpt = rutaProcesoCatRepo.findByCategoria_CategoriaId(categoria.getCategoriaId());
        if (rutaOpt.isEmpty()) {
            log.info("Categoria {} no tiene ruta de proceso definida", categoria.getCategoriaId());
            return;
        }

        RutaProcesoCat ruta = rutaOpt.get();
        if (ruta.getNodes() == null || ruta.getNodes().isEmpty()) {
            log.info("Ruta de proceso para categoria {} no tiene nodos definidos", categoria.getCategoriaId());
            return;
        }

        Set<Long> nodosConPredecesores = ruta.getEdges().stream()
                .map(edge -> edge.getTargetNode().getId())
                .collect(Collectors.toSet());

        int posicion = 0;
        LocalDateTime ahora = LocalDateTime.now(applicationClock);

        for (RutaProcesoNode node : ruta.getNodes()) {
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
        }

        log.info("Seguimiento inicializado para orden {} con {} nodos", orden.getOrdenId(), posicion);
    }

    public SeguimientoOrdenAreaDTO reportarEnProceso(int ordenId, int areaId, Long userId, String observaciones) {
        SeguimientoOrdenArea seguimiento = seguimientoRepo
                .findByOrdenProduccion_OrdenIdAndAreaOperativa_AreaIdAndEstado(
                        ordenId,
                        areaId,
                        EstadoSeguimientoOrdenArea.ESPERA.getCode())
                .orElseThrow(() -> new IllegalArgumentException(
                        "No se encontro seguimiento en espera para orden " + ordenId + " y area " + areaId));

        transicionarSeguimiento(
                seguimiento,
                EstadoSeguimientoOrdenArea.EN_PROCESO,
                ActorTipoEventoSeguimiento.USER,
                requireUser(userId),
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

        transicionarSeguimiento(
                seguimiento,
                EstadoSeguimientoOrdenArea.ESPERA,
                ActorTipoEventoSeguimiento.USER,
                requireUser(userId),
                observaciones
        );

        return toDTO(seguimiento);
    }

    /**
     * El cierre manual solo es valido para pasos actualmente en proceso.
     */
    public SeguimientoOrdenAreaDTO reportarCompletado(int ordenId, int areaId, Long userId, String observaciones) {
        SeguimientoOrdenArea seguimiento = seguimientoRepo
                .findByOrdenProduccion_OrdenIdAndAreaOperativa_AreaIdAndEstado(
                        ordenId,
                        areaId,
                        EstadoSeguimientoOrdenArea.EN_PROCESO.getCode())
                .orElseThrow(() -> new IllegalArgumentException(
                        "No se encontro seguimiento en proceso para orden " + ordenId + " y area " + areaId));

        completarSeguimiento(seguimiento, ActorTipoEventoSeguimiento.USER, requireUser(userId), observaciones);
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
        List<SeguimientoOrdenAreaDTO> tarjetas = seguimientoRepo.findTableroByResponsableUserId(userId)
                .stream()
                .map(this::toDTO)
                .toList();

        return buildTableroOperativo(tarjetas);
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
        detalle.setOrdenObservaciones(orden.getObservaciones());
        detalle.setFechaCreacion(orden.getFechaCreacion());
        detalle.setFechaInicio(orden.getFechaInicio());
        detalle.setFechaFinal(orden.getFechaFinal());
        detalle.setFechaFinalPlanificada(orden.getFechaFinalPlanificada());
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

    private void registrarEvento(
            SeguimientoOrdenArea seguimiento,
            EstadoSeguimientoOrdenArea estadoOrigen,
            EstadoSeguimientoOrdenArea estadoDestino,
            ActorTipoEventoSeguimiento actorTipo,
            User actor,
            String nota,
            LocalDateTime fechaEvento
    ) {
        SeguimientoOrdenAreaEvento evento = new SeguimientoOrdenAreaEvento();
        evento.setSeguimientoOrdenArea(seguimiento);
        evento.setEstadoOrigen(estadoOrigen != null ? estadoOrigen.getCode() : null);
        evento.setEstadoDestino(estadoDestino.getCode());
        evento.setFechaEvento(fechaEvento);
        evento.setActorTipo(actorTipo);
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
        dto.setCantidadProducir(entity.getOrdenProduccion().getCantidadProducir());
        dto.setEstadoOrden(entity.getOrdenProduccion().getEstadoOrden());
        dto.setOrdenObservaciones(entity.getOrdenProduccion().getObservaciones());
        dto.setFechaFinalPlanificada(entity.getOrdenProduccion().getFechaFinalPlanificada());

        dto.setNodeId(entity.getRutaProcesoNode().getId());
        dto.setNodeLabel(entity.getRutaProcesoNode().getLabel());

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

        SeguimientoOrdenAreaEvento ultimoEvento = eventos.stream()
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

    private ResponsableAreaResumenDTO toResponsableResumen(User user) {
        ResponsableAreaResumenDTO dto = new ResponsableAreaResumenDTO();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setNombreCompleto(user.getNombreCompleto());
        return dto;
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
        private double cantidadProducir;
        private int estadoOrden;
        private String ordenObservaciones;
        private LocalDateTime fechaFinalPlanificada;

        private Long nodeId;
        private String nodeLabel;

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
        private EstadoResumenDTO resumen;
        private List<SeguimientoOrdenAreaDTO> cola = new ArrayList<>();
        private List<SeguimientoOrdenAreaDTO> espera = new ArrayList<>();
        private List<SeguimientoOrdenAreaDTO> enProceso = new ArrayList<>();
        private List<SeguimientoOrdenAreaDTO> completado = new ArrayList<>();
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
        private String ordenObservaciones;
        private LocalDateTime fechaCreacion;
        private LocalDateTime fechaInicio;
        private LocalDateTime fechaFinal;
        private LocalDateTime fechaFinalPlanificada;
        private List<RutaEstadoDTO> rutaEstados = new ArrayList<>();
    }

    @Data
    public static class ResponsableAreaResumenDTO {
        private Long id;
        private String username;
        private String nombreCompleto;
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
    }
}
