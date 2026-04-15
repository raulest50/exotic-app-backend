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
import java.time.LocalDateTime;
import java.util.List;
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
    public List<SeguimientoOrdenAreaDTO> getProgresoOrden(int ordenId) {
        return seguimientoRepo.findByOrdenProduccion_OrdenIdOrderByPosicionSecuenciaAsc(ordenId)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
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
        dto.setFechaCompletado(entity.getFechaCompletado());
        dto.setObservaciones(entity.getObservaciones());

        if (entity.getUsuarioReporta() != null) {
            dto.setUsuarioReportaId(entity.getUsuarioReporta().getId());
            dto.setUsuarioReportaNombre(entity.getUsuarioReporta().getNombreCompleto());
        }

        return dto;
    }

    private String getEstadoDescripcion(int estado) {
        return EstadoSeguimientoOrdenArea.fromCode(estado).getDescripcion();
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
        private LocalDateTime fechaCompletado;

        private Long usuarioReportaId;
        private String usuarioReportaNombre;
        private String observaciones;
    }

    @Data
    public static class ReportarCompletadoRequest {
        private int ordenId;
        private int areaId;
        private String observaciones;
    }
}
