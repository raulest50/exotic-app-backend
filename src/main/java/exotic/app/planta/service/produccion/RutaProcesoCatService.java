package exotic.app.planta.service.produccion;

import exotic.app.planta.config.AppTime;
import exotic.app.planta.config.initializers.AreaOperativaInitializer;
import exotic.app.planta.model.organizacion.AreaOperativa;
import exotic.app.planta.model.producto.Categoria;
import exotic.app.planta.model.produccion.ruprocatdesigner.RutaProcesoCat;
import exotic.app.planta.model.produccion.ruprocatdesigner.RutaProcesoCatVersion;
import exotic.app.planta.model.produccion.ruprocatdesigner.RutaProcesoEdge;
import exotic.app.planta.model.produccion.ruprocatdesigner.RutaProcesoNode;
import exotic.app.planta.repo.producto.CategoriaRepo;
import exotic.app.planta.repo.producto.procesos.AreaProduccionRepo;
import exotic.app.planta.repo.produccion.ruprocatdesigner.RutaProcesoCatRepo;
import exotic.app.planta.repo.produccion.ruprocatdesigner.RutaProcesoCatVersionRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@Transactional(rollbackFor = Exception.class)
@RequiredArgsConstructor
public class RutaProcesoCatService {

    private final RutaProcesoCatRepo rutaProcesoCatRepo;
    private final RutaProcesoCatVersionRepo rutaProcesoCatVersionRepo;
    private final CategoriaRepo categoriaRepo;
    private final AreaProduccionRepo areaProduccionRepo;

