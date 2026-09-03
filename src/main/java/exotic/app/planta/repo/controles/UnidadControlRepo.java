package exotic.app.planta.repo.controles;

import exotic.app.planta.model.controles.UnidadControl;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UnidadControlRepo extends JpaRepository<UnidadControl, Long> {
    Optional<UnidadControl> findByCodigoIgnoreCase(String codigo);
    List<UnidadControl> findAllByOrderByNombreAsc();
    List<UnidadControl> findByActivoTrueOrderByNombreAsc();
}
