package exotic.app.planta.service.bi.inventario;

import exotic.app.planta.model.bi.dto.InformeInventarioDTO;
import exotic.app.planta.model.bi.dto.PaginaInformeInventarioDTO;
import exotic.app.planta.model.inventarios.Movimiento;
import exotic.app.planta.model.inventarios.TransaccionAlmacen;
import exotic.app.planta.repo.compras.ItemOrdenCompraRepo;
import exotic.app.planta.repo.inventarios.TransaccionAlmacenRepo;
import exotic.app.planta.repo.produccion.OrdenProduccionRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
class PendientesInventarioAssembler {
    private static final int PENDING_WAREHOUSE_RECEIPT_STATUS = 2;
    private static final Movimiento.Almacen GENERAL = Movimiento.Almacen.GENERAL;
    private static final List<TransaccionAlmacen.TipoEntidadCausante> PRODUCTION_CAUSES =
            List.of(
                    TransaccionAlmacen.TipoEntidadCausante.OD,
                    TransaccionAlmacen.TipoEntidadCausante.OD_RA);
    private static final TransaccionAlmacen.TipoEntidadCausante NORMAL_DISPENSATION_CAUSE =
            TransaccionAlmacen.TipoEntidadCausante.OD;

    private final ItemOrdenCompraRepo purchaseItemRepo;
    private final OrdenProduccionRepo productionOrderRepo;
    private final TransaccionAlmacenRepo movementRepo;

    InformeInventarioDTO.OcmPendientesDTO buildPendingPurchaseOrders() {
        return summarizePendingPurchaseOrders(
                loadPendingPurchaseOrders().values());
    }

    List<InformeInventarioDTO.OcmDTO> getAllPendingPurchaseOrders() {
        return loadPendingPurchaseOrders().values().stream()
                .map(PendingPurchaseOrderAccumulator::toDto)
                .toList();
    }

    private Map<Integer, PendingPurchaseOrderAccumulator> loadPendingPurchaseOrders() {
        List<ItemOrdenCompraRepo.PendingPurchaseItemProjection> rows =
                purchaseItemRepo.findPendingRowsForBi(PENDING_WAREHOUSE_RECEIPT_STATUS);
        if (rows.isEmpty()) return Map.of();

        Set<Integer> orderIds = new HashSet<>();
        rows.forEach(row -> orderIds.add(row.getOcmId()));
        Map<PurchaseMaterialKey, Double> received = loadReceivedQuantities(orderIds);
        return assemblePendingOrders(rows, received);
    }

    PaginaInformeInventarioDTO<InformeInventarioDTO.OcmDTO> getPendingPurchaseOrdersPage(
            int page,
            int size
    ) {
        Page<Integer> orderIds = purchaseItemRepo.findPendingOrderIdsForBi(
                PENDING_WAREHOUSE_RECEIPT_STATUS,
                GENERAL,
                Movimiento.TipoMovimiento.COMPRA,
                TransaccionAlmacen.TipoEntidadCausante.OCM,
                PageRequest.of(page, size));
        if (orderIds.isEmpty()) return emptyPage(orderIds);

        List<ItemOrdenCompraRepo.PendingPurchaseItemProjection> rows =
                purchaseItemRepo.findPendingRowsForBiByOrderIds(
                        PENDING_WAREHOUSE_RECEIPT_STATUS,
                        orderIds.getContent());
        Map<PurchaseMaterialKey, Double> received = loadReceivedQuantities(
                new HashSet<>(orderIds.getContent()));
        Map<Integer, PendingPurchaseOrderAccumulator> orders =
                assemblePendingOrders(rows, received);
        List<InformeInventarioDTO.OcmDTO> items = orderIds.getContent().stream()
                .map(orders::get)
                .filter(accumulator -> accumulator != null)
                .map(PendingPurchaseOrderAccumulator::toDto)
                .toList();
        return toPage(items, orderIds);
    }

