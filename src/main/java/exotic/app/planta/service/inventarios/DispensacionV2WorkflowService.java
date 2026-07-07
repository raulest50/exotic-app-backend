package exotic.app.planta.service.inventarios;

import exotic.app.planta.config.initializers.AreaOperativaInitializer;
import exotic.app.planta.model.inventarios.Lote;
import exotic.app.planta.model.inventarios.Movimiento;
import exotic.app.planta.model.inventarios.TransaccionAlmacen;
import exotic.app.planta.model.inventarios.dto.*;
import exotic.app.planta.model.organizacion.AreaOperativa;
import exotic.app.planta.model.producto.Material;
import exotic.app.planta.model.producto.Producto;
import exotic.app.planta.model.producto.Terminado;
import exotic.app.planta.model.producto.dto.InsumoWithStockDTO;
import exotic.app.planta.model.producto.manufacturing.packaging.CasePack;
import exotic.app.planta.model.producto.manufacturing.packaging.InsumoEmpaque;
import exotic.app.planta.model.produccion.OrdenProduccion;
import exotic.app.planta.repo.inventarios.TransaccionAlmacenHeaderRepo;
import exotic.app.planta.repo.inventarios.TransaccionAlmacenRepo;
import exotic.app.planta.repo.produccion.OrdenProduccionRepo;
import exotic.app.planta.repo.producto.ProductoRepo;
import exotic.app.planta.repo.producto.procesos.AreaProduccionRepo;
import exotic.app.planta.service.produccion.SeguimientoOrdenAreaService;
import exotic.app.planta.service.productos.ProductoService;
import lombok.RequiredArgsConstructor;
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

