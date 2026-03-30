package exotic.app.planta.service.productos;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import exotic.app.planta.model.organizacion.AreaOperativa;
import exotic.app.planta.model.producto.*;
import exotic.app.planta.model.producto.dto.manufacturing.*;
import exotic.app.planta.model.producto.manufacturing.packaging.CasePack;
import exotic.app.planta.model.producto.manufacturing.packaging.InsumoEmpaque;
import exotic.app.planta.model.producto.manufacturing.procesos.ProcesoProduccion;
import exotic.app.planta.model.producto.manufacturing.procesos.ProcesoProduccionCompleto;
import exotic.app.planta.model.producto.manufacturing.procesos.nodo.*;
import exotic.app.planta.model.producto.manufacturing.receta.Insumo;
import exotic.app.planta.model.producto.manufacturing.snapshots.ManufacturingVersions;
import exotic.app.planta.repo.producto.*;
import exotic.app.planta.repo.producto.manufacturing.snapshots.ManufacturingVersionRepo;
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
public class ProductoManufacturingService {

    private final ProductoRepo productoRepo;
    private final TerminadoRepo terminadoRepo;
    private final SemiTerminadoRepo semiTerminadoRepo;
    private final MaterialRepo materialRepo;
    private final CategoriaRepo categoriaRepo;
    private final ProcesoProduccionRepo procesoProduccionRepo;
    private final AreaProduccionRepo areaProduccionRepo;
    private final ManufacturingVersionRepo manufacturingVersionRepo;
    private final ObjectMapper objectMapper;

    @Transactional
    public ProductoManufacturingDTO createProductoManufacturing(ProductoManufacturingDTO dto) {
        if (dto.getProductoId() == null || dto.getProductoId().isBlank()) {
            throw new IllegalArgumentException("El productoId es obligatorio");
        }
        if (productoRepo.existsById(dto.getProductoId())) {
            throw new IllegalArgumentException("Ya existe un producto con ID: " + dto.getProductoId());
        }

        Producto producto = instantiateProducto(dto.getTipoProducto());
        applyCommonProductFields(producto, dto);
        updateTypedFields(producto, dto);
        assignInsumos(producto, dto.getInsumos());
        assignProceso(producto, dto.getProcesoProduccionCompleto());

        Producto saved = productoRepo.save(producto);
        return toDTO(saved);
    }

    @Transactional
    public ProductoManufacturingDTO updateProductoManufacturing(String productoId, ProductoManufacturingDTO dto) {
        if (productoId == null || productoId.isBlank()) {
            throw new IllegalArgumentException("El productoId es obligatorio");
        }
        if (dto.getProductoId() != null && !productoId.equals(dto.getProductoId())) {
            throw new IllegalArgumentException("El productoId del path no coincide con el del cuerpo");
        }

        Producto existing = productoRepo.findById(productoId)
                .orElseThrow(() -> new IllegalArgumentException("Producto no encontrado: " + productoId));

        if (!(existing instanceof Terminado) && !(existing instanceof SemiTerminado)) {
            throw new IllegalArgumentException("Solo se puede actualizar manufactura de terminados o semiterminados");
        }

        dto.setProductoId(productoId);
        validateTipoCompatibility(existing, dto.getTipoProducto());

        applyCommonProductFields(existing, dto);
        updateTypedFields(existing, dto);
        assignInsumos(existing, dto.getInsumos());
        assignProceso(existing, dto.getProcesoProduccionCompleto());

        Producto saved = productoRepo.save(existing);
        saveManufacturingSnapshot(saved);
        return toDTO(saved);
    }

    @Transactional(readOnly = true)
    public ProductoManufacturingDTO getProductoManufacturing(String productoId) {
        Producto producto = productoRepo.findById(productoId)
                .orElseThrow(() -> new IllegalArgumentException("Producto no encontrado: " + productoId));

        if (!(producto instanceof Terminado) && !(producto instanceof SemiTerminado)) {
            throw new IllegalArgumentException("El producto no es terminado ni semiterminado: " + productoId);
        }

        return toDTO(producto);
    }

    private Producto instantiateProducto(String tipoProducto) {
        if ("T".equalsIgnoreCase(tipoProducto)) {
            return new Terminado();
        }
        if ("S".equalsIgnoreCase(tipoProducto)) {
            return new SemiTerminado();
        }
        throw new IllegalArgumentException("tipoProducto no soportado: " + tipoProducto);
    }

