package exotic.app.planta.service.bi.inventario;

import exotic.app.planta.model.inventarios.Movimiento;
import exotic.app.planta.model.producto.Producto;
import exotic.app.planta.repo.inventarios.TransaccionAlmacenRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
class InventarioStockReader {
    private final TransaccionAlmacenRepo movimientoRepo;

    List<ProductoStockSnapshot> readGeneralStock() {
        return movimientoRepo
                .findInventariablesWithStockByAlmacen(Movimiento.Almacen.GENERAL)
                .stream()
                .map(this::toSnapshot)
                .toList();
    }

    private ProductoStockSnapshot toSnapshot(Object[] row) {
        Producto producto = (Producto) row[0];
        double stock = InventarioBiUtils.numberValue(row[1]);
        return new ProductoStockSnapshot(producto, stock);
    }
}
