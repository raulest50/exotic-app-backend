package exotic.app.planta.repo.controles;

import exotic.app.planta.model.controles.*;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Collection;
import java.util.Optional;

public interface VersionPlanControlRepo extends JpaRepository<VersionPlanControl, Long> {
    Optional<VersionPlanControl> findByIdAndPlan_Ambito(Long id, AmbitoControl ambito);
    Optional<VersionPlanControl> findByLegacyPlantilla_Id(Long legacyPlantillaId);
    boolean existsByPlan_IdAndLegacyPlantillaIsNull(Long planId);
    Optional<VersionPlanControl> findFirstByPlan_IdAndEstado(Long planId, EstadoVersionPlanControl estado);
    @Query("select coalesce(max(v.numero), 0) from VersionPlanControl v where v.plan.id = :planId")
    int maxNumero(@Param("planId") Long planId);
    List<VersionPlanControl> findByEstado(EstadoVersionPlanControl estado);
    List<VersionPlanControl> findByEstadoIn(Collection<EstadoVersionPlanControl> estados);
}
