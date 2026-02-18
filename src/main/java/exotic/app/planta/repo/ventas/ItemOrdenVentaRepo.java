package exotic.app.planta.repo.ventas;

import exotic.app.planta.model.ventas.ItemOrdenVenta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ItemOrdenVentaRepo extends JpaRepository<ItemOrdenVenta, Integer> {

    @Modifying
    @Query("UPDATE ItemOrdenVenta i SET i.producto = null WHERE i.producto IS NOT NULL")
    int clearProductoReferences();
}