    InformeInventarioDTO.MaterialDirectoOpDTO buildOpenProductionOrderMaterial() {
        List<TransaccionAlmacenRepo.OpenProductionMaterialProjection> rows =
                movementRepo.findOpenProductionMaterialTotals(
                        GENERAL,
                        Movimiento.TipoMovimiento.DISPENSACION,
                        PRODUCTION_CAUSES);
        return summarizeOpenProductionOrders(assembleOpenOrders(rows).values());
    }

    PaginaInformeInventarioDTO<InformeInventarioDTO.OpMaterialDTO>
    getOpenProductionOrderMaterialPage(int page, int size) {
        Page<Integer> orderIds = productionOrderRepo.findOpenOrderIdsWithDispensationsForBi(
                GENERAL,
                Movimiento.TipoMovimiento.DISPENSACION,
                PRODUCTION_CAUSES,
                PageRequest.of(page, size));
        if (orderIds.isEmpty()) return emptyPage(orderIds);

        List<TransaccionAlmacenRepo.OpenProductionMaterialProjection> rows =
                movementRepo.findOpenProductionMaterialTotalsByOrderIds(
                        GENERAL,
                        Movimiento.TipoMovimiento.DISPENSACION,
                        PRODUCTION_CAUSES,
                        orderIds.getContent());
        Map<Integer, OpenProductionOrderAccumulator> orders = assembleOpenOrders(rows);
        List<InformeInventarioDTO.OpMaterialDTO> items = orderIds.getContent().stream()
                .map(orders::get)
                .filter(accumulator -> accumulator != null)
                .map(OpenProductionOrderAccumulator::toDto)
                .toList();
        return toPage(items, orderIds);
    }

    InformeInventarioDTO.WipMaterialEstimadoDTO buildWipMaterialEstimate() {
        return summarizeWipMaterial(getAllWipMaterialSnapshot());
    }

    PaginaInformeInventarioDTO<InformeInventarioDTO.OpWipMaterialDTO>
    getWipMaterialEstimatePage(int page, int size) {
        Page<Integer> orderIds = productionOrderRepo.findOpenOrderIdsWithWipMaterialForBi(
                GENERAL,
                Movimiento.TipoMovimiento.DISPENSACION,
                Movimiento.TipoMovimiento.CONSUMO,
                PRODUCTION_CAUSES,
                NORMAL_DISPENSATION_CAUSE,
                PageRequest.of(page, size));
        if (orderIds.isEmpty()) return emptyPage(orderIds);

        List<TransaccionAlmacenRepo.OpenProductionMaterialDetailProjection> rows =
                movementRepo.findOpenWipMaterialDetailsByOrderIds(
                        GENERAL,
                        Movimiento.TipoMovimiento.DISPENSACION,
                        Movimiento.TipoMovimiento.CONSUMO,
                        PRODUCTION_CAUSES,
                        NORMAL_DISPENSATION_CAUSE,
                        orderIds.getContent());
        Map<Integer, MaterialOpOrderAccumulator> orders =
                assembleMaterialOpOrders(rows);
        List<InformeInventarioDTO.OpWipMaterialDTO> items = orderIds.getContent().stream()
                .map(orders::get)
                .filter(accumulator -> accumulator != null)
                .map(MaterialOpOrderAccumulator::toWipDto)
                .toList();
        return toPage(items, orderIds);
    }

    MaterialOpSnapshot getAllDispensedMaterialSnapshot() {
        return assembleMaterialOpSnapshot(
                movementRepo.findOpenProductionMaterialDetails(
                        GENERAL,
                        Movimiento.TipoMovimiento.DISPENSACION,
                        PRODUCTION_CAUSES),
                false);
    }

    MaterialOpSnapshot getAllWipMaterialSnapshot() {
        return assembleMaterialOpSnapshot(
                movementRepo.findOpenWipMaterialDetails(
                        GENERAL,
                        Movimiento.TipoMovimiento.DISPENSACION,
                        Movimiento.TipoMovimiento.CONSUMO,
                        PRODUCTION_CAUSES,
                        NORMAL_DISPENSATION_CAUSE),
                true);
    }

