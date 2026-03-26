package exotic.app.planta.service.produccion;

import exotic.app.planta.model.organizacion.AreaOperativa;
import exotic.app.planta.model.producto.Categoria;
import exotic.app.planta.model.produccion.ruprocatdesigner.RutaProcesoCat;
import exotic.app.planta.model.produccion.ruprocatdesigner.RutaProcesoEdge;
import exotic.app.planta.model.produccion.ruprocatdesigner.RutaProcesoNode;
import exotic.app.planta.repo.producto.CategoriaRepo;
import exotic.app.planta.repo.producto.procesos.AreaProduccionRepo;
import exotic.app.planta.repo.produccion.ruprocatdesigner.RutaProcesoCatRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@Transactional(rollbackFor = Exception.class)
@RequiredArgsConstructor
public class RutaProcesoCatService {

    private final RutaProcesoCatRepo rutaProcesoCatRepo;
    private final CategoriaRepo categoriaRepo;
    private final AreaProduccionRepo areaProduccionRepo;

    public RutaProcesoCatDTO getRutaByCategoria(int categoriaId) {
        return rutaProcesoCatRepo.findByCategoria_CategoriaId(categoriaId)
                .map(this::toDTO)
                .orElse(null);
    }

    public RutaProcesoCatDTO saveRuta(int categoriaId, RutaProcesoCatDTO dto) {
        Categoria categoria = categoriaRepo.findById(categoriaId)
                .orElseThrow(() -> new IllegalArgumentException("Categoria no encontrada con ID: " + categoriaId));

        RutaProcesoCat ruta = rutaProcesoCatRepo.findByCategoria_CategoriaId(categoriaId)
                .orElseGet(() -> {
                    RutaProcesoCat newRuta = new RutaProcesoCat();
                    newRuta.setCategoria(categoria);
                    return newRuta;
                });

        // Clear existing nodes and edges
        ruta.getNodes().clear();
        ruta.getEdges().clear();

        // Create a map to track frontendId -> RutaProcesoNode for edge creation
        Map<String, RutaProcesoNode> nodeMap = new HashMap<>();

        // Create nodes
        for (RutaProcesoNodeDTO nodeDto : dto.getNodes()) {
            RutaProcesoNode node = new RutaProcesoNode();
            node.setRutaProcesoCat(ruta);  // Set parent reference
            node.setFrontendId(nodeDto.getId());
            node.setPosicionX(nodeDto.getPosicionX());
            node.setPosicionY(nodeDto.getPosicionY());
            node.setLabel(nodeDto.getLabel());
            node.setHasLeftHandle(nodeDto.isHasLeftHandle());
            node.setHasRightHandle(nodeDto.isHasRightHandle());

            if (nodeDto.getAreaOperativaId() != null) {
                AreaOperativa area = areaProduccionRepo.findById(nodeDto.getAreaOperativaId())
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Area operativa no encontrada con ID: " + nodeDto.getAreaOperativaId()));
                node.setAreaOperativa(area);
            }

            ruta.getNodes().add(node);
            nodeMap.put(nodeDto.getId(), node);
        }

        // Save to get node IDs
        ruta = rutaProcesoCatRepo.save(ruta);

        // Rebuild nodeMap with persisted nodes
        nodeMap.clear();
        for (RutaProcesoNode node : ruta.getNodes()) {
            nodeMap.put(node.getFrontendId(), node);
        }

        // Create edges
        for (RutaProcesoEdgeDTO edgeDto : dto.getEdges()) {
            RutaProcesoNode sourceNode = nodeMap.get(edgeDto.getSourceNodeId());
            RutaProcesoNode targetNode = nodeMap.get(edgeDto.getTargetNodeId());

            if (sourceNode == null || targetNode == null) {
                log.warn("Edge references non-existent node: source={}, target={}",
                        edgeDto.getSourceNodeId(), edgeDto.getTargetNodeId());
                continue;
            }

            RutaProcesoEdge edge = new RutaProcesoEdge();
            edge.setRutaProcesoCat(ruta);  // Set parent reference
            edge.setFrontendId(edgeDto.getId());
            edge.setSourceNode(sourceNode);
            edge.setTargetNode(targetNode);
            ruta.getEdges().add(edge);
        }

        ruta = rutaProcesoCatRepo.save(ruta);
        return toDTO(ruta);
    }

    public void deleteRuta(int categoriaId) {
        rutaProcesoCatRepo.findByCategoria_CategoriaId(categoriaId)
                .ifPresent(rutaProcesoCatRepo::delete);
    }

    public Map<Integer, Boolean> checkRoutesExist(List<Integer> categoriaIds) {
        Map<Integer, Boolean> result = new HashMap<>();
        for (Integer categoriaId : categoriaIds) {
            result.put(categoriaId, rutaProcesoCatRepo.existsByCategoria_CategoriaId(categoriaId));
        }
        return result;
    }

    private RutaProcesoCatDTO toDTO(RutaProcesoCat ruta) {
        RutaProcesoCatDTO dto = new RutaProcesoCatDTO();
        dto.setId(ruta.getId());
        dto.setCategoriaId(ruta.getCategoria().getCategoriaId());

        List<RutaProcesoNodeDTO> nodeDtos = ruta.getNodes().stream()
                .map(this::toNodeDTO)
                .collect(Collectors.toList());
        dto.setNodes(nodeDtos);

        List<RutaProcesoEdgeDTO> edgeDtos = ruta.getEdges().stream()
                .map(this::toEdgeDTO)
                .collect(Collectors.toList());
        dto.setEdges(edgeDtos);

        return dto;
    }

    private RutaProcesoNodeDTO toNodeDTO(RutaProcesoNode node) {
        RutaProcesoNodeDTO dto = new RutaProcesoNodeDTO();
        dto.setId(node.getFrontendId());
        dto.setPosicionX(node.getPosicionX());
        dto.setPosicionY(node.getPosicionY());
        dto.setLabel(node.getLabel());
        dto.setHasLeftHandle(node.isHasLeftHandle());
        dto.setHasRightHandle(node.isHasRightHandle());

        if (node.getAreaOperativa() != null) {
            dto.setAreaOperativaId(node.getAreaOperativa().getAreaId());
            dto.setAreaOperativaNombre(node.getAreaOperativa().getNombre());
        }

        return dto;
    }

    private RutaProcesoEdgeDTO toEdgeDTO(RutaProcesoEdge edge) {
        RutaProcesoEdgeDTO dto = new RutaProcesoEdgeDTO();
        dto.setId(edge.getFrontendId());
        dto.setSourceNodeId(edge.getSourceNode().getFrontendId());
        dto.setTargetNodeId(edge.getTargetNode().getFrontendId());
        return dto;
    }

    // DTOs
    @lombok.Data
    public static class RutaProcesoCatDTO {
        private Long id;
        private int categoriaId;
        private List<RutaProcesoNodeDTO> nodes = new ArrayList<>();
        private List<RutaProcesoEdgeDTO> edges = new ArrayList<>();
    }

    @lombok.Data
    public static class RutaProcesoNodeDTO {
        private String id;
        private double posicionX;
        private double posicionY;
        private Integer areaOperativaId;
        private String areaOperativaNombre;
        private String label;
        private boolean hasLeftHandle = true;
        private boolean hasRightHandle = true;
    }

    @lombok.Data
    public static class RutaProcesoEdgeDTO {
        private String id;
        private String sourceNodeId;
        private String targetNodeId;
    }
}
