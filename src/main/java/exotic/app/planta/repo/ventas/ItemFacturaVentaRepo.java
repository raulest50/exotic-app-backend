package exotic.app.planta.repo.ventas;

import exotic.app.planta.model.ventas.ItemFacturaVenta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ItemFacturaVentaRepo extends JpaRepository<ItemFacturaVenta, Integer> {

    @Modifying
    @Query("UPDATE ItemFacturaVenta i SET i.producto = null WHERE i.producto IS NOT NULL")
    int clearProductoReferences();

    List<ItemFacturaVenta> findByProducto_ProductoId(String productoId);

    long countByFacturaVenta_FacturaVentaId(int facturaVentaId);
}
