package exotic.app.planta.repo.controles;

import exotic.app.planta.model.controles.RevalidacionControl;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RevalidacionControlRepo extends JpaRepository<RevalidacionControl, Long> {
    boolean existsByControlRequerido_IdAndCicloRevisionNumero(Long requisitoId, Integer ciclo);
    List<RevalidacionControl> findByControlRequerido_IdOrderByIdAsc(Long requisitoId);
}