    private Map<Integer, PendingPurchaseOrderAccumulator> assemblePendingOrders(
            List<ItemOrdenCompraRepo.PendingPurchaseItemProjection> rows,
            Map<PurchaseMaterialKey, Double> receivedByPurchaseAndMaterial
    ) {
        Map<Integer, PendingPurchaseOrderAccumulator> accumulators = new LinkedHashMap<>();
        for (ItemOrdenCompraRepo.PendingPurchaseItemProjection row : rows) {
            PurchaseMaterialKey key = new PurchaseMaterialKey(
                    row.getOcmId(),
                    row.getProductoId());
            double availableReceipt = receivedByPurchaseAndMaterial.getOrDefault(key, 0d);
            double appliedReceipt = Math.min(row.getCantidad(), availableReceipt);
            receivedByPurchaseAndMaterial.put(
                    key,
                    Math.max(0, availableReceipt - appliedReceipt));
            if (appliedReceipt >= row.getCantidad()) continue;

            accumulators.computeIfAbsent(
                            row.getOcmId(),
                            ignored -> new PendingPurchaseOrderAccumulator(row))
                    .addLine(row, appliedReceipt);
        }
        return accumulators;
    }

    private Map<Integer, OpenProductionOrderAccumulator> assembleOpenOrders(
            List<TransaccionAlmacenRepo.OpenProductionMaterialProjection> rows
    ) {
        Map<Integer, OpenProductionOrderAccumulator> accumulators = new LinkedHashMap<>();
        for (TransaccionAlmacenRepo.OpenProductionMaterialProjection row : rows) {
            accumulators.computeIfAbsent(
                            row.getOpId(),
                            ignored -> new OpenProductionOrderAccumulator(row))
                    .addDispensation(row);
        }
        return accumulators;
    }

    private Map<Integer, MaterialOpOrderAccumulator> assembleMaterialOpOrders(
            List<TransaccionAlmacenRepo.OpenProductionMaterialDetailProjection> rows
    ) {
        Map<Integer, MaterialOpOrderAccumulator> accumulators = new LinkedHashMap<>();
        for (TransaccionAlmacenRepo.OpenProductionMaterialDetailProjection row : rows) {
            accumulators.computeIfAbsent(
                            row.getOpId(),
                            ignored -> new MaterialOpOrderAccumulator(row))
                    .add(row);
        }
        return accumulators;
    }

    private MaterialOpSnapshot assembleMaterialOpSnapshot(
            List<TransaccionAlmacenRepo.OpenProductionMaterialDetailProjection> rows,
            boolean wip
    ) {
        Map<Integer, MaterialOpOrderAccumulator> orders =
                assembleMaterialOpOrders(rows);
        List<MaterialOpLineSnapshot> lines = rows.stream()
                .map(this::toMaterialOpLine)
                .toList();
        return new MaterialOpSnapshot(
                orders.values().stream()
                        .map(order -> order.toSnapshot(wip))
                        .toList(),
                lines);
    }

    private MaterialOpLineSnapshot toMaterialOpLine(
            TransaccionAlmacenRepo.OpenProductionMaterialDetailProjection row
    ) {
        BigDecimal cost = row.getCosto();
        boolean costAvailable = cost != null && cost.signum() > 0;
        double unitCost = costAvailable ? cost.doubleValue() : 0;
        double quantity = row.getCantidad();
        return new MaterialOpLineSnapshot(
                row.getOpId(),
                row.getLote(),
                row.getEstado(),
                row.getFechaReferencia(),
                row.getFechaPrimerMovimiento(),
                row.getProductoId(),
                row.getProductoNombre(),
                InventarioBiUtils.normalizeUnit(row.getUnidadMedida()),
                resolveOrigin(row),
                quantity,
                unitCost,
                costAvailable,
                quantity * unitCost);
    }

    private MaterialOrigin resolveOrigin(
            TransaccionAlmacenRepo.OpenProductionMaterialDetailProjection row
    ) {
        if (row.getTipoMovimiento() == Movimiento.TipoMovimiento.CONSUMO) {
            return MaterialOrigin.CONSUMO_DIRECTO;
        }
        if (row.getCausa() == TransaccionAlmacen.TipoEntidadCausante.OD_RA) {
            return MaterialOrigin.REPOSICION_AVERIA;
        }
        return MaterialOrigin.DISPENSACION_NORMAL;
    }

