package exotic.app.planta.service.commons;

import exotic.app.planta.model.commons.dto.eliminaciones.*;
import exotic.app.planta.model.contabilidad.AsientoContable;
import exotic.app.planta.model.inventarios.Movimiento;
import exotic.app.planta.model.inventarios.TransaccionAlmacen;
import exotic.app.planta.model.compras.ItemOrdenCompra;
import exotic.app.planta.model.inventarios.Lote;
import exotic.app.planta.model.produccion.OrdenSeguimiento;
import exotic.app.planta.repo.compras.ItemOrdenCompraRepo;
import exotic.app.planta.repo.compras.OrdenCompraRepo;
import exotic.app.planta.repo.inventarios.LoteRepo;
import exotic.app.planta.repo.inventarios.TransaccionAlmacenHeaderRepo;
import exotic.app.planta.repo.produccion.OrdenProduccionRepo;
import exotic.app.planta.repo.produccion.OrdenSeguimientoRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class EliminacionesForzadasService {

    private final OrdenCompraRepo ordenCompraRepo;
    private final ItemOrdenCompraRepo itemOrdenCompraRepo;
    private final LoteRepo loteRepo;
    private final TransaccionAlmacenHeaderRepo transaccionAlmacenHeaderRepo;
    private final OrdenProduccionRepo ordenProduccionRepo;
    private final OrdenSeguimientoRepo ordenSeguimientoRepo;

    /**
     * Estudia qué registros bloquean la eliminación de una OrdenCompraMateriales por integridad referencial.
     *
     * @param ordenCompraId ID de la orden de compra
     * @return DTO con items, lotes, transacciones (y movimientos) y asientos contables que referencian la OCM
     * @throws RuntimeException si la orden no existe
     */
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

    /**
     * Ejecuta la eliminación forzada de una OrdenCompraMateriales solo si no tiene TransaccionAlmacen asociadas.
     * Orden: desvincular lotes, borrar ítems, borrar OCM (si hay transacciones no se permite).
     *
     * @param ordenCompraId ID de la orden de compra
     * @throws IllegalStateException si tiene al menos una transacción de almacén asociada
     * @throws RuntimeException si la orden no existe
     */
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

        for (TransaccionAlmacen ta : transacciones) {
            ta.setAsientoContable(null);
            transaccionAlmacenHeaderRepo.save(ta);
            transaccionAlmacenHeaderRepo.delete(ta);
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

    /**
     * Estudia qué registros están asociados a una Orden de Producción (OP).
     * La eliminación solo está permitida si no hay TransaccionAlmacen asociadas (eliminable = true).
     *
     * @param ordenProduccionId ID de la orden de producción
     * @return DTO con ordenes de seguimiento, lotes, transacciones y asientos; eliminable si transacciones vacías
     * @throws RuntimeException si la orden no existe
     */
    @Transactional(readOnly = true)
    public EstudiarEliminacionOPResponseDTO estudiarEliminacionOrdenProduccion(int ordenProduccionId) {
        if (!ordenProduccionRepo.existsById(ordenProduccionId)) {
            throw new RuntimeException("OrdenProduccion not found with id: " + ordenProduccionId);
        }

        List<OrdenSeguimiento> seguimientos = ordenSeguimientoRepo.findByOrdenProduccion_OrdenId(ordenProduccionId);
        List<OrdenSeguimientoResumenDTO> ordenesSeguimientoDto = seguimientos.stream()
                .map(this::toOrdenSeguimientoResumen)
                .collect(Collectors.toList());

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
        response.setOrdenesSeguimiento(ordenesSeguimientoDto);
        response.setLotes(lotes);
        response.setTransaccionesAlmacen(transaccionesDto);
        response.setAsientosContables(new ArrayList<>(asientosSet));
        return response;
    }

    /**
     * Ejecuta la eliminación forzada de una Orden de Producción solo si no tiene TransaccionAlmacen asociadas.
     * Orden: borrar lotes asociados a la OP (lote reservado FG), luego borrar OP (cascade OrdenSeguimiento y RecursoAsignadoOrden).
     *
     * @param ordenProduccionId ID de la orden de producción
     * @throws IllegalStateException si existe al menos una transacción de almacén asociada
     * @throws RuntimeException si la orden no existe
     */
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

    private OrdenSeguimientoResumenDTO toOrdenSeguimientoResumen(OrdenSeguimiento seg) {
        OrdenSeguimientoResumenDTO dto = new OrdenSeguimientoResumenDTO();
        dto.setSeguimientoId(seg.getSeguimientoId());
        dto.setEstado(seg.getEstado());
        dto.setProductoId(seg.getInsumo() != null && seg.getInsumo().getProducto() != null
                ? seg.getInsumo().getProducto().getProductoId()
                : null);
        return dto;
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
