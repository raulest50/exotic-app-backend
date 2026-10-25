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
import exotic.app.planta.model.producto.costos.ProductoCostoOrigen;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
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
    private final ProductoCostoService productoCostoService;
    private final UnidadMedidaProductoService unidades;

    @Transactional
    public ProductoManufacturingDTO createProductoManufacturing(ProductoManufacturingDTO dto) {
        log.info("[PRODUCTOS_MANUFACTURING] service create begin {}", dtoContext(dto));
        if (dto == null) {
            throw new IllegalArgumentException("La solicitud de producto con manufactura no puede ser nula");
        }
        if (dto.getProductoId() == null || dto.getProductoId().isBlank()) {
            throw new IllegalArgumentException("El productoId es obligatorio");
        }
        if (productoRepo.existsById(dto.getProductoId())) {
            throw new IllegalArgumentException("Ya existe un producto con ID: " + dto.getProductoId());
        }

        normalizeExplicitRecipe(dto);
        Producto producto = instantiateProducto(dto.getTipoProducto());
        log.debug("[PRODUCTOS_MANUFACTURING] service create instantiated productoId={} entityType={}", dto.getProductoId(), producto.getClass().getSimpleName());
        producto.asignarCostoInicial(java.math.BigDecimal.valueOf(defaultDouble(dto.getCosto())));
        applyCommonProductFields(producto, dto);
        updateTypedFields(producto, dto);
        assignInsumos(producto, dto.getInsumos());
        assignProceso(producto, dto.getProcesoProduccionCompleto());

        Producto saved = productoRepo.save(producto);
        productoCostoService.registrarCostoInicial(
                saved,
                ProductoCostoService.ContextoCambio.sistema(ProductoCostoOrigen.CREACION));
        saveManufacturingSnapshot(saved);
        log.info(
                "[PRODUCTOS_MANUFACTURING] service create persisted productoId={} entityType={} insumos={} procesoNodes={} procesoEdges={}",
                saved.getProductoId(),
                saved.getClass().getSimpleName(),
                getInsumos(saved).size(),
                procesoNodesCount(getProcesoProduccionCompleto(saved)),
                procesoEdgesCount(getProcesoProduccionCompleto(saved))
        );
        return toDTO(saved);
    }

    @Transactional
    public ProductoManufacturingDTO updateProductoManufacturing(String productoId, ProductoManufacturingDTO dto) {
        log.info("[PRODUCTOS_MANUFACTURING] service update begin pathProductoId={} {}", productoId, dtoContext(dto));
        if (dto == null) {
            throw new IllegalArgumentException("La solicitud de producto con manufactura no puede ser nula");
        }
        if (productoId == null || productoId.isBlank()) {
            throw new IllegalArgumentException("El productoId es obligatorio");
        }
        if (dto.getProductoId() != null && !productoId.equals(dto.getProductoId())) {
            throw new IllegalArgumentException("El productoId del path no coincide con el del cuerpo");
        }

        Producto existing = productoRepo.findById(productoId)
                .orElseThrow(() -> new IllegalArgumentException("Producto no encontrado: " + productoId));
        log.debug("[PRODUCTOS_MANUFACTURING] service update loaded productoId={} entityType={}", productoId, existing.getClass().getSimpleName());

        if (!(existing instanceof Terminado) && !(existing instanceof SemiTerminado)) {
            throw new IllegalArgumentException("Solo se puede actualizar manufactura de terminados o semiterminados");
        }

        dto.setProductoId(productoId);
        validateTipoCompatibility(existing, dto.getTipoProducto());

        if (getProcesoProduccionCompleto(existing) != null
                && getProcesoProduccionCompleto(existing).getCantidadSalidaReceta() != null
                && !hasExplicitRecipe(dto)) {
            throw new IllegalArgumentException("Esta receta tiene una salida explícita. Debe conservar sus cantidades y unidades al editarla.");
        }
        normalizeExplicitRecipe(dto);
        if (hasExplicitRecipe(dto) && !Objects.equals(existing.getTipoUnidades(), dto.getTipoUnidades())) {
            throw new IllegalArgumentException("La unidad de salida e inventario de un producto existente no se puede cambiar desde el diseñador.");
        }
        boolean originalInventareable = existing.isInventareable();
        java.math.BigDecimal requestedCosto = java.math.BigDecimal.valueOf(defaultDouble(dto.getCosto()));
        applyCommonProductFields(existing, dto);
        existing.setInventareable(originalInventareable);
        updateTypedFields(existing, dto);
        assignInsumos(existing, dto.getInsumos());
        assignProceso(existing, dto.getProcesoProduccionCompleto());

        Producto saved = productoRepo.save(existing);
        saved = productoCostoService.actualizarCosto(
                saved.getProductoId(), requestedCosto,
                ProductoCostoService.ContextoCambio.sistema(ProductoCostoOrigen.MANUFACTURING_EDIT)).producto();
        saveManufacturingSnapshot(saved);
        log.info(
                "[PRODUCTOS_MANUFACTURING] service update persisted productoId={} entityType={} insumos={} procesoNodes={} procesoEdges={}",
                saved.getProductoId(),
                saved.getClass().getSimpleName(),
                getInsumos(saved).size(),
                procesoNodesCount(getProcesoProduccionCompleto(saved)),
                procesoEdgesCount(getProcesoProduccionCompleto(saved))
        );
        return toDTO(saved);
    }

    @Transactional(readOnly = true)
    public ProductoManufacturingDTO getProductoManufacturing(String productoId) {
        log.debug("[PRODUCTOS_MANUFACTURING] service get begin productoId={}", productoId);
        Producto producto = productoRepo.findById(productoId)
                .orElseThrow(() -> new IllegalArgumentException("Producto no encontrado: " + productoId));

        if (!(producto instanceof Terminado) && !(producto instanceof SemiTerminado)) {
            throw new IllegalArgumentException("El producto no es terminado ni semiterminado: " + productoId);
        }

        log.debug("[PRODUCTOS_MANUFACTURING] service get loaded productoId={} entityType={}", productoId, producto.getClass().getSimpleName());
        return toDTO(producto);
    }

    public static boolean hasExplicitRecipe(ProductoManufacturingDTO dto) {
        return dto != null && ((dto.getProcesoProduccionCompleto() != null
                && dto.getProcesoProduccionCompleto().getCantidadSalidaReceta() != null)
                || (dto.getInsumos() != null && dto.getInsumos().stream().filter(Objects::nonNull)
                .anyMatch(i -> i.getCantidadReceta() != null || i.getUnidadReceta() != null)));
    }

    @Transactional
    public ProductoManufacturingDTO createFromDesigner(ProductoManufacturingDTO dto) {
        if (!hasExplicitRecipe(dto)) throw new IllegalArgumentException("Debe indicar la salida de la receta.");
        return createProductoManufacturing(dto);
    }

    /** Retain authored batch amounts; expose legacy coefficients to existing consumers.
     * Recipe output is already in Producto.tipoUnidades; only input units need conversion.
     * Package content applies only to finished products in U, without density conversion.
     * Null metadata means a historical recipe, never an inferred one-unit batch.
     */
    private void normalizeExplicitRecipe(ProductoManufacturingDTO dto) {
        if (!hasExplicitRecipe(dto)) return;
        if (dto.getProductoId() == null || !dto.getProductoId().matches("[A-Z0-9]{1,255}"))
            throw new IllegalArgumentException("El código debe ser alfanumérico y estar en mayúsculas.");
        if (dto.getNombre() == null || dto.getNombre().isBlank() || dto.getNombre().length() > 200)
            throw new IllegalArgumentException("El nombre es obligatorio y admite hasta 200 caracteres.");
        if (dto.getTipoProducto() == null || !List.of("T", "S").contains(dto.getTipoProducto()))
            throw new IllegalArgumentException("Seleccione terminado o semiterminado.");
        dto.setTipoUnidades(catalogUnit(dto.getTipoUnidades()));
        if ("T".equals(dto.getTipoProducto()) && "U".equals(dto.getTipoUnidades())) {
            if (!positiveFinite(dto.getCantidadUnidad())) throw new IllegalArgumentException("El contenido por envase debe ser positivo.");
            dto.setUnidadContenidoPorEnvase(catalogUnit(dto.getUnidadContenidoPorEnvase()));
        } else {
            dto.setCantidadUnidad(0d);
            dto.setUnidadContenidoPorEnvase(null);
        }
        if ("T".equals(dto.getTipoProducto()) && dto.getCategoriaId() == null)
            throw new IllegalArgumentException("El terminado requiere categoría.");
        boolean requiereOrden = "T".equals(dto.getTipoProducto()) || Boolean.TRUE.equals(dto.getRequiereOrdenFabricacion());
        if (requiereOrden && !Boolean.TRUE.equals(dto.getInventareable()))
            throw new IllegalArgumentException("Un producto con orden propia debe ser inventariable.");
        if ((requiereOrden || (dto.getPrefijoLote() != null && !dto.getPrefijoLote().isBlank()))
                && (dto.getPrefijoLote() == null || !dto.getPrefijoLote().matches("[A-Z0-9]{1,20}")))
            throw new IllegalArgumentException("Indique un prefijo de lote válido.");
        if (dto.getIvaPercentual() == null || !List.of(0d, 5d, 19d).contains(dto.getIvaPercentual()))
            throw new IllegalArgumentException("El IVA debe ser 0, 5 o 19.");
        var recipe = dto.getProcesoProduccionCompleto();
        if (recipe == null || recipe.getRendimientoTeorico() == null
                || !Double.isFinite(recipe.getRendimientoTeorico()) || recipe.getRendimientoTeorico() < 0)
            throw new IllegalArgumentException("Indique un rendimiento válido.");
        // La cantidad obtenida ya usa la unidad de inventario del producto destino.
        BigDecimal output = recipeAmount(recipe.getCantidadSalidaReceta());
        if (dto.getInsumos() == null || dto.getInsumos().isEmpty())
            throw new IllegalArgumentException("La receta debe incluir insumos.");
        Set<String> inputIds = new HashSet<>();
        BigDecimal cost = BigDecimal.ZERO;
        for (var input : dto.getInsumos()) {
            if (input == null || input.getProductoId() == null || !inputIds.add(input.getProductoId()))
                throw new IllegalArgumentException("Hay insumos vacíos o repetidos.");
            Producto source = productoRepo.findById(input.getProductoId())
                    .orElseThrow(() -> new IllegalArgumentException("Insumo inexistente: " + input.getProductoId()));
            if (!(source instanceof Material) && !(source instanceof SemiTerminado))
                throw new IllegalArgumentException("Los insumos deben ser materiales o semiterminados.");
            rejectRecipeCycle(source, dto.getProductoId(), new HashSet<>());
            input.setUnidadReceta(catalogUnit(input.getUnidadReceta()));
            BigDecimal amount = unidades.convertir(recipeAmount(input.getCantidadReceta()), input.getUnidadReceta(), source.getTipoUnidades());
            BigDecimal coefficient = amount.divide(output, MathContext.DECIMAL128);
            if (!positiveFinite(coefficient.doubleValue())) throw new IllegalArgumentException("La proporción excede el rango permitido.");
            input.setCantidadRequerida(coefficient.doubleValue());
            cost = cost.add(source.getCosto().multiply(coefficient));
        }
        cost = cost.setScale(6, RoundingMode.HALF_UP);
        if (cost.signum() < 0 || cost.precision() > 19) throw new IllegalArgumentException("El costo calculado excede el rango permitido.");
        dto.setCosto(cost.doubleValue());
        validateExplicitGraph(recipe, inputIds);
    }

    private String catalogUnit(String code) {
        unidades.convertir(BigDecimal.ONE, code, code);
        return code.strip().toUpperCase(Locale.ROOT);
    }

    private static boolean positiveFinite(Double value) {
        return value != null && Double.isFinite(value) && value > 0;
    }

    private BigDecimal recipeAmount(BigDecimal value) {
        if (value == null || value.signum() <= 0 || value.stripTrailingZeros().scale() > 12
                || value.precision() - value.scale() > 12)
            throw new IllegalArgumentException("Las cantidades deben ser positivas, con hasta 12 enteros y 12 decimales.");
        return value;
    }

    private void rejectRecipeCycle(Producto source, String outputId, Set<String> seen) {
        if (outputId.equals(source.getProductoId())) throw new IllegalArgumentException("La receta contiene una dependencia circular de productos.");
        if (!seen.add(source.getProductoId())) throw new IllegalArgumentException("Un semiterminado contiene una receta circular.");
        if (source instanceof SemiTerminado semi && semi.getInsumos() != null)
            for (Insumo child : semi.getInsumos()) rejectRecipeCycle(child.getProducto(), outputId, seen);
        seen.remove(source.getProductoId());
    }

    private void validateExplicitGraph(ProcesoProduccionCompletoDTO recipe, Set<String> inputs) {
        if (recipe.getNodes() == null || recipe.getEdges() == null
                || recipe.getNodes().stream().anyMatch(Objects::isNull) || recipe.getEdges().stream().anyMatch(Objects::isNull))
            throw new IllegalArgumentException("El diagrama está incompleto.");
        validateProcesoDto(recipe, inputs);
        Set<String> mapped = new HashSet<>();
        Map<String, ProcesoFabricacionNodoDTO> nodes = new HashMap<>();
        Map<String, Integer> incoming = new HashMap<>(), outgoing = new HashMap<>();
        for (var node : recipe.getNodes()) {
            if (!Double.isFinite(node.getPosicionX()) || !Double.isFinite(node.getPosicionY()))
                throw new IllegalArgumentException("El diagrama contiene posiciones inválidas.");
            nodes.put(node.getFrontendId(), node);
            if (node instanceof NodoInsumoDTO input && !mapped.add(input.getInputProductoId()))
                throw new IllegalArgumentException("Un insumo aparece varias veces en el diagrama.");
        }
        if (!mapped.equals(inputs) || nodes.values().stream().noneMatch(NodoProcesoDTO.class::isInstance))
            throw new IllegalArgumentException("El diagrama debe incluir todos los insumos y al menos un proceso.");
        Set<String> edgeIds = new HashSet<>();
        for (var edge : recipe.getEdges()) {
            var source = nodes.get(edge.getSourceFrontendId());
            var target = nodes.get(edge.getTargetFrontendId());
            if (edge.getFrontendId() == null || edge.getFrontendId().isBlank() || !edgeIds.add(edge.getFrontendId())
                    || source == null || target == null || source == target || source instanceof NodoTargetDTO
                    || !(target instanceof NodoProcesoDTO || (source instanceof NodoProcesoDTO && target instanceof NodoTargetDTO)))
                throw new IllegalArgumentException("Conexión inválida en el diagrama.");
            outgoing.merge(source.getFrontendId(), 1, Integer::sum);
            incoming.merge(target.getFrontendId(), 1, Integer::sum);
        }
        for (var node : nodes.values()) {
            int in = incoming.getOrDefault(node.getFrontendId(), 0), out = outgoing.getOrDefault(node.getFrontendId(), 0);
            if ((node instanceof NodoInsumoDTO && (in != 0 || out != 1))
                    || (node instanceof NodoProcesoDTO && (in < 1 || out != 1))
                    || (node instanceof NodoTargetDTO && (in < 1 || out != 0)))
                throw new IllegalArgumentException("Complete las entradas y salidas de todos los nodos.");
        }
        ArrayDeque<String> queue = new ArrayDeque<>();
        nodes.keySet().stream().filter(id -> !incoming.containsKey(id)).forEach(queue::add);
        int visited = 0;
        while (!queue.isEmpty()) {
            String id = queue.remove(); visited++;
            for (var edge : recipe.getEdges()) if (id.equals(edge.getSourceFrontendId())) {
                if (incoming.merge(edge.getTargetFrontendId(), -1, Integer::sum) == 0) queue.add(edge.getTargetFrontendId());
            }
        }
        if (visited != nodes.size()) throw new IllegalArgumentException("El diagrama contiene ciclos.");
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
        producto.setIvaPercentual(defaultDouble(dto.getIvaPercentual()));
        producto.setTipoUnidades(dto.getTipoUnidades());
        producto.setCantidadUnidad(defaultDouble(dto.getCantidadUnidad()));
        if (hasExplicitRecipe(dto)) producto.setUnidadContenidoPorEnvase(dto.getUnidadContenidoPorEnvase());
        producto.setInventareable(Boolean.TRUE.equals(dto.getInventareable()));
    }

    private void updateTypedFields(Producto producto, ProductoManufacturingDTO dto) {
        if (producto instanceof Terminado terminado) {
            terminado.setStatus(dto.getStatus() != null ? dto.getStatus() : terminado.getStatus());
            terminado.setFotoUrl(dto.getFotoUrl());
            String prefijoLote = normalizeOptionalPrefijoLote(dto.getPrefijoLote());
            validatePrefijoLoteDisponible(prefijoLote, producto.getProductoId());
            terminado.setPrefijoLote(prefijoLote);
            terminado.setCategoria(resolveCategoria(dto.getCategoriaId()));
            terminado.setCasePack(buildCasePack(dto.getCasePack()));
            return;
        }

        if (dto.getCasePack() != null) {
            throw new IllegalArgumentException("Solo los terminados pueden tener case pack");
        }
        SemiTerminado semiTerminado = (SemiTerminado) producto;
        boolean requiereOrden = dto.getRequiereOrdenFabricacion() != null
                ? dto.getRequiereOrdenFabricacion()
                : semiTerminado.isRequiereOrdenFabricacion();
        semiTerminado.setRequiereOrdenFabricacion(requiereOrden);
        if (requiereOrden) {
            String prefijoLote = normalizeOptionalPrefijoLote(dto.getPrefijoLote());
            if (prefijoLote == null) {
                throw new IllegalArgumentException(
                        "Un semiterminado que requiere orden de fabricacion debe tener prefijo de lote.");
            }
            validatePrefijoLoteDisponible(prefijoLote, producto.getProductoId());
            semiTerminado.setPrefijoLote(prefijoLote);
            semiTerminado.setInventareable(true);
        } else if (hasExplicitRecipe(dto)) {
            String prefix = normalizeOptionalPrefijoLote(dto.getPrefijoLote());
            validatePrefijoLoteDisponible(prefix, producto.getProductoId());
            semiTerminado.setPrefijoLote(prefix);
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
        log.debug("[PRODUCTOS_MANUFACTURING] service assign insumos productoId={} count={}", producto.getProductoId(), nuevosInsumos.size());

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
        insumo.setCantidadReceta(dto.getCantidadReceta());
        insumo.setUnidadReceta(dto.getUnidadReceta());
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
        proceso.setCantidadSalidaReceta(procesoDto.getCantidadSalidaReceta());

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
        log.debug(
                "[PRODUCTOS_MANUFACTURING] service proceso validated productoId={} inputInsumos={} nodes={} edges={} rendimientoTeorico={}",
                producto.getProductoId(),
                insumoByProductoId.size(),
                managedNodes.size(),
                managedEdges.size(),
                proceso.getRendimientoTeorico()
        );
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
            log.debug("[PRODUCTOS_MANUFACTURING] service snapshot begin productoId={}", producto.getProductoId());
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
            log.info("[PRODUCTOS_MANUFACTURING] service snapshot saved productoId={} versionNumber={}", producto.getProductoId(), nextVersion);
        } catch (JsonProcessingException e) {
            log.error("[PRODUCTOS_MANUFACTURING] service snapshot serialization_error productoId={} message={}", producto.getProductoId(), e.getMessage(), e);
            throw new IllegalStateException("No se pudo serializar el snapshot de manufactura", e);
        }
    }

    private ProductoManufacturingDTO toDTO(Producto producto) {
        ProductoManufacturingDTO dto = new ProductoManufacturingDTO();
        dto.setProductoId(producto.getProductoId());
        dto.setTipoProducto(producto.getTipo_producto());
        dto.setNombre(producto.getNombre());
        dto.setObservaciones(producto.getObservaciones());
        dto.setCosto(producto.getCosto().doubleValue());
        dto.setIvaPercentual(producto.getIvaPercentual());
        dto.setTipoUnidades(producto.getTipoUnidades());
        dto.setCantidadUnidad(producto.getCantidadUnidad());
        dto.setUnidadContenidoPorEnvase(producto.getUnidadContenidoPorEnvase());
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
        } else if (producto instanceof SemiTerminado semiTerminado) {
            dto.setRequiereOrdenFabricacion(semiTerminado.isRequiereOrdenFabricacion());
            dto.setPrefijoLote(semiTerminado.getPrefijoLote());
        }

        return dto;
    }

    private ProductoManufacturingInsumoDTO toInsumoDTO(Insumo insumo) {
        Producto producto = insumo.getProducto();
        double subtotal = producto != null ? producto.getCosto().doubleValue() * insumo.getCantidadRequerida() : 0.0;
        return new ProductoManufacturingInsumoDTO(
                insumo.getInsumoId(),
                producto != null ? producto.getProductoId() : null,
                producto != null ? producto.getNombre() : null,
                producto != null ? producto.getCosto().doubleValue() : null,
                producto != null ? producto.getTipoUnidades() : null,
                insumo.getCantidadRequerida(),
                subtotal, insumo.getCantidadReceta(), insumo.getUnidadReceta()
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
                edges, proceso.getCantidadSalidaReceta()
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

    private String normalizeOptionalPrefijoLote(String value) {
        String normalized = blankToNull(value);
        return normalized != null ? normalized.toUpperCase(Locale.ROOT) : null;
    }

    private void validatePrefijoLoteDisponible(String prefijoLote, String productoIdExcluir) {
        if (prefijoLote == null) {
            return;
        }

        Optional<Producto> existing = productoRepo.findByPrefijoLoteIgnoreCase(prefijoLote);
        if (existing.isPresent() && !existing.get().getProductoId().equals(productoIdExcluir)) {
            throw new IllegalArgumentException("El prefijo de lote ya esta asignado a otro producto.");
        }
    }

    private String dtoContext(ProductoManufacturingDTO dto) {
        if (dto == null) {
            return "productoId=<null> tipoProducto=<null> categoriaId=<null> prefijoLotePresent=false insumos=0 casePackPresent=false casePackInsumos=0 procesoNodes=0 procesoEdges=0";
        }

        int insumosCount = dto.getInsumos() != null ? dto.getInsumos().size() : 0;
        int casePackInsumosCount = dto.getCasePack() != null && dto.getCasePack().getInsumosEmpaque() != null
                ? dto.getCasePack().getInsumosEmpaque().size()
                : 0;
        ProcesoProduccionCompletoDTO proceso = dto.getProcesoProduccionCompleto();
        int nodesCount = proceso != null && proceso.getNodes() != null ? proceso.getNodes().size() : 0;
        int edgesCount = proceso != null && proceso.getEdges() != null ? proceso.getEdges().size() : 0;

        return "productoId=" + nullSafe(dto.getProductoId())
                + " tipoProducto=" + nullSafe(dto.getTipoProducto())
                + " categoriaId=" + dto.getCategoriaId()
                + " prefijoLotePresent=" + hasText(dto.getPrefijoLote())
                + " insumos=" + insumosCount
                + " casePackPresent=" + (dto.getCasePack() != null)
                + " casePackInsumos=" + casePackInsumosCount
                + " procesoNodes=" + nodesCount
                + " procesoEdges=" + edgesCount;
    }

    private int procesoNodesCount(ProcesoProduccionCompleto proceso) {
        return proceso != null && proceso.getNodes() != null ? proceso.getNodes().size() : 0;
    }

    private int procesoEdgesCount(ProcesoProduccionCompleto proceso) {
        return proceso != null && proceso.getEdges() != null ? proceso.getEdges().size() : 0;
    }

    private String nullSafe(String value) {
        return hasText(value) ? value.trim() : "<null>";
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