    private void validateTipoCompatibility(Producto existing, String requestedTipo) {
        if (requestedTipo == null || requestedTipo.isBlank()) {
            return;
        }
        String existingTipo = existing.getTipo_producto();
        if (!existingTipo.equalsIgnoreCase(requestedTipo)) {
            throw new IllegalArgumentException("No se permite cambiar el tipo de producto existente");
        }
    }

    private void applyCommonProductFields(Producto producto, ProductoManufacturingDTO dto) {
        producto.setProductoId(dto.getProductoId());
        producto.setNombre(dto.getNombre());
        producto.setObservaciones(dto.getObservaciones());
        producto.setCosto(defaultDouble(dto.getCosto()));
        producto.setIvaPercentual(defaultDouble(dto.getIvaPercentual()));
        producto.setTipoUnidades(dto.getTipoUnidades());
        producto.setCantidadUnidad(defaultDouble(dto.getCantidadUnidad()));
        producto.setInventareable(Boolean.TRUE.equals(dto.getInventareable()));
    }

    private void updateTypedFields(Producto producto, ProductoManufacturingDTO dto) {
        if (producto instanceof Terminado terminado) {
            terminado.setStatus(dto.getStatus() != null ? dto.getStatus() : terminado.getStatus());
            terminado.setFotoUrl(dto.getFotoUrl());
            terminado.setPrefijoLote(blankToNull(dto.getPrefijoLote()));
            terminado.setCategoria(resolveCategoria(dto.getCategoriaId()));
            terminado.setCasePack(buildCasePack(dto.getCasePack()));
            return;
        }

        if (dto.getCasePack() != null) {
            throw new IllegalArgumentException("Solo los terminados pueden tener case pack");
        }
    }

    private Categoria resolveCategoria(Integer categoriaId) {
        if (categoriaId == null) {
            return null;
        }
        return categoriaRepo.findById(categoriaId)
                .orElseThrow(() -> new IllegalArgumentException("Categoria no encontrada: " + categoriaId));
    }

    private void assignInsumos(Producto producto, List<ProductoManufacturingInsumoDTO> insumoDtos) {
        List<Insumo> nuevosInsumos = Optional.ofNullable(insumoDtos).orElseGet(List::of)
                .stream()
                .map(this::buildInsumo)
                .collect(Collectors.toList());

        if (producto instanceof Terminado terminado) {
            List<Insumo> managed = terminado.getInsumos();
            if (managed == null) {
                managed = new ArrayList<>();
                terminado.setInsumos(managed);
            }
            managed.clear();
            managed.addAll(nuevosInsumos);
            return;
        }

        SemiTerminado semiTerminado = (SemiTerminado) producto;
        List<Insumo> managed = semiTerminado.getInsumos();
        if (managed == null) {
            managed = new ArrayList<>();
            semiTerminado.setInsumos(managed);
        }
        managed.clear();
        managed.addAll(nuevosInsumos);
    }

    private Insumo buildInsumo(ProductoManufacturingInsumoDTO dto) {
        if (dto.getProductoId() == null || dto.getProductoId().isBlank()) {
            throw new IllegalArgumentException("Cada insumo debe indicar productoId");
        }
        if (dto.getCantidadRequerida() == null || dto.getCantidadRequerida() <= 0) {
            throw new IllegalArgumentException("Cada insumo debe tener cantidadRequerida mayor que cero");
        }

        Producto inputProducto = productoRepo.findById(dto.getProductoId())
                .orElseThrow(() -> new IllegalArgumentException("Producto no encontrado para insumo: " + dto.getProductoId()));

        Insumo insumo = new Insumo();
        insumo.setProducto(inputProducto);
        insumo.setCantidadRequerida(dto.getCantidadRequerida());
        return insumo;
    }

