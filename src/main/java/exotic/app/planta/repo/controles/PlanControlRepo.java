package exotic.app.planta.repo.controles;

import exotic.app.planta.model.controles.AmbitoControl;
import exotic.app.planta.model.controles.PlanControl;
import exotic.app.planta.model.controles.EstadoVersionPlanControl;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Collection;
import java.util.Optional;
import java.time.LocalDateTime;

public interface PlanControlRepo extends JpaRepository<PlanControl, Long> {
    interface ResumenPlan {
        Long getId();
        String getCodigo();
        String getNombre();
        AmbitoControl getAmbito();
        LocalDateTime getCreadoEn();
    }

    // Scalar projection: pagination must never fetch the versiones collection.
    @Query(value = """
            select p.id as id, p.codigo as codigo, p.nombre as nombre,
                   p.ambito as ambito, p.creadoEn as creadoEn
            from PlanControl p
            where p.ambito = :ambito
              and (:search = '' or locate(:search, lower(p.codigo)) > 0
                   or locate(:search, lower(p.nombre)) > 0)
              and exists (
                   select v.id from VersionPlanControl v where v.plan = p and v.estado in :estados)
            order by p.codigo asc, p.id asc
            """, countQuery = """
            select count(p) from PlanControl p
            where p.ambito = :ambito
              and (:search = '' or locate(:search, lower(p.codigo)) > 0
                   or locate(:search, lower(p.nombre)) > 0)
              and exists (
                   select v.id from VersionPlanControl v where v.plan = p and v.estado in :estados)
            """)
    Page<ResumenPlan> findResumenes(@Param("ambito") AmbitoControl ambito,
                                  @Param("search") String search,
                                  @Param("estados") Collection<EstadoVersionPlanControl> estados, Pageable pageable);

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
