package exotic.app.planta.repo.produccion;

import exotic.app.planta.model.produccion.despeje.PlantillaDespeje;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PlantillaDespejeRepo extends JpaRepository<PlantillaDespeje, Long> {
    List<PlantillaDespeje> findByAreaOperativa_AreaIdOrderByIdAsc(Integer areaId);
}
