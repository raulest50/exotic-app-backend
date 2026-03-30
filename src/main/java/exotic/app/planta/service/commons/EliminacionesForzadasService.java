package exotic.app.planta.service.commons;

import exotic.app.planta.model.commons.dto.eliminaciones.*;
import exotic.app.planta.model.contabilidad.AsientoContable;
import exotic.app.planta.model.compras.ItemOrdenCompra;
import exotic.app.planta.model.inventarios.Lote;
import exotic.app.planta.model.inventarios.Movimiento;
import exotic.app.planta.model.inventarios.TransaccionAlmacen;
import exotic.app.planta.model.producto.Producto;
import exotic.app.planta.model.producto.Terminado;
import exotic.app.planta.model.producto.manufacturing.procesos.ProcesoProduccionCompleto;
import exotic.app.planta.model.producto.manufacturing.receta.Insumo;
import exotic.app.planta.model.producto.manufacturing.snapshots.ManufacturingVersions;
import exotic.app.planta.model.produccion.OrdenProduccion;
import exotic.app.planta.model.ventas.ItemFacturaVenta;
import exotic.app.planta.model.ventas.ItemOrdenVenta;
import exotic.app.planta.repo.activos.fijos.gestion.DepreciacionActivoRepo;
import exotic.app.planta.repo.activos.fijos.gestion.DocumentoBajaActivoRepo;
import exotic.app.planta.repo.activos.fijos.gestion.IncorporacionActivoHeaderRepo;
import exotic.app.planta.repo.compras.ItemOrdenCompraRepo;
import exotic.app.planta.repo.compras.OrdenCompraRepo;
import exotic.app.planta.repo.contabilidad.AsientoContableRepo;
import exotic.app.planta.repo.inventarios.LoteRepo;
import exotic.app.planta.repo.inventarios.TransaccionAlmacenHeaderRepo;
import exotic.app.planta.repo.inventarios.TransaccionAlmacenRepo;
import exotic.app.planta.repo.produccion.OrdenProduccionRepo;
import exotic.app.planta.repo.producto.InsumoRepo;
import exotic.app.planta.repo.producto.ProductoRepo;
import exotic.app.planta.repo.producto.TerminadoRepo;
import exotic.app.planta.repo.producto.manufacturing.snapshots.ManufacturingVersionRepo;
import exotic.app.planta.repo.producto.procesos.ProcesoProduccionCompletoRepo;
import exotic.app.planta.repo.ventas.FacturaVentaRepo;
import exotic.app.planta.repo.ventas.ItemFacturaVentaRepo;
import exotic.app.planta.repo.ventas.ItemOrdenVentaRepo;
import exotic.app.planta.repo.ventas.OrdenVentaRepo;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class EliminacionesForzadasService {

    private static final List<TransaccionAlmacen.TipoEntidadCausante> TIPOS_TRANSACCION_OP_PURGA = List.of(
            TransaccionAlmacen.TipoEntidadCausante.OD,
            TransaccionAlmacen.TipoEntidadCausante.OD_RA,
            TransaccionAlmacen.TipoEntidadCausante.RA,
            TransaccionAlmacen.TipoEntidadCausante.OP
    );

    private static final String PURGA_TERMINADOS_OPERATION = "La purga total de terminados";
    private static final String ELIMINACION_TERMINADO_OPERATION = "La eliminación forzada de un terminado";

    private final OrdenCompraRepo ordenCompraRepo;
    private final ItemOrdenCompraRepo itemOrdenCompraRepo;
    private final LoteRepo loteRepo;
    private final TransaccionAlmacenHeaderRepo transaccionAlmacenHeaderRepo;
    private final OrdenProduccionRepo ordenProduccionRepo;
    private final ProductoRepo productoRepo;
    private final TerminadoRepo terminadoRepo;
    private final InsumoRepo insumoRepo;
    private final TransaccionAlmacenRepo transaccionAlmacenRepo;
    private final ManufacturingVersionRepo manufacturingVersionRepo;
    private final ItemOrdenVentaRepo itemOrdenVentaRepo;
    private final ItemFacturaVentaRepo itemFacturaVentaRepo;
    private final OrdenVentaRepo ordenVentaRepo;
    private final FacturaVentaRepo facturaVentaRepo;
    private final ProcesoProduccionCompletoRepo procesoProduccionCompletoRepo;
    private final AsientoContableRepo asientoContableRepo;
    private final IncorporacionActivoHeaderRepo incorporacionActivoHeaderRepo;
    private final DepreciacionActivoRepo depreciacionActivoRepo;
    private final DocumentoBajaActivoRepo documentoBajaActivoRepo;
    private final DangerousOperationGuard dangerousOperationGuard;
    private final TransactionTemplate transactionTemplate;
    private final EntityManager entityManager;

    @Transactional(readOnly = true)
    public EstudiarEliminacionOCMResponseDTO estudiarEliminacionOrdenCompra(int ordenCompraId) {
        if (!ordenCompraRepo.existsById(ordenCompraId)) {
            throw new RuntimeException("OrdenCompraMateriales not found with id: " + ordenCompraId);
        }

        List<ItemOrdenCompraResumenDTO> items = itemOrdenCompraRepo
                .findByOrdenCompraMateriales_OrdenCompraId(ordenCompraId)
                .stream()
                .map(this::toItemResumen)
                .collect(Collectors.toList());

        List<LoteResumenDTO> lotes = loteRepo
                .findByOrdenCompraMateriales_OrdenCompraId(ordenCompraId)
                .stream()
                .map(this::toLoteResumen)
                .collect(Collectors.toList());

        List<TransaccionAlmacen> transacciones = transaccionAlmacenHeaderRepo
                .findByTipoEntidadCausanteAndIdEntidadCausanteWithMovimientos(
                        TransaccionAlmacen.TipoEntidadCausante.OCM,
                        ordenCompraId
                );

        List<TransaccionAlmacenResumenDTO> transaccionesDto = new ArrayList<>();
        Set<AsientoContableResumenDTO> asientosSet = new LinkedHashSet<>();

        for (TransaccionAlmacen ta : transacciones) {
            TransaccionAlmacenResumenDTO dto = toTransaccionResumen(ta);
            transaccionesDto.add(dto);
            if (ta.getAsientoContable() != null) {
                asientosSet.add(toAsientoResumen(ta.getAsientoContable()));
            }
        }

        EstudiarEliminacionOCMResponseDTO response = new EstudiarEliminacionOCMResponseDTO();
        response.setOrdenCompraId(ordenCompraId);
        response.setEliminable(transacciones.isEmpty());
        response.setItemsOrdenCompra(items);
        response.setLotes(lotes);
        response.setTransaccionesAlmacen(transaccionesDto);
        response.setAsientosContables(new ArrayList<>(asientosSet));
        return response;
    }

    @Transactional
    public void ejecutarEliminacionOrdenCompra(int ordenCompraId) {
        if (!ordenCompraRepo.existsById(ordenCompraId)) {
            throw new RuntimeException("OrdenCompraMateriales not found with id: " + ordenCompraId);
        }

        List<TransaccionAlmacen> transacciones = transaccionAlmacenHeaderRepo
                .findByTipoEntidadCausanteAndIdEntidadCausanteWithMovimientos(
                        TransaccionAlmacen.TipoEntidadCausante.OCM,
                        ordenCompraId
                );
        if (!transacciones.isEmpty()) {
            throw new IllegalStateException(
                    "No se puede eliminar la orden de compra: tiene " + transacciones.size() + " transacción(es) de almacén asociada(s).");
        }

        List<Lote> lotes = loteRepo.findByOrdenCompraMateriales_OrdenCompraId(ordenCompraId);
        for (Lote lote : lotes) {
            lote.setOrdenCompraMateriales(null);
            loteRepo.save(lote);
        }

        List<ItemOrdenCompra> items = itemOrdenCompraRepo.findByOrdenCompraMateriales_OrdenCompraId(ordenCompraId);
        itemOrdenCompraRepo.deleteAll(items);

        ordenCompraRepo.deleteById(ordenCompraId);
        log.info("Eliminación forzada ejecutada para OCM id: {}", ordenCompraId);
    }

    @Transactional(readOnly = true)
    public EstudiarEliminacionOPResponseDTO estudiarEliminacionOrdenProduccion(int ordenProduccionId) {
        if (!ordenProduccionRepo.existsById(ordenProduccionId)) {
            throw new RuntimeException("OrdenProduccion not found with id: " + ordenProduccionId);
        }

        List<LoteResumenDTO> lotes = loteRepo.findByOrdenProduccion_OrdenId(ordenProduccionId)
                .stream()
                .map(this::toLoteResumen)
                .collect(Collectors.toList());

        List<TransaccionAlmacen> transacciones = transaccionAlmacenHeaderRepo
                .findByTipoEntidadCausanteAndIdEntidadCausanteWithMovimientos(
                        TransaccionAlmacen.TipoEntidadCausante.OP,
                        ordenProduccionId
                );

        List<TransaccionAlmacenResumenDTO> transaccionesDto = new ArrayList<>();
        Set<AsientoContableResumenDTO> asientosSet = new LinkedHashSet<>();
        for (TransaccionAlmacen ta : transacciones) {
            TransaccionAlmacenResumenDTO dto = toTransaccionResumen(ta);
            transaccionesDto.add(dto);
            if (ta.getAsientoContable() != null) {
                asientosSet.add(toAsientoResumen(ta.getAsientoContable()));
            }
        }

        EstudiarEliminacionOPResponseDTO response = new EstudiarEliminacionOPResponseDTO();
        response.setOrdenProduccionId(ordenProduccionId);
        response.setEliminable(transacciones.isEmpty());
        response.setLotes(lotes);
        response.setTransaccionesAlmacen(transaccionesDto);
        response.setAsientosContables(new ArrayList<>(asientosSet));
        return response;
    }

    @Transactional
    public void ejecutarEliminacionOrdenProduccion(int ordenProduccionId) {
        if (!ordenProduccionRepo.existsById(ordenProduccionId)) {
            throw new RuntimeException("OrdenProduccion not found with id: " + ordenProduccionId);
        }

        List<TransaccionAlmacen> transacciones = transaccionAlmacenHeaderRepo
                .findByTipoEntidadCausanteAndIdEntidadCausanteWithMovimientos(
                        TransaccionAlmacen.TipoEntidadCausante.OP,
                        ordenProduccionId
                );
        if (!transacciones.isEmpty()) {
            throw new IllegalStateException(
                    "No se puede eliminar la orden de producción: tiene " + transacciones.size() + " transacción(es) de almacén asociada(s).");
        }

        List<Lote> lotes = loteRepo.findByOrdenProduccion_OrdenId(ordenProduccionId);
        for (Lote lote : lotes) {
            loteRepo.delete(lote);
        }

        ordenProduccionRepo.deleteById(ordenProduccionId);
        log.info("Eliminación forzada ejecutada para OP id: {}", ordenProduccionId);
    }

    public EliminacionTerminadosBatchResultDTO ejecutarEliminacionTodosLosTerminados() {
        dangerousOperationGuard.assertNotProduction(PURGA_TERMINADOS_OPERATION);

        List<String> productoIds = terminadoRepo.findAllProductoIdsOrderByProductoIdAsc();
        EliminacionTerminadosBatchResultDTO result = new EliminacionTerminadosBatchResultDTO();
        result.setPermitted(true);
        result.setExecuted(true);
        result.setTotalTerminados(productoIds.size());
        result.setProductoIdsProcesados(new ArrayList<>(productoIds));

        if (productoIds.isEmpty()) {
            result.setMessage("No se encontraron terminados para purgar.");
            return result;
        }

        int eliminados = 0;
        List<EliminacionBatchFailureDTO> failures = new ArrayList<>();

        for (String productoId : productoIds) {
            try {
                transactionTemplate.executeWithoutResult(status -> ejecutarEliminacionTerminado(productoId));
                eliminados++;
            } catch (RuntimeException e) {
                log.warn("Falló la purga del terminado {}: {}", productoId, e.getMessage(), e);
                failures.add(new EliminacionBatchFailureDTO(productoId, e.getMessage()));
            }
        }

        result.setEliminados(eliminados);
        result.setFailures(failures);
        result.setFallidos(failures.size());
        result.setMessage(failures.isEmpty()
                ? "Purga total de terminados completada."
                : "Purga total de terminados finalizada con errores parciales.");
        return result;
    }

    @Transactional
    public void ejecutarEliminacionTerminado(String productoId) {
        dangerousOperationGuard.assertNotProduction(ELIMINACION_TERMINADO_OPERATION);

        Producto producto = productoRepo.findById(productoId)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró el producto con ID: " + productoId));

        if (!(producto instanceof Terminado)) {
            throw new IllegalStateException("El producto con ID " + productoId + " no es un Terminado.");
        }

        terminadoRepo.findById(productoId)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró el terminado con ID: " + productoId));

        deleteRecipeConsumers(productoId);
        deleteProductionHistory(productoId);
        deleteResidualProductTransactions(productoId);
        deleteManufacturingVersions(productoId);
        deleteSalesArtifacts(productoId);
        deleteAssociatedProcess(productoId);

        terminadoRepo.deleteById(productoId);
        log.info("Eliminación forzada ejecutada para Terminado id: {}", productoId);
    }

    private void deleteRecipeConsumers(String productoId) {
        List<Insumo> insumosConsumidores = insumoRepo.findByProducto_ProductoId(productoId);
        if (!insumosConsumidores.isEmpty()) {
            insumoRepo.deleteAll(insumosConsumidores);
        }
    }

    private void deleteProductionHistory(String productoId) {
        List<OrdenProduccion> ordenesProduccion = ordenProduccionRepo.findByProducto_ProductoId(productoId);
        for (OrdenProduccion ordenProduccion : ordenesProduccion) {
            deleteOrderRootedTransactions(ordenProduccion.getOrdenId());

            List<Lote> lotes = loteRepo.findByOrdenProduccion_OrdenId(ordenProduccion.getOrdenId());
            if (!lotes.isEmpty()) {
                loteRepo.deleteAll(lotes);
            }

            ordenProduccionRepo.delete(ordenProduccion);
        }
    }

    private void deleteOrderRootedTransactions(int ordenProduccionId) {
        List<TransaccionAlmacen> transacciones = transaccionAlmacenHeaderRepo
                .findByTipoEntidadCausanteInAndIdEntidadCausanteWithMovimientos(
                        TIPOS_TRANSACCION_OP_PURGA,
                        ordenProduccionId
                );

        for (TransaccionAlmacen transaccion : transacciones) {
            deleteWholeTransaction(transaccion);
        }
    }

    private void deleteResidualProductTransactions(String productoId) {
        List<TransaccionAlmacen> transacciones = transaccionAlmacenHeaderRepo.findDistinctByProductoIdWithMovimientos(productoId);
        Set<Long> candidateStandaloneLoteIds = new LinkedHashSet<>();

        for (TransaccionAlmacen transaccion : transacciones) {
            List<Movimiento> movimientosObjetivo = safeList(transaccion.getMovimientosTransaccion()).stream()
                    .filter(movimiento -> productoId.equals(getProductoId(movimiento)))
                    .toList();

            for (Movimiento movimiento : movimientosObjetivo) {
                if (movimiento.getLote() != null && movimiento.getLote().getId() != null) {
                    candidateStandaloneLoteIds.add(movimiento.getLote().getId());
                }
            }

            if (movimientosObjetivo.isEmpty()) {
                continue;
            }

            if (movimientosObjetivo.size() == safeList(transaccion.getMovimientosTransaccion()).size()) {
                deleteWholeTransaction(transaccion);
                continue;
            }

            transaccion.getMovimientosTransaccion().removeIf(movimiento -> productoId.equals(getProductoId(movimiento)));
            transaccionAlmacenHeaderRepo.save(transaccion);
        }

        deleteOrphanStandaloneLotes(candidateStandaloneLoteIds);
    }

    private void deleteOrphanStandaloneLotes(Set<Long> loteIds) {
        for (Long loteId : loteIds) {
            loteRepo.findById(loteId).ifPresent(lote -> {
                boolean noTieneRelacionesRaiz = lote.getOrdenCompraMateriales() == null && lote.getOrdenProduccion() == null;
                boolean sinMovimientos = transaccionAlmacenRepo.countByLote_Id(loteId) == 0;
                if (noTieneRelacionesRaiz && sinMovimientos) {
                    loteRepo.delete(lote);
                }
            });
        }
    }

    private void deleteManufacturingVersions(String productoId) {
        List<ManufacturingVersions> versions = manufacturingVersionRepo.findByProducto_ProductoId(productoId);
        if (!versions.isEmpty()) {
            manufacturingVersionRepo.deleteAll(versions);
        }
    }

    private void deleteSalesArtifacts(String productoId) {
        List<ItemFacturaVenta> itemsFactura = itemFacturaVentaRepo.findByProducto_ProductoId(productoId);
        Set<Integer> facturaIds = itemsFactura.stream()
                .map(ItemFacturaVenta::getFacturaVenta)
                .filter(Objects::nonNull)
                .map(factura -> factura.getFacturaVentaId())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<Integer> ordenIds = itemsFactura.stream()
                .map(ItemFacturaVenta::getFacturaVenta)
                .filter(Objects::nonNull)
                .map(factura -> factura.getOrdenVenta() != null ? factura.getOrdenVenta().getOrdenVentaId() : null)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (!itemsFactura.isEmpty()) {
            itemFacturaVentaRepo.deleteAll(itemsFactura);
        }

        List<ItemOrdenVenta> itemsOrden = itemOrdenVentaRepo.findByProducto_ProductoId(productoId);
        ordenIds.addAll(itemsOrden.stream()
                .map(ItemOrdenVenta::getOrdenVenta)
                .filter(Objects::nonNull)
                .map(orden -> orden.getOrdenVentaId())
                .collect(Collectors.toCollection(LinkedHashSet::new)));
        if (!itemsOrden.isEmpty()) {
            itemOrdenVentaRepo.deleteAll(itemsOrden);
        }

        for (Integer facturaId : facturaIds) {
            if (itemFacturaVentaRepo.countByFacturaVenta_FacturaVentaId(facturaId) == 0) {
                facturaVentaRepo.deleteById(facturaId);
            }
        }

        for (Integer ordenId : ordenIds) {
            boolean hasItems = itemOrdenVentaRepo.countByOrdenVenta_OrdenVentaId(ordenId) > 0;
            boolean hasFacturas = facturaVentaRepo.existsByOrdenVenta_OrdenVentaId(ordenId);
            if (!hasItems && !hasFacturas) {
                ordenVentaRepo.deleteById(ordenId);
            }
        }
    }

    private void deleteAssociatedProcess(String productoId) {
        List<ProcesoProduccionCompleto> procesos = procesoProduccionCompletoRepo.findByProducto_ProductoIdWithNodes(productoId);

        terminadoRepo.clearProcesoProduccionCompletoByProductoId(productoId);
        entityManager.flush();

        if (!procesos.isEmpty()) {
            procesoProduccionCompletoRepo.deleteAll(procesos);
            entityManager.flush();
        }

        procesoProduccionCompletoRepo.clearProductoByProductoId(productoId);
        entityManager.flush();
        entityManager.clear();
    }

    private void deleteWholeTransaction(TransaccionAlmacen transaccion) {
        AsientoContable asientoContable = transaccion.getAsientoContable();
        if (asientoContable != null) {
            transaccion.setAsientoContable(null);
            transaccionAlmacenHeaderRepo.saveAndFlush(transaccion);
        }

        transaccionAlmacenHeaderRepo.delete(transaccion);
        transaccionAlmacenHeaderRepo.flush();

        if (asientoContable != null && !isAsientoStillReferenced(asientoContable.getId())) {
            asientoContableRepo.deleteById(asientoContable.getId());
        }
    }

    private boolean isAsientoStillReferenced(Long asientoId) {
        return transaccionAlmacenHeaderRepo.existsByAsientoContable_Id(asientoId)
                || incorporacionActivoHeaderRepo.existsByAsientoContable_Id(asientoId)
                || depreciacionActivoRepo.existsByAsientoContable_Id(asientoId)
                || documentoBajaActivoRepo.existsByAsientoContable_Id(asientoId);
    }

    private String getProductoId(Movimiento movimiento) {
        return movimiento.getProducto() != null ? movimiento.getProducto().getProductoId() : null;
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private ItemOrdenCompraResumenDTO toItemResumen(ItemOrdenCompra item) {
        ItemOrdenCompraResumenDTO dto = new ItemOrdenCompraResumenDTO();
        dto.setItemOrdenId(item.getItemOrdenId());
        dto.setProductId(item.getMaterial() != null ? item.getMaterial().getProductoId() : null);
        dto.setCantidad(item.getCantidad());
        dto.setPrecioUnitario(item.getPrecioUnitario());
        dto.setSubTotal(item.getSubTotal());
        return dto;
    }

    private LoteResumenDTO toLoteResumen(Lote lote) {
        LoteResumenDTO dto = new LoteResumenDTO();
        dto.setId(lote.getId());
        dto.setBatchNumber(lote.getBatchNumber());
        dto.setProductionDate(lote.getProductionDate());
        dto.setExpirationDate(lote.getExpirationDate());
        return dto;
    }

    private MovimientoResumenDTO toMovimientoResumen(Movimiento m) {
        MovimientoResumenDTO dto = new MovimientoResumenDTO();
        dto.setMovimientoId(m.getMovimientoId());
        dto.setCantidad(m.getCantidad());
        dto.setProductId(m.getProducto() != null ? m.getProducto().getProductoId() : null);
        dto.setTipoMovimiento(m.getTipoMovimiento() != null ? m.getTipoMovimiento().name() : null);
        dto.setAlmacen(m.getAlmacen() != null ? m.getAlmacen().name() : null);
        dto.setFechaMovimiento(m.getFechaMovimiento());
        return dto;
    }

    private AsientoContableResumenDTO toAsientoResumen(AsientoContable a) {
        AsientoContableResumenDTO dto = new AsientoContableResumenDTO();
        dto.setId(a.getId());
        dto.setFecha(a.getFecha());
        dto.setDescripcion(a.getDescripcion());
        dto.setModulo(a.getModulo());
        dto.setDocumentoOrigen(a.getDocumentoOrigen());
        dto.setEstado(a.getEstado() != null ? a.getEstado().name() : null);
        return dto;
    }

    private TransaccionAlmacenResumenDTO toTransaccionResumen(TransaccionAlmacen ta) {
        TransaccionAlmacenResumenDTO dto = new TransaccionAlmacenResumenDTO();
        dto.setTransaccionId(ta.getTransaccionId());
        dto.setFechaTransaccion(ta.getFechaTransaccion());
        dto.setEstadoContable(ta.getEstadoContable() != null ? ta.getEstadoContable().name() : null);
        dto.setObservaciones(ta.getObservaciones());
        if (ta.getMovimientosTransaccion() != null) {
            dto.setMovimientos(ta.getMovimientosTransaccion().stream()
                    .map(this::toMovimientoResumen)
                    .collect(Collectors.toList()));
        } else {
            dto.setMovimientos(List.of());
        }
        dto.setAsientoContable(ta.getAsientoContable() != null ? toAsientoResumen(ta.getAsientoContable()) : null);
        return dto;
    }
}
