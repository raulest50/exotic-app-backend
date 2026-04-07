package exotic.app.planta.service.produccion;

import exotic.app.planta.model.organizacion.AreaOperativa;
import exotic.app.planta.model.produccion.OrdenProduccion;
import exotic.app.planta.model.produccion.SeguimientoOrdenArea;
import exotic.app.planta.model.produccion.ruprocatdesigner.RutaProcesoCat;
import exotic.app.planta.model.produccion.ruprocatdesigner.RutaProcesoEdge;
import exotic.app.planta.model.produccion.ruprocatdesigner.RutaProcesoNode;
import exotic.app.planta.model.producto.Categoria;
import exotic.app.planta.model.producto.Terminado;
import exotic.app.planta.model.users.User;
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

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@Transactional(rollbackFor = Exception.class)
@RequiredArgsConstructor
public class SeguimientoOrdenAreaService {

    public static final int ALMACEN_GENERAL_AREA_ID = -1;

    private final SeguimientoOrdenAreaRepo seguimientoRepo;
    private final RutaProcesoCatRepo rutaProcesoCatRepo;
    private final UserRepository userRepository;

    /**
     * Inicializa el seguimiento para una nueva orden de producción.
     * Crea registros de seguimiento para cada nodo de la ruta de proceso de la categoría del producto.
     * Los nodos iniciales (sin predecesores) se marcan como VISIBLE.
     */
    public void inicializarSeguimiento(OrdenProduccion orden) {
        if (orden == null || orden.getProducto() == null) {
            log.warn("No se puede inicializar seguimiento: orden o producto nulo");
            return;
        }

        // Solo para productos terminados que tienen categoría
        if (!(orden.getProducto() instanceof Terminado terminado)) {
            log.info("Producto {} no es Terminado, no se inicializa seguimiento", orden.getProducto().getProductoId());
            return;
        }

        Categoria categoria = terminado.getCategoria();
        if (categoria == null) {
            log.info("Producto terminado {} no tiene categoría asignada", terminado.getProductoId());
            return;
        }

        Optional<RutaProcesoCat> rutaOpt = rutaProcesoCatRepo.findByCategoria_CategoriaId(categoria.getCategoriaId());
        if (rutaOpt.isEmpty()) {
            log.info("Categoría {} no tiene ruta de proceso definida", categoria.getCategoriaId());
            return;
        }

        RutaProcesoCat ruta = rutaOpt.get();
        if (ruta.getNodes() == null || ruta.getNodes().isEmpty()) {
            log.info("Ruta de proceso para categoría {} no tiene nodos definidos", categoria.getCategoriaId());
            return;
        }

        // Construir conjunto de nodos que tienen predecesores
        Set<Long> nodosConPredecesores = ruta.getEdges().stream()
                .map(edge -> edge.getTargetNode().getId())
                .collect(Collectors.toSet());

        // Crear registros de seguimiento
        int posicion = 0;
        LocalDateTime ahora = LocalDateTime.now();

        for (RutaProcesoNode node : ruta.getNodes()) {
            if (node.getAreaOperativa() == null) {
                log.warn("Nodo {} no tiene área operativa asignada, se omite", node.getId());
                continue;
            }

            SeguimientoOrdenArea seguimiento = new SeguimientoOrdenArea();
            seguimiento.setOrdenProduccion(orden);
            seguimiento.setRutaProcesoNode(node);
            seguimiento.setAreaOperativa(node.getAreaOperativa());
            seguimiento.setPosicionSecuencia(posicion++);

            // Nodos sin predecesores son nodos iniciales -> VISIBLE
            boolean esNodoInicial = !nodosConPredecesores.contains(node.getId());
            if (esNodoInicial) {
                seguimiento.setEstado(SeguimientoOrdenArea.ESTADO_VISIBLE);
                seguimiento.setFechaVisible(ahora);
            } else {
                seguimiento.setEstado(SeguimientoOrdenArea.ESTADO_PENDIENTE);
            }

            seguimientoRepo.save(seguimiento);
        }

        log.info("Seguimiento inicializado para orden {} con {} nodos", orden.getOrdenId(), posicion);
    }

    /**
     * Reporta como completado el trabajo de un área para una orden.
     * Propaga la visibilidad a los nodos sucesores si todos sus predecesores están completados.
     */
    public SeguimientoOrdenAreaDTO reportarCompletado(int ordenId, int areaId, Long userId, String observaciones) {
        // Buscar el seguimiento visible para esta orden y área
        SeguimientoOrdenArea seguimiento = seguimientoRepo
                .findByOrdenProduccion_OrdenIdAndAreaOperativa_AreaIdAndEstado(
                        ordenId, areaId, SeguimientoOrdenArea.ESTADO_VISIBLE)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No se encontró seguimiento visible para orden " + ordenId + " y área " + areaId));

        completarSeguimiento(seguimiento, userId, observaciones);

        return toDTO(seguimiento);
    }