    private void assignProceso(Producto producto, ProcesoProduccionCompletoDTO procesoDto) {
        if (procesoDto == null) {
            throw new IllegalArgumentException("El procesoProduccionCompleto es obligatorio");
        }

        Map<String, Insumo> insumoByProductoId = getInsumos(producto).stream()
                .collect(Collectors.toMap(
                        insumo -> insumo.getProducto().getProductoId(),
                        insumo -> insumo,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));

        ProcesoProduccionCompleto proceso = getProcesoProduccionCompleto(producto);
        if (proceso == null) {
            proceso = new ProcesoProduccionCompleto();
        }
        proceso.setProducto(producto);

        proceso.setRendimientoTeorico(defaultDouble(procesoDto.getRendimientoTeorico()));

        List<ProcesoFabricacionNodo> managedNodes = proceso.getNodes();
        if (managedNodes == null) {
            managedNodes = new ArrayList<>();
            proceso.setNodes(managedNodes);
        } else {
            managedNodes.clear();
        }

        List<ProcesoFabricacionEdge> managedEdges = proceso.getEdges();
        if (managedEdges == null) {
            managedEdges = new ArrayList<>();
            proceso.setEdges(managedEdges);
        } else {
            managedEdges.clear();
        }

        validateProcesoDto(procesoDto, insumoByProductoId.keySet());

        Map<String, ProcesoFabricacionNodo> nodeMap = new LinkedHashMap<>();
        for (ProcesoFabricacionNodoDTO nodeDto : procesoDto.getNodes()) {
            ProcesoFabricacionNodo node = buildNode(nodeDto, proceso, insumoByProductoId);
            managedNodes.add(node);
            nodeMap.put(node.getFrontendId(), node);
        }

        for (ProcesoFabricacionEdgeDTO edgeDto : procesoDto.getEdges()) {
            ProcesoFabricacionNodo sourceNode = nodeMap.get(edgeDto.getSourceFrontendId());
            ProcesoFabricacionNodo targetNode = nodeMap.get(edgeDto.getTargetFrontendId());
            if (sourceNode == null || targetNode == null) {
                throw new IllegalArgumentException("Edge referencia nodos inexistentes: " + edgeDto.getFrontendId());
            }
            validateEdgeEndpoints(sourceNode, targetNode);

            ProcesoFabricacionEdge edge = new ProcesoFabricacionEdge();
            edge.setFrontendId(edgeDto.getFrontendId());
            edge.setProcesoProduccionCompleto(proceso);
            edge.setSourceNode(sourceNode);
            edge.setTargetNode(targetNode);
            managedEdges.add(edge);
        }

        validateGraphConnectivity(nodeMap, managedEdges);
        setProcesoProduccionCompleto(producto, proceso);
    }

