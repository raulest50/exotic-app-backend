package exotic.app.planta.repo.producto.costos;

import exotic.app.planta.model.producto.costos.ProductoCostoHistorial;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductoCostoHistorialRepo extends JpaRepository<ProductoCostoHistorial, Long> {
    @Query("select coalesce(max(h.version), 0) from ProductoCostoHistorial h where h.productoId = :productoId")
    long findUltimaVersion(@Param("productoId") String productoId);
}