@Service
@RequiredArgsConstructor
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

    @Transactional(readOnly = true)
    public DispensacionV2PreparacionResponseDTO preparar(DispensacionV2PreparacionRequestDTO request) {
        AreaOperativa area = requireArea(request != null ? request.getAreaId() : null);
        List<OrdenInput> ordenes = normalizePreparacionOrdenes(request != null ? request.getOrdenes() : null);
        return buildResponse(area, ordenes, Collections.emptyMap(), false, false);
    }

    @Transactional(readOnly = true)
    public DispensacionV2PreparacionResponseDTO asignarLotes(DispensacionV2AsignacionLotesRequestDTO request) {
        AreaOperativa area = requireArea(request != null ? request.getAreaId() : null);
        List<OrdenInput> ordenes = normalizeAsignacionOrdenes(request != null ? request.getOrdenes() : null);
        Map<Integer, Map<String, DispensacionV2MaterialEditableRequestDTO>> overrides = buildOverrides(
                request != null ? request.getOrdenes() : null
        );
        return buildResponse(area, ordenes, overrides, true, false);
    }

    @Transactional(readOnly = true)
    public LoteDisponiblePageResponseDTO getLotesDisponiblesV2(String productoId, int page, int size) {
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

        return new LoteDisponiblePageResponseDTO(
                productoId,
                producto.getNombre(),
                pageItems,
                totalPages,
                lotes.size(),
                safePage,
                safeSize
        );
    }

    private DispensacionV2PreparacionResponseDTO buildResponse(
            AreaOperativa area,
            List<OrdenInput> ordenInputs,
            Map<Integer, Map<String, DispensacionV2MaterialEditableRequestDTO>> overrides,
            boolean asignarLotes,
            boolean defaultChecked
    ) {
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
        if (!(producto instanceof Terminado terminado)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "La orden " + orden.getOrdenId() + " no tiene un producto terminado valido."
            );
        }

        Map<String, MaterialAccumulator> materiales = buildMaterialesRequeridos(terminado, orden.getCantidadProducir());
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
            if (asignarLotes && dto.isChecked() && dto.isInventareable() && dto.getCantidadADispensar() > TOLERANCE) {
                asignarLotesSugeridos(dto, stockCache, stockRestantePorLote);
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
        return dto;
    }

    private Map<String, MaterialAccumulator> buildMaterialesRequeridos(Terminado terminado, double cantidadOrden) {
        Map<String, MaterialAccumulator> materiales = new LinkedHashMap<>();
        List<InsumoWithStockDTO> insumos = terminado.getInsumos() == null
                ? Collections.emptyList()
                : productoService.getInsumosWithStock(terminado.getProductoId());
        aplanarInsumos(insumos, materiales, cantidadOrden, 1.0);
        agregarInsumosEmpaque(terminado, materiales, cantidadOrden);
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
            if (insumo.getSubInsumos() != null && !insumo.getSubInsumos().isEmpty()) {
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
        for (InsumoEmpaque insumoEmpaque : casePack.getInsumosEmpaque()) {
            Material material = insumoEmpaque.getMaterial();
            if (material == null) {
                continue;
            }

            double cantidadTotal = hasUnitsPerCase
                    ? (cantidadOrden / casePack.getUnitsPerCase()) * insumoEmpaque.getCantidad()
                    : insumoEmpaque.getCantidad() * cantidadOrden;

            addMaterial(
                    materiales,
                    material.getProductoId(),
                    material.getNombre(),
                    normalizeUnidad(insumoEmpaque.getUom(), normalizeUnidad(material.getTipoUnidades(), "U")),
                    "MATERIAL_EMPAQUE",
                    material.isInventareable(),
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
            double cantidad
    ) {
        if (productoId == null || productoId.isBlank() || cantidad <= TOLERANCE) {
            return;
        }

        MaterialAccumulator accumulator = materiales.computeIfAbsent(
                productoId,
                ignored -> new MaterialAccumulator(productoId, productoNombre, tipoUnidades, tipoProducto, inventareable)
        );
        accumulator.addCantidad(cantidad);
        accumulator.inventareable = accumulator.inventareable && inventareable;
        if ("MATERIAL_EMPAQUE".equals(accumulator.tipoProducto) && !"MATERIAL_EMPAQUE".equals(tipoProducto)) {
            accumulator.tipoProducto = tipoProducto;
        }
    }

    private DispensacionV2MaterialDTO toMaterialDTO(
            MaterialAccumulator material,
            double cantidadHistorica,
            DispensacionV2MaterialEditableRequestDTO override,
            boolean defaultChecked
    ) {
        boolean checked = override != null && override.getChecked() != null
                ? override.getChecked()
                : defaultChecked && material.inventareable;
        if (!material.inventareable) {
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
        dto.setChecked(checked);
        dto.setCantidadReceta(material.cantidadReceta);
        dto.setCantidadADispensar(cantidadADispensar);
        dto.setCantidadHistorica(cantidadHistorica);
        dto.setTotalConHistorico(totalConHistorico);
        dto.setExcedeReceta(excede);
        if (!material.inventareable) {
            dto.setWarning("Material no inventariable; no requiere salida de lote.");
        } else if (excede) {
            dto.setWarning("La suma de historico y dispensacion actual excede la receta.");
        }
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

        for (LoteStock stock : stockLotes) {
            if (cantidadRestante <= TOLERANCE) {
                break;
            }

            String key = material.getProductoId() + "|" + stock.lote().getId();
            double restanteEnLote = stockRestantePorLote.getOrDefault(key, stock.stockDisponible());
            if (restanteEnLote <= TOLERANCE) {
                continue;
            }

            double cantidadAsignada = Math.min(cantidadRestante, restanteEnLote);
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
        }

        material.setLotesOrigen(lotes);
        if (cantidadRestante > TOLERANCE) {
            appendWarning(material, "Stock insuficiente para cubrir " + round(cantidadRestante) + " " + material.getTipoUnidades() + ".");
        }
    }

    private List<LoteStock> findStockGeneral(String productoId) {
        return transaccionAlmacenRepo
                .findLotesWithStockByProductoIdAndAlmacenOrderByExpirationDate(
                        productoId,
                        Movimiento.Almacen.GENERAL
                )
                .stream()
                .map(this::toLoteStock)
                .filter(stock -> stock.stockDisponible() > TOLERANCE)
                .toList();
    }

    private LoteStock toLoteStock(Object[] row) {
        if (row == null || row.length < 2 || !(row[0] instanceof Lote lote) || !(row[1] instanceof Number stock)) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Respuesta invalida al consultar lotes disponibles.");
        }
        return new LoteStock(lote, stock.doubleValue());
    }

    private Map<String, Double> calcularHistoricoPorProducto(int ordenProduccionId) {
        Map<String, Double> historico = new HashMap<>();
        List<TransaccionAlmacen> transacciones = transaccionAlmacenHeaderRepo
                .findByTipoEntidadCausanteAndIdEntidadCausanteWithMovimientos(
                        TransaccionAlmacen.TipoEntidadCausante.OD,
                        ordenProduccionId
                );

        for (TransaccionAlmacen transaccion : transacciones) {
            if (transaccion.getMovimientosTransaccion() == null) {
                continue;
            }
            transaccion.getMovimientosTransaccion().forEach(movimiento -> {
                if (movimiento.getProducto() == null || movimiento.getProducto().getProductoId() == null) {
                    return;
                }
                historico.merge(
                        movimiento.getProducto().getProductoId(),
                        Math.abs(movimiento.getCantidad()),
                        Double::sum
                );
            });
        }
        return historico;
    }

    private AreaOperativa requireArea(Integer areaId) {
        if (areaId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El areaId es obligatorio.");
        }
        if (areaId == AreaOperativaInitializer.ALMACEN_GENERAL_ID) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Almacen General no es un area destino valida.");
        }
        return areaProduccionRepo.findById(areaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Area operativa no encontrada."));
    }

    private OrdenProduccion requireOrden(Integer ordenProduccionId) {
        if (ordenProduccionId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cada orden debe tener ordenProduccionId.");
        }
        return ordenProduccionRepo.findById(ordenProduccionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Orden de produccion no encontrada: " + ordenProduccionId));
    }

    private void validateOrden(AreaOperativa area, OrdenProduccion orden) {
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
                continue;
            }
            Map<String, DispensacionV2MaterialEditableRequestDTO> byProducto = new HashMap<>();
            for (DispensacionV2MaterialEditableRequestDTO material : orden.getMateriales()) {
                if (material != null && material.getProductoId() != null && !material.getProductoId().isBlank()) {
                    byProducto.put(material.getProductoId(), material);
                }
            }
            overrides.put(orden.getOrdenProduccionId(), byProducto);
        }
        return overrides;
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

    private record LoteStock(Lote lote, double stockDisponible) {
    }

    private static class MaterialAccumulator {
        private final String productoId;
        private final String productoNombre;
        private final String tipoUnidades;
        private String tipoProducto;
        private boolean inventareable;
        private double cantidadReceta;

        private MaterialAccumulator(
                String productoId,
                String productoNombre,
                String tipoUnidades,
                String tipoProducto,
                boolean inventareable
        ) {
            this.productoId = productoId;
            this.productoNombre = productoNombre;
            this.tipoUnidades = tipoUnidades;
            this.tipoProducto = tipoProducto;
            this.inventareable = inventareable;
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
            if (material.isChecked() && material.isInventareable()) {
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
