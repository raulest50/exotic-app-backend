package exotic.app.planta.repo.producto.manufacturing.snapshots;

import exotic.app.planta.model.producto.Producto;
import exotic.app.planta.model.producto.manufacturing.snapshots.ManufacturingVersions;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ManufacturingVersionRepo extends JpaRepository<ManufacturingVersions, Long> {
    Optional<ManufacturingVersions> findTopByProductoOrderByVersionNumberDesc(Producto producto);

    List<ManufacturingVersions> findByProducto_ProductoId(String productoId);
}
