package exotic.app.planta.service.inventarios;

import exotic.app.planta.model.inventarios.Movimiento;
import exotic.app.planta.model.inventarios.TransaccionAlmacen;
import exotic.app.planta.model.inventarios.dto.ItemDispensadoAveriaDTO;
import exotic.app.planta.model.produccion.OrdenProduccion;
import exotic.app.planta.model.produccion.OrdenSeguimiento;
import exotic.app.planta.model.produccion.dto.OrdenProduccionDTO;
import exotic.app.planta.model.produccion.dto.OrdenSeguimientoDTO;
import exotic.app.planta.repo.inventarios.TransaccionAlmacenHeaderRepo;
import exotic.app.planta.repo.produccion.OrdenProduccionRepo;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AveriasService {

    private final OrdenProduccionRepo ordenProduccionRepo;
    private final TransaccionAlmacenHeaderRepo transaccionAlmacenHeaderRepo;

    public Page<OrdenProduccionDTO> searchOrdenesProduccionByLoteAsignado(String loteAsignado, Pageable pageable) {
        Page<OrdenProduccion> page = ordenProduccionRepo.findByLoteAsignadoContaining(loteAsignado, pageable);
        page.getContent().forEach(orden -> {
            Hibernate.initialize(orden.getOrdenesSeguimiento());
            Hibernate.initialize(orden.getProducto());
        });
        List<OrdenProduccionDTO> dtoList = page.getContent().stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
        return new PageImpl<>(dtoList, pageable, page.getTotalElements());
    }

    private OrdenProduccionDTO convertToDto(OrdenProduccion orden) {
        OrdenProduccionDTO dto = new OrdenProduccionDTO();
        dto.setOrdenId(orden.getOrdenId());
        dto.setProductoId(orden.getProducto().getProductoId());
        dto.setProductoNombre(orden.getProducto().getNombre());
        dto.setFechaInicio(orden.getFechaInicio());
        dto.setFechaCreacion(orden.getFechaCreacion());
        dto.setFechaLanzamiento(orden.getFechaLanzamiento());
        dto.setFechaFinalPlanificada(orden.getFechaFinalPlanificada());
        dto.setEstadoOrden(orden.getEstadoOrden());
        dto.setObservaciones(orden.getObservaciones());
        dto.setCantidadProducir(orden.getCantidadProducir());
        dto.setNumeroPedidoComercial(orden.getNumeroPedidoComercial());
        dto.setAreaOperativa(orden.getAreaOperativa());
        dto.setDepartamentoOperativo(orden.getDepartamentoOperativo());
        dto.setLoteAsignado(orden.getLoteAsignado());
        if (orden.getVendedorResponsable() != null) {
            dto.setResponsableId(orden.getVendedorResponsable().getCedula());
        }

        List<OrdenSeguimientoDTO> seguimientoDTOs = orden.getOrdenesSeguimiento().stream()
                .map(this::convertSeguimientoToDto)
                .collect(Collectors.toList());
        dto.setOrdenesSeguimiento(seguimientoDTOs);

        return dto;
    }

    private OrdenSeguimientoDTO convertSeguimientoToDto(OrdenSeguimiento seguimiento) {
        OrdenSeguimientoDTO dto = new OrdenSeguimientoDTO();
        dto.setSeguimientoId(seguimiento.getSeguimientoId());
        dto.setInsumoNombre(seguimiento.getInsumo().getProducto().getNombre());
        dto.setCantidadRequerida(seguimiento.getInsumo().getCantidadRequerida());
        dto.setEstado(seguimiento.getEstado());
        return dto;
    }

    public List<ItemDispensadoAveriaDTO> getItemsDispensadosParaAveria(int ordenProduccionId) {
        List<TransaccionAlmacen> dispensaciones = transaccionAlmacenHeaderRepo
                .findByTipoEntidadCausanteAndIdEntidadCausanteWithMovimientos(
                        TransaccionAlmacen.TipoEntidadCausante.OD, ordenProduccionId);

        Map<String, double[]> aggregated = new LinkedHashMap<>();
        Map<String, String> nombres = new HashMap<>();
        Map<String, String> unidades = new HashMap<>();

        for (TransaccionAlmacen tx : dispensaciones) {
            for (Movimiento mov : tx.getMovimientosTransaccion()) {
                String pid = mov.getProducto().getProductoId();
                aggregated.computeIfAbsent(pid, k -> new double[2]);
                aggregated.get(pid)[0] += Math.abs(mov.getCantidad());
                nombres.putIfAbsent(pid, mov.getProducto().getNombre());
                unidades.putIfAbsent(pid, mov.getProducto().getTipoUnidades());
            }
        }

        List<TransaccionAlmacen> reportesAveria = transaccionAlmacenHeaderRepo
                .findByTipoEntidadCausanteAndIdEntidadCausanteWithMovimientos(
                        TransaccionAlmacen.TipoEntidadCausante.RA, ordenProduccionId);

        for (TransaccionAlmacen tx : reportesAveria) {
            for (Movimiento mov : tx.getMovimientosTransaccion()) {
                String pid = mov.getProducto().getProductoId();
                if (aggregated.containsKey(pid)) {
                    aggregated.get(pid)[1] += Math.abs(mov.getCantidad());
                }
            }
        }

        List<ItemDispensadoAveriaDTO> result = new ArrayList<>();
        for (Map.Entry<String, double[]> entry : aggregated.entrySet()) {
            String pid = entry.getKey();
            double dispensada = entry.getValue()[0];
            double averiadaPrevia = entry.getValue()[1];
            double disponible = dispensada - averiadaPrevia;
            if (disponible > 0) {
                result.add(new ItemDispensadoAveriaDTO(
                        pid, nombres.get(pid), unidades.get(pid),
                        dispensada, averiadaPrevia, disponible));
            }
        }
        return result;
    }
}
