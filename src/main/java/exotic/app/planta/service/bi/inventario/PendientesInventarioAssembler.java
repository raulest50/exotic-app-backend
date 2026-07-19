package exotic.app.planta.service.bi.inventario;

import exotic.app.planta.model.bi.dto.InformeInventarioDTO;
import exotic.app.planta.model.compras.ItemOrdenCompra;
import exotic.app.planta.model.inventarios.Movimiento;
import exotic.app.planta.model.inventarios.TransaccionAlmacen;
import exotic.app.planta.model.produccion.OrdenProduccion;
import exotic.app.planta.repo.compras.ItemOrdenCompraRepo;
import exotic.app.planta.repo.inventarios.TransaccionAlmacenRepo;
import exotic.app.planta.repo.produccion.OrdenProduccionRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

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

    private final ItemOrdenCompraRepo purchaseItemRepo;
    private final OrdenProduccionRepo productionOrderRepo;
    private final TransaccionAlmacenRepo movementRepo;

    InformeInventarioDTO.OcmPendientesDTO buildPendingPurchaseOrders() {
        List<ItemOrdenCompra> pendingItems = purchaseItemRepo
                .findAllByOrdenEstadoForBi(PENDING_WAREHOUSE_RECEIPT_STATUS);
        if (pendingItems.isEmpty()) {
            return emptyPendingPurchaseOrders();
        }

        Set<Integer> purchaseOrderIds = new HashSet<>();
        pendingItems.forEach(item -> purchaseOrderIds.add(
                item.getOrdenCompraMateriales().getOrdenCompraId()));

        Map<PurchaseMaterialKey, Double> receivedByPurchaseAndMaterial =
                loadReceivedQuantities(purchaseOrderIds);
        Map<Integer, PendingPurchaseOrderAccumulator> accumulators = new LinkedHashMap<>();

        for (ItemOrdenCompra item : pendingItems) {
            int purchaseOrderId = item.getOrdenCompraMateriales().getOrdenCompraId();
            PurchaseMaterialKey key = new PurchaseMaterialKey(
                    purchaseOrderId,
                    item.getMaterial().getProductoId());
            double availableReceipt = receivedByPurchaseAndMaterial.getOrDefault(key, 0d);
            double appliedReceipt = Math.min(item.getCantidad(), availableReceipt);
            receivedByPurchaseAndMaterial.put(
                    key,
                    Math.max(0, availableReceipt - appliedReceipt));

            if (appliedReceipt >= item.getCantidad()) {
                continue;
            }

            accumulators.computeIfAbsent(
                            purchaseOrderId,
                            ignored -> new PendingPurchaseOrderAccumulator(item))
                    .addLine(item, appliedReceipt);
        }

        return summarizePendingPurchaseOrders(accumulators.values());
    }

    InformeInventarioDTO.MaterialDirectoOpDTO buildOpenProductionOrderMaterial() {
        List<OrdenProduccion> openOrders = productionOrderRepo.findAllOpenForBi();
        if (openOrders.isEmpty()) {
            return emptyOpenProductionOrderMaterial();
        }

        Map<Integer, OrdenProduccion> orderById = new HashMap<>();
        openOrders.forEach(order -> orderById.put(order.getOrdenId(), order));

        List<Movimiento> dispensations = movementRepo
                .findDispensacionesPorCausantesYEntidades(
                        GENERAL,
                        Movimiento.TipoMovimiento.DISPENSACION,
                        List.of(
                                TransaccionAlmacen.TipoEntidadCausante.OD,
                                TransaccionAlmacen.TipoEntidadCausante.OD_RA),
                        orderById.keySet());

        Map<Integer, OpenProductionOrderAccumulator> accumulators = new LinkedHashMap<>();
        for (Movimiento dispensation : dispensations) {
            int productionOrderId = dispensation.getTransaccionAlmacen()
                    .getIdEntidadCausante();
            OrdenProduccion productionOrder = orderById.get(productionOrderId);
            if (productionOrder == null) continue;

            accumulators.computeIfAbsent(
                            productionOrderId,
                            ignored -> new OpenProductionOrderAccumulator(productionOrder))
                    .addDispensation(dispensation);
        }

        return summarizeOpenProductionOrders(accumulators.values());
    }

    private Map<PurchaseMaterialKey, Double> loadReceivedQuantities(
            Set<Integer> purchaseOrderIds
    ) {
        Map<PurchaseMaterialKey, Double> received = new HashMap<>();
        List<Movimiento> receipts = movementRepo.findRecepcionesPorCausanteYEntidades(
                GENERAL,
                Movimiento.TipoMovimiento.COMPRA,
                TransaccionAlmacen.TipoEntidadCausante.OCM,
                purchaseOrderIds);

        for (Movimiento receipt : receipts) {
            PurchaseMaterialKey key = new PurchaseMaterialKey(
                    receipt.getTransaccionAlmacen().getIdEntidadCausante(),
                    receipt.getProducto().getProductoId());
            received.merge(key, receipt.getCantidad(), Double::sum);
        }
        return received;
    }

    private InformeInventarioDTO.OcmPendientesDTO summarizePendingPurchaseOrders(
            Collection<PendingPurchaseOrderAccumulator> accumulators
    ) {
        UnitQuantityAccumulator totalQuantities = new UnitQuantityAccumulator();
        Set<String> productIds = new HashSet<>();
        double totalValue = 0;
        List<InformeInventarioDTO.OcmDTO> orders = new ArrayList<>();

        for (PendingPurchaseOrderAccumulator accumulator : accumulators) {
            orders.add(accumulator.toDto());
            totalQuantities.addAll(accumulator.pendingQuantities);
            productIds.addAll(accumulator.productIds);
            totalValue += accumulator.pendingValue;
        }

        return InformeInventarioDTO.OcmPendientesDTO.builder()
                .ordenes(accumulators.size())
                .referencias(productIds.size())
                .cantidadesPorUnidad(totalQuantities.toDto())
                .valorPendienteSinIva(totalValue)
                .items(orders)
                .build();
    }

    private InformeInventarioDTO.MaterialDirectoOpDTO summarizeOpenProductionOrders(
            Collection<OpenProductionOrderAccumulator> accumulators
    ) {
        UnitQuantityAccumulator totalQuantities = new UnitQuantityAccumulator();
        Set<String> productIds = new HashSet<>();
        double totalValue = 0;
        List<InformeInventarioDTO.OpMaterialDTO> orders = new ArrayList<>();

        for (OpenProductionOrderAccumulator accumulator : accumulators) {
            orders.add(accumulator.toDto());
            totalQuantities.addAll(accumulator.dispensedQuantities);
            productIds.addAll(accumulator.productIds);
            totalValue += accumulator.estimatedValue;
        }

        return InformeInventarioDTO.MaterialDirectoOpDTO.builder()
                .ordenes(accumulators.size())
                .referencias(productIds.size())
                .cantidadesPorUnidad(totalQuantities.toDto())
                .valorEstimado(totalValue)
                .items(orders)
                .build();
    }

    private InformeInventarioDTO.OcmPendientesDTO emptyPendingPurchaseOrders() {
        return InformeInventarioDTO.OcmPendientesDTO.builder()
                .ordenes(0)
                .referencias(0)
                .cantidadesPorUnidad(List.of())
                .valorPendienteSinIva(0)
                .items(List.of())
                .build();
    }

    private InformeInventarioDTO.MaterialDirectoOpDTO emptyOpenProductionOrderMaterial() {
        return InformeInventarioDTO.MaterialDirectoOpDTO.builder()
                .ordenes(0)
                .referencias(0)
                .cantidadesPorUnidad(List.of())
                .valorEstimado(0)
                .items(List.of())
                .build();
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

        private PendingPurchaseOrderAccumulator(ItemOrdenCompra firstItem) {
            purchaseOrderId = firstItem.getOrdenCompraMateriales().getOrdenCompraId();
            issueDate = firstItem.getOrdenCompraMateriales().getFechaEmision();
            supplierName = firstItem.getOrdenCompraMateriales().getProveedor().getNombre();
        }

        void addLine(ItemOrdenCompra item, double appliedReceipt) {
            double pendingQuantity = Math.max(item.getCantidad() - appliedReceipt, 0);
            double lineValue = pendingQuantity * item.getPrecioUnitario();
            String unit = InventarioBiUtils.unitOf(item.getMaterial());

            productIds.add(item.getMaterial().getProductoId());
            pendingQuantities.add(unit, pendingQuantity);
            pendingValue += lineValue;
            lines.add(InformeInventarioDTO.LineaOcmDTO.builder()
                    .itemId(item.getItemOrdenId())
                    .productoId(item.getMaterial().getProductoId())
                    .productoNombre(item.getMaterial().getNombre())
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
        private final OrdenProduccion productionOrder;
        private final Set<String> productIds = new HashSet<>();
        private final UnitQuantityAccumulator dispensedQuantities =
                new UnitQuantityAccumulator();
        private double estimatedValue;

        private OpenProductionOrderAccumulator(OrdenProduccion productionOrder) {
            this.productionOrder = productionOrder;
        }

        void addDispensation(Movimiento movement) {
            double quantity = Math.abs(movement.getCantidad());
            String unit = InventarioBiUtils.unitOf(movement.getProducto());
            productIds.add(movement.getProducto().getProductoId());
            dispensedQuantities.add(unit, quantity);
            if (InventarioBiUtils.hasValidCost(movement.getProducto())) {
                estimatedValue += quantity * InventarioBiUtils.costAsDouble(
                        movement.getProducto());
            }
        }

        InformeInventarioDTO.OpMaterialDTO toDto() {
            return InformeInventarioDTO.OpMaterialDTO.builder()
                    .opId(productionOrder.getOrdenId())
                    .lote(productionOrder.getLoteAsignado())
                    .estado(productionOrder.getEstadoOrden())
                    .referencias(productIds.size())
                    .cantidadesPorUnidad(dispensedQuantities.toDto())
                    .valorEstimado(estimatedValue)
                    .build();
        }
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
