package exotic.app.planta.repo.producto.manufacturing;

import exotic.app.planta.model.producto.manufacturing.packaging.InsumoEmpaque;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InsumoEmpaqueRepo extends JpaRepository<InsumoEmpaque, Long> {
    List<InsumoEmpaque> findByMaterial_ProductoId(String productoId);
}
