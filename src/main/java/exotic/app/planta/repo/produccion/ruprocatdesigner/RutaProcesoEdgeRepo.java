package exotic.app.planta.repo.produccion.ruprocatdesigner;

import exotic.app.planta.model.produccion.ruprocatdesigner.RutaProcesoEdge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RutaProcesoEdgeRepo extends JpaRepository<RutaProcesoEdge, Long> {
}