    private Map<PurchaseMaterialKey, Double> loadReceivedQuantities(Set<Integer> orderIds) {
        if (orderIds.isEmpty()) return new HashMap<>();

        Map<PurchaseMaterialKey, Double> received = new HashMap<>();
        movementRepo.findReceiptTotalsByCauseAndEntities(
                        GENERAL,
                        Movimiento.TipoMovimiento.COMPRA,
                        TransaccionAlmacen.TipoEntidadCausante.OCM,
                        orderIds)
                .forEach(row -> received.put(
                        new PurchaseMaterialKey(row.getEntityId(), row.getProductId()),
                        row.getQuantity()));
        return received;
    }

    private InformeInventarioDTO.OcmPendientesDTO summarizePendingPurchaseOrders(
            Collection<PendingPurchaseOrderAccumulator> accumulators
    ) {
        UnitQuantityAccumulator quantities = new UnitQuantityAccumulator();
        Set<String> productIds = new HashSet<>();
        double totalValue = 0;
        for (PendingPurchaseOrderAccumulator accumulator : accumulators) {
            quantities.addAll(accumulator.pendingQuantities);
            productIds.addAll(accumulator.productIds);
            totalValue += accumulator.pendingValue;
        }
        return InformeInventarioDTO.OcmPendientesDTO.builder()
                .ordenes(accumulators.size())
                .referencias(productIds.size())
                .cantidadesPorUnidad(quantities.toDto())
                .valorPendienteSinIva(totalValue)
                .build();
    }

    private InformeInventarioDTO.MaterialDirectoOpDTO summarizeOpenProductionOrders(
            Collection<OpenProductionOrderAccumulator> accumulators
    ) {
        UnitQuantityAccumulator quantities = new UnitQuantityAccumulator();
        Set<String> productIds = new HashSet<>();
        double totalValue = 0;
        for (OpenProductionOrderAccumulator accumulator : accumulators) {
            quantities.addAll(accumulator.dispensedQuantities);
            productIds.addAll(accumulator.productIds);
            totalValue += accumulator.estimatedValue;
        }
        return InformeInventarioDTO.MaterialDirectoOpDTO.builder()
                .ordenes(accumulators.size())
                .referencias(productIds.size())
                .cantidadesPorUnidad(quantities.toDto())
                .valorEstimado(totalValue)
                .build();
    }

    private InformeInventarioDTO.MaterialDirectoOpDTO emptyOpenProductionOrderMaterial() {
        return new InformeInventarioDTO.MaterialDirectoOpDTO(0, 0, List.of(), 0);
    }

    private InformeInventarioDTO.WipMaterialEstimadoDTO summarizeWipMaterial(
            MaterialOpSnapshot snapshot
    ) {
        UnitQuantityAccumulator quantities = new UnitQuantityAccumulator();
        snapshot.orders().forEach(order ->
                order.cantidadesPorUnidad().forEach(quantity ->
                        quantities.add(quantity.unidadMedida(), quantity.cantidad())));
        int references = (int) snapshot.lines().stream()
                .map(MaterialOpLineSnapshot::productoId)
                .distinct()
                .count();
        double estimatedValue = snapshot.orders().stream()
                .mapToDouble(MaterialOpOrderSnapshot::valorEstimado)
                .sum();
        return InformeInventarioDTO.WipMaterialEstimadoDTO.builder()
                .ordenes(snapshot.orders().size())
                .referencias(references)
                .cantidadesPorUnidad(quantities.toDto())
                .valorEstimado(estimatedValue)
                .build();
    }

    private static <T> PaginaInformeInventarioDTO<T> toPage(
            List<T> items,
            Page<?> page
    ) {
        return new PaginaInformeInventarioDTO<>(
                items,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast());
    }

    private static <T> PaginaInformeInventarioDTO<T> emptyPage(Page<?> page) {
        return toPage(List.of(), page);
    }

