package exotic.app.planta.repo.controles;

import exotic.app.planta.model.controles.MagnitudControl;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MagnitudControlRepo extends JpaRepository<MagnitudControl, Long> {
    Optional<MagnitudControl> findByCodigoIgnoreCase(String codigo);
    List<MagnitudControl> findAllByOrderByNombreAsc();
    List<MagnitudControl> findByActivoTrueOrderByNombreAsc();
}
