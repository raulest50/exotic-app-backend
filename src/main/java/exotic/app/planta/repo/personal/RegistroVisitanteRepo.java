package exotic.app.planta.repo.personal;

import exotic.app.planta.model.organizacion.personal.RegistroVisitante;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface RegistroVisitanteRepo extends JpaRepository<RegistroVisitante, Long>, JpaSpecificationExecutor<RegistroVisitante> {
}