    private void validateProcesoDto(ProcesoProduccionCompletoDTO procesoDto, Set<String> inputProductoIds) {
        if (procesoDto.getNodes() == null || procesoDto.getNodes().isEmpty()) {
            throw new IllegalArgumentException("El diagrama debe contener nodos");
        }
        if (procesoDto.getEdges() == null || procesoDto.getEdges().isEmpty()) {
            throw new IllegalArgumentException("El diagrama debe contener edges");
        }

        long targetCount = procesoDto.getNodes().stream()
                .filter(node -> "TARGET".equalsIgnoreCase(node.getNodeType()))
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
                if (inputProductoId == null) {
                    throw new IllegalArgumentException("Cada nodo insumo debe indicar inputProductoId");
                }
                if (!inputProductoIds.contains(inputProductoId)) {
                    throw new IllegalArgumentException("Nodo insumo referencia un insumo que no pertenece al producto: " + inputProductoId);
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

    private ProcesoFabricacionNodo buildNode(
            ProcesoFabricacionNodoDTO nodeDto,
            ProcesoProduccionCompleto proceso,
            Map<String, Insumo> insumoByProductoId
    ) {
        ProcesoFabricacionNodo node;

        if (nodeDto instanceof NodoInsumoDTO insumoDto) {
            String inputProductoId = blankToNull(insumoDto.getInputProductoId());
            Insumo insumo = insumoByProductoId.get(inputProductoId);
            if (insumo == null) {
                throw new IllegalArgumentException("No se encontró el insumo para el nodo: " + inputProductoId);
            }
            NodoInsumo nodoInsumo = new NodoInsumo();
            nodoInsumo.setInsumo(insumo);
            node = nodoInsumo;
        } else if (nodeDto instanceof NodoProcesoDTO procesoDto) {
            ProcesoProduccion procesoProduccion = procesoProduccionRepo.findById(procesoDto.getProcesoId())
                    .orElseThrow(() -> new IllegalArgumentException("ProcesoProduccion no encontrado: " + procesoDto.getProcesoId()));
            AreaOperativa areaOperativa = areaProduccionRepo.findById(procesoDto.getAreaOperativaId())
                    .orElseThrow(() -> new IllegalArgumentException("AreaOperativa no encontrada: " + procesoDto.getAreaOperativaId()));
            NodoProceso nodoProceso = new NodoProceso();
            nodoProceso.setProcesoProduccion(procesoProduccion);
            nodoProceso.setAreaOperativa(areaOperativa);
            node = nodoProceso;
        } else if (nodeDto instanceof NodoTargetDTO) {
            node = new NodoTarget();
        } else {
            throw new IllegalArgumentException("Tipo de nodo no soportado: " + nodeDto.getNodeType());
        }

        node.setFrontendId(nodeDto.getFrontendId());
        node.setPosicionX(nodeDto.getPosicionX());
        node.setPosicionY(nodeDto.getPosicionY());
        node.setLabel(nodeDto.getLabel());
        node.setProcesoProduccionCompleto(proceso);
        return node;
    }

    private void validateEdgeEndpoints(ProcesoFabricacionNodo sourceNode, ProcesoFabricacionNodo targetNode) {
        if (sourceNode instanceof NodoTarget) {
            throw new IllegalArgumentException("Un nodo TARGET no puede ser source de una conexión");
        }
        if (targetNode instanceof NodoInsumo) {
            throw new IllegalArgumentException("Un nodo INSUMO no puede ser target de una conexión");
        }
    }

    private void validateGraphConnectivity(
            Map<String, ProcesoFabricacionNodo> nodeMap,
            List<ProcesoFabricacionEdge> edges
    ) {
        Map<String, Long> outgoingByNode = edges.stream()
                .collect(Collectors.groupingBy(edge -> edge.getSourceNode().getFrontendId(), Collectors.counting()));
        Map<String, Long> incomingByNode = edges.stream()
                .collect(Collectors.groupingBy(edge -> edge.getTargetNode().getFrontendId(), Collectors.counting()));

        for (ProcesoFabricacionNodo node : nodeMap.values()) {
            long incoming = incomingByNode.getOrDefault(node.getFrontendId(), 0L);
            long outgoing = outgoingByNode.getOrDefault(node.getFrontendId(), 0L);

            if (node instanceof NodoInsumo && outgoing < 1) {
                throw new IllegalArgumentException("Cada nodo INSUMO debe tener al menos una salida");
            }
            if (node instanceof NodoProceso) {
                if (incoming < 1) {
                    throw new IllegalArgumentException("Cada nodo PROCESO debe tener al menos una entrada");
                }
                if (outgoing != 1) {
                    throw new IllegalArgumentException("Cada nodo PROCESO debe tener exactamente una salida");
                }
            }
            if (node instanceof NodoTarget && incoming < 1) {
                throw new IllegalArgumentException("El nodo TARGET debe tener al menos una entrada");
            }
        }
    }

    private CasePack buildCasePack(ProductoManufacturingCasePackDTO dto) {
        if (dto == null) {
            return null;
        }
        if (dto.getUnitsPerCase() == null || dto.getUnitsPerCase() < 1) {
            throw new IllegalArgumentException("unitsPerCase debe ser mayor o igual a 1");
        }

        CasePack casePack = new CasePack();
        casePack.setUnitsPerCase(dto.getUnitsPerCase());
        casePack.setEan14(blankToNull(dto.getEan14()));
        casePack.setLargoCm(dto.getLargoCm());
        casePack.setAnchoCm(dto.getAnchoCm());
        casePack.setAltoCm(dto.getAltoCm());
        casePack.setGrossWeightKg(dto.getGrossWeightKg());
        casePack.setDefaultForShipping(dto.getDefaultForShipping());

        List<InsumoEmpaque> insumosEmpaque = Optional.ofNullable(dto.getInsumosEmpaque()).orElseGet(List::of)
                .stream()
                .map(this::buildInsumoEmpaque)
                .collect(Collectors.toList());
        casePack.setInsumosEmpaque(insumosEmpaque);
        return casePack;
    }

    private InsumoEmpaque buildInsumoEmpaque(ProductoManufacturingInsumoEmpaqueDTO dto) {
        if (dto.getMaterialId() == null || dto.getMaterialId().isBlank()) {
            throw new IllegalArgumentException("Cada insumo de empaque debe indicar materialId");
        }

        Material material = materialRepo.findById(dto.getMaterialId())
                .orElseThrow(() -> new IllegalArgumentException("Material no encontrado: " + dto.getMaterialId()));

        InsumoEmpaque insumoEmpaque = new InsumoEmpaque();
        insumoEmpaque.setMaterial(material);
        insumoEmpaque.setCantidad(defaultDouble(dto.getCantidad()));
        insumoEmpaque.setUom(blankToNull(dto.getUom()));
        return insumoEmpaque;
    }

    private void saveManufacturingSnapshot(Producto producto) {
        try {
            ProductoManufacturingDTO dto = toDTO(producto);
            ManufacturingVersions version = new ManufacturingVersions();
            version.setProducto(producto);
            int nextVersion = manufacturingVersionRepo.findTopByProductoOrderByVersionNumberDesc(producto)
                    .map(v -> v.getVersionNumber() + 1)
                    .orElse(1);
            version.setVersionNumber(nextVersion);
            version.setInsumosJson(objectMapper.writeValueAsString(dto.getInsumos()));
            version.setProcesoProduccionJson(objectMapper.writeValueAsString(dto.getProcesoProduccionCompleto()));
            version.setCasePackJson(objectMapper.writeValueAsString(dto.getCasePack()));
            manufacturingVersionRepo.save(version);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo serializar el snapshot de manufactura", e);
        }
    }

    private ProductoManufacturingDTO toDTO(Producto producto) {
        ProductoManufacturingDTO dto = new ProductoManufacturingDTO();
        dto.setProductoId(producto.getProductoId());
        dto.setTipoProducto(producto.getTipo_producto());
        dto.setNombre(producto.getNombre());
        dto.setObservaciones(producto.getObservaciones());
        dto.setCosto(producto.getCosto());
        dto.setIvaPercentual(producto.getIvaPercentual());
        dto.setTipoUnidades(producto.getTipoUnidades());
        dto.setCantidadUnidad(producto.getCantidadUnidad());
        dto.setInventareable(producto.isInventareable());
        dto.setInsumos(getInsumos(producto).stream().map(this::toInsumoDTO).collect(Collectors.toList()));
        dto.setProcesoProduccionCompleto(toProcesoDTO(getProcesoProduccionCompleto(producto)));

        if (producto instanceof Terminado terminado) {
            dto.setStatus(terminado.getStatus());
            dto.setFotoUrl(terminado.getFotoUrl());
            dto.setPrefijoLote(terminado.getPrefijoLote());
            if (terminado.getCategoria() != null) {
                dto.setCategoriaId(terminado.getCategoria().getCategoriaId());
                dto.setCategoriaNombre(terminado.getCategoria().getCategoriaNombre());
            }
            dto.setCasePack(toCasePackDTO(terminado.getCasePack()));
        }

        return dto;
    }

    private ProductoManufacturingInsumoDTO toInsumoDTO(Insumo insumo) {
        Producto producto = insumo.getProducto();
        double subtotal = producto != null ? producto.getCosto() * insumo.getCantidadRequerida() : 0.0;
        return new ProductoManufacturingInsumoDTO(
                insumo.getInsumoId(),
                producto != null ? producto.getProductoId() : null,
                producto != null ? producto.getNombre() : null,
                producto != null ? producto.getCosto() : null,
                producto != null ? producto.getTipoUnidades() : null,
                insumo.getCantidadRequerida(),
                subtotal
        );
    }

    private ProductoManufacturingCasePackDTO toCasePackDTO(CasePack casePack) {
        if (casePack == null) {
            return null;
        }

        List<ProductoManufacturingInsumoEmpaqueDTO> insumosEmpaque = Optional.ofNullable(casePack.getInsumosEmpaque())
                .orElseGet(List::of)
                .stream()
                .map(insumo -> new ProductoManufacturingInsumoEmpaqueDTO(
                        insumo.getId(),
                        insumo.getMaterial() != null ? insumo.getMaterial().getProductoId() : null,
                        insumo.getMaterial() != null ? insumo.getMaterial().getNombre() : null,
                        insumo.getCantidad(),
                        insumo.getUom()
                ))
                .collect(Collectors.toList());

        return new ProductoManufacturingCasePackDTO(
                casePack.getId(),
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

    private ProcesoProduccionCompletoDTO toProcesoDTO(ProcesoProduccionCompleto proceso) {
        if (proceso == null) {
            return null;
        }

        List<ProcesoFabricacionNodoDTO> nodes = Optional.ofNullable(proceso.getNodes()).orElseGet(List::of)
                .stream()
                .map(this::toNodeDTO)
                .collect(Collectors.toList());

        List<ProcesoFabricacionEdgeDTO> edges = Optional.ofNullable(proceso.getEdges()).orElseGet(List::of)
                .stream()
                .map(edge -> new ProcesoFabricacionEdgeDTO(
                        edge.getId(),
                        edge.getFrontendId(),
                        edge.getSourceNode().getFrontendId(),
                        edge.getTargetNode().getFrontendId()
                ))
                .collect(Collectors.toList());

        return new ProcesoProduccionCompletoDTO(
                proceso.getProcesoCompletoId(),
                proceso.getRendimientoTeorico(),
                nodes,
                edges
        );
    }

    private ProcesoFabricacionNodoDTO toNodeDTO(ProcesoFabricacionNodo node) {
        ProcesoFabricacionNodoDTO dto;
        if (node instanceof NodoInsumo nodoInsumo) {
            NodoInsumoDTO nodoDto = new NodoInsumoDTO();
            nodoDto.setInsumoId(nodoInsumo.getInsumo() != null ? nodoInsumo.getInsumo().getInsumoId() : null);
            nodoDto.setInputProductoId(
                    nodoInsumo.getInsumo() != null && nodoInsumo.getInsumo().getProducto() != null
                            ? nodoInsumo.getInsumo().getProducto().getProductoId()
                            : null
            );
            dto = nodoDto;
        } else if (node instanceof NodoProceso nodoProceso) {
            NodoProcesoDTO nodoDto = new NodoProcesoDTO();
            nodoDto.setProcesoId(nodoProceso.getProcesoProduccion() != null ? nodoProceso.getProcesoProduccion().getProcesoId() : null);
            nodoDto.setProcesoNombre(nodoProceso.getProcesoProduccion() != null ? nodoProceso.getProcesoProduccion().getNombre() : null);
            nodoDto.setAreaOperativaId(nodoProceso.getAreaOperativa() != null ? nodoProceso.getAreaOperativa().getAreaId() : null);
            nodoDto.setAreaOperativaNombre(nodoProceso.getAreaOperativa() != null ? nodoProceso.getAreaOperativa().getNombre() : null);
            if (nodoProceso.getProcesoProduccion() != null) {
                nodoDto.setSetUpTime(nodoProceso.getProcesoProduccion().getSetUpTime());
                nodoDto.setModel(
                        nodoProceso.getProcesoProduccion().getModel() != null
                                ? nodoProceso.getProcesoProduccion().getModel().name()
                                : null
                );
                nodoDto.setConstantSeconds(nodoProceso.getProcesoProduccion().getConstantSeconds());
                nodoDto.setThroughputUnitsPerSec(nodoProceso.getProcesoProduccion().getThroughputUnitsPerSec());
                nodoDto.setSecondsPerUnit(nodoProceso.getProcesoProduccion().getSecondsPerUnit());
                nodoDto.setSecondsPerBatch(nodoProceso.getProcesoProduccion().getSecondsPerBatch());
                nodoDto.setBatchSize(nodoProceso.getProcesoProduccion().getBatchSize());
            }
            dto = nodoDto;
        } else if (node instanceof NodoTarget) {
            dto = new NodoTargetDTO();
        } else {
            throw new IllegalStateException("Tipo de nodo no soportado en respuesta: " + node.getClass().getName());
        }

        dto.setId(node.getId());
        dto.setFrontendId(node.getFrontendId());
        dto.setPosicionX(node.getPosicionX());
        dto.setPosicionY(node.getPosicionY());
        dto.setLabel(node.getLabel());
        return dto;
    }

    private List<Insumo> getInsumos(Producto producto) {
        if (producto instanceof Terminado terminado) {
            return Optional.ofNullable(terminado.getInsumos()).orElseGet(List::of);
        }
        return Optional.ofNullable(((SemiTerminado) producto).getInsumos()).orElseGet(List::of);
    }

    private ProcesoProduccionCompleto getProcesoProduccionCompleto(Producto producto) {
        if (producto instanceof Terminado terminado) {
            return terminado.getProcesoProduccionCompleto();
        }
        return ((SemiTerminado) producto).getProcesoProduccionCompleto();
    }

    private void setProcesoProduccionCompleto(Producto producto, ProcesoProduccionCompleto proceso) {
        if (producto instanceof Terminado terminado) {
            terminado.setProcesoProduccionCompleto(proceso);
            return;
        }
        ((SemiTerminado) producto).setProcesoProduccionCompleto(proceso);
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
