package exotic.app.planta.repo.produccion;

import exotic.app.planta.model.produccion.EstadoMpsSemanal;
import exotic.app.planta.model.produccion.MasterProductionScheduleSemanal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface MasterProductionScheduleSemanalRepo extends JpaRepository<MasterProductionScheduleSemanal, Integer> {
    Optional<MasterProductionScheduleSemanal> findByWeekStartDate(LocalDate weekStartDate);
    List<MasterProductionScheduleSemanal> findAllByEstadoOrderByWeekStartDateDesc(EstadoMpsSemanal estado);
    List<MasterProductionScheduleSemanal> findAllByOrderByWeekStartDateDesc();
}
