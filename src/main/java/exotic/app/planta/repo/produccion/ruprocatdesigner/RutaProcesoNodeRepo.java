package exotic.app.planta.repo.produccion.ruprocatdesigner;

import exotic.app.planta.model.produccion.ruprocatdesigner.RutaProcesoNode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RutaProcesoNodeRepo extends JpaRepository<RutaProcesoNode, Long> {
}
