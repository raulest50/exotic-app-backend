package exotic.app.planta.service.produccion;

import exotic.app.planta.config.initializers.AreaOperativaInitializer;
import exotic.app.planta.model.organizacion.AreaOperativa;
import exotic.app.planta.model.producto.Categoria;
import exotic.app.planta.model.produccion.ruprocatdesigner.RutaProcesoCat;
import exotic.app.planta.model.produccion.ruprocatdesigner.RutaProcesoEdge;
import exotic.app.planta.model.produccion.ruprocatdesigner.RutaProcesoNode;
import exotic.app.planta.repo.producto.CategoriaRepo;
import exotic.app.planta.repo.producto.procesos.AreaProduccionRepo;
import exotic.app.planta.repo.produccion.OrdenProduccionRepo;
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
    private final OrdenProduccionRepo ordenProduccionRepo;

    public RutaProcesoCatDTO getRutaByCategoria(int categoriaId) {
        return rutaProcesoCatRepo.findByCategoria_CategoriaId(categoriaId)
                .map(this::toDTO)
                .orElse(null);
    }

    public RutaProcesoCatDTO saveRuta(int categoriaId, RutaProcesoCatDTO dto) {
        Categoria categoria = categoriaRepo.findById(categoriaId)
                .orElseThrow(() -> new IllegalArgumentException("Categoria no encontrada con ID: " + categoriaId));

        if (ordenProduccionRepo.countActiveByCategoriaId(categoriaId) > 0) {
            throw new IllegalStateException("No se puede editar la ruta porque la categoría tiene órdenes de producción activas.");
        }

        validateRuta(dto);

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
        if (ordenProduccionRepo.countActiveByCategoriaId(categoriaId) > 0) {
            throw new IllegalStateException("No se puede eliminar la ruta porque la categoría tiene órdenes de producción activas.");
        }
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

    private void validateRuta(RutaProcesoCatDTO dto) {
        if (dto == null) {
            throw new IllegalArgumentException("La ruta de proceso no puede estar vacía.");
        }

        List<RutaProcesoNodeDTO> nodes = Optional.ofNullable(dto.getNodes()).orElse(Collections.emptyList());
        List<RutaProcesoEdgeDTO> edges = Optional.ofNullable(dto.getEdges()).orElse(Collections.emptyList());

        if (nodes.isEmpty()) {
            throw new IllegalArgumentException("La ruta debe contener al menos un nodo.");
        }

        Map<String, RutaProcesoNodeDTO> nodesById = new LinkedHashMap<>();
        Map<String, Integer> indegree = new LinkedHashMap<>();
        Map<String, Set<String>> adjacency = new LinkedHashMap<>();
        Set<Integer> usedAreaIds = new HashSet<>();
        int almacenCount = 0;

        for (RutaProcesoNodeDTO node : nodes) {
            String nodeId = normalizeId(node.getId());
            if (nodeId == null) {
                throw new IllegalArgumentException("Todos los nodos deben tener un identificador válido.");
            }
            if (nodesById.containsKey(nodeId)) {
                throw new IllegalArgumentException("La ruta contiene nodos duplicados.");
            }
            if (node.getAreaOperativaId() == null) {
                throw new IllegalArgumentException("Todos los nodos deben tener un área operativa asignada.");
            }
            if (!usedAreaIds.add(node.getAreaOperativaId())) {
                throw new IllegalArgumentException(
                        "No se permite repetir la misma área operativa en la ruta de proceso.");
            }
            if (node.getAreaOperativaId() == AreaOperativaInitializer.ALMACEN_GENERAL_ID) {
                almacenCount++;
            }

            nodesById.put(nodeId, node);
            indegree.put(nodeId, 0);
            adjacency.put(nodeId, new LinkedHashSet<>());
        }

        if (almacenCount != 1) {
            throw new IllegalArgumentException("La ruta debe incluir exactamente un nodo de Almacen General.");
        }

        for (RutaProcesoEdgeDTO edge : edges) {
            String sourceId = normalizeId(edge.getSourceNodeId());
            String targetId = normalizeId(edge.getTargetNodeId());

            if (sourceId == null || targetId == null) {
                throw new IllegalArgumentException("Todas las conexiones deben tener origen y destino válidos.");
            }
            if (!nodesById.containsKey(sourceId) || !nodesById.containsKey(targetId)) {
                throw new IllegalArgumentException("La ruta contiene conexiones hacia nodos inexistentes.");
            }
            if (sourceId.equals(targetId)) {
                throw new IllegalArgumentException("No se permiten ciclos directos de un nodo hacia sí mismo.");
            }
            if (!adjacency.get(sourceId).add(targetId)) {
                throw new IllegalArgumentException("No se permiten conexiones duplicadas entre la misma pareja de nodos.");
            }

            indegree.put(targetId, indegree.get(targetId) + 1);
        }

        List<String> rootIds = indegree.entrySet().stream()
                .filter(entry -> entry.getValue() == 0)
                .map(Map.Entry::getKey)
                .toList();

        if (rootIds.size() != 1) {
            throw new IllegalArgumentException("La ruta debe tener exactamente un nodo raíz.");
        }

        String rootId = rootIds.get(0);
        RutaProcesoNodeDTO rootNode = nodesById.get(rootId);
        if (!Objects.equals(rootNode.getAreaOperativaId(), AreaOperativaInitializer.ALMACEN_GENERAL_ID)) {
            throw new IllegalArgumentException("El nodo raíz debe ser Almacen General.");
        }

        for (Map.Entry<String, RutaProcesoNodeDTO> entry : nodesById.entrySet()) {
            if (Objects.equals(entry.getValue().getAreaOperativaId(), AreaOperativaInitializer.ALMACEN_GENERAL_ID)
                    && indegree.get(entry.getKey()) > 0) {
                throw new IllegalArgumentException("Almacen General no puede tener predecesores.");
            }
        }

        Set<String> reachable = new HashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        queue.add(rootId);
        reachable.add(rootId);

        while (!queue.isEmpty()) {
            String currentId = queue.removeFirst();
            for (String targetId : adjacency.get(currentId)) {
                if (reachable.add(targetId)) {
                    queue.addLast(targetId);
                }
            }
        }

        if (reachable.size() != nodesById.size()) {
            throw new IllegalArgumentException(
                    "La ruta no puede contener nodos huérfanos o desconectados del flujo principal.");
        }

        Map<String, Integer> remainingIndegree = new LinkedHashMap<>(indegree);
        Deque<String> zeroIndegreeQueue = remainingIndegree.entrySet().stream()
                .filter(entry -> entry.getValue() == 0)
                .map(Map.Entry::getKey)
                .collect(Collectors.toCollection(ArrayDeque::new));

        int processedNodes = 0;
        while (!zeroIndegreeQueue.isEmpty()) {
            String currentId = zeroIndegreeQueue.removeFirst();
            processedNodes++;

            for (String targetId : adjacency.get(currentId)) {
                int nextIndegree = remainingIndegree.get(targetId) - 1;
                remainingIndegree.put(targetId, nextIndegree);
                if (nextIndegree == 0) {
                    zeroIndegreeQueue.addLast(targetId);
                }
            }
        }

        if (processedNodes != nodesById.size()) {
            throw new IllegalArgumentException("La ruta debe ser un grafo acíclico dirigido (DAG).");
        }
    }

    private String normalizeId(String rawId) {
        if (rawId == null) {
            return null;
        }
        String normalized = rawId.trim();
        return normalized.isEmpty() ? null : normalized;
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
