package exotic.app.planta.service.inventarios;

import exotic.app.planta.model.inventarios.Movimiento;
import exotic.app.planta.model.inventarios.TransaccionAlmacen;
import exotic.app.planta.model.inventarios.dto.HistorialAveriaDTO;
import exotic.app.planta.model.inventarios.dto.HistorialAveriaItemDTO;
import exotic.app.planta.model.inventarios.dto.ItemDispensadoAveriaDTO;
import exotic.app.planta.model.inventarios.dto.ReporteAveriaDTO;
import exotic.app.planta.model.inventarios.dto.ReporteAveriaItemDTO;
import exotic.app.planta.model.produccion.OrdenProduccion;
import exotic.app.planta.model.produccion.dto.OrdenProduccionDTO;
import exotic.app.planta.model.producto.Producto;
import exotic.app.planta.model.producto.manufacturing.procesos.AreaProduccion;
import exotic.app.planta.model.users.User;
import exotic.app.planta.repo.inventarios.TransaccionAlmacenHeaderRepo;
import exotic.app.planta.repo.produccion.OrdenProduccionRepo;
import exotic.app.planta.repo.producto.ProductoRepo;
import exotic.app.planta.repo.producto.procesos.AreaProduccionRepo;
import exotic.app.planta.repo.usuarios.UserRepository;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AveriasService {

    private final OrdenProduccionRepo ordenProduccionRepo;
    private final TransaccionAlmacenHeaderRepo transaccionAlmacenHeaderRepo;
    private final ProductoRepo productoRepo;
    private final AreaProduccionRepo areaProduccionRepo;
    private final UserRepository userRepository;

    public Page<OrdenProduccionDTO> searchOrdenesProduccionByLoteAsignado(String loteAsignado, Pageable pageable) {
        Page<OrdenProduccion> page = ordenProduccionRepo.findByLoteAsignadoContaining(loteAsignado, pageable);
        page.getContent().forEach(orden -> {
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

    public List<HistorialAveriaDTO> getHistorialAverias(int ordenProduccionId) {
        List<TransaccionAlmacen> reportes = transaccionAlmacenHeaderRepo
                .findByTipoEntidadCausanteAndIdEntidadCausanteWithMovimientos(
                        TransaccionAlmacen.TipoEntidadCausante.RA, ordenProduccionId);

        return reportes.stream().map(tx -> {
            List<HistorialAveriaItemDTO> items = tx.getMovimientosTransaccion().stream()
                    .map(mov -> new HistorialAveriaItemDTO(
                            mov.getProducto().getProductoId(),
                            mov.getProducto().getNombre(),
                            mov.getProducto().getTipoUnidades(),
                            Math.abs(mov.getCantidad())
                    ))
                    .collect(Collectors.toList());

            String usuario = null;
            if (tx.getUsuarioAprobador() != null) {
                Hibernate.initialize(tx.getUsuarioAprobador());
                usuario = tx.getUsuarioAprobador().getUsername();
            }

            return new HistorialAveriaDTO(
                    tx.getTransaccionId(),
                    tx.getFechaTransaccion(),
                    tx.getObservaciones(),
                    usuario,
                    items
            );
        }).collect(Collectors.toList());
    }

    @Transactional
    public TransaccionAlmacen crearReporteAveria(ReporteAveriaDTO dto) {
        AreaProduccion area = areaProduccionRepo.findById(dto.getAreaProduccionId())
                .orElseThrow(() -> new RuntimeException("Área de producción no encontrada con ID: " + dto.getAreaProduccionId()));

        TransaccionAlmacen transaccion = new TransaccionAlmacen();
        transaccion.setTipoEntidadCausante(TransaccionAlmacen.TipoEntidadCausante.RA);
        transaccion.setIdEntidadCausante(dto.getOrdenProduccionId());
        transaccion.setObservaciones(dto.getObservaciones());

        if (dto.getUsername() != null && !dto.getUsername().isEmpty()) {
            Optional<User> userOpt = userRepository.findByUsername(dto.getUsername());
            userOpt.ifPresent(transaccion::setUsuarioAprobador);
        }

        List<Movimiento> movimientos = new ArrayList<>();
        for (ReporteAveriaItemDTO item : dto.getItems()) {
            Producto producto = productoRepo.findByProductoId(item.getProductoId())
                    .orElseThrow(() -> new RuntimeException("Producto no encontrado con ID: " + item.getProductoId()));

            Movimiento movimiento = new Movimiento();
            movimiento.setCantidad(item.getCantidadAveria());
            movimiento.setProducto(producto);
            movimiento.setTipoMovimiento(Movimiento.TipoMovimiento.AVERIA);
            movimiento.setAlmacen(Movimiento.Almacen.AVERIAS);
            movimiento.setAreaProduccion(area);
            movimiento.setTransaccionAlmacen(transaccion);
            movimientos.add(movimiento);
        }

        transaccion.setMovimientosTransaccion(movimientos);
        return transaccionAlmacenHeaderRepo.save(transaccion);
    }
}
