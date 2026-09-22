package exotic.app.planta.service.compras;

import exotic.app.planta.model.compras.ItemOrdenCompra;
import exotic.app.planta.model.compras.OrdenCompraMateriales;
import exotic.app.planta.model.inventarios.Movimiento;
import exotic.app.planta.model.inventarios.TransaccionAlmacen;
import exotic.app.planta.repo.inventarios.TransaccionAlmacenRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RecepcionCompletaOcmService {
    private final TransaccionAlmacenRepo movimientos;

    public boolean estaCompleta(OrdenCompraMateriales orden) {
        return evaluar(List.of(orden)).get(orden.getOrdenCompraId());
    }

    public Map<Integer, Boolean> evaluar(Collection<OrdenCompraMateriales> ordenes) {
        Map<Integer, Boolean> resultado = new HashMap<>();
        if (ordenes.isEmpty()) return resultado;
        List<Integer> ids = ordenes.stream().map(OrdenCompraMateriales::getOrdenCompraId).toList();
        Map<Integer, Map<String, BigDecimal>> recibidas = new HashMap<>();
        for (var fila : movimientos.findReceiptQuantities(TransaccionAlmacen.TipoEntidadCausante.OCM,
                Movimiento.TipoMovimiento.COMPRA, Movimiento.Almacen.GENERAL, ids)) {
            if (!Double.isFinite(fila.getQuantity()) || fila.getQuantity() <= 0 || fila.getProductId() == null) continue;
            recibidas.computeIfAbsent(fila.getEntityId(), key -> new HashMap<>())
                    .merge(fila.getProductId(), BigDecimal.valueOf(fila.getQuantity()), BigDecimal::add);
        }
        for (var orden : ordenes) {
            resultado.put(orden.getOrdenCompraId(), cubreTodosLosMateriales(orden.getItemsOrdenCompra(),
                    recibidas.getOrDefault(orden.getOrdenCompraId(), Map.of())));
        }
        return resultado;
    }

    static boolean cubreTodosLosMateriales(List<ItemOrdenCompra> items, Map<String, BigDecimal> recibidas) {
        if (items == null || items.isEmpty()) return false;
        Map<String, BigDecimal> solicitadas = new HashMap<>();
        for (ItemOrdenCompra item : items) {
            if (item == null || item.getMaterial() == null || item.getMaterial().getProductoId() == null
                    || item.getCantidad() <= 0) return false;
            solicitadas.merge(item.getMaterial().getProductoId(), BigDecimal.valueOf(item.getCantidad()), BigDecimal::add);
        }
        return solicitadas.entrySet().stream().allMatch(entry ->
                recibidas.getOrDefault(entry.getKey(), BigDecimal.ZERO).compareTo(entry.getValue()) >= 0);
    }
}