    /**
     * Marca automÃ¡ticamente como completado el nodo visible de Almacen General cuando
     * una dispensaciÃ³n normal de materiales se registra exitosamente.
     */
    public SeguimientoOrdenAreaDTO autoCompletarAlmacenGeneralPorDispensacion(int ordenId, Long userId, String observaciones) {
        Optional<SeguimientoOrdenArea> seguimientoOpt = seguimientoRepo
                .findByOrdenProduccion_OrdenIdAndAreaOperativa_AreaIdAndEstado(
                        ordenId,
                        ALMACEN_GENERAL_AREA_ID,
                        SeguimientoOrdenArea.ESTADO_VISIBLE
                );

        if (seguimientoOpt.isEmpty()) {
            log.info("No hay seguimiento visible de Almacen General para orden {}. Se omite auto-completado.", ordenId);
            return null;
        }

        SeguimientoOrdenArea seguimiento = seguimientoOpt.get();
        completarSeguimiento(seguimiento, userId, observaciones);
        return toDTO(seguimiento);
    }

    @Transactional(readOnly = true)
    public boolean tieneAreaOperativaEnSeguimiento(int ordenId, int areaId) {
        return seguimientoRepo.findByOrdenProduccion_OrdenIdOrderByPosicionSecuenciaAsc(ordenId)
                .stream()
                .anyMatch(seguimiento -> seguimiento.getAreaOperativa() != null
                        && seguimiento.getAreaOperativa().getAreaId() == areaId);
    }

    private void completarSeguimiento(SeguimientoOrdenArea seguimiento, Long userId, String observaciones) {
        seguimiento.setEstado(SeguimientoOrdenArea.ESTADO_COMPLETADO);
        seguimiento.setFechaCompletado(LocalDateTime.now());
        seguimiento.setObservaciones(observaciones);

        if (userId != null) {
            User usuario = userRepository.findById(userId).orElse(null);
            seguimiento.setUsuarioReporta(usuario);
        }

        seguimientoRepo.save(seguimiento);
        propagarVisibilidad(seguimiento.getOrdenProduccion().getOrdenId(), seguimiento.getRutaProcesoNode().getId());
    }

    /**
     * Propaga la visibilidad a los nodos sucesores.
     * Un sucesor se hace visible solo si todos sus predecesores están completados.
     */
    private void propagarVisibilidad(int ordenId, Long sourceNodeId) {
        List<SeguimientoOrdenArea> sucesoresPendientes = seguimientoRepo.findSucesoresPendientes(ordenId, sourceNodeId);

        LocalDateTime ahora = LocalDateTime.now();
        for (SeguimientoOrdenArea sucesor : sucesoresPendientes) {
            // Verificar si todos los predecesores del sucesor están completados
            long predecesoresNoCompletados = seguimientoRepo.countPredecesoresNoCompletados(
                    ordenId, sucesor.getRutaProcesoNode().getId());

            if (predecesoresNoCompletados == 0) {
                sucesor.setEstado(SeguimientoOrdenArea.ESTADO_VISIBLE);
                sucesor.setFechaVisible(ahora);
                seguimientoRepo.save(sucesor);
                log.info("Nodo {} ahora visible para orden {}", sucesor.getRutaProcesoNode().getId(), ordenId);
            }
        }
    }

    /**
     * Obtiene las órdenes pendientes (visibles) para un área específica
     */
    @Transactional(readOnly = true)
    public Page<SeguimientoOrdenAreaDTO> getOrdenesPendientesPorArea(int areaId, Pageable pageable) {
        return seguimientoRepo.findOrdenesVisiblesByAreaId(areaId, pageable)
                .map(this::toDTO);
    }

    /**
     * Obtiene las órdenes pendientes (visibles) para las áreas donde el usuario es responsable
     */
    @Transactional(readOnly = true)
    public Page<SeguimientoOrdenAreaDTO> getOrdenesPendientesPorUsuario(Long userId, Pageable pageable) {
        return seguimientoRepo.findOrdenesVisiblesByResponsableUserId(userId, pageable)
                .map(this::toDTO);
    }

    /**
     * Obtiene el progreso completo de una orden (todos los nodos con su estado)
     */
    @Transactional(readOnly = true)
    public List<SeguimientoOrdenAreaDTO> getProgresoOrden(int ordenId) {
        return seguimientoRepo.findByOrdenProduccion_OrdenIdOrderByPosicionSecuenciaAsc(ordenId)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * Verifica si una orden ya tiene seguimiento inicializado
     */
    @Transactional(readOnly = true)
    public boolean tieneSegumiento(int ordenId) {
        return seguimientoRepo.existsByOrdenProduccion_OrdenId(ordenId);
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
        return switch (estado) {
            case SeguimientoOrdenArea.ESTADO_PENDIENTE -> "Pendiente";
            case SeguimientoOrdenArea.ESTADO_VISIBLE -> "Visible";
            case SeguimientoOrdenArea.ESTADO_COMPLETADO -> "Completado";
            case SeguimientoOrdenArea.ESTADO_OMITIDO -> "Omitido";
            default -> "Desconocido";
        };
    }

    // DTO interno
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
