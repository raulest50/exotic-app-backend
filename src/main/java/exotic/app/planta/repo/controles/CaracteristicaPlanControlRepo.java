package exotic.app.planta.repo.controles;

import exotic.app.planta.model.controles.CaracteristicaPlanControl;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CaracteristicaPlanControlRepo extends JpaRepository<CaracteristicaPlanControl, Long> {
    boolean existsByMagnitud_Id(Long magnitudId);
    boolean existsByUnidad_Id(Long unidadId);
    Optional<CaracteristicaPlanControl> findByLegacyCaracteristica_Id(Long legacyCaracteristicaId);
}