    @Transactional(readOnly = true)
    public RutaProcesoCatDTO getRutaByCategoria(int categoriaId) {
        return rutaProcesoCatVersionRepo.findByCategoriaIdAndEstado(categoriaId, RutaProcesoCatVersion.Estado.VIGENTE)
                .map(this::toDTO)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public List<RutaProcesoCatDTO> getVersionesByCategoria(int categoriaId) {
        return rutaProcesoCatVersionRepo.findAllByCategoriaIdOrderByVersionDesc(categoriaId)
                .stream()
                .map(this::toDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public RutaProcesoCatDTO getVersionByCategoria(int categoriaId, Long versionId) {
        return rutaProcesoCatVersionRepo.findByCategoriaIdAndVersionId(categoriaId, versionId)
                .map(this::toDTO)
                .orElse(null);
    }

    public RutaProcesoCatDTO saveRuta(int categoriaId, RutaProcesoCatDTO dto, String username) {
        Categoria categoria = categoriaRepo.findById(categoriaId)
                .orElseThrow(() -> new IllegalArgumentException("Categoria no encontrada con ID: " + categoriaId));

        validateRuta(dto);

        LocalDateTime now = AppTime.now();
        RutaProcesoCat ruta = rutaProcesoCatRepo.findByCategoria_CategoriaId(categoriaId)
                .orElseGet(() -> {
                    RutaProcesoCat newRuta = new RutaProcesoCat();
                    newRuta.setCategoria(categoria);
                    return rutaProcesoCatRepo.save(newRuta);
                });

        rutaProcesoCatVersionRepo.findByCategoriaIdAndEstadoForUpdate(categoriaId, RutaProcesoCatVersion.Estado.VIGENTE)
                .ifPresent(vigente -> {
                    vigente.setEstado(RutaProcesoCatVersion.Estado.RETIRADA);
                    vigente.setVigenteHasta(now);
                    rutaProcesoCatVersionRepo.save(vigente);
                });

        RutaProcesoCatVersion nuevaVersion = new RutaProcesoCatVersion();
        nuevaVersion.setRutaProcesoCat(ruta);
        nuevaVersion.setVersionNumber(rutaProcesoCatVersionRepo.findMaxVersionNumberByCategoriaId(categoriaId) + 1);
        nuevaVersion.setEstado(RutaProcesoCatVersion.Estado.VIGENTE);
        nuevaVersion.setVigenteDesde(now);
        nuevaVersion.setCreadoEn(now);
        nuevaVersion.setCreadoPor(normalizeText(username));
        nuevaVersion.setMotivoCambio(normalizeText(dto.getMotivoCambio()));

        Map<String, RutaProcesoNode> nodeMap = new HashMap<>();
        for (RutaProcesoNodeDTO nodeDto : dto.getNodes()) {
            RutaProcesoNode node = new RutaProcesoNode();
            node.setRutaProcesoCatVersion(nuevaVersion);
            node.setFrontendId(nodeDto.getId());
            node.setPosicionX(nodeDto.getPosicionX());
            node.setPosicionY(nodeDto.getPosicionY());
            node.setLabel(nodeDto.getLabel());
            node.setHasLeftHandle(nodeDto.isHasLeftHandle());
            node.setHasRightHandle(nodeDto.isHasRightHandle());
            node.setDuracionEstimadaMinutos(nodeDto.getDuracionEstimadaMinutos());
            node.setRequiereJornadaLaboral(nodeDto.isRequiereJornadaLaboral());

            if (nodeDto.getAreaOperativaId() != null) {
                AreaOperativa area = areaProduccionRepo.findById(nodeDto.getAreaOperativaId())
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Area operativa no encontrada con ID: " + nodeDto.getAreaOperativaId()));
                node.setAreaOperativa(area);
            }

            nuevaVersion.getNodes().add(node);
            nodeMap.put(nodeDto.getId(), node);
        }

        nuevaVersion = rutaProcesoCatVersionRepo.saveAndFlush(nuevaVersion);

        nodeMap.clear();
        for (RutaProcesoNode node : nuevaVersion.getNodes()) {
            nodeMap.put(node.getFrontendId(), node);
        }

        for (RutaProcesoEdgeDTO edgeDto : dto.getEdges()) {
            RutaProcesoNode sourceNode = nodeMap.get(edgeDto.getSourceNodeId());
            RutaProcesoNode targetNode = nodeMap.get(edgeDto.getTargetNodeId());

            if (sourceNode == null || targetNode == null) {
                log.warn("Edge references non-existent node: source={}, target={}",
                        edgeDto.getSourceNodeId(), edgeDto.getTargetNodeId());
                continue;
            }

            RutaProcesoEdge edge = new RutaProcesoEdge();
            edge.setRutaProcesoCatVersion(nuevaVersion);
            edge.setFrontendId(edgeDto.getId());
            edge.setSourceNode(sourceNode);
            edge.setTargetNode(targetNode);
            nuevaVersion.getEdges().add(edge);
        }

        return toDTO(rutaProcesoCatVersionRepo.save(nuevaVersion));
    }

    public void deleteRuta(int categoriaId) {
        LocalDateTime now = AppTime.now();
        rutaProcesoCatVersionRepo.findByCategoriaIdAndEstadoForUpdate(categoriaId, RutaProcesoCatVersion.Estado.VIGENTE)
                .ifPresent(vigente -> {
                    vigente.setEstado(RutaProcesoCatVersion.Estado.RETIRADA);
                    vigente.setVigenteHasta(now);
                    rutaProcesoCatVersionRepo.save(vigente);
                });
    }

    @Transactional(readOnly = true)
    public Map<Integer, Boolean> checkRoutesExist(List<Integer> categoriaIds) {
        Map<Integer, Boolean> result = new HashMap<>();
        for (Integer categoriaId : categoriaIds) {
            result.put(categoriaId, rutaProcesoCatVersionRepo.existsByRutaProcesoCat_Categoria_CategoriaIdAndEstado(
                    categoriaId,
                    RutaProcesoCatVersion.Estado.VIGENTE
            ));
        }
        return result;
    }

    private RutaProcesoCatDTO toDTO(RutaProcesoCatVersion version) {
        RutaProcesoCatDTO dto = new RutaProcesoCatDTO();
        dto.setId(version.getRutaProcesoCat().getId());
        dto.setCategoriaId(version.getRutaProcesoCat().getCategoria().getCategoriaId());
        dto.setVersionId(version.getId());
        dto.setVersionNumber(version.getVersionNumber());
        dto.setEstado(version.getEstado().name());
        dto.setVigenteDesde(version.getVigenteDesde());
        dto.setVigenteHasta(version.getVigenteHasta());
        dto.setCreadoEn(version.getCreadoEn());
        dto.setCreadoPor(version.getCreadoPor());
        dto.setMotivoCambio(version.getMotivoCambio());

        List<RutaProcesoNodeDTO> nodeDtos = version.getNodes().stream()
                .map(this::toNodeDTO)
                .collect(Collectors.toList());
        dto.setNodes(nodeDtos);

        List<RutaProcesoEdgeDTO> edgeDtos = version.getEdges().stream()
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
        dto.setDuracionEstimadaMinutos(node.getDuracionEstimadaMinutos());
        dto.setRequiereJornadaLaboral(node.isRequiereJornadaLaboral());

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
            if (node.getDuracionEstimadaMinutos() < 0) {
                throw new IllegalArgumentException("La duración estimada de los nodos debe ser mayor o igual a 0 minutos.");
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
        if (nodes.size() < 2) {
            throw new IllegalArgumentException("La ruta debe incluir al menos un area productiva ademas de Almacen General.");
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

        List<String> terminalIds = adjacency.entrySet().stream()
                .filter(entry -> entry.getValue().isEmpty())
                .map(Map.Entry::getKey)
                .toList();
        if (terminalIds.size() != 1) {
            throw new IllegalArgumentException("La ruta debe terminar en exactamente una unica area operativa.");
        }
        if (Objects.equals(
                nodesById.get(terminalIds.get(0)).getAreaOperativaId(),
                AreaOperativaInitializer.ALMACEN_GENERAL_ID)) {
            throw new IllegalArgumentException("Almacen General no puede ser el nodo terminal de la ruta.");
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

    private String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    @lombok.Data
    public static class RutaProcesoCatDTO {
        private Long id;
        private int categoriaId;
        private Long versionId;
        private Integer versionNumber;
        private String estado;
        private LocalDateTime vigenteDesde;
        private LocalDateTime vigenteHasta;
        private LocalDateTime creadoEn;
        private String creadoPor;
        private String motivoCambio;
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
        private int duracionEstimadaMinutos = 0;
        private boolean requiereJornadaLaboral = true;
    }

    @lombok.Data
    public static class RutaProcesoEdgeDTO {
        private String id;
        private String sourceNodeId;
        private String targetNodeId;
    }
}
