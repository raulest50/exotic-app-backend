package exotic.app.planta.repo.producto.manufacturing;

import exotic.app.planta.model.producto.manufacturing.packaging.InsumoEmpaque;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InsumoEmpaqueRepo extends JpaRepository<InsumoEmpaque, Long> {
}
