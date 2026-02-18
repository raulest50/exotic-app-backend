package exotic.app.planta.repo.producto.manufacturing;

import exotic.app.planta.model.producto.manufacturing.packaging.CasePack;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CasePackRepo extends JpaRepository<CasePack, Long> {
}