    private record PurchaseMaterialKey(int purchaseOrderId, String materialId) {
    }

    private static final class PendingPurchaseOrderAccumulator {
        private final int purchaseOrderId;
        private final LocalDateTime issueDate;
        private final String supplierName;
        private final Set<String> productIds = new HashSet<>();
        private final UnitQuantityAccumulator pendingQuantities =
                new UnitQuantityAccumulator();
        private final List<InformeInventarioDTO.LineaOcmDTO> lines = new ArrayList<>();
        private double pendingValue;

        private PendingPurchaseOrderAccumulator(
                ItemOrdenCompraRepo.PendingPurchaseItemProjection firstItem
        ) {
            purchaseOrderId = firstItem.getOcmId();
            issueDate = firstItem.getFechaEmision();
            supplierName = firstItem.getProveedor();
        }

        void addLine(
                ItemOrdenCompraRepo.PendingPurchaseItemProjection item,
                double appliedReceipt
        ) {
            double pendingQuantity = Math.max(item.getCantidad() - appliedReceipt, 0);
            double lineValue = pendingQuantity * item.getPrecioUnitario();
            String unit = InventarioBiUtils.normalizeUnit(item.getUnidadMedida());

            productIds.add(item.getProductoId());
            pendingQuantities.add(unit, pendingQuantity);
            pendingValue += lineValue;
            lines.add(InformeInventarioDTO.LineaOcmDTO.builder()
                    .itemId(item.getItemId())
                    .productoId(item.getProductoId())
                    .productoNombre(item.getProductoNombre())
                    .unidadMedida(unit)
                    .ordenado(item.getCantidad())
                    .recibidoAplicado(appliedReceipt)
                    .pendiente(pendingQuantity)
                    .precioUnitarioSinIva(item.getPrecioUnitario())
                    .valorPendienteSinIva(lineValue)
                    .build());
        }

        InformeInventarioDTO.OcmDTO toDto() {
            return InformeInventarioDTO.OcmDTO.builder()
                    .ocmId(purchaseOrderId)
                    .fechaEmision(issueDate)
                    .proveedor(supplierName)
                    .referencias(productIds.size())
                    .cantidadesPorUnidad(pendingQuantities.toDto())
                    .valorPendienteSinIva(pendingValue)
                    .lineas(List.copyOf(lines))
                    .build();
        }
    }

    private static final class OpenProductionOrderAccumulator {
        private final int productionOrderId;
        private final String batch;
        private final int status;
        private final LocalDateTime referenceDate;
        private final Set<String> productIds = new HashSet<>();
        private final UnitQuantityAccumulator dispensedQuantities =
                new UnitQuantityAccumulator();
        private double estimatedValue;

        private OpenProductionOrderAccumulator(
                TransaccionAlmacenRepo.OpenProductionMaterialProjection firstRow
        ) {
            productionOrderId = firstRow.getOpId();
            batch = firstRow.getLote();
            status = firstRow.getEstado();
            referenceDate = firstRow.getFechaReferencia();
        }

        void addDispensation(
                TransaccionAlmacenRepo.OpenProductionMaterialProjection row
        ) {
            double quantity = row.getCantidad();
            productIds.add(row.getProductoId());
            dispensedQuantities.add(
                    InventarioBiUtils.normalizeUnit(row.getUnidadMedida()),
                    quantity);
            BigDecimal cost = row.getCosto();
            if (cost != null && cost.signum() > 0) {
                estimatedValue += quantity * cost.doubleValue();
            }
        }

        InformeInventarioDTO.OpMaterialDTO toDto() {
            return InformeInventarioDTO.OpMaterialDTO.builder()
                    .opId(productionOrderId)
                    .lote(batch)
                    .estado(status)
                    .fechaReferencia(referenceDate)
                    .referencias(productIds.size())
                    .cantidadesPorUnidad(dispensedQuantities.toDto())
                    .valorEstimado(estimatedValue)
                    .build();
        }
    }

