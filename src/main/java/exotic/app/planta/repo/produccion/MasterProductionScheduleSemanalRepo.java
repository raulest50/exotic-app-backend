package exotic.app.planta.repo.produccion;

import exotic.app.planta.model.produccion.MasterProductionScheduleSemanal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface MasterProductionScheduleSemanalRepo extends JpaRepository<MasterProductionScheduleSemanal, Integer> {
    Optional<MasterProductionScheduleSemanal> findByWeekStartDate(LocalDate weekStartDate);
}
