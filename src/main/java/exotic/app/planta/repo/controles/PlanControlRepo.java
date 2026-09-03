package exotic.app.planta.repo.controles;

import exotic.app.planta.model.controles.AmbitoControl;
import exotic.app.planta.model.controles.PlanControl;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface PlanControlRepo extends JpaRepository<PlanControl, Long> {
    boolean existsByCodigoIgnoreCase(String codigo);
    Optional<PlanControl> findByCodigoIgnoreCase(String codigo);
    @EntityGraph(attributePaths = "versiones")
    List<PlanControl> findByAmbitoOrderByCodigoAsc(AmbitoControl ambito);
    @EntityGraph(attributePaths = "versiones")
    Optional<PlanControl> findByIdAndAmbito(Long id, AmbitoControl ambito);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PlanControl p where p.id = :id and p.ambito = :ambito")
    Optional<PlanControl> findByIdAndAmbitoForUpdate(@Param("id") Long id, @Param("ambito") AmbitoControl ambito);
}