    private static final class MaterialOpOrderAccumulator {
        private final int productionOrderId;
        private final String batch;
        private final int status;
        private final LocalDateTime referenceDate;
        private LocalDateTime wipStartDate;
        private final Set<String> productIds = new HashSet<>();
        private final UnitQuantityAccumulator quantities =
                new UnitQuantityAccumulator();
        private double estimatedValue;

        private MaterialOpOrderAccumulator(
                TransaccionAlmacenRepo.OpenProductionMaterialDetailProjection firstRow
        ) {
            productionOrderId = firstRow.getOpId();
            batch = firstRow.getLote();
            status = firstRow.getEstado();
            referenceDate = firstRow.getFechaReferencia();
            wipStartDate = firstRow.getFechaPrimerMovimiento();
        }

        void add(
                TransaccionAlmacenRepo.OpenProductionMaterialDetailProjection row
        ) {
            productIds.add(row.getProductoId());
            quantities.add(
                    InventarioBiUtils.normalizeUnit(row.getUnidadMedida()),
                    row.getCantidad());
            BigDecimal cost = row.getCosto();
            if (cost != null && cost.signum() > 0) {
                estimatedValue += row.getCantidad() * cost.doubleValue();
            }
            if (row.getFechaPrimerMovimiento() != null
                    && (wipStartDate == null
                    || row.getFechaPrimerMovimiento().isBefore(wipStartDate))) {
                wipStartDate = row.getFechaPrimerMovimiento();
            }
        }

        InformeInventarioDTO.OpWipMaterialDTO toWipDto() {
            return InformeInventarioDTO.OpWipMaterialDTO.builder()
                    .opId(productionOrderId)
                    .lote(batch)
                    .estado(status)
                    .fechaInicioWip(wipStartDate)
                    .referencias(productIds.size())
                    .cantidadesPorUnidad(quantities.toDto())
                    .valorEstimado(estimatedValue)
                    .build();
        }

        MaterialOpOrderSnapshot toSnapshot(boolean wip) {
            return new MaterialOpOrderSnapshot(
                    productionOrderId,
                    batch,
                    status,
                    wip ? wipStartDate : referenceDate,
                    productIds.size(),
                    quantities.toDto(),
                    estimatedValue);
        }
    }

    enum MaterialOrigin {
        DISPENSACION_NORMAL("Dispensación normal"),
        REPOSICION_AVERIA("Reposición por avería"),
        CONSUMO_DIRECTO("Consumo directo");

        private final String label;

        MaterialOrigin(String label) {
            this.label = label;
        }

        String label() {
            return label;
        }
    }

    record MaterialOpLineSnapshot(
            int opId,
            String lote,
            int estado,
            LocalDateTime fechaReferencia,
            LocalDateTime fechaPrimerMovimiento,
            String productoId,
            String productoNombre,
            String unidadMedida,
            MaterialOrigin origen,
            double cantidad,
            double costoUnitario,
            boolean costoDisponible,
            double valorEstimado
    ) {
    }

    record MaterialOpOrderSnapshot(
            int opId,
            String lote,
            int estado,
            LocalDateTime fechaReferencia,
            int referencias,
            List<InformeInventarioDTO.CantidadUnidadDTO> cantidadesPorUnidad,
            double valorEstimado
    ) {
    }

    record MaterialOpSnapshot(
            List<MaterialOpOrderSnapshot> orders,
            List<MaterialOpLineSnapshot> lines
    ) {
    }

    private static final class UnitQuantityAccumulator {
        private final Map<String, Double> quantities = new LinkedHashMap<>();

        void add(String unit, double quantity) {
            if (quantity <= 0) return;
            quantities.merge(unit, quantity, Double::sum);
        }

        void addAll(UnitQuantityAccumulator other) {
            other.quantities.forEach(this::add);
        }

        List<InformeInventarioDTO.CantidadUnidadDTO> toDto() {
            return quantities.entrySet().stream()
                    .map(entry -> InformeInventarioDTO.CantidadUnidadDTO.builder()
                            .unidadMedida(entry.getKey())
                            .cantidad(entry.getValue())
                            .build())
                    .toList();
        }
    }
}
