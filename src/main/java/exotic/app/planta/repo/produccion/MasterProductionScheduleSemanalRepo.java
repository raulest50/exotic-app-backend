package exotic.app.planta.repo.produccion;

import exotic.app.planta.model.produccion.EstadoMpsSemanal;
import exotic.app.planta.model.produccion.MasterProductionScheduleSemanal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface MasterProductionScheduleSemanalRepo extends JpaRepository<MasterProductionScheduleSemanal, Integer> {
    Optional<MasterProductionScheduleSemanal> findByWeekStartDate(LocalDate weekStartDate);
    Optional<MasterProductionScheduleSemanal> findBySemanaMps_Id(Long semanaMpsId);
    List<MasterProductionScheduleSemanal> findAllByWeekStartDateBetween(LocalDate startDate, LocalDate endDate);
    List<MasterProductionScheduleSemanal> findAllByEstadoOrderByWeekStartDateDesc(EstadoMpsSemanal estado);
    List<MasterProductionScheduleSemanal> findAllByOrderByWeekStartDateDesc();

    @Query("""
            SELECT mps
            FROM MasterProductionScheduleSemanal mps
            WHERE mps.weekStartDate <= :fecha
              AND mps.weekEndDate >= :fecha
            ORDER BY mps.revisionNumero DESC, mps.mpsId DESC
            """)
    List<MasterProductionScheduleSemanal> findAllContainingDate(@Param("fecha") LocalDate fecha);
}
