package exotic.app.planta.service.produccion;

import exotic.app.planta.model.organizacion.AreaOperativa;
import exotic.app.planta.model.producto.Material;
import exotic.app.planta.model.producto.Terminado;
import exotic.app.planta.model.producto.dto.InsumoWithStockDTO;
import exotic.app.planta.model.producto.manufacturing.packaging.InsumoEmpaque;
import exotic.app.planta.model.produccion.EstadoSeguimientoOrdenArea;
import exotic.app.planta.model.produccion.OrdenProduccion;
import exotic.app.planta.model.produccion.SeguimientoOrdenArea;
import exotic.app.planta.model.produccion.ruprocatdesigner.RutaProcesoCat;
import exotic.app.planta.model.produccion.ruprocatdesigner.RutaProcesoEdge;
import exotic.app.planta.model.produccion.ruprocatdesigner.RutaProcesoNode;
import exotic.app.planta.repo.producto.procesos.AreaProduccionRepo;
import exotic.app.planta.repo.produccion.SeguimientoOrdenAreaRepo;
import exotic.app.planta.repo.produccion.ruprocatdesigner.RutaProcesoCatRepo;
import exotic.app.planta.service.productos.ProductoService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AreaOperativaPanelDetalleService {

    private final SeguimientoOrdenAreaRepo seguimientoOrdenAreaRepo;
    private final AreaProduccionRepo areaProduccionRepo;
    private final RutaProcesoCatRepo rutaProcesoCatRepo;
    private final ProductoService productoService;

    public AreaOperativaOrdenDetalleDTO getDetalleOperativoOrden(int ordenId, Long userId) {
        if (userId == null) {
            throw new AccessDeniedException("Se requiere un usuario autenticado.");
        }

        List<AreaOperativa> areasResponsables = areaProduccionRepo.findAllByResponsableArea_Id(userId);
        if (areasResponsables.isEmpty()) {
            throw new AccessDeniedException("El usuario no es responsable de un área operativa.");
        }

        List<SeguimientoOrdenArea> seguimientos = seguimientoOrdenAreaRepo.findDetalleByOrdenId(ordenId);
        if (seguimientos.isEmpty()) {
            throw new NoSuchElementException("No se encontró detalle operativo para la orden " + ordenId + ".");
        }

        Set<Integer> areaIdsResponsables = areasResponsables.stream()
                .map(AreaOperativa::getAreaId)
                .collect(Collectors.toSet());

        boolean canAccess = seguimientos.stream()
                .map(SeguimientoOrdenArea::getAreaOperativa)
                .filter(area -> area != null)
                .map(AreaOperativa::getAreaId)
                .anyMatch(areaIdsResponsables::contains);

        if (!canAccess) {
            throw new AccessDeniedException("La orden no pertenece al seguimiento del área operativa responsable del usuario.");
        }

        OrdenProduccion orden = seguimientos.get(0).getOrdenProduccion();
        if (!(orden.getProducto() instanceof Terminado terminado)) {
            throw new NoSuchElementException("La orden no está asociada a un producto terminado.");
        }

        AreaOperativaOrdenDetalleDTO dto = new AreaOperativaOrdenDetalleDTO();
        dto.setOrden(buildOrdenDTO(orden, terminado));
        dto.setSeguimiento(seguimientos.stream()
                .sorted(Comparator.comparing(SeguimientoOrdenArea::getPosicionSecuencia, Comparator.nullsLast(Integer::compareTo)))
                .map(this::buildSeguimientoDTO)
                .toList());
        dto.setRutaProceso(buildRutaProcesoDTO(terminado, seguimientos, areaIdsResponsables));
        dto.setBom(buildBomDTO(terminado, orden.getCantidadProducir()));
        return dto;
    }

    private OrdenOperativaResumenDTO buildOrdenDTO(OrdenProduccion orden, Terminado terminado) {
        OrdenOperativaResumenDTO dto = new OrdenOperativaResumenDTO();
        dto.setOrdenId(orden.getOrdenId());
        dto.setLoteAsignado(orden.getLoteAsignado());
        dto.setProductoId(terminado.getProductoId());
        dto.setProductoNombre(terminado.getNombre());
        dto.setCantidadProducir(orden.getCantidadProducir());
        dto.setEstadoOrden(orden.getEstadoOrden());
        dto.setOrdenObservaciones(orden.getObservaciones());
        dto.setFechaCreacion(orden.getFechaCreacion());
        dto.setFechaInicio(orden.getFechaInicio());
        dto.setFechaFinal(orden.getFechaFinal());
        dto.setFechaFinalPlanificada(orden.getFechaFinalPlanificada());
        if (terminado.getCategoria() != null) {
            dto.setCategoriaId(terminado.getCategoria().getCategoriaId());
            dto.setCategoriaNombre(terminado.getCategoria().getCategoriaNombre());
        }
        return dto;
    }

    private SeguimientoOperativoItemDTO buildSeguimientoDTO(SeguimientoOrdenArea seguimiento) {
        SeguimientoOperativoItemDTO dto = new SeguimientoOperativoItemDTO();
        dto.setSeguimientoId(seguimiento.getId());
        dto.setNodeId(seguimiento.getRutaProcesoNode().getId());
        dto.setNodeLabel(seguimiento.getRutaProcesoNode().getLabel());
        dto.setAreaId(seguimiento.getAreaOperativa().getAreaId());
        dto.setAreaNombre(seguimiento.getAreaOperativa().getNombre());
        dto.setEstado(seguimiento.getEstado());
        dto.setEstadoDescripcion(EstadoSeguimientoOrdenArea.fromCode(seguimiento.getEstado()).getDescripcion());
        dto.setFechaVisible(seguimiento.getFechaVisible());
        dto.setFechaEstadoActual(seguimiento.getFechaEstadoActual());
        dto.setFechaCompletado(seguimiento.getFechaCompletado());
        dto.setUsuarioReportaNombre(seguimiento.getUsuarioReporta() != null ? seguimiento.getUsuarioReporta().getNombreCompleto() : null);
        dto.setObservaciones(seguimiento.getObservaciones());
        return dto;
    }

    private RutaProcesoVisualDTO buildRutaProcesoDTO(
            Terminado terminado,
            List<SeguimientoOrdenArea> seguimientos,
            Set<Integer> areaIdsResponsables
    ) {
        RutaProcesoVisualDTO dto = new RutaProcesoVisualDTO();

        if (terminado.getCategoria() == null) {
            return dto;
        }

        Optional<RutaProcesoCat> rutaOpt = rutaProcesoCatRepo.findByCategoria_CategoriaId(terminado.getCategoria().getCategoriaId());
        if (rutaOpt.isEmpty()) {
            return dto;
        }

        RutaProcesoCat ruta = rutaOpt.get();
        Map<Long, SeguimientoOrdenArea> seguimientoPorNodeId = seguimientos.stream()
                .collect(Collectors.toMap(
                        seguimiento -> seguimiento.getRutaProcesoNode().getId(),
                        seguimiento -> seguimiento,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));

        dto.setNodes(ruta.getNodes().stream()
                .sorted(Comparator.comparing(RutaProcesoNode::getId))
                .map(node -> buildRutaNodeDTO(node, seguimientoPorNodeId.get(node.getId()), areaIdsResponsables))
                .toList());

        dto.setEdges(ruta.getEdges().stream()
                .sorted(Comparator.comparing(RutaProcesoEdge::getId))
                .map(this::buildRutaEdgeDTO)
                .toList());

        return dto;
    }

    private RutaProcesoVisualNodeDTO buildRutaNodeDTO(
            RutaProcesoNode node,
            SeguimientoOrdenArea seguimiento,
            Set<Integer> areaIdsResponsables
    ) {
        RutaProcesoVisualNodeDTO dto = new RutaProcesoVisualNodeDTO();
        dto.setNodeId(node.getId());
        dto.setFrontendId(node.getFrontendId());
        dto.setLabel(node.getLabel());
        dto.setAreaId(node.getAreaOperativa() != null ? node.getAreaOperativa().getAreaId() : null);
        dto.setAreaNombre(node.getAreaOperativa() != null ? node.getAreaOperativa().getNombre() : null);
        dto.setPosicionX(node.getPosicionX());
        dto.setPosicionY(node.getPosicionY());
        dto.setHasLeftHandle(node.isHasLeftHandle());
        dto.setHasRightHandle(node.isHasRightHandle());
        dto.setCurrentLeaderArea(node.getAreaOperativa() != null && areaIdsResponsables.contains(node.getAreaOperativa().getAreaId()));

        if (seguimiento != null) {
            dto.setSeguimientoId(seguimiento.getId());
            dto.setEstadoActual(seguimiento.getEstado());
            dto.setEstadoDescripcion(EstadoSeguimientoOrdenArea.fromCode(seguimiento.getEstado()).getDescripcion());
            dto.setFechaEstadoActual(seguimiento.getFechaEstadoActual());
        }

        return dto;
    }

    private RutaProcesoVisualEdgeDTO buildRutaEdgeDTO(RutaProcesoEdge edge) {
        RutaProcesoVisualEdgeDTO dto = new RutaProcesoVisualEdgeDTO();
        dto.setEdgeId(edge.getId());
        dto.setFrontendId(edge.getFrontendId());
        dto.setSourceNodeId(edge.getSourceNode().getId());
        dto.setTargetNodeId(edge.getTargetNode().getId());
        dto.setSourceFrontendId(edge.getSourceNode().getFrontendId());
        dto.setTargetFrontendId(edge.getTargetNode().getFrontendId());
        return dto;
    }

    private BomJerarquicoDTO buildBomDTO(Terminado terminado, double cantidadOrden) {
        BomJerarquicoDTO dto = new BomJerarquicoDTO();

        List<InsumoWithStockDTO> insumos = terminado.getInsumos() != null
                ? productoService.getInsumosWithStock(terminado.getProductoId())
                : List.of();
        dto.setReceta(insumos.stream()
                .map(insumo -> buildBomRecetaNode(insumo, cantidadOrden, 1.0))
                .toList());

        List<BomEmpaqueItemDTO> empaque = new ArrayList<>();
        if (terminado.getCasePack() != null && terminado.getCasePack().getInsumosEmpaque() != null) {
            for (InsumoEmpaque insumoEmpaque : terminado.getCasePack().getInsumosEmpaque()) {
                Material material = insumoEmpaque.getMaterial();
                if (material == null) {
                    continue;
                }

                BomEmpaqueItemDTO item = new BomEmpaqueItemDTO();
                item.setProductoId(material.getProductoId());
                item.setProductoNombre(material.getNombre());
                item.setCantidadTotalRequerida(insumoEmpaque.getCantidad() * cantidadOrden);
                item.setTipoUnidades(insumoEmpaque.getUom() != null && !insumoEmpaque.getUom().isBlank()
                        ? insumoEmpaque.getUom()
                        : material.getTipoUnidades());
                item.setInventareable(material.isInventareable());
                empaque.add(item);
            }
        }

        dto.setEmpaque(empaque);
        return dto;
    }

    private BomRecetaNodeDTO buildBomRecetaNode(InsumoWithStockDTO insumo, double cantidadOrden, double multiplicadorActual) {
        double cantidadTotal = insumo.getCantidadRequerida() * cantidadOrden * multiplicadorActual;

        BomRecetaNodeDTO dto = new BomRecetaNodeDTO();
        dto.setInsumoId(insumo.getInsumoId());
        dto.setProductoId(insumo.getProductoId());
        dto.setProductoNombre(insumo.getProductoNombre());
        dto.setCantidadTotalRequerida(cantidadTotal);
        dto.setTipoUnidades(insumo.getTipoUnidades());
        dto.setTipoProducto(resolveTipoProducto(insumo.getTipoProducto()));
        dto.setInventareable(insumo.getInventareable() == null || insumo.getInventareable());

        if (insumo.getSubInsumos() != null && !insumo.getSubInsumos().isEmpty()) {
            double nuevoMultiplicador = multiplicadorActual * insumo.getCantidadRequerida();
            dto.setSubInsumos(insumo.getSubInsumos().stream()
                    .map(subInsumo -> buildBomRecetaNode(subInsumo, cantidadOrden, nuevoMultiplicador))
                    .toList());
        }

        return dto;
    }

    private String resolveTipoProducto(InsumoWithStockDTO.TipoProducto tipoProducto) {
        if (tipoProducto == null) {
            return "DESCONOCIDO";
        }

        return switch (tipoProducto) {
            case M -> "MATERIAL";
            case S -> "SEMITERMINADO";
            case T -> "TERMINADO";
        };
    }

    @Data
    public static class AreaOperativaOrdenDetalleDTO {
        private OrdenOperativaResumenDTO orden;
        private List<SeguimientoOperativoItemDTO> seguimiento = new ArrayList<>();
        private RutaProcesoVisualDTO rutaProceso = new RutaProcesoVisualDTO();
        private BomJerarquicoDTO bom = new BomJerarquicoDTO();
    }

    @Data
    public static class OrdenOperativaResumenDTO {
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
        private Integer categoriaId;
        private String categoriaNombre;
    }

    @Data
    public static class SeguimientoOperativoItemDTO {
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
        private String usuarioReportaNombre;
        private String observaciones;
    }

    @Data
    public static class RutaProcesoVisualDTO {
        private List<RutaProcesoVisualNodeDTO> nodes = new ArrayList<>();
        private List<RutaProcesoVisualEdgeDTO> edges = new ArrayList<>();
    }

    @Data
    public static class RutaProcesoVisualNodeDTO {
        private Long nodeId;
        private String frontendId;
        private String label;
        private Integer areaId;
        private String areaNombre;
        private double posicionX;
        private double posicionY;
        private boolean hasLeftHandle;
        private boolean hasRightHandle;
        private Long seguimientoId;
        private Integer estadoActual;
        private String estadoDescripcion;
        private LocalDateTime fechaEstadoActual;
        private boolean currentLeaderArea;
    }

    @Data
    public static class RutaProcesoVisualEdgeDTO {
        private Long edgeId;
        private String frontendId;
        private Long sourceNodeId;
        private Long targetNodeId;
        private String sourceFrontendId;
        private String targetFrontendId;
    }

    @Data
    public static class BomJerarquicoDTO {
        private List<BomRecetaNodeDTO> receta = new ArrayList<>();
        private List<BomEmpaqueItemDTO> empaque = new ArrayList<>();
    }

    @Data
    public static class BomRecetaNodeDTO {
        private Integer insumoId;
        private String productoId;
        private String productoNombre;
        private double cantidadTotalRequerida;
        private String tipoUnidades;
        private String tipoProducto;
        private boolean inventareable;
        private List<BomRecetaNodeDTO> subInsumos = new ArrayList<>();
    }

    @Data
    public static class BomEmpaqueItemDTO {
        private String productoId;
        private String productoNombre;
        private double cantidadTotalRequerida;
        private String tipoUnidades;
        private boolean inventareable;
    }
}
