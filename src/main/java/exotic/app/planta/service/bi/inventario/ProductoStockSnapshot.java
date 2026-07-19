package exotic.app.planta.service.bi.inventario;

import exotic.app.planta.model.producto.Producto;

record ProductoStockSnapshot(Producto producto, double stockGeneral) {
}
