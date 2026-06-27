package exotic.app.planta.service.productos.templates;

import exotic.app.planta.model.organizacion.AreaOperativa;
import exotic.app.planta.model.producto.Categoria;
import exotic.app.planta.model.producto.Material;
import exotic.app.planta.model.producto.Producto;
import exotic.app.planta.model.producto.dto.manufacturing.*;
import exotic.app.planta.model.producto.dto.manufacturing.templates.CategoriaManufacturingTemplateDTO;
import exotic.app.planta.model.producto.manufacturing.procesos.ProcesoProduccion;
import exotic.app.planta.model.producto.manufacturing.templates.*;
import exotic.app.planta.repo.producto.CategoriaRepo;
import exotic.app.planta.repo.producto.MaterialRepo;
import exotic.app.planta.repo.producto.ProductoRepo;
import exotic.app.planta.repo.producto.manufacturing.templates.CategoriaManufacturingTemplateRepo;
import exotic.app.planta.repo.producto.procesos.AreaProduccionRepo;
import exotic.app.planta.repo.producto.procesos.ProcesoProduccionRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CategoriaManufacturingTemplateService {

    private static final String NODE_INSUMO = "INSUMO";
    private static final String NODE_PROCESO = "PROCESO";
    private static final String NODE_TARGET = "TARGET";

    private final CategoriaManufacturingTemplateRepo templateRepo;
    private final CategoriaRepo categoriaRepo;
    private final ProductoRepo productoRepo;
    private final MaterialRepo materialRepo;
    private final ProcesoProduccionRepo procesoProduccionRepo;
    private final AreaProduccionRepo areaProduccionRepo;

    @Transactional(readOnly = true)
    public CategoriaManufacturingTemplateDTO getTemplateByCategoria(int categoriaId) {
        return templateRepo.findByCategoria_CategoriaId(categoriaId)
                .map(this::toDTO)
                .orElse(null);
    }

    @Transactional
    public CategoriaManufacturingTemplateDTO saveTemplate(int categoriaId, CategoriaManufacturingTemplateDTO dto) {
        if (dto == null) {
            throw new IllegalArgumentException("La plantilla de manufactura no puede ser nula");
        }
        if (dto.getCategoriaId() != null && !dto.getCategoriaId().equals(categoriaId)) {
            throw new IllegalArgumentException("La categoria del path no coincide con la del cuerpo");
        }

        Categoria categoria = categoriaRepo.findById(categoriaId)
                .orElseThrow(() -> new IllegalArgumentException("Categoria no encontrada: " + categoriaId));

        validateTemplateDto(dto);

        CategoriaManufacturingTemplate template = templateRepo.findByCategoria_CategoriaId(categoriaId)
                .orElseGet(CategoriaManufacturingTemplate::new);
        template.setCategoria(categoria);
        template.setRendimientoTeorico(defaultDouble(dto.getRendimientoTeorico()));

        assignInsumos(template, dto.getInsumos());
        assignCasePack(template, dto.getCasePack());
        assignProceso(template, dto.getProcesoProduccionCompleto());

        CategoriaManufacturingTemplate saved = templateRepo.save(template);
        log.info(
                "[CATEGORIA_MF_TEMPLATE] saved categoriaId={} templateId={} insumos={} nodes={} edges={}",
                categoriaId,
                saved.getId(),
                saved.getInsumos().size(),
                saved.getNodes().size(),
                saved.getEdges().size()
        );
        return toDTO(saved);
    }

    @Transactional
    public void deleteTemplate(int categoriaId) {
        templateRepo.findByCategoria_CategoriaId(categoriaId).ifPresent(templateRepo::delete);
    }

    @Transactional(readOnly = true)
    public Map<Integer, Boolean> existsBatch(List<Integer> categoriaIds) {
        Map<Integer, Boolean> result = new LinkedHashMap<>();
        for (Integer categoriaId : Optional.ofNullable(categoriaIds).orElseGet(List::of)) {
            if (categoriaId != null) {
                result.put(categoriaId, templateRepo.existsByCategoria_CategoriaId(categoriaId));
            }
        }
        return result;
    }

    private void validateTemplateDto(CategoriaManufacturingTemplateDTO dto) {
        if (dto.getInsumos() == null || dto.getInsumos().isEmpty()) {
            throw new IllegalArgumentException("La plantilla debe tener al menos un insumo");
        }
        if (dto.getProcesoProduccionCompleto() == null) {
            throw new IllegalArgumentException("La plantilla debe tener procesoProduccionCompleto");
        }
        if (defaultDouble(dto.getRendimientoTeorico()) <= 0) {
            throw new IllegalArgumentException("El rendimiento teorico debe ser mayor que cero");
        }
    }

    private void assignInsumos(
            CategoriaManufacturingTemplate template,
            List<ProductoManufacturingInsumoDTO> insumoDtos
    ) {
        template.getInsumos().clear();
        List<ProductoManufacturingInsumoDTO> safeDtos = Optional.ofNullable(insumoDtos).orElseGet(List::of);
        int orden = 0;
        Set<String> seenProductIds = new HashSet<>();
        for (ProductoManufacturingInsumoDTO dto : safeDtos) {
            if (dto.getProductoId() == null || dto.getProductoId().isBlank()) {
                throw new IllegalArgumentException("Cada insumo de plantilla debe indicar productoId");
            }
            if (dto.getCantidadRequerida() == null || dto.getCantidadRequerida() <= 0) {
                throw new IllegalArgumentException("Cada insumo de plantilla debe tener cantidadRequerida mayor que cero");
            }
            String productoId = dto.getProductoId().trim();
            if (!seenProductIds.add(productoId)) {
                throw new IllegalArgumentException("Insumo duplicado en plantilla: " + productoId);
            }
            Producto producto = productoRepo.findById(productoId)
                    .orElseThrow(() -> new IllegalArgumentException("Producto de insumo no encontrado: " + productoId));

            CategoriaTemplateInsumo insumo = new CategoriaTemplateInsumo();
            insumo.setTemplate(template);
            insumo.setProducto(producto);
            insumo.setCantidadRequerida(dto.getCantidadRequerida());
            insumo.setOrden(orden++);
            template.getInsumos().add(insumo);
        }
    }

    private void assignCasePack(
            CategoriaManufacturingTemplate template,
            ProductoManufacturingCasePackDTO casePackDto
    ) {
        if (casePackDto == null) {
            template.setCasePack(null);
            return;
        }
        if (casePackDto.getUnitsPerCase() == null || casePackDto.getUnitsPerCase() < 1) {
            throw new IllegalArgumentException("unitsPerCase debe ser mayor o igual a 1");
        }

        CategoriaTemplateCasePack casePack = template.getCasePack();
        if (casePack == null) {
            casePack = new CategoriaTemplateCasePack();
        }
        casePack.setTemplate(template);
        casePack.setUnitsPerCase(casePackDto.getUnitsPerCase());
        casePack.setEan14(blankToNull(casePackDto.getEan14()));
        casePack.setLargoCm(casePackDto.getLargoCm());
        casePack.setAnchoCm(casePackDto.getAnchoCm());
        casePack.setAltoCm(casePackDto.getAltoCm());
        casePack.setGrossWeightKg(casePackDto.getGrossWeightKg());
        casePack.setDefaultForShipping(casePackDto.getDefaultForShipping());
        casePack.getInsumosEmpaque().clear();

        for (ProductoManufacturingInsumoEmpaqueDTO dto : Optional.ofNullable(casePackDto.getInsumosEmpaque()).orElseGet(List::of)) {
            if (dto.getMaterialId() == null || dto.getMaterialId().isBlank()) {
                throw new IllegalArgumentException("Cada insumo de empaque debe indicar materialId");
            }
            Material material = materialRepo.findById(dto.getMaterialId().trim())
                    .orElseThrow(() -> new IllegalArgumentException("Material de empaque no encontrado: " + dto.getMaterialId()));
            if (material.getTipoMaterial() != 2) {
                throw new IllegalArgumentException("El material de empaque debe tener tipoMaterial=2: " + dto.getMaterialId());
            }
            CategoriaTemplateInsumoEmpaque insumoEmpaque = new CategoriaTemplateInsumoEmpaque();
            insumoEmpaque.setCasePack(casePack);
            insumoEmpaque.setMaterial(material);
            insumoEmpaque.setCantidad(defaultDouble(dto.getCantidad()));
            insumoEmpaque.setUom(blankToNull(dto.getUom()));
            casePack.getInsumosEmpaque().add(insumoEmpaque);
        }

        template.setCasePack(casePack);
    }

    private void assignProceso(
            CategoriaManufacturingTemplate template,
            ProcesoProduccionCompletoDTO procesoDto
    ) {
        Set<String> templateInsumoIds = template.getInsumos().stream()
                .map(insumo -> insumo.getProducto().getProductoId())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        validateProcesoDto(procesoDto, templateInsumoIds);

        template.getNodes().clear();
        template.getEdges().clear();

        Map<String, CategoriaTemplateProcesoNode> nodeMap = new LinkedHashMap<>();
        for (ProcesoFabricacionNodoDTO nodeDto : procesoDto.getNodes()) {
            CategoriaTemplateProcesoNode node = buildNode(template, nodeDto);
            template.getNodes().add(node);
            nodeMap.put(node.getFrontendId(), node);
        }

        for (ProcesoFabricacionEdgeDTO edgeDto : procesoDto.getEdges()) {
            CategoriaTemplateProcesoNode sourceNode = nodeMap.get(edgeDto.getSourceFrontendId());
            CategoriaTemplateProcesoNode targetNode = nodeMap.get(edgeDto.getTargetFrontendId());
            if (sourceNode == null || targetNode == null) {
                throw new IllegalArgumentException("Edge referencia nodos inexistentes: " + edgeDto.getFrontendId());
            }
            validateEdgeEndpoints(sourceNode, targetNode);

            CategoriaTemplateProcesoEdge edge = new CategoriaTemplateProcesoEdge();
            edge.setTemplate(template);
            edge.setFrontendId(edgeDto.getFrontendId());
            edge.setSourceFrontendId(edgeDto.getSourceFrontendId());
            edge.setTargetFrontendId(edgeDto.getTargetFrontendId());
            template.getEdges().add(edge);
        }

        validateGraphConnectivity(nodeMap, template.getEdges());
    }

    private void validateProcesoDto(ProcesoProduccionCompletoDTO procesoDto, Set<String> inputProductoIds) {
        if (procesoDto == null || procesoDto.getNodes() == null || procesoDto.getNodes().isEmpty()) {
            throw new IllegalArgumentException("El diagrama debe contener nodos");
        }
        if (procesoDto.getEdges() == null || procesoDto.getEdges().isEmpty()) {
            throw new IllegalArgumentException("El diagrama debe contener edges");
        }

        long targetCount = procesoDto.getNodes().stream()
                .filter(node -> NODE_TARGET.equalsIgnoreCase(node.getNodeType()))
                .count();
        if (targetCount != 1) {
            throw new IllegalArgumentException("El diagrama debe contener exactamente un nodo TARGET");
        }

        Set<String> frontendIds = new HashSet<>();
        for (ProcesoFabricacionNodoDTO nodeDto : procesoDto.getNodes()) {
            if (nodeDto.getFrontendId() == null || nodeDto.getFrontendId().isBlank()) {
                throw new IllegalArgumentException("Cada nodo debe tener frontendId");
            }
            if (!frontendIds.add(nodeDto.getFrontendId())) {
                throw new IllegalArgumentException("frontendId de nodo duplicado: " + nodeDto.getFrontendId());
            }

            if (nodeDto instanceof NodoInsumoDTO insumoDto) {
                String inputProductoId = blankToNull(insumoDto.getInputProductoId());
                if (inputProductoId == null || !inputProductoIds.contains(inputProductoId)) {
                    throw new IllegalArgumentException("Nodo insumo referencia un insumo que no pertenece a la plantilla: " + inputProductoId);
                }
            }

            if (nodeDto instanceof NodoProcesoDTO procesoNodeDto) {
                if (procesoNodeDto.getProcesoId() == null) {
                    throw new IllegalArgumentException("Cada nodo proceso debe indicar procesoId");
                }
                if (procesoNodeDto.getAreaOperativaId() == null) {
                    throw new IllegalArgumentException("Cada nodo proceso debe indicar areaOperativaId");
                }
            }
        }
    }

    private CategoriaTemplateProcesoNode buildNode(
            CategoriaManufacturingTemplate template,
            ProcesoFabricacionNodoDTO nodeDto
    ) {
        CategoriaTemplateProcesoNode node = new CategoriaTemplateProcesoNode();
        node.setTemplate(template);
        node.setNodeType(nodeDto.getNodeType());
        node.setFrontendId(nodeDto.getFrontendId());
        node.setPosicionX(nodeDto.getPosicionX());
        node.setPosicionY(nodeDto.getPosicionY());
        node.setLabel(nodeDto.getLabel());

        if (nodeDto instanceof NodoInsumoDTO insumoDto) {
            Producto producto = productoRepo.findById(insumoDto.getInputProductoId())
                    .orElseThrow(() -> new IllegalArgumentException("Producto de nodo insumo no encontrado: " + insumoDto.getInputProductoId()));
            node.setNodeType(NODE_INSUMO);
            node.setInputProducto(producto);
            return node;
        }

        if (nodeDto instanceof NodoProcesoDTO procesoDto) {
            ProcesoProduccion proceso = procesoProduccionRepo.findById(procesoDto.getProcesoId())
                    .orElseThrow(() -> new IllegalArgumentException("ProcesoProduccion no encontrado: " + procesoDto.getProcesoId()));
            AreaOperativa area = areaProduccionRepo.findById(procesoDto.getAreaOperativaId())
                    .orElseThrow(() -> new IllegalArgumentException("AreaOperativa no encontrada: " + procesoDto.getAreaOperativaId()));
            node.setNodeType(NODE_PROCESO);
            node.setProcesoProduccion(proceso);
            node.setAreaOperativa(area);
            return node;
        }

        if (nodeDto instanceof NodoTargetDTO) {
            node.setNodeType(NODE_TARGET);
            return node;
        }

        throw new IllegalArgumentException("Tipo de nodo no soportado: " + nodeDto.getNodeType());
    }

    private void validateEdgeEndpoints(CategoriaTemplateProcesoNode sourceNode, CategoriaTemplateProcesoNode targetNode) {
        if (NODE_TARGET.equalsIgnoreCase(sourceNode.getNodeType())) {
            throw new IllegalArgumentException("Un nodo TARGET no puede ser source de una conexion");
        }
        if (NODE_INSUMO.equalsIgnoreCase(targetNode.getNodeType())) {
            throw new IllegalArgumentException("Un nodo INSUMO no puede ser target de una conexion");
        }
    }

    private void validateGraphConnectivity(
            Map<String, CategoriaTemplateProcesoNode> nodeMap,
            List<CategoriaTemplateProcesoEdge> edges
    ) {
        Map<String, Long> outgoingByNode = edges.stream()
                .collect(Collectors.groupingBy(CategoriaTemplateProcesoEdge::getSourceFrontendId, Collectors.counting()));
        Map<String, Long> incomingByNode = edges.stream()
                .collect(Collectors.groupingBy(CategoriaTemplateProcesoEdge::getTargetFrontendId, Collectors.counting()));

        for (CategoriaTemplateProcesoNode node : nodeMap.values()) {
            long incoming = incomingByNode.getOrDefault(node.getFrontendId(), 0L);
            long outgoing = outgoingByNode.getOrDefault(node.getFrontendId(), 0L);
            String nodeType = node.getNodeType();

            if (NODE_INSUMO.equalsIgnoreCase(nodeType) && outgoing < 1) {
                throw new IllegalArgumentException("Cada nodo INSUMO debe tener al menos una salida");
            }
            if (NODE_PROCESO.equalsIgnoreCase(nodeType)) {
                if (incoming < 1) {
                    throw new IllegalArgumentException("Cada nodo PROCESO debe tener al menos una entrada");
                }
                if (outgoing != 1) {
                    throw new IllegalArgumentException("Cada nodo PROCESO debe tener exactamente una salida");
                }
            }
            if (NODE_TARGET.equalsIgnoreCase(nodeType) && incoming < 1) {
                throw new IllegalArgumentException("El nodo TARGET debe tener al menos una entrada");
            }
        }
    }

    private CategoriaManufacturingTemplateDTO toDTO(CategoriaManufacturingTemplate template) {
        CategoriaManufacturingTemplateDTO dto = new CategoriaManufacturingTemplateDTO();
        dto.setId(template.getId());
        dto.setCategoriaId(template.getCategoria().getCategoriaId());
        dto.setCategoriaNombre(template.getCategoria().getCategoriaNombre());
        dto.setRendimientoTeorico(template.getRendimientoTeorico());
        dto.setInsumos(template.getInsumos().stream().map(this::toInsumoDTO).collect(Collectors.toList()));
        dto.setCasePack(toCasePackDTO(template.getCasePack()));
        dto.setProcesoProduccionCompleto(toProcesoDTO(template));
        return dto;
    }

    private ProductoManufacturingInsumoDTO toInsumoDTO(CategoriaTemplateInsumo insumo) {
        Producto producto = insumo.getProducto();
        double subtotal = producto.getCosto() * insumo.getCantidadRequerida();
        return new ProductoManufacturingInsumoDTO(
                null,
                producto.getProductoId(),
                producto.getNombre(),
                producto.getCosto(),
                producto.getTipoUnidades(),
                insumo.getCantidadRequerida(),
                subtotal
        );
    }

    private ProductoManufacturingCasePackDTO toCasePackDTO(CategoriaTemplateCasePack casePack) {
        if (casePack == null) {
            return null;
        }
        List<ProductoManufacturingInsumoEmpaqueDTO> insumosEmpaque = casePack.getInsumosEmpaque().stream()
                .map(insumo -> new ProductoManufacturingInsumoEmpaqueDTO(
                        null,
                        insumo.getMaterial().getProductoId(),
                        insumo.getMaterial().getNombre(),
                        insumo.getCantidad(),
                        insumo.getUom()
                ))
                .collect(Collectors.toList());
        return new ProductoManufacturingCasePackDTO(
                null,
                casePack.getUnitsPerCase(),
                casePack.getEan14(),
                casePack.getLargoCm(),
                casePack.getAnchoCm(),
                casePack.getAltoCm(),
                casePack.getGrossWeightKg(),
                casePack.getDefaultForShipping(),
                insumosEmpaque
        );
    }

    private ProcesoProduccionCompletoDTO toProcesoDTO(CategoriaManufacturingTemplate template) {
        List<ProcesoFabricacionNodoDTO> nodes = template.getNodes().stream()
                .map(this::toNodeDTO)
                .collect(Collectors.toList());
        List<ProcesoFabricacionEdgeDTO> edges = template.getEdges().stream()
                .map(edge -> new ProcesoFabricacionEdgeDTO(
                        edge.getId(),
                        edge.getFrontendId(),
                        edge.getSourceFrontendId(),
                        edge.getTargetFrontendId()
                ))
                .collect(Collectors.toList());
        return new ProcesoProduccionCompletoDTO(null, template.getRendimientoTeorico(), nodes, edges);
    }

    private ProcesoFabricacionNodoDTO toNodeDTO(CategoriaTemplateProcesoNode node) {
        ProcesoFabricacionNodoDTO dto;
        if (NODE_INSUMO.equalsIgnoreCase(node.getNodeType())) {
            NodoInsumoDTO insumoDto = new NodoInsumoDTO();
            insumoDto.setInputProductoId(node.getInputProducto() != null ? node.getInputProducto().getProductoId() : null);
            dto = insumoDto;
        } else if (NODE_PROCESO.equalsIgnoreCase(node.getNodeType())) {
            NodoProcesoDTO procesoDto = new NodoProcesoDTO();
            ProcesoProduccion proceso = node.getProcesoProduccion();
            AreaOperativa area = node.getAreaOperativa();
            procesoDto.setProcesoId(proceso != null ? proceso.getProcesoId() : null);
            procesoDto.setProcesoNombre(proceso != null ? proceso.getNombre() : null);
            procesoDto.setAreaOperativaId(area != null ? area.getAreaId() : null);
            procesoDto.setAreaOperativaNombre(area != null ? area.getNombre() : null);
            if (proceso != null) {
                procesoDto.setSetUpTime(proceso.getSetUpTime());
                procesoDto.setModel(proceso.getModel() != null ? proceso.getModel().name() : null);
                procesoDto.setConstantSeconds(proceso.getConstantSeconds());
                procesoDto.setThroughputUnitsPerSec(proceso.getThroughputUnitsPerSec());
                procesoDto.setSecondsPerUnit(proceso.getSecondsPerUnit());
                procesoDto.setSecondsPerBatch(proceso.getSecondsPerBatch());
                procesoDto.setBatchSize(proceso.getBatchSize());
            }
            dto = procesoDto;
        } else if (NODE_TARGET.equalsIgnoreCase(node.getNodeType())) {
            dto = new NodoTargetDTO();
        } else {
            throw new IllegalStateException("Tipo de nodo de plantilla no soportado: " + node.getNodeType());
        }

        dto.setId(node.getId());
        dto.setFrontendId(node.getFrontendId());
        dto.setPosicionX(node.getPosicionX());
        dto.setPosicionY(node.getPosicionY());
        dto.setLabel(node.getLabel());
        return dto;
    }

    private double defaultDouble(Double value) {
        return value != null ? value : 0.0;
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
