package exotic.app.planta.service.inventarios;

import exotic.app.planta.model.inventarios.Lote;
import exotic.app.planta.model.inventarios.Movimiento;
import exotic.app.planta.model.inventarios.TransaccionAlmacen;
import exotic.app.planta.model.inventarios.dto.HistorialAveriaDTO;
import exotic.app.planta.model.inventarios.dto.HistorialAveriaItemDTO;
import exotic.app.planta.model.inventarios.dto.ItemDispensadoAveriaDTO;
import exotic.app.planta.model.inventarios.dto.MaterialByLoteDTO;
import exotic.app.planta.model.inventarios.dto.ReporteAveriaAlmacenDTO;
import exotic.app.planta.model.inventarios.dto.ReporteAveriaAlmacenItemDTO;
import exotic.app.planta.model.inventarios.dto.ReporteAveriaDTO;
import exotic.app.planta.model.inventarios.dto.ReporteAveriaItemDTO;
import exotic.app.planta.model.produccion.OrdenProduccion;
import exotic.app.planta.model.produccion.dto.OrdenProduccionDTO;
import exotic.app.planta.model.producto.Producto;
import exotic.app.planta.model.producto.manufacturing.procesos.AreaProduccion;
import exotic.app.planta.model.users.User;
import exotic.app.planta.repo.inventarios.LoteRepo;
import exotic.app.planta.repo.inventarios.TransaccionAlmacenHeaderRepo;
import exotic.app.planta.repo.inventarios.TransaccionAlmacenRepo;
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
    private final TransaccionAlmacenRepo transaccionAlmacenRepo;
    private final LoteRepo loteRepo;
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

        // aggregated[0] = cantidadDispensada, aggregated[1] = cantidadAveriadaPrevia
        Map<String, double[]> aggregated = new LinkedHashMap<>();
        Map<String, String> nombres = new HashMap<>();
        Map<String, String> unidades = new HashMap<>();
        Map<String, Long> loteIds = new HashMap<>();
        Map<String, String> batchNumbers = new HashMap<>();

        for (TransaccionAlmacen tx : dispensaciones) {
            for (Movimiento mov : tx.getMovimientosTransaccion()) {
                if (mov.getLote() == null) continue;
                String pid = mov.getProducto().getProductoId();
                Long lid = mov.getLote().getId();
                String key = pid + "|" + lid;
                aggregated.computeIfAbsent(key, k -> new double[2]);
                aggregated.get(key)[0] += Math.abs(mov.getCantidad());
                nombres.putIfAbsent(key, mov.getProducto().getNombre());
                unidades.putIfAbsent(key, mov.getProducto().getTipoUnidades());
                loteIds.putIfAbsent(key, lid);
                batchNumbers.putIfAbsent(key, mov.getLote().getBatchNumber());
            }
        }

        List<TransaccionAlmacen> reportesAveria = transaccionAlmacenHeaderRepo
                .findByTipoEntidadCausanteAndIdEntidadCausanteWithMovimientos(
                        TransaccionAlmacen.TipoEntidadCausante.RA, ordenProduccionId);

        Map<String, Double> legacyAveriaPorProducto = new HashMap<>();
        for (TransaccionAlmacen tx : reportesAveria) {
            for (Movimiento mov : tx.getMovimientosTransaccion()) {
                String pid = mov.getProducto().getProductoId();
                if (mov.getLote() != null) {
                    String key = pid + "|" + mov.getLote().getId();
                    if (aggregated.containsKey(key)) {
                        aggregated.get(key)[1] += Math.abs(mov.getCantidad());
                    }
                } else {
                    legacyAveriaPorProducto.merge(pid, Math.abs(mov.getCantidad()), Double::sum);
                }
            }
        }

        if (!legacyAveriaPorProducto.isEmpty()) {
            Map<String, Double> totalDispensadaPorProducto = new HashMap<>();
            for (Map.Entry<String, double[]> entry : aggregated.entrySet()) {
                String pid = entry.getKey().split("\\|")[0];
                totalDispensadaPorProducto.merge(pid, entry.getValue()[0], Double::sum);
            }
            for (Map.Entry<String, double[]> entry : aggregated.entrySet()) {
                String pid = entry.getKey().split("\\|")[0];
                Double legacyTotal = legacyAveriaPorProducto.get(pid);
                if (legacyTotal != null && legacyTotal > 0) {
                    double totalDisp = totalDispensadaPorProducto.getOrDefault(pid, 0.0);
                    if (totalDisp > 0) {
                        double proportion = entry.getValue()[0] / totalDisp;
                        entry.getValue()[1] += legacyTotal * proportion;
                    }
                }
            }
        }

        List<ItemDispensadoAveriaDTO> result = new ArrayList<>();
        for (Map.Entry<String, double[]> entry : aggregated.entrySet()) {
            String key = entry.getKey();
            String pid = key.split("\\|")[0];
            double dispensada = entry.getValue()[0];
            double averiadaPrevia = entry.getValue()[1];
            double disponible = dispensada - averiadaPrevia;
            if (disponible > 0) {
                result.add(new ItemDispensadoAveriaDTO(
                        pid, nombres.get(key), unidades.get(key),
                        loteIds.get(key), batchNumbers.get(key),
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
            if (item.getLoteId() == null) {
                throw new RuntimeException("loteId es obligatorio para cada item del reporte de avería (productoId: " + item.getProductoId() + ")");
            }

            Producto producto = productoRepo.findByProductoId(item.getProductoId())
                    .orElseThrow(() -> new RuntimeException("Producto no encontrado con ID: " + item.getProductoId()));

            Lote lote = loteRepo.findById(item.getLoteId())
                    .orElseThrow(() -> new RuntimeException("Lote no encontrado con ID: " + item.getLoteId()));

            Movimiento movimiento = new Movimiento();
            movimiento.setCantidad(item.getCantidadAveria());
            movimiento.setProducto(producto);
            movimiento.setLote(lote);
            movimiento.setTipoMovimiento(Movimiento.TipoMovimiento.AVERIA);
            movimiento.setAlmacen(Movimiento.Almacen.AVERIAS);
            movimiento.setAreaProduccion(area);
            movimiento.setTransaccionAlmacen(transaccion);
            movimientos.add(movimiento);
        }

        transaccion.setMovimientosTransaccion(movimientos);
        return transaccionAlmacenHeaderRepo.save(transaccion);
    }

    public List<MaterialByLoteDTO> searchMaterialesByLote(String batchNumber) {
        List<Object[]> rows = transaccionAlmacenRepo
                .findMaterialesWithStockByBatchNumberAndAlmacen(batchNumber, Movimiento.Almacen.GENERAL);

        return rows.stream().map(row -> new MaterialByLoteDTO(
                (String) row[0],
                (String) row[1],
                (String) row[2],
                (Long) row[3],
                (String) row[4],
                ((Number) row[5]).doubleValue()
        )).collect(Collectors.toList());
    }

    @Transactional
    public TransaccionAlmacen crearReporteAveriaAlmacen(ReporteAveriaAlmacenDTO dto) {
        TransaccionAlmacen transaccion = new TransaccionAlmacen();
        transaccion.setTipoEntidadCausante(TransaccionAlmacen.TipoEntidadCausante.RAA);
        transaccion.setIdEntidadCausante(0);
        transaccion.setObservaciones(dto.getObservaciones());

        if (dto.getUsername() != null && !dto.getUsername().isEmpty()) {
            Optional<User> userOpt = userRepository.findByUsername(dto.getUsername());
            userOpt.ifPresent(transaccion::setUsuarioAprobador);
        }

        List<Movimiento> movimientos = new ArrayList<>();
        for (ReporteAveriaAlmacenItemDTO item : dto.getItems()) {
            if (item.getLoteId() == null) {
                throw new RuntimeException("loteId es obligatorio para cada item del reporte de avería de almacén (productoId: " + item.getProductoId() + ")");
            }

            Producto producto = productoRepo.findByProductoId(item.getProductoId())
                    .orElseThrow(() -> new RuntimeException("Producto no encontrado con ID: " + item.getProductoId()));

            Lote lote = loteRepo.findById(item.getLoteId())
                    .orElseThrow(() -> new RuntimeException("Lote no encontrado con ID: " + item.getLoteId()));

            // Salida de almacén GENERAL (cantidad negativa)
            Movimiento salidaGeneral = new Movimiento();
            salidaGeneral.setCantidad(-item.getCantidadAveria());
            salidaGeneral.setProducto(producto);
            salidaGeneral.setLote(lote);
            salidaGeneral.setTipoMovimiento(Movimiento.TipoMovimiento.AVERIA);
            salidaGeneral.setAlmacen(Movimiento.Almacen.GENERAL);
            salidaGeneral.setTransaccionAlmacen(transaccion);
            movimientos.add(salidaGeneral);

            // Entrada a almacén AVERIAS (cantidad positiva)
            Movimiento entradaAverias = new Movimiento();
            entradaAverias.setCantidad(item.getCantidadAveria());
            entradaAverias.setProducto(producto);
            entradaAverias.setLote(lote);
            entradaAverias.setTipoMovimiento(Movimiento.TipoMovimiento.AVERIA);
            entradaAverias.setAlmacen(Movimiento.Almacen.AVERIAS);
            entradaAverias.setTransaccionAlmacen(transaccion);
            movimientos.add(entradaAverias);
        }

        transaccion.setMovimientosTransaccion(movimientos);
        return transaccionAlmacenHeaderRepo.save(transaccion);
    }

    /**
     * El lote de origen (proveedor) se guarda explícitamente en Movimiento.lote porque NO es inferible
     * cuando hay múltiples lotes dispensados del mismo material. El lote de la OP SÍ es inferible
     * vía RA → idEntidadCausante → OrdenProduccion.loteAsignado. Método no usado aún; creado para uso futuro.
     */
    public String getLoteOrdenProduccionByMovimientoId(int movimientoId) {
        Movimiento mov = transaccionAlmacenRepo.findById(movimientoId)
                .orElseThrow(() -> new RuntimeException("Movimiento no encontrado con ID: " + movimientoId));

        TransaccionAlmacen tx = mov.getTransaccionAlmacen();
        if (tx == null || tx.getTipoEntidadCausante() != TransaccionAlmacen.TipoEntidadCausante.RA) {
            throw new RuntimeException("El movimiento " + movimientoId + " no pertenece a un Reporte de Avería (RA)");
        }

        OrdenProduccion op = ordenProduccionRepo.findById(tx.getIdEntidadCausante())
                .orElseThrow(() -> new RuntimeException("Orden de producción no encontrada con ID: " + tx.getIdEntidadCausante()));

        return op.getLoteAsignado();
    }
}
