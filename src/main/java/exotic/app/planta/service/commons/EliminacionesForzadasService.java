package exotic.app.planta.service.commons;

import exotic.app.planta.model.commons.dto.eliminaciones.*;
import exotic.app.planta.model.contabilidad.AsientoContable;
import exotic.app.planta.model.inventarios.Movimiento;
import exotic.app.planta.model.inventarios.TransaccionAlmacen;
import exotic.app.planta.model.compras.ItemOrdenCompra;
import exotic.app.planta.model.inventarios.Lote;
import exotic.app.planta.repo.compras.ItemOrdenCompraRepo;
import exotic.app.planta.repo.compras.OrdenCompraRepo;
import exotic.app.planta.repo.inventarios.LoteRepo;
import exotic.app.planta.repo.inventarios.TransaccionAlmacenHeaderRepo;
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
        response.setItemsOrdenCompra(items);
        response.setLotes(lotes);
        response.setTransaccionesAlmacen(transaccionesDto);
        response.setAsientosContables(new ArrayList<>(asientosSet));
        return response;
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
