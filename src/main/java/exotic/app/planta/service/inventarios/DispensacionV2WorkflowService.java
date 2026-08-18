package exotic.app.planta.service.inventarios;

import exotic.app.planta.config.initializers.AreaOperativaInitializer;
import exotic.app.planta.model.inventarios.Lote;
import exotic.app.planta.model.inventarios.Movimiento;
import exotic.app.planta.model.inventarios.TransaccionAlmacen;
import exotic.app.planta.model.inventarios.dto.*;
import exotic.app.planta.model.organizacion.AreaOperativa;
import exotic.app.planta.model.producto.Material;
import exotic.app.planta.model.producto.Producto;
import exotic.app.planta.model.producto.SemiTerminado;
import exotic.app.planta.model.producto.Terminado;
import exotic.app.planta.model.producto.dto.InsumoWithStockDTO;
import exotic.app.planta.model.producto.manufacturing.packaging.CasePack;
import exotic.app.planta.model.producto.manufacturing.packaging.InsumoEmpaque;
import exotic.app.planta.model.produccion.OrdenProduccion;
import exotic.app.planta.model.produccion.EstadoDispensacionMateriales;
import exotic.app.planta.model.produccion.batchrecord.BatchRecord;
import exotic.app.planta.model.produccion.fabricacion.EstadoOrdenFabricacion;
import exotic.app.planta.model.produccion.fabricacion.OrdenFabricacion;
import exotic.app.planta.model.produccion.fabricacion.OrdenFabricacionOperacion;
import exotic.app.planta.model.users.User;
import exotic.app.planta.repo.inventarios.TransaccionAlmacenHeaderRepo;
import exotic.app.planta.repo.inventarios.TransaccionAlmacenRepo;
import exotic.app.planta.repo.inventarios.LoteRepo;
import exotic.app.planta.repo.produccion.OrdenProduccionRepo;
import exotic.app.planta.repo.produccion.batchrecord.BatchRecordRepo;
import exotic.app.planta.repo.produccion.fabricacion.OrdenFabricacionOperacionRepo;
import exotic.app.planta.repo.produccion.fabricacion.OrdenFabricacionRepo;
import exotic.app.planta.repo.producto.ProductoRepo;
import exotic.app.planta.repo.producto.procesos.AreaProduccionRepo;
import exotic.app.planta.service.produccion.SeguimientoOrdenAreaService;
import exotic.app.planta.service.produccion.MaterialRequirementSnapshotService;
import exotic.app.planta.service.produccion.BatchRecordService;
import exotic.app.planta.service.master.configs.MasterDirectiveService;
import exotic.app.planta.service.productos.ProductoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class DispensacionV2WorkflowService {

    private static final double TOLERANCE = 0.01;
    private static final int MAX_LOTES_PAGE_SIZE = 100;

    private final OrdenProduccionRepo ordenProduccionRepo;
    private final ProductoRepo productoRepo;
    private final AreaProduccionRepo areaProduccionRepo;
    private final ProductoService productoService;
    private final SeguimientoOrdenAreaService seguimientoOrdenAreaService;
    private final TransaccionAlmacenHeaderRepo transaccionAlmacenHeaderRepo;
    private final TransaccionAlmacenRepo transaccionAlmacenRepo;
    private final SalidaAlmacenService salidaAlmacenService;
    private final OrdenFabricacionRepo ordenFabricacionRepo;
    private final OrdenFabricacionOperacionRepo ordenFabricacionOperacionRepo;
    private final BatchRecordRepo batchRecordRepo;
    private final LoteRepo loteRepo;
    private final MaterialRequirementSnapshotService materialRequirementSnapshotService;
    private final MasterDirectiveService masterDirectiveService;
    private final BatchRecordService batchRecordService;

    @Transactional(readOnly = true)
    public List<DispensacionV2OrdenFabricacionDTOs.Option> buscarOrdenesFabricacion(
            Integer areaId, String search) {
        requireBatchRecordWorkflow();
        AreaOperativa area = requireArea(areaId);
        String searchPattern = search == null || search.isBlank()
                ? "" : "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
        Map<Long, OrdenFabricacion> ordenes = new LinkedHashMap<>();
        for (OrdenFabricacionOperacion operacion
                : ordenFabricacionOperacionRepo.findParaDispensacionPorArea(
                area.getAreaId(),
                List.of(EstadoOrdenFabricacion.LIBERADA, EstadoOrdenFabricacion.EN_EJECUCION),
                searchPattern)) {
            OrdenFabricacion orden = operacion.getOrdenFabricacion();
            ordenes.putIfAbsent(orden.getOrdenFabricacionId(), orden);
        }
        return ordenes.values().stream().map(this::toOrdenFabricacionOption).toList();
    }

    @Transactional(readOnly = true)
    public DispensacionV2OrdenFabricacionDTOs.PreparationResponse prepararOrdenFabricacion(
            Long ordenFabricacionId, Integer areaId) {
        return buildOrdenFabricacionResponse(
                ordenFabricacionId, areaId, Map.of(), false, false);
    }

    @Transactional(readOnly = true)
    public DispensacionV2OrdenFabricacionDTOs.PreparationResponse asignarLotesOrdenFabricacion(
            Long ordenFabricacionId,
            DispensacionV2OrdenFabricacionDTOs.AssignmentRequest request) {
        Map<String, DispensacionV2MaterialEditableRequestDTO> overrides = new HashMap<>();
        if (request != null && request.getMateriales() != null) {
            for (DispensacionV2MaterialEditableRequestDTO material : request.getMateriales()) {
                if (material != null && material.getProductoId() != null
                        && !material.getProductoId().isBlank()) {
                    overrides.put(material.getProductoId(), material);
                }
            }
        }
        return buildOrdenFabricacionResponse(
                ordenFabricacionId,
                request == null ? null : request.getAreaId(),
                overrides,
                true,
                false);
    }

    @Transactional
    public DispensacionV2OrdenFabricacionDTOs.FinalizationResponse finalizarOrdenFabricacion(
            Long ordenFabricacionId,
            DispensacionV2OrdenFabricacionDTOs.FinalizationRequest request,
            User currentUser) {
        requireBatchRecordWorkflow();
        AreaOperativa area = requireArea(request == null ? null : request.getAreaId());
        OrdenFabricacion orden = requireOrdenFabricacion(ordenFabricacionId);
        validateOrdenFabricacion(area, orden);
        BatchRecord record = requireBatchRecordFabricacion(orden);
        List<MaterialRequirementSnapshotService.RequirementView> requirements =
                materialRequirementSnapshotService.leer(
                        record.getRequerimientosMaterialesJson());
        Set<String> productosPermitidos = requirements.stream()
                .map(MaterialRequirementSnapshotService.RequirementView::productoId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        List<DispensacionV2FinalizacionMaterialRequestDTO> materiales =
                request == null || request.getMateriales() == null
                        ? List.of() : request.getMateriales();
        boolean contieneMaterialAjeno = materiales.stream()
                .filter(Objects::nonNull)
                .filter(material -> Boolean.TRUE.equals(material.getChecked()))
                .map(DispensacionV2FinalizacionMaterialRequestDTO::getProductoId)
                .anyMatch(productoId -> !productosPermitidos.contains(productoId));
        if (contieneMaterialAjeno) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "La dispensacion contiene un material ajeno al snapshot de la OF.");
        }

        DispensacionV2FinalizacionOrdenRequestDTO requestCompatible =
                new DispensacionV2FinalizacionOrdenRequestDTO();
        requestCompatible.setOrdenProduccionId(Math.toIntExact(ordenFabricacionId));
        requestCompatible.setMateriales(materiales);
        Map<StockDemandKey, Double> demandaPorLote = new HashMap<>();
        List<DispensacionItemDTO> items = buildFinalizacionItems(
                requestCompatible, demandaPorLote);
        if (items.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Debe seleccionar al menos un material de la OF.");
        }
        validateStockDisponible(demandaPorLote);
        String observaciones = "Dispensacion v2 de OF " + ordenFabricacionId
                + " hacia " + area.getNombre()
                + (request.getObservaciones() == null || request.getObservaciones().isBlank()
                ? "" : ". " + request.getObservaciones().trim());
        TransaccionAlmacen transaccion = salidaAlmacenService
                .createDispensacionOrdenFabricacion(
                        ordenFabricacionId, area.getAreaId(), items,
                        observaciones, currentUser);
        batchRecordService.sincronizarConsumosOrdenFabricacion(ordenFabricacionId);

        Map<String, Double> historico = calcularHistoricoPorProducto(
                TransaccionAlmacen.TipoEntidadCausante.OD_OF,
                Math.toIntExact(ordenFabricacionId));
        boolean completa = requirements.stream()
                .filter(requirement -> requirement.inventareable()
                        || requirement.consumoDirecto())
                .allMatch(requirement -> historico.getOrDefault(
                        requirement.productoId(), 0.0) + TOLERANCE
                        >= requirement.cantidad().doubleValue());
        orden.setEstadoDispensacionMateriales(completa
                ? EstadoDispensacionMateriales.COMPLETA
                : EstadoDispensacionMateriales.PARCIAL);
        ordenFabricacionRepo.save(orden);

        return DispensacionV2OrdenFabricacionDTOs.FinalizationResponse.builder()
                .ordenFabricacionId(ordenFabricacionId)
                .lote(loteFabricacion(orden).getBatchNumber())
                .transaccionId(transaccion.getTransaccionId())
                .build();
    }

    private DispensacionV2OrdenFabricacionDTOs.PreparationResponse buildOrdenFabricacionResponse(
            Long ordenFabricacionId,
            Integer areaId,
            Map<String, DispensacionV2MaterialEditableRequestDTO> overrides,
            boolean asignarLotes,
            boolean defaultChecked) {
        requireBatchRecordWorkflow();
        AreaOperativa area = requireArea(areaId);
        OrdenFabricacion orden = requireOrdenFabricacion(ordenFabricacionId);
        validateOrdenFabricacion(area, orden);
        BatchRecord record = requireBatchRecordFabricacion(orden);
        Map<String, MaterialAccumulator> requirements = buildMaterialesDesdeSnapshot(
                record.getRequerimientosMaterialesJson());
        Map<String, Double> historico = calcularHistoricoPorProducto(
                TransaccionAlmacen.TipoEntidadCausante.OD_OF,
                Math.toIntExact(ordenFabricacionId));
        Map<String, List<LoteStock>> stockCache = new HashMap<>();
        Map<String, Double> stockRestantePorLote = new HashMap<>();
        List<DispensacionV2MaterialDTO> materiales = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        for (MaterialAccumulator requirement : requirements.values()) {
            DispensacionV2MaterialDTO material = toMaterialDTO(
                    requirement,
                    historico.getOrDefault(requirement.productoId, 0.0),
                    overrides.get(requirement.productoId),
                    defaultChecked);
            if (asignarLotes && material.isChecked() && material.isInventareable()
                    && material.getCantidadADispensar() > TOLERANCE) {
                asignarLotesSugeridos(material, stockCache, stockRestantePorLote);
            }
            if (material.getWarning() != null && !material.getWarning().isBlank()) {
                warnings.add(material.getProductoId() + ": " + material.getWarning());
            }
            materiales.add(material);
        }
        return DispensacionV2OrdenFabricacionDTOs.PreparationResponse.builder()
                .orden(toOrdenFabricacionOption(orden))
                .area(toAreaDTO(area))
                .materiales(materiales)
                .warnings(warnings)
                .build();
    }

    private void requireBatchRecordWorkflow() {
        if (!masterDirectiveService.isBatchRecordWorkflowEnabled()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "El flujo de ordenes de fabricacion esta deshabilitado por directiva maestra.");
        }
    }

    private OrdenFabricacion requireOrdenFabricacion(Long id) {
        if (id == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "La ordenFabricacionId es obligatoria.");
        }
        return ordenFabricacionRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Orden de fabricacion no encontrada."));
    }

    private void validateOrdenFabricacion(AreaOperativa area, OrdenFabricacion orden) {
        if (orden.getEstado() != EstadoOrdenFabricacion.LIBERADA
                && orden.getEstado() != EstadoOrdenFabricacion.EN_EJECUCION) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "La OF debe estar liberada o en ejecucion para dispensar.");
        }
        if (!ordenFabricacionOperacionRepo
                .existsByOrdenFabricacion_OrdenFabricacionIdAndAreaOperativa_AreaId(
                        orden.getOrdenFabricacionId(), area.getAreaId())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "El area no pertenece al proceso congelado de la OF.");
        }
    }

    private BatchRecord requireBatchRecordFabricacion(OrdenFabricacion orden) {
        return batchRecordRepo.findByOrdenFabricacion_OrdenFabricacionId(
                        orden.getOrdenFabricacionId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "La OF no tiene un expediente con materiales congelados."));
    }

    private DispensacionV2OrdenFabricacionDTOs.Option toOrdenFabricacionOption(
            OrdenFabricacion orden) {
        return DispensacionV2OrdenFabricacionDTOs.Option.builder()
                .ordenFabricacionId(orden.getOrdenFabricacionId())
                .lote(loteFabricacion(orden).getBatchNumber())
                .semiTerminadoId(orden.getSemiTerminado().getProductoId())
                .semiTerminadoNombre(orden.getSemiTerminado().getNombre())
                .cantidadPlanificada(orden.getCantidadPlanificada().doubleValue())
                .unidadMedida(orden.getUnidadMedida())
                .estado(orden.getEstado().name())
                .estadoDispensacionMateriales(
                        orden.getEstadoDispensacionMateriales().name())
                .build();
    }

    private Lote loteFabricacion(OrdenFabricacion orden) {
        return loteRepo.findByOrdenFabricacion_OrdenFabricacionId(
                        orden.getOrdenFabricacionId()).stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT, "La OF no tiene lote de resultado."));
    }

    @Transactional(readOnly = true)
    public DispensacionV2PreparacionResponseDTO preparar(DispensacionV2PreparacionRequestDTO request) {
        log.info(
                "[DISP_V2][PREPARACION_START] areaId={} ordenCount={}",
                request != null ? request.getAreaId() : null,
                request != null && request.getOrdenes() != null ? request.getOrdenes().size() : null
        );
        AreaOperativa area = requireArea(request != null ? request.getAreaId() : null);
        List<OrdenInput> ordenes = normalizePreparacionOrdenes(request != null ? request.getOrdenes() : null);
        DispensacionV2PreparacionResponseDTO response =
                buildResponse(area, ordenes, Collections.emptyMap(), false, false);
        log.info(
                "[DISP_V2][PREPARACION_COMPLETE] areaId={} ordenCount={} totalMaterialCount={} warningCount={}",
                area.getAreaId(),
                response.getOrdenes().size(),
                response.getTotalesMateriales().size(),
                response.getWarnings().size()
        );
        return response;
    }

    @Transactional(readOnly = true)
    public DispensacionV2PreparacionResponseDTO asignarLotes(DispensacionV2AsignacionLotesRequestDTO request) {
        log.info(
                "[DISP_V2][ASIGNACION_START] areaId={} ordenCount={}",
                request != null ? request.getAreaId() : null,
                request != null && request.getOrdenes() != null ? request.getOrdenes().size() : null
        );
        AreaOperativa area = requireArea(request != null ? request.getAreaId() : null);
        List<OrdenInput> ordenes = normalizeAsignacionOrdenes(request != null ? request.getOrdenes() : null);
        Map<Integer, Map<String, DispensacionV2MaterialEditableRequestDTO>> overrides = buildOverrides(
                request != null ? request.getOrdenes() : null
        );
        DispensacionV2PreparacionResponseDTO response = buildResponse(area, ordenes, overrides, true, false);
        log.info(
                "[DISP_V2][ASIGNACION_COMPLETE] areaId={} ordenCount={} totalMaterialCount={} warningCount={}",
                area.getAreaId(),
                response.getOrdenes().size(),
                response.getTotalesMateriales().size(),
                response.getWarnings().size()
        );
        return response;
    }

    @Transactional
    public DispensacionV2FinalizacionResponseDTO finalizar(DispensacionV2FinalizacionRequestDTO request, User currentUser) {
        log.info(
                "[DISP_V2][FINALIZACION_START] areaId={} ordenCount={} userId={} username={}",
                request != null ? request.getAreaId() : null,
                request != null && request.getOrdenes() != null ? request.getOrdenes().size() : null,
                currentUser != null ? currentUser.getId() : null,
                currentUser != null ? currentUser.getUsername() : null
        );
        AreaOperativa area = requireArea(request != null ? request.getAreaId() : null);
        int currentUserId = requireCurrentUserId(currentUser);
        List<DispensacionV2FinalizacionOrdenRequestDTO> ordenesRequest = normalizeFinalizacionOrdenes(
                request != null ? request.getOrdenes() : null
        );

        List<FinalizacionDraft> drafts = new ArrayList<>();
        Map<StockDemandKey, Double> demandaPorLote = new HashMap<>();
        int totalItems = 0;

        for (DispensacionV2FinalizacionOrdenRequestDTO ordenRequest : ordenesRequest) {
            log.info(
                    "[DISP_V2][FINALIZACION_ORDER_START] ordenProduccionId={} mpsItemId={} mpsLotePlanificadoId={} materialCount={}",
                    ordenRequest.getOrdenProduccionId(),
                    ordenRequest.getMpsItemId(),
                    ordenRequest.getMpsLotePlanificadoId(),
                    ordenRequest.getMateriales() != null ? ordenRequest.getMateriales().size() : null
            );
            OrdenProduccion orden = requireOrden(ordenRequest.getOrdenProduccionId());
            validateOrden(area, orden);

            List<DispensacionItemDTO> items = buildFinalizacionItems(ordenRequest, demandaPorLote);
            log.info(
                    "[DISP_V2][FINALIZACION_ORDER_ITEMS] ordenProduccionId={} itemCount={}",
                    orden.getOrdenId(),
                    items.size()
            );
            if (items.isEmpty()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "La OP " + orden.getOrdenId()
                                + " debe tener al menos un material fisico o de consumo directo seleccionado."
                );
            }
            totalItems += items.size();

            DispensacionDTO dispensacionDTO = new DispensacionDTO();
            dispensacionDTO.setOrdenProduccionId(orden.getOrdenId());
            dispensacionDTO.setAreaOperativaDestinoId(area.getAreaId());
            dispensacionDTO.setUsuarioId(currentUserId);
            dispensacionDTO.setUsuarioRealizadorIds(Collections.singletonList(currentUserId));
            dispensacionDTO.setUsuarioAprobadorId(currentUserId);
            dispensacionDTO.setObservaciones(buildObservacionFinalizacionV2(ordenRequest, area));
            dispensacionDTO.setItems(items);
            drafts.add(new FinalizacionDraft(orden, dispensacionDTO));
        }

        if (totalItems == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Debe seleccionar al menos un material para finalizar.");
        }

        validateStockDisponible(demandaPorLote);
        log.info(
                "[DISP_V2][FINALIZACION_STOCK_VALIDATED] demandKeyCount={} totalItems={}",
                demandaPorLote.size(),
                totalItems
        );

        List<DispensacionV2FinalizacionOrdenResponseDTO> ordenesResponse = new ArrayList<>();
        for (FinalizacionDraft draft : drafts) {
            log.info(
                    "[DISP_V2][FINALIZACION_PERSIST_START] ordenProduccionId={} itemCount={} areaId={}",
                    draft.orden().getOrdenId(),
                    draft.dispensacionDTO().getItems() != null ? draft.dispensacionDTO().getItems().size() : null,
                    area.getAreaId()
            );
            TransaccionAlmacen transaccion = salidaAlmacenService.createDispensacion(
                    draft.dispensacionDTO(),
                    currentUser.getId()
            );
            if (masterDirectiveService.isBatchRecordWorkflowEnabled()) {
                batchRecordService.sincronizarConsumosOrdenProduccion(
                        draft.orden().getOrdenId());
            }
            log.info(
                    "[DISP_V2][FINALIZACION_PERSIST_COMPLETE] ordenProduccionId={} transaccionId={}",
                    draft.orden().getOrdenId(),
                    transaccion.getTransaccionId()
            );
            ordenesResponse.add(new DispensacionV2FinalizacionOrdenResponseDTO(
                    draft.orden().getOrdenId(),
                    draft.orden().getLoteAsignado(),
                    transaccion.getTransaccionId()
            ));
        }

        log.info(
                "[DISP_V2][FINALIZACION_COMPLETE] areaId={} ordenCount={} transactionCount={}",
                area.getAreaId(),
                ordenesRequest.size(),
                ordenesResponse.size()
        );
        return new DispensacionV2FinalizacionResponseDTO(toAreaDTO(area), ordenesResponse, new ArrayList<>());
    }

    @Transactional(readOnly = true)
    public DispensacionV2MaterialesRecetaResponseDTO prepararMaterialesReceta(DispensacionV2MaterialesRecetaRequestDTO request) {
        AreaOperativa area = requireArea(request != null ? request.getAreaId() : null);
        String productoId = request != null ? request.getProductoId() : null;
        double requestedCantidadBase = request != null && request.getCantidadBase() != null
                ? request.getCantidadBase()
                : 0;
        log.info(
                "[DISP_V2][MATERIALES_RECETA_START] areaId={} productoId={} cantidadBase={} tolerance={}",
                area.getAreaId(),
                productoId,
                requestedCantidadBase,
                TOLERANCE
        );
        if (productoId == null || productoId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El productoId es obligatorio.");
        }
        double cantidadBase = requestedCantidadBase;
        if (cantidadBase <= TOLERANCE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La cantidadBase debe ser mayor a cero.");
        }

        Producto producto = productoRepo.findById(productoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado."));
        if (!(producto instanceof Terminado terminado)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El producto seleccionado no es un terminado valido.");
        }

        List<String> warnings = new ArrayList<>();
        List<DispensacionV2MaterialDTO> materiales = buildMaterialesRequeridos(terminado, cantidadBase)
                .values()
                .stream()
                .map(material -> toMaterialDTO(material, 0, null, false))
                .peek(material -> {
                    if (material.getWarning() != null && !material.getWarning().isBlank()) {
                        warnings.add(material.getProductoNombre() + ": " + material.getWarning());
                    }
                })
                .toList();

        DispensacionV2MaterialesRecetaResponseDTO response = new DispensacionV2MaterialesRecetaResponseDTO(
                toAreaDTO(area),
                producto.getProductoId(),
                producto.getNombre(),
                cantidadBase,
                materiales,
                warnings
        );
        log.info(
                "[DISP_V2][MATERIALES_RECETA_COMPLETE] areaId={} productoId={} cantidadBase={} materialCount={} warningCount={}",
                area.getAreaId(),
                productoId,
                cantidadBase,
                materiales.size(),
                warnings.size()
        );
        materiales.forEach(material -> log.info(
                "[DISP_V2][MATERIALES_RECETA_RESULT] productoId={} nombre={} tipo={} unidad={} inventareable={} checked={} cantidadReceta={} cantidadADispensar={} warning={}",
                material.getProductoId(),
                material.getProductoNombre(),
                material.getTipoProducto(),
                material.getTipoUnidades(),
                material.isInventareable(),
                material.isChecked(),
                material.getCantidadReceta(),
                material.getCantidadADispensar(),
                material.getWarning()
        ));
        return response;
    }

    @Transactional(readOnly = true)
    public LoteDisponiblePageResponseDTO getLotesDisponiblesV2(String productoId, int page, int size) {
        log.info(
                "[DISP_V2][LOTES_DISPONIBLES_START] productoId={} requestedPage={} requestedSize={}",
                productoId,
                page,
                size
        );
        if (productoId == null || productoId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El productoId es obligatorio.");
        }

        Producto producto = productoRepo.findById(productoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado."));

        int safePage = Math.max(page, 0);
        int safeSize = Math.max(1, Math.min(size, MAX_LOTES_PAGE_SIZE));
        List<LoteRecomendadoDTO> lotes = findStockGeneral(productoId).stream()
                .filter(stock -> stock.stockDisponible() > TOLERANCE)
                .map(stock -> new LoteRecomendadoDTO(
                        stock.lote().getId(),
                        stock.lote().getBatchNumber(),
                        stock.lote().getProductionDate(),
                        stock.lote().getExpirationDate(),
                        stock.stockDisponible(),
                        0
                ))
                .toList();

        int start = safePage * safeSize;
        int end = Math.min(start + safeSize, lotes.size());
        List<LoteRecomendadoDTO> pageItems = start < lotes.size()
                ? lotes.subList(start, end)
                : new ArrayList<>();
        int totalPages = (int) Math.ceil((double) lotes.size() / safeSize);

        LoteDisponiblePageResponseDTO response = new LoteDisponiblePageResponseDTO(
                productoId,
                producto.getNombre(),
                pageItems,
                totalPages,
                lotes.size(),
                safePage,
                safeSize
        );
        log.info(
                "[DISP_V2][LOTES_DISPONIBLES_COMPLETE] productoId={} totalElements={} returnedElements={} page={} size={}",
                productoId,
                lotes.size(),
                pageItems.size(),
                safePage,
                safeSize
        );
        return response;
    }

    private DispensacionV2PreparacionResponseDTO buildResponse(
            AreaOperativa area,
            List<OrdenInput> ordenInputs,
            Map<Integer, Map<String, DispensacionV2MaterialEditableRequestDTO>> overrides,
            boolean asignarLotes,
            boolean defaultChecked
    ) {
        log.debug(
                "[DISP_V2][BUILD_RESPONSE_START] areaId={} ordenCount={} asignarLotes={} defaultChecked={} overrideOrderCount={}",
                area.getAreaId(),
                ordenInputs.size(),
                asignarLotes,
                defaultChecked,
                overrides.size()
        );
        DispensacionV2PreparacionResponseDTO response = new DispensacionV2PreparacionResponseDTO();
        response.setArea(toAreaDTO(area));

        Map<String, TotalAccumulator> totales = new LinkedHashMap<>();
        Map<String, List<LoteStock>> stockCache = new HashMap<>();
        Map<String, Double> stockRestantePorLote = new HashMap<>();

        List<DispensacionV2OrdenDTO> ordenes = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        for (OrdenInput input : ordenInputs) {
            OrdenProduccion orden = requireOrden(input.ordenProduccionId());
            validateOrden(area, orden);

            DispensacionV2OrdenDTO ordenDTO = buildOrdenDTO(
                    area,
                    input,
                    orden,
                    overrides.getOrDefault(orden.getOrdenId(), Collections.emptyMap()),
                    asignarLotes,
                    defaultChecked,
                    stockCache,
                    stockRestantePorLote
            );

            ordenDTO.getMateriales().forEach(material -> {
                totales.computeIfAbsent(material.getProductoId(), ignored -> new TotalAccumulator(material))
                        .add(material);
                if (material.getWarning() != null && !material.getWarning().isBlank()) {
                    ordenDTO.getWarnings().add(material.getProductoNombre() + ": " + material.getWarning());
                }
            });

            warnings.addAll(ordenDTO.getWarnings().stream()
                    .map(warning -> "OP " + ordenDTO.getOrdenProduccionId() + " - " + warning)
                    .toList());
            ordenes.add(ordenDTO);
        }

        response.setOrdenes(ordenes);
        response.setTotalesMateriales(totales.values().stream()
                .map(TotalAccumulator::toDTO)
                .toList());
        response.setWarnings(warnings);
        response.getTotalesMateriales().forEach(total -> log.info(
                "[DISP_V2][TOTAL_MATERIAL] productoId={} nombre={} unidad={} cantidadRecetaTotal={} cantidadADispensarTotal={} cantidadHistoricaTotal={} totalConHistorico={} excedeReceta={} warning={}",
                total.getProductoId(),
                total.getProductoNombre(),
                total.getTipoUnidades(),
                total.getCantidadRecetaTotal(),
                total.getCantidadADispensarTotal(),
                total.getCantidadHistoricaTotal(),
                total.getTotalConHistorico(),
                total.isExcedeReceta(),
                total.getWarning()
        ));
        log.debug(
                "[DISP_V2][BUILD_RESPONSE_COMPLETE] areaId={} ordenCount={} totalMaterialCount={} warningCount={}",
                area.getAreaId(),
                ordenes.size(),
                response.getTotalesMateriales().size(),
                warnings.size()
        );
        return response;
    }

    private DispensacionV2OrdenDTO buildOrdenDTO(
            AreaOperativa area,
            OrdenInput input,
            OrdenProduccion orden,
            Map<String, DispensacionV2MaterialEditableRequestDTO> overrides,
            boolean asignarLotes,
            boolean defaultChecked,
            Map<String, List<LoteStock>> stockCache,
            Map<String, Double> stockRestantePorLote
    ) {
        Producto producto = orden.getProducto();
        log.info(
                "[DISP_V2][ORDER_BUILD_START] ordenProduccionId={} estado={} productoTerminadoId={} cantidadProducir={} areaId={} asignarLotes={} overrideCount={}",
                orden.getOrdenId(),
                orden.getEstadoOrden(),
                producto != null ? producto.getProductoId() : null,
                orden.getCantidadProducir(),
                area.getAreaId(),
                asignarLotes,
                overrides.size()
        );
        if (!(producto instanceof Terminado terminado)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "La orden " + orden.getOrdenId() + " no tiene un producto terminado valido."
            );
        }

        Map<String, MaterialAccumulator> materiales = buildMaterialesRequeridos(orden, terminado);
        Map<String, Double> historico = calcularHistoricoPorProducto(orden.getOrdenId());

        List<DispensacionV2MaterialDTO> materialesDTO = new ArrayList<>();
        for (MaterialAccumulator material : materiales.values()) {
            DispensacionV2MaterialEditableRequestDTO override = overrides.get(material.productoId);
            DispensacionV2MaterialDTO dto = toMaterialDTO(
                    material,
                    historico.getOrDefault(material.productoId, 0.0),
                    override,
                    defaultChecked
            );
            log.info(
                    "[DISP_V2][ORDER_MATERIAL] ordenProduccionId={} productoId={} nombre={} tipo={} unidad={} inventareable={} overridePresent={} checked={} cantidadReceta={} cantidadHistorica={} cantidadADispensar={} totalConHistorico={} excedeReceta={} tolerance={} warning={}",
                    orden.getOrdenId(),
                    dto.getProductoId(),
                    dto.getProductoNombre(),
                    dto.getTipoProducto(),
                    dto.getTipoUnidades(),
                    dto.isInventareable(),
                    override != null,
                    dto.isChecked(),
                    dto.getCantidadReceta(),
                    dto.getCantidadHistorica(),
                    dto.getCantidadADispensar(),
                    dto.getTotalConHistorico(),
                    dto.isExcedeReceta(),
                    TOLERANCE,
                    dto.getWarning()
            );
            if (asignarLotes && dto.isChecked() && dto.isInventareable() && dto.getCantidadADispensar() > TOLERANCE) {
                asignarLotesSugeridos(dto, stockCache, stockRestantePorLote);
            } else if (asignarLotes) {
                log.debug(
                        "[DISP_V2][LOT_ASSIGNMENT_SKIPPED] ordenProduccionId={} productoId={} checked={} inventareable={} cantidadADispensar={} tolerance={}",
                        orden.getOrdenId(),
                        dto.getProductoId(),
                        dto.isChecked(),
                        dto.isInventareable(),
                        dto.getCantidadADispensar(),
                        TOLERANCE
                );
            }
            materialesDTO.add(dto);
        }

        DispensacionV2OrdenDTO dto = new DispensacionV2OrdenDTO();
        dto.setOrdenProduccionId(orden.getOrdenId());
        dto.setLoteAsignado(orden.getLoteAsignado());
        dto.setProductoTerminadoId(producto.getProductoId());
        dto.setProductoTerminadoNombre(producto.getNombre());
        dto.setCantidadProducir(orden.getCantidadProducir());
        dto.setMpsLotePlanificadoId(input.mpsLotePlanificadoId());
        dto.setMpsItemId(input.mpsItemId());
        dto.setArea(toAreaDTO(area));
        dto.setMateriales(materialesDTO);
        log.info(
                "[DISP_V2][ORDER_BUILD_COMPLETE] ordenProduccionId={} materialCount={} historicalProductCount={}",
                orden.getOrdenId(),
                materialesDTO.size(),
                historico.size()
        );
        return dto;
    }

    private Map<String, MaterialAccumulator> buildMaterialesRequeridos(Terminado terminado, double cantidadOrden) {
        Map<String, MaterialAccumulator> materiales = new LinkedHashMap<>();
        List<InsumoWithStockDTO> insumos = terminado.getInsumos() == null
                ? Collections.emptyList()
                : productoService.getInsumosWithStock(terminado.getProductoId());
        log.info(
                "[DISP_V2][RECIPE_BUILD_START] terminadoId={} terminadoNombre={} cantidadOrden={} rootInsumoCount={} hasCasePack={}",
                terminado.getProductoId(),
                terminado.getNombre(),
                cantidadOrden,
                insumos.size(),
                terminado.getCasePack() != null
        );
        aplanarInsumos(insumos, materiales, cantidadOrden, 1.0);
        agregarInsumosEmpaque(terminado, materiales, cantidadOrden);
        log.info(
                "[DISP_V2][RECIPE_BUILD_COMPLETE] terminadoId={} cantidadOrden={} materialCount={} materialIds={}",
                terminado.getProductoId(),
                cantidadOrden,
                materiales.size(),
                materiales.keySet()
        );
        return materiales;
    }

    private Map<String, MaterialAccumulator> buildMaterialesRequeridos(
            OrdenProduccion orden, Terminado terminado) {
        if (masterDirectiveService.isBatchRecordWorkflowEnabled()) {
            BatchRecord record = batchRecordRepo.findByOrdenProduccion_OrdenId(
                    orden.getOrdenId()).orElse(null);
            if (record != null && record.getRequerimientosMaterialesJson() != null) {
                return buildMaterialesDesdeSnapshot(record.getRequerimientosMaterialesJson());
            }
        }
        return buildMaterialesRequeridos(terminado, orden.getCantidadProducir());
    }

    private Map<String, MaterialAccumulator> buildMaterialesDesdeSnapshot(String json) {
        Map<String, MaterialAccumulator> materiales = new LinkedHashMap<>();
        for (MaterialRequirementSnapshotService.RequirementView requirement
                : materialRequirementSnapshotService.leer(json)) {
            addMaterial(
                    materiales,
                    requirement.productoId(),
                    requirement.productoNombre(),
                    normalizeUnidad(requirement.unidadMedida(), "U"),
                    requirement.tipoProducto(),
                    requirement.inventareable(),
                    requirement.consumoDirecto(),
                    requirement.cantidad().doubleValue());
        }
        return materiales;
    }

    private void aplanarInsumos(
            List<InsumoWithStockDTO> insumos,
            Map<String, MaterialAccumulator> materiales,
            double cantidadOrden,
            double multiplicadorActual
    ) {
        if (insumos == null) {
            return;
        }

        for (InsumoWithStockDTO insumo : insumos) {
            double cantidadTotal = insumo.getCantidadRequerida() * cantidadOrden * multiplicadorActual;
            boolean hasSubInsumos = insumo.getSubInsumos() != null && !insumo.getSubInsumos().isEmpty();
            log.debug(
                    "[DISP_V2][RECIPE_NODE] productoId={} nombre={} tipoProducto={} unidad={} inventareable={} cantidadRequerida={} cantidadOrden={} multiplicador={} cantidadCalculada={} hasSubInsumos={} subInsumoCount={}",
                    insumo.getProductoId(),
                    insumo.getProductoNombre(),
                    insumo.getTipoProducto(),
                    insumo.getTipoUnidades(),
                    insumo.getInventareable(),
                    insumo.getCantidadRequerida(),
                    cantidadOrden,
                    multiplicadorActual,
                    cantidadTotal,
                    hasSubInsumos,
                    insumo.getSubInsumos() != null ? insumo.getSubInsumos().size() : 0
            );
            boolean semiterminadoConOrdenPropia = hasSubInsumos
                    && masterDirectiveService.isBatchRecordWorkflowEnabled()
                    && productoRepo.findById(insumo.getProductoId())
                    .filter(SemiTerminado.class::isInstance)
                    .map(SemiTerminado.class::cast)
                    .map(SemiTerminado::isRequiereOrdenFabricacion)
                    .orElse(false);
            if (hasSubInsumos && !semiterminadoConOrdenPropia) {
                aplanarInsumos(
                        insumo.getSubInsumos(),
                        materiales,
                        cantidadOrden,
                        multiplicadorActual * insumo.getCantidadRequerida()
                );
                continue;
            }

            String tipoProducto = insumo.getTipoProducto() == InsumoWithStockDTO.TipoProducto.M
                    ? "MATERIAL"
                    : "SEMITERMINADO";
            addMaterial(
                    materiales,
                    insumo.getProductoId(),
                    insumo.getProductoNombre(),
                    normalizeUnidad(insumo.getTipoUnidades(), "KG"),
                    tipoProducto,
                    insumo.getInventareable() == null || insumo.getInventareable(),
                    Boolean.TRUE.equals(insumo.getConsumoDirecto()),
                    cantidadTotal
            );
        }
    }

    private void agregarInsumosEmpaque(
            Terminado terminado,
            Map<String, MaterialAccumulator> materiales,
            double cantidadOrden
    ) {
        CasePack casePack = terminado.getCasePack();
        if (casePack == null || casePack.getInsumosEmpaque() == null) {
            return;
        }

        boolean hasUnitsPerCase = casePack.getUnitsPerCase() != null && casePack.getUnitsPerCase() > 0;
        log.info(
                "[DISP_V2][PACKAGING_START] terminadoId={} cantidadOrden={} unitsPerCase={} hasUnitsPerCase={} packagingMaterialCount={}",
                terminado.getProductoId(),
                cantidadOrden,
                casePack.getUnitsPerCase(),
                hasUnitsPerCase,
                casePack.getInsumosEmpaque().size()
        );
        for (InsumoEmpaque insumoEmpaque : casePack.getInsumosEmpaque()) {
            Material material = insumoEmpaque.getMaterial();
            if (material == null) {
                log.warn(
                        "[DISP_V2][PACKAGING_SKIPPED] terminadoId={} reason=MATERIAL_NULL cantidadConfigurada={} unidadConfigurada={}",
                        terminado.getProductoId(),
                        insumoEmpaque.getCantidad(),
                        insumoEmpaque.getUom()
                );
                continue;
            }

            double cantidadTotal = hasUnitsPerCase
                    ? (cantidadOrden / casePack.getUnitsPerCase()) * insumoEmpaque.getCantidad()
                    : insumoEmpaque.getCantidad() * cantidadOrden;

            log.debug(
                    "[DISP_V2][PACKAGING_COMPONENT] terminadoId={} productoId={} nombre={} cantidadConfigurada={} cantidadOrden={} unitsPerCase={} cantidadCalculada={} unidadConfigurada={} unidadMaterial={} inventareable={}",
                    terminado.getProductoId(),
                    material.getProductoId(),
                    material.getNombre(),
                    insumoEmpaque.getCantidad(),
                    cantidadOrden,
                    casePack.getUnitsPerCase(),
                    cantidadTotal,
                    insumoEmpaque.getUom(),
                    material.getTipoUnidades(),
                    material.isInventareable()
            );
            addMaterial(
                    materiales,
                    material.getProductoId(),
                    material.getNombre(),
                    normalizeUnidad(insumoEmpaque.getUom(), normalizeUnidad(material.getTipoUnidades(), "U")),
                    "MATERIAL_EMPAQUE",
                    material.isInventareable(),
                    material.isConsumoDirecto(),
                    cantidadTotal
            );
        }
    }

    private void addMaterial(
            Map<String, MaterialAccumulator> materiales,
            String productoId,
            String productoNombre,
            String tipoUnidades,
            String tipoProducto,
            boolean inventareable,
            boolean consumoDirecto,
            double cantidad
    ) {
        if (productoId == null || productoId.isBlank() || cantidad <= TOLERANCE) {
            log.warn(
                    "[DISP_V2][MATERIAL_SKIPPED] productoId={} nombre={} tipo={} unidad={} inventareable={} cantidad={} tolerance={} reason={}",
                    productoId,
                    productoNombre,
                    tipoProducto,
                    tipoUnidades,
                    inventareable,
                    cantidad,
                    TOLERANCE,
                    productoId == null || productoId.isBlank() ? "PRODUCTO_ID_INVALIDO" : "CANTIDAD_NO_SUPERA_TOLERANCIA"
            );
            return;
        }

        MaterialAccumulator accumulator = materiales.get(productoId);
        boolean existing = accumulator != null;
        if (accumulator == null) {
            accumulator = new MaterialAccumulator(
                    productoId,
                    productoNombre,
                    tipoUnidades,
                    tipoProducto,
                    inventareable,
                    consumoDirecto
            );
            materiales.put(productoId, accumulator);
        }
        double previousQuantity = accumulator.cantidadReceta;
        boolean previousInventareable = accumulator.inventareable;
        String previousTipoProducto = accumulator.tipoProducto;
        accumulator.addCantidad(cantidad);
        accumulator.inventareable = accumulator.inventareable && inventareable;
        accumulator.consumoDirecto = accumulator.consumoDirecto || consumoDirecto;
        if ("MATERIAL_EMPAQUE".equals(accumulator.tipoProducto) && !"MATERIAL_EMPAQUE".equals(tipoProducto)) {
            accumulator.tipoProducto = tipoProducto;
        }
        log.info(
                "[DISP_V2][MATERIAL_ACCUMULATED] productoId={} nombre={} existing={} incomingTipo={} previousTipo={} resultingTipo={} incomingUnidad={} accumulatorUnidad={} incomingInventareable={} previousInventareable={} resultingInventareable={} previousCantidad={} addedCantidad={} resultingCantidad={}",
                productoId,
                productoNombre,
                existing,
                tipoProducto,
                previousTipoProducto,
                accumulator.tipoProducto,
                tipoUnidades,
                accumulator.tipoUnidades,
                inventareable,
                previousInventareable,
                accumulator.inventareable,
                previousQuantity,
                cantidad,
                accumulator.cantidadReceta
        );
    }

    private DispensacionV2MaterialDTO toMaterialDTO(
            MaterialAccumulator material,
            double cantidadHistorica,
            DispensacionV2MaterialEditableRequestDTO override,
            boolean defaultChecked
    ) {
        boolean suministrable = material.inventareable || material.consumoDirecto;
        boolean checked = override != null && override.getChecked() != null
                ? override.getChecked()
                : material.consumoDirecto || (defaultChecked && material.inventareable);
        if (!suministrable) {
            checked = false;
        }

        double cantidadADispensar = override != null && override.getCantidadADispensar() != null
                ? Math.max(override.getCantidadADispensar(), 0)
                : material.cantidadReceta;
        double cantidadActualEfectiva = checked ? cantidadADispensar : 0;
        double totalConHistorico = cantidadHistorica + cantidadActualEfectiva;
        boolean excede = totalConHistorico - material.cantidadReceta > TOLERANCE;

        DispensacionV2MaterialDTO dto = new DispensacionV2MaterialDTO();
        dto.setProductoId(material.productoId);
        dto.setProductoNombre(material.productoNombre);
        dto.setTipoUnidades(material.tipoUnidades);
        dto.setTipoProducto(material.tipoProducto);
        dto.setInventareable(material.inventareable);
        dto.setConsumoDirecto(material.consumoDirecto);
        dto.setChecked(checked);
        dto.setCantidadReceta(material.cantidadReceta);
        dto.setCantidadADispensar(cantidadADispensar);
        dto.setCantidadHistorica(cantidadHistorica);
        dto.setTotalConHistorico(totalConHistorico);
        dto.setExcedeReceta(excede);
        if (!material.inventareable && !material.consumoDirecto) {
            dto.setWarning("Material no inventariable sin consumo directo; no participa en la dispensacion.");
        } else if (excede) {
            dto.setWarning("La suma de historico y dispensacion actual excede la receta.");
        }
        log.debug(
                "[DISP_V2][MATERIAL_DTO] productoId={} inventareable={} defaultChecked={} overridePresent={} overrideChecked={} overrideCantidad={} checked={} cantidadReceta={} cantidadHistorica={} cantidadADispensar={} cantidadActualEfectiva={} totalConHistorico={} deltaReceta={} tolerance={} excedeReceta={} warning={}",
                material.productoId,
                material.inventareable,
                defaultChecked,
                override != null,
                override != null ? override.getChecked() : null,
                override != null ? override.getCantidadADispensar() : null,
                checked,
                material.cantidadReceta,
                cantidadHistorica,
                cantidadADispensar,
                cantidadActualEfectiva,
                totalConHistorico,
                totalConHistorico - material.cantidadReceta,
                TOLERANCE,
                excede,
                dto.getWarning()
        );
        return dto;
    }

    private void asignarLotesSugeridos(
            DispensacionV2MaterialDTO material,
            Map<String, List<LoteStock>> stockCache,
            Map<String, Double> stockRestantePorLote
    ) {
        double cantidadRestante = material.getCantidadADispensar();
        List<LoteStock> stockLotes = stockCache.computeIfAbsent(material.getProductoId(), this::findStockGeneral);
        List<DispensacionV2LoteOrigenDTO> lotes = new ArrayList<>();
        log.info(
                "[DISP_V2][LOT_ASSIGNMENT_START] productoId={} cantidadSolicitada={} candidateCount={} cachedStockProductCount={}",
                material.getProductoId(),
                material.getCantidadADispensar(),
                stockLotes.size(),
                stockCache.size()
        );

        for (LoteStock stock : stockLotes) {
            if (cantidadRestante <= TOLERANCE) {
                log.debug(
                        "[DISP_V2][LOT_ASSIGNMENT_STOP] productoId={} cantidadRestante={} tolerance={} reason=DEMAND_COVERED",
                        material.getProductoId(),
                        cantidadRestante,
                        TOLERANCE
                );
                break;
            }

            String key = material.getProductoId() + "|" + stock.lote().getId();
            double restanteEnLote = stockRestantePorLote.getOrDefault(key, stock.stockDisponible());
            if (restanteEnLote <= TOLERANCE) {
                log.debug(
                        "[DISP_V2][LOT_CANDIDATE_SKIPPED] productoId={} loteId={} batchNumber={} stockOriginal={} stockRestanteCompartido={} tolerance={}",
                        material.getProductoId(),
                        stock.lote().getId(),
                        stock.lote().getBatchNumber(),
                        stock.stockDisponible(),
                        restanteEnLote,
                        TOLERANCE
                );
                continue;
            }

            double cantidadAsignada = Math.min(cantidadRestante, restanteEnLote);
            double cantidadRestanteBefore = cantidadRestante;
            lotes.add(new DispensacionV2LoteOrigenDTO(
                    stock.lote().getId(),
                    stock.lote().getBatchNumber(),
                    stock.lote().getProductionDate(),
                    stock.lote().getExpirationDate(),
                    restanteEnLote,
                    cantidadAsignada,
                    true
            ));
            stockRestantePorLote.put(key, restanteEnLote - cantidadAsignada);
            cantidadRestante -= cantidadAsignada;
            log.info(
                    "[DISP_V2][LOT_ASSIGNED] productoId={} loteId={} batchNumber={} expirationDate={} stockOriginal={} stockRestanteBefore={} demandBefore={} cantidadAsignada={} stockRestanteAfter={} demandAfter={}",
                    material.getProductoId(),
                    stock.lote().getId(),
                    stock.lote().getBatchNumber(),
                    stock.lote().getExpirationDate(),
                    stock.stockDisponible(),
                    restanteEnLote,
                    cantidadRestanteBefore,
                    cantidadAsignada,
                    restanteEnLote - cantidadAsignada,
                    cantidadRestante
            );
        }

        material.setLotesOrigen(lotes);
        if (cantidadRestante > TOLERANCE) {
            appendWarning(material, "Stock insuficiente para cubrir " + round(cantidadRestante) + " " + material.getTipoUnidades() + ".");
            log.warn(
                    "[DISP_V2][LOT_ASSIGNMENT_SHORTAGE] productoId={} cantidadSolicitada={} cantidadRestante={} assignedLotCount={} tolerance={} warning={}",
                    material.getProductoId(),
                    material.getCantidadADispensar(),
                    cantidadRestante,
                    lotes.size(),
                    TOLERANCE,
                    material.getWarning()
            );
        } else {
            log.info(
                    "[DISP_V2][LOT_ASSIGNMENT_COMPLETE] productoId={} cantidadSolicitada={} cantidadRestante={} assignedLotCount={}",
                    material.getProductoId(),
                    material.getCantidadADispensar(),
                    cantidadRestante,
                    lotes.size()
            );
        }
    }

    private List<LoteStock> findStockGeneral(String productoId) {
        log.info(
                "[DISP_V2][STOCK_QUERY_START] productoId={} almacen={} tolerance={}",
                productoId,
                Movimiento.Almacen.GENERAL,
                TOLERANCE
        );
        List<Object[]> rows = transaccionAlmacenRepo
                .findLotesWithStockByProductoIdAndAlmacenOrderByExpirationDate(
                        productoId,
                        Movimiento.Almacen.GENERAL
                );
        log.info(
                "[DISP_V2][STOCK_QUERY_ROWS] productoId={} rowCount={}",
                productoId,
                rows.size()
        );

        List<LoteStock> allStock = rows.stream()
                .map(this::toLoteStock)
                .toList();
        allStock.forEach(stock -> log.info(
                "[DISP_V2][STOCK_ROW] productoId={} loteId={} batchNumber={} productionDate={} expirationDate={} stockDisponible={} eligible={} tolerance={}",
                productoId,
                stock.lote().getId(),
                stock.lote().getBatchNumber(),
                stock.lote().getProductionDate(),
                stock.lote().getExpirationDate(),
                stock.stockDisponible(),
                stock.stockDisponible() > TOLERANCE,
                TOLERANCE
        ));

        List<LoteStock> eligibleStock = allStock.stream()
                .filter(stock -> stock.stockDisponible() > TOLERANCE)
                .toList();
        log.info(
                "[DISP_V2][STOCK_QUERY_COMPLETE] productoId={} rowCount={} eligibleCount={}",
                productoId,
                allStock.size(),
                eligibleStock.size()
        );
        return eligibleStock;
    }

    private LoteStock toLoteStock(Object[] row) {
        if (row == null || row.length < 2 || !(row[0] instanceof Lote lote) || !(row[1] instanceof Number stock)) {
            log.error(
                    "[DISP_V2][STOCK_ROW_INVALID] rowNull={} rowLength={} firstType={} secondType={}",
                    row == null,
                    row != null ? row.length : null,
                    row != null && row.length > 0 && row[0] != null ? row[0].getClass().getName() : null,
                    row != null && row.length > 1 && row[1] != null ? row[1].getClass().getName() : null
            );
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Respuesta invalida al consultar lotes disponibles.");
        }
        return new LoteStock(lote, stock.doubleValue());
    }

    private Map<String, Double> calcularHistoricoPorProducto(int ordenProduccionId) {
        return calcularHistoricoPorProducto(
                TransaccionAlmacen.TipoEntidadCausante.OD, ordenProduccionId);
    }

    private Map<String, Double> calcularHistoricoPorProducto(
            TransaccionAlmacen.TipoEntidadCausante tipoEntidadCausante,
            int entidadCausanteId) {
        Map<String, Double> historico = new HashMap<>();
        List<TransaccionAlmacen> transacciones = transaccionAlmacenHeaderRepo
                .findByTipoEntidadCausanteAndIdEntidadCausanteWithMovimientos(
                        tipoEntidadCausante,
                        entidadCausanteId
                );

        log.info(
                "[DISP_V2][HISTORY_QUERY] tipoEntidad={} entidadCausanteId={} transactionCount={}",
                tipoEntidadCausante,
                entidadCausanteId,
                transacciones.size()
        );
        for (TransaccionAlmacen transaccion : transacciones) {
            if (transaccion.getMovimientosTransaccion() == null) {
                log.debug(
                        "[DISP_V2][HISTORY_TRANSACTION_SKIPPED] ordenProduccionId={} transaccionId={} reason=MOVIMIENTOS_NULL",
                        entidadCausanteId,
                        transaccion.getTransaccionId()
                );
                continue;
            }
            transaccion.getMovimientosTransaccion().forEach(movimiento -> {
                if (movimiento.getProducto() == null || movimiento.getProducto().getProductoId() == null) {
                    log.warn(
                            "[DISP_V2][HISTORY_MOVEMENT_SKIPPED] ordenProduccionId={} transaccionId={} movimientoId={} reason=PRODUCTO_NULL",
                            entidadCausanteId,
                            transaccion.getTransaccionId(),
                            movimiento.getMovimientoId()
                    );
                    return;
                }
                log.debug(
                        "[DISP_V2][HISTORY_MOVEMENT] ordenProduccionId={} transaccionId={} movimientoId={} productoId={} cantidad={} absoluteCantidad={} loteId={} almacen={} tipoMovimiento={}",
                        entidadCausanteId,
                        transaccion.getTransaccionId(),
                        movimiento.getMovimientoId(),
                        movimiento.getProducto().getProductoId(),
                        movimiento.getCantidad(),
                        Math.abs(movimiento.getCantidad()),
                        movimiento.getLote() != null ? movimiento.getLote().getId() : null,
                        movimiento.getAlmacen(),
                        movimiento.getTipoMovimiento()
                );
                if (movimiento.getTipoMovimiento() == Movimiento.TipoMovimiento.DISPENSACION
                        || movimiento.getTipoMovimiento() == Movimiento.TipoMovimiento.CONSUMO) {
                    historico.merge(
                            movimiento.getProducto().getProductoId(),
                            Math.abs(movimiento.getCantidad()),
                            Double::sum
                    );
                }
            });
        }
        historico.forEach((productoId, cantidad) -> log.info(
                "[DISP_V2][HISTORY_TOTAL] ordenProduccionId={} productoId={} cantidadHistorica={}",
                entidadCausanteId,
                productoId,
                cantidad
        ));
        return historico;
    }

    private AreaOperativa requireArea(Integer areaId) {
        log.debug("[DISP_V2][AREA_VALIDATE] areaId={}", areaId);
        if (areaId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El areaId es obligatorio.");
        }
        if (areaId == AreaOperativaInitializer.ALMACEN_GENERAL_ID) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Almacen General no es un area destino valida.");
        }
        AreaOperativa area = areaProduccionRepo.findById(areaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Area operativa no encontrada."));
        log.debug(
                "[DISP_V2][AREA_FOUND] areaId={} nombre={}",
                area.getAreaId(),
                area.getNombre()
        );
        return area;
    }

    private OrdenProduccion requireOrden(Integer ordenProduccionId) {
        log.debug("[DISP_V2][ORDER_LOOKUP] ordenProduccionId={}", ordenProduccionId);
        if (ordenProduccionId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cada orden debe tener ordenProduccionId.");
        }
        OrdenProduccion orden = ordenProduccionRepo.findById(ordenProduccionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Orden de produccion no encontrada: " + ordenProduccionId));
        log.debug(
                "[DISP_V2][ORDER_FOUND] ordenProduccionId={} estado={} loteAsignado={} productoId={} cantidadProducir={}",
                orden.getOrdenId(),
                orden.getEstadoOrden(),
                orden.getLoteAsignado(),
                orden.getProducto() != null ? orden.getProducto().getProductoId() : null,
                orden.getCantidadProducir()
        );
        return orden;
    }

    private void validateOrden(AreaOperativa area, OrdenProduccion orden) {
        log.debug(
                "[DISP_V2][ORDER_VALIDATE] ordenProduccionId={} estado={} areaId={}",
                orden.getOrdenId(),
                orden.getEstadoOrden(),
                area.getAreaId()
        );
        if (orden.getEstadoOrden() == 2 || orden.getEstadoOrden() == -1) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "No se puede preparar dispensacion para una orden terminada o cancelada: " + orden.getOrdenId()
            );
        }

        boolean areaPerteneceAlSeguimiento = seguimientoOrdenAreaService.tieneAreaOperativaEnSeguimiento(
                orden.getOrdenId(),
                area.getAreaId()
        );
        log.info(
                "[DISP_V2][ORDER_AREA_MEMBERSHIP] ordenProduccionId={} areaId={} belongsToTracking={}",
                orden.getOrdenId(),
                area.getAreaId(),
                areaPerteneceAlSeguimiento
        );
        if (!areaPerteneceAlSeguimiento) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "El area operativa no pertenece al seguimiento de la orden " + orden.getOrdenId()
            );
        }
    }

    private List<OrdenInput> normalizePreparacionOrdenes(List<DispensacionV2OrdenRequestDTO> ordenes) {
        if (ordenes == null || ordenes.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Debe seleccionar al menos una orden de produccion.");
        }

        Set<Integer> seen = new HashSet<>();
        List<OrdenInput> result = new ArrayList<>();
        for (DispensacionV2OrdenRequestDTO orden : ordenes) {
            if (orden == null || orden.getOrdenProduccionId() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cada orden debe tener ordenProduccionId.");
            }
            if (!seen.add(orden.getOrdenProduccionId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La orden " + orden.getOrdenProduccionId() + " esta duplicada.");
            }
            result.add(new OrdenInput(orden.getOrdenProduccionId(), orden.getMpsLotePlanificadoId(), orden.getMpsItemId()));
        }
        return result;
    }

    private List<OrdenInput> normalizeAsignacionOrdenes(List<DispensacionV2AsignacionOrdenRequestDTO> ordenes) {
        if (ordenes == null || ordenes.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Debe seleccionar al menos una orden de produccion.");
        }

        Set<Integer> seen = new HashSet<>();
        List<OrdenInput> result = new ArrayList<>();
        for (DispensacionV2AsignacionOrdenRequestDTO orden : ordenes) {
            if (orden == null || orden.getOrdenProduccionId() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cada orden debe tener ordenProduccionId.");
            }
            if (!seen.add(orden.getOrdenProduccionId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La orden " + orden.getOrdenProduccionId() + " esta duplicada.");
            }
            result.add(new OrdenInput(orden.getOrdenProduccionId(), orden.getMpsLotePlanificadoId(), orden.getMpsItemId()));
        }
        return result;
    }

    private Map<Integer, Map<String, DispensacionV2MaterialEditableRequestDTO>> buildOverrides(
            List<DispensacionV2AsignacionOrdenRequestDTO> ordenes
    ) {
        Map<Integer, Map<String, DispensacionV2MaterialEditableRequestDTO>> overrides = new HashMap<>();
        if (ordenes == null) {
            return overrides;
        }

        for (DispensacionV2AsignacionOrdenRequestDTO orden : ordenes) {
            if (orden == null || orden.getOrdenProduccionId() == null || orden.getMateriales() == null) {
                log.debug(
                        "[DISP_V2][OVERRIDE_ORDER_SKIPPED] ordenNull={} ordenProduccionId={} materialesNull={}",
                        orden == null,
                        orden != null ? orden.getOrdenProduccionId() : null,
                        orden == null || orden.getMateriales() == null
                );
                continue;
            }
            Map<String, DispensacionV2MaterialEditableRequestDTO> byProducto = new HashMap<>();
            for (DispensacionV2MaterialEditableRequestDTO material : orden.getMateriales()) {
                if (material != null && material.getProductoId() != null && !material.getProductoId().isBlank()) {
                    DispensacionV2MaterialEditableRequestDTO previous =
                            byProducto.put(material.getProductoId(), material);
                    log.info(
                            "[DISP_V2][OVERRIDE_INPUT] ordenProduccionId={} productoId={} checked={} cantidadADispensar={} duplicateInPayload={}",
                            orden.getOrdenProduccionId(),
                            material.getProductoId(),
                            material.getChecked(),
                            material.getCantidadADispensar(),
                            previous != null
                    );
                } else {
                    log.warn(
                            "[DISP_V2][OVERRIDE_SKIPPED] ordenProduccionId={} materialNull={} productoId={}",
                            orden.getOrdenProduccionId(),
                            material == null,
                            material != null ? material.getProductoId() : null
                    );
                }
            }
            overrides.put(orden.getOrdenProduccionId(), byProducto);
        }
        return overrides;
    }

    private int requireCurrentUserId(User currentUser) {
        if (currentUser == null || currentUser.getId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario autenticado no encontrado.");
        }
        return Math.toIntExact(currentUser.getId());
    }

    private List<DispensacionV2FinalizacionOrdenRequestDTO> normalizeFinalizacionOrdenes(
            List<DispensacionV2FinalizacionOrdenRequestDTO> ordenes
    ) {
        if (ordenes == null || ordenes.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Debe seleccionar al menos una orden de produccion.");
        }

        Set<Integer> seen = new HashSet<>();
        List<DispensacionV2FinalizacionOrdenRequestDTO> result = new ArrayList<>();
        for (DispensacionV2FinalizacionOrdenRequestDTO orden : ordenes) {
            if (orden == null || orden.getOrdenProduccionId() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cada orden debe tener ordenProduccionId.");
            }
            if (!seen.add(orden.getOrdenProduccionId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La orden " + orden.getOrdenProduccionId() + " esta duplicada.");
            }
            result.add(orden);
        }
        return result;
    }

    private List<DispensacionItemDTO> buildFinalizacionItems(
            DispensacionV2FinalizacionOrdenRequestDTO orden,
            Map<StockDemandKey, Double> demandaPorLote
    ) {
        List<DispensacionItemDTO> items = new ArrayList<>();
        log.debug(
                "[DISP_V2][FINAL_ITEMS_START] ordenProduccionId={} materialCount={}",
                orden.getOrdenProduccionId(),
                orden.getMateriales() != null ? orden.getMateriales().size() : null
        );
        if (orden.getMateriales() == null) {
            return items;
        }

        for (DispensacionV2FinalizacionMaterialRequestDTO material : orden.getMateriales()) {
            if (material == null || !Boolean.TRUE.equals(material.getChecked())) {
                log.debug(
                        "[DISP_V2][FINAL_MATERIAL_SKIPPED] ordenProduccionId={} productoId={} materialNull={} checked={} reason=NOT_SELECTED",
                        orden.getOrdenProduccionId(),
                        material != null ? material.getProductoId() : null,
                        material == null,
                        material != null ? material.getChecked() : null
                );
                continue;
            }

            String productoId = material.getProductoId();
            log.info(
                    "[DISP_V2][FINAL_MATERIAL_START] ordenProduccionId={} productoId={} checked={} cantidadADispensar={} loteCount={}",
                    orden.getOrdenProduccionId(),
                    productoId,
                    material.getChecked(),
                    material.getCantidadADispensar(),
                    material.getLotesOrigen() != null ? material.getLotesOrigen().size() : null
            );
            if (productoId == null || productoId.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cada material seleccionado debe tener productoId.");
            }

            Producto producto = productoRepo.findById(productoId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado: " + productoId));
            log.info(
                    "[DISP_V2][FINAL_PRODUCT_FOUND] ordenProduccionId={} productoId={} entityType={} nombre={} inventareable={} unidad={}",
                    orden.getOrdenProduccionId(),
                    productoId,
                    producto.getClass().getSimpleName(),
                    producto.getNombre(),
                    producto.isInventareable(),
                    producto.getTipoUnidades()
            );
            boolean consumoDirecto = producto instanceof Material materialProducto
                    && materialProducto.isConsumoDirecto();
            if (producto.isInventareable() && consumoDirecto) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "El material " + productoId
                                + " tiene una configuracion invalida: inventariable y consumo directo."
                );
            }
            if (!producto.isInventareable() && !consumoDirecto) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "El producto " + productoId
                                + " no es inventariable ni esta configurado para consumo directo."
                );
            }

            double cantidadADispensar = material.getCantidadADispensar() != null ? material.getCantidadADispensar() : 0;
            if (cantidadADispensar <= TOLERANCE) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "La cantidad a dispensar para el producto " + productoId + " debe ser mayor a cero."
                );
            }

            if (consumoDirecto) {
                if (material.getLotesOrigen() != null && !material.getLotesOrigen().isEmpty()) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "El consumo directo del producto " + productoId + " no debe incluir lotes origen."
                    );
                }
                items.add(new DispensacionItemDTO(productoId, cantidadADispensar, null));
                continue;
            }

            if (material.getLotesOrigen() == null || material.getLotesOrigen().isEmpty()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "El producto " + productoId + " debe tener lotes origen para finalizar."
                );
            }

            double totalLotes = 0;
            for (DispensacionV2FinalizacionLoteRequestDTO lote : material.getLotesOrigen()) {
                if (lote == null || lote.getLoteId() == null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cada lote origen debe tener loteId.");
                }
                double cantidadAsignada = lote.getCantidadAsignada() != null ? lote.getCantidadAsignada() : 0;
                if (cantidadAsignada <= TOLERANCE) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "Cada lote origen del producto " + productoId + " debe tener cantidad asignada mayor a cero."
                    );
                }

                totalLotes += cantidadAsignada;
                StockDemandKey key = new StockDemandKey(productoId, lote.getLoteId());
                double previousDemand = demandaPorLote.getOrDefault(key, 0.0);
                demandaPorLote.merge(key, cantidadAsignada, Double::sum);
                items.add(new DispensacionItemDTO(productoId, cantidadAsignada, Math.toIntExact(lote.getLoteId())));
                log.info(
                        "[DISP_V2][FINAL_LOT_INPUT] ordenProduccionId={} productoId={} loteId={} cantidadAsignada={} runningMaterialLotTotal={} previousAggregateDemand={} resultingAggregateDemand={}",
                        orden.getOrdenProduccionId(),
                        productoId,
                        lote.getLoteId(),
                        cantidadAsignada,
                        totalLotes,
                        previousDemand,
                        previousDemand + cantidadAsignada
                );
            }

            double lotDifference = totalLotes - cantidadADispensar;
            double absoluteLotDifference = Math.abs(lotDifference);
            boolean matchesWithinTolerance = absoluteLotDifference <= TOLERANCE;
            log.info(
                    "[DISP_V2][FINAL_MATERIAL_SUM] ordenProduccionId={} productoId={} cantidadADispensar={} totalLotes={} difference={} absoluteDifference={} tolerance={} matchesWithinTolerance={}",
                    orden.getOrdenProduccionId(),
                    productoId,
                    cantidadADispensar,
                    totalLotes,
                    lotDifference,
                    absoluteLotDifference,
                    TOLERANCE,
                    matchesWithinTolerance
            );
            if (!matchesWithinTolerance) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "La suma de lotes del producto " + productoId + " (" + round(totalLotes)
                        + ") no coincide con la cantidad a dispensar (" + round(cantidadADispensar) + ")."
                );
            }
        }

        log.debug(
                "[DISP_V2][FINAL_ITEMS_COMPLETE] ordenProduccionId={} itemCount={}",
                orden.getOrdenProduccionId(),
                items.size()
        );
        return items;
    }

    private void validateStockDisponible(Map<StockDemandKey, Double> demandaPorLote) {
        log.info(
                "[DISP_V2][FINAL_STOCK_VALIDATION_START] demandKeyCount={} tolerance={}",
                demandaPorLote.size(),
                TOLERANCE
        );
        for (Map.Entry<StockDemandKey, Double> entry : demandaPorLote.entrySet()) {
            StockDemandKey key = entry.getKey();
            double solicitado = entry.getValue();
            Double stock = transaccionAlmacenRepo.findTotalCantidadByProductoIdAndLoteIdAndAlmacen(
                    key.productoId(),
                    key.loteId(),
                    Movimiento.Almacen.GENERAL
            );
            double stockDisponible = stock != null ? stock : 0;
            double difference = solicitado - stockDisponible;
            boolean sufficient = difference <= TOLERANCE;
            log.info(
                    "[DISP_V2][FINAL_STOCK_CHECK] productoId={} loteId={} almacen={} solicitado={} stockQueryResult={} stockDisponible={} difference={} tolerance={} sufficient={}",
                    key.productoId(),
                    key.loteId(),
                    Movimiento.Almacen.GENERAL,
                    solicitado,
                    stock,
                    stockDisponible,
                    difference,
                    TOLERANCE,
                    sufficient
            );
            if (!sufficient) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Stock insuficiente para producto " + key.productoId()
                                + " lote " + key.loteId()
                                + ". Disponible: " + round(stockDisponible)
                                + ", solicitado: " + round(solicitado) + "."
                );
            }
        }
    }

    private String buildObservacionFinalizacionV2(
            DispensacionV2FinalizacionOrdenRequestDTO orden,
            AreaOperativa area
    ) {
        StringBuilder observacion = new StringBuilder("Dispensacion v2");
        observacion.append(" hacia ").append(area.getNombre()).append(" (ID ").append(area.getAreaId()).append(")");
        if (orden.getMpsItemId() != null) {
            observacion.append(". MPS item ").append(orden.getMpsItemId());
        }
        if (orden.getMpsLotePlanificadoId() != null) {
            observacion.append(". Lote MPS ").append(orden.getMpsLotePlanificadoId());
        }
        return observacion.toString();
    }

    private DispensacionV2AreaDTO toAreaDTO(AreaOperativa area) {
        return new DispensacionV2AreaDTO(area.getAreaId(), area.getNombre());
    }

    private String normalizeUnidad(String unidad, String fallback) {
        return unidad != null && !unidad.isBlank() ? unidad : fallback;
    }

    private void appendWarning(DispensacionV2MaterialDTO material, String warning) {
        if (warning == null || warning.isBlank()) {
            return;
        }
        if (material.getWarning() == null || material.getWarning().isBlank()) {
            material.setWarning(warning);
            return;
        }
        material.setWarning(material.getWarning() + " " + warning);
    }

    private double round(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }

    private record OrdenInput(Integer ordenProduccionId, Long mpsLotePlanificadoId, Long mpsItemId) {
    }

    private record FinalizacionDraft(OrdenProduccion orden, DispensacionDTO dispensacionDTO) {
    }

    private record LoteStock(Lote lote, double stockDisponible) {
    }

    private record StockDemandKey(String productoId, Long loteId) {
    }

    private static class MaterialAccumulator {
        private final String productoId;
        private final String productoNombre;
        private final String tipoUnidades;
        private String tipoProducto;
        private boolean inventareable;
        private boolean consumoDirecto;
        private double cantidadReceta;

        private MaterialAccumulator(
                String productoId,
                String productoNombre,
                String tipoUnidades,
                String tipoProducto,
                boolean inventareable,
                boolean consumoDirecto
        ) {
            this.productoId = productoId;
            this.productoNombre = productoNombre;
            this.tipoUnidades = tipoUnidades;
            this.tipoProducto = tipoProducto;
            this.inventareable = inventareable;
            this.consumoDirecto = consumoDirecto;
        }

        private void addCantidad(double cantidad) {
            this.cantidadReceta += cantidad;
        }
    }

    private static class TotalAccumulator {
        private final String productoId;
        private final String productoNombre;
        private final String tipoUnidades;
        private double cantidadRecetaTotal;
        private double cantidadADispensarTotal;
        private double cantidadHistoricaTotal;

        private TotalAccumulator(DispensacionV2MaterialDTO material) {
            this.productoId = material.getProductoId();
            this.productoNombre = material.getProductoNombre();
            this.tipoUnidades = material.getTipoUnidades();
        }

        private void add(DispensacionV2MaterialDTO material) {
            this.cantidadRecetaTotal += material.getCantidadReceta();
            this.cantidadHistoricaTotal += material.getCantidadHistorica();
            if (material.isChecked() && (material.isInventareable() || material.isConsumoDirecto())) {
                this.cantidadADispensarTotal += material.getCantidadADispensar();
            }
        }

        private DispensacionV2TotalMaterialDTO toDTO() {
            double total = cantidadHistoricaTotal + cantidadADispensarTotal;
            boolean excede = total - cantidadRecetaTotal > TOLERANCE;
            return new DispensacionV2TotalMaterialDTO(
                    productoId,
                    productoNombre,
                    tipoUnidades,
                    cantidadRecetaTotal,
                    cantidadADispensarTotal,
                    cantidadHistoricaTotal,
                    total,
                    excede,
                    excede ? "La suma global de historico y dispensacion actual excede la receta." : null
            );
        }
    }
}
